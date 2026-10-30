package cz.dmuzik.dimensioncontrol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEndPortal;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Per-player bookkeeping done once per server tick:
 * <ul>
 * <li>remembers the last position and the last safe spot (on the ground, outside any portal),</li>
 * <li>pushes players that were just blocked out of the portal they stand in, and puts them back where they
 * stood if the mod whose transfer was cancelled moved them anyway (Runic Dungeons does that),</li>
 * <li>fallback: notices dimension changes that did not go through the hooks (mods with a hand-written
 * transfer) and sends the player back if they are not allowed in.</li>
 * </ul>
 */
public class PlayerTracker {

    private static final int NONE = Integer.MIN_VALUE;
    /** How long a hook approval stays valid for the fallback check. */
    private static final int APPROVAL_TICKS = 20;
    private static final int SAFE_SPOT_MAX_AGE = 30 * 20;
    private static final double SAFE_SPOT_MAX_DISTANCE_SQ = 16 * 16;
    private static final int LOOP_WINDOW_TICKS = 10 * 20;
    private static final int LOOP_WARN_COUNT = 5;

    /** Further than this from where a transfer was blocked means something moved the player anyway. */
    private static final double MOVED_AFTER_BLOCK_SQ = 1.5 * 1.5;

    private static final Map<UUID, Track> tracks = new HashMap<UUID, Track>();
    /** Where players stood when a transfer was blocked during the current tick. */
    private static final Map<UUID, Pos> blockedAt = new HashMap<UUID, Pos>();

    public static void approve(EntityPlayerMP player, int dimension) {
        Track track = tracks.get(player.getUniqueID());
        if (track != null) {
            track.approvedDim = dimension;
            track.approvedTick = MinecraftServer.getServer().getTickCounter();
        }
    }

    /**
     * Remembers where the player stood when a transfer was blocked. The follow-up (push back, position restore)
     * happens at the end of the tick, outside of the packet handling or block code that triggered the transfer.
     */
    public static void onBlocked(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        if (!blockedAt.containsKey(id)) {
            blockedAt.put(id, new Pos(player, MinecraftServer.getServer().getTickCounter()));
        }
    }

    public static void clear() {
        tracks.clear();
        blockedAt.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        int now = server.getTickCounter();
        List<?> players = new ArrayList<Object>(server.getConfigurationManager().playerEntityList);

        if (!blockedAt.isEmpty()) {
            for (Object o : players) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                Pos pos = blockedAt.get(player.getUniqueID());
                if (pos != null) {
                    afterBlocked(player, pos, tracks.get(player.getUniqueID()), now);
                }
            }
            blockedAt.clear();
        }

        for (Object o : players) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            if (!AccessManager.isRealPlayer(player)) {
                continue;
            }
            Track track = tracks.get(player.getUniqueID());
            if (track == null) {
                tracks.put(player.getUniqueID(), new Track(player, now));
                continue;
            }
            if (track.player != player) {
                // Respawn creates a new player object. After a death that is a normal respawn; a living player
                // getting a new object means something used the respawn code to move them (e.g. leaving the End).
                boolean died = track.player.getHealth() <= 0.0F;
                track.player = player;
                if (died) {
                    track.reset(player, now);
                    continue;
                }
            }
            if (player.dimension != track.dim && handleDimensionChange(server, player, track, now)) {
                continue;
            }
            track.record(player, now);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        tracks.remove(event.player.getUniqueID());
        blockedAt.remove(event.player.getUniqueID());
        AccessManager.forget(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // Wherever the player logged out is where they are allowed to be now
        tracks.remove(event.player.getUniqueID());
        if (event.player instanceof EntityPlayerMP) {
            AdminNotices.onLogin((EntityPlayerMP) event.player);
        }
    }

    /** Returns true when the player was sent back. */
    private static boolean handleDimensionChange(MinecraftServer server, EntityPlayerMP player, Track track,
        int now) {
        int to = player.dimension;
        boolean approved = track.approvedDim == to && now >= track.approvedTick
            && now - track.approvedTick <= APPROVAL_TICKS;
        track.approvedDim = NONE;
        if (approved || !DCConfig.fallbackCheck || AccessManager.allowArrival(player, to)) {
            track.dim = to;
            return false;
        }
        sendBack(server, player, track, now);
        return true;
    }

    private static void sendBack(MinecraftServer server, final EntityPlayerMP player, Track track, int now) {
        final int from = track.dim;
        final Pos target = track.safe != null && track.safe.dim == from ? track.safe : track.last;
        final WorldServer world = server.worldServerForDimension(from);
        final boolean leavingEnd = player.dimension == 1;
        final MinecraftServer srv = server;

        DimensionControl.LOG.info(
            "Fallback check: {} got into dimension {} without access, sending back to dimension {} ({}, {}, {})",
            player.getCommandSenderName(),
            player.dimension,
            from,
            (int) target.x,
            (int) target.y,
            (int) target.z);

        AccessManager.runUnchecked(new Runnable() {

            @Override
            public void run() {
                srv.getConfigurationManager()
                    .transferPlayerToDimension(player, from, new FixedTeleporter(world, target));
            }
        });
        // Vanilla does not add a player leaving the End to the new world when it is moved this way
        if (leavingEnd && !world.loadedEntityList.contains(player)) {
            world.spawnEntityInWorld(player);
            world.updateEntityWithOptionalForce(player, false);
        }
        player.playerNetServerHandler.setPlayerLocation(target.x, target.y, target.z, target.yaw, target.pitch);
        player.fallDistance = 0.0F;
        player.timeUntilPortal = Math.max(player.timeUntilPortal, DCConfig.portalCooldownTicks);

        track.dim = player.dimension;
        track.record(player, now);

        if (now - track.loopWindowStart > LOOP_WINDOW_TICKS) {
            track.loopWindowStart = now;
            track.loopCount = 0;
        }
        if (++track.loopCount == LOOP_WARN_COUNT) {
            DimensionControl.LOG.warn(
                "{} was sent back {} times in {} s - some mod keeps moving them without going through the hooks",
                player.getCommandSenderName(),
                LOOP_WARN_COUNT,
                LOOP_WINDOW_TICKS / 20);
        }
    }

    private static void afterBlocked(EntityPlayerMP player, Pos pos, Track track, int now) {
        if (player.dimension != pos.dim) {
            return;
        }
        if (player.getDistanceSq(pos.x, pos.y, pos.z) > MOVED_AFTER_BLOCK_SQ) {
            // The mod whose transfer was cancelled moved the player anyway, e.g. to the destination coordinates
            DimensionControl.LOG.info(
                "{} was moved after a blocked transfer, putting them back to {}, {}, {}",
                player.getCommandSenderName(),
                (int) pos.x,
                (int) pos.y,
                (int) pos.z);
            player.playerNetServerHandler.setPlayerLocation(pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
            player.fallDistance = 0.0F;
        }
        if (DCConfig.pushBackFromPortal && isInPortal(player)) {
            pushBack(player, track, now);
        }
    }

    private static void pushBack(EntityPlayerMP player, Track track, int now) {
        Pos safe = track == null ? null : track.safe;
        if (safe != null && safe.dim == player.dimension
            && now - safe.tick <= SAFE_SPOT_MAX_AGE
            && player.getDistanceSq(safe.x, safe.y, safe.z) <= SAFE_SPOT_MAX_DISTANCE_SQ) {
            player.playerNetServerHandler
                .setPlayerLocation(safe.x, safe.y, safe.z, player.rotationYaw, player.rotationPitch);
            player.fallDistance = 0.0F;
            return;
        }
        // No known safe spot: knock the player backwards, away from where they are looking
        double yaw = Math.toRadians(player.rotationYaw);
        player.motionX = Math.sin(yaw) * 0.6D;
        player.motionY = 0.35D;
        player.motionZ = -Math.cos(yaw) * 0.6D;
        player.playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(player));
    }

    static boolean isInPortal(Entity entity) {
        AxisAlignedBB box = entity.boundingBox;
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX);
        int minY = MathHelper.floor_double(box.minY);
        int maxY = MathHelper.floor_double(box.maxY);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isPortalBlock(entity.worldObj.getBlock(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isPortalBlock(Block block) {
        return block instanceof BlockPortal || block instanceof BlockEndPortal
            || block.getMaterial() == Material.portal
            || block.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("portal");
    }

    private static final class Pos {

        final int dim;
        final double x, y, z;
        final float yaw, pitch;
        final int tick;

        Pos(EntityPlayerMP player, int tick) {
            this.dim = player.dimension;
            this.x = player.posX;
            this.y = player.posY;
            this.z = player.posZ;
            this.yaw = player.rotationYaw;
            this.pitch = player.rotationPitch;
            this.tick = tick;
        }
    }

    private static final class Track {

        EntityPlayerMP player;
        int dim;
        Pos last;
        Pos safe;
        int approvedDim = NONE;
        int approvedTick;
        int loopWindowStart;
        int loopCount;

        Track(EntityPlayerMP player, int now) {
            reset(player, now);
        }

        void reset(EntityPlayerMP player, int now) {
            this.player = player;
            this.dim = player.dimension;
            this.safe = null;
            this.approvedDim = NONE;
            record(player, now);
        }

        void record(EntityPlayerMP player, int now) {
            last = new Pos(player, now);
            if (player.onGround && player.ridingEntity == null && !isInPortal(player)) {
                safe = last;
            }
        }
    }

    /** Puts the player exactly at a remembered position without looking for or building a portal. */
    private static final class FixedTeleporter extends Teleporter {

        private final Pos target;

        FixedTeleporter(WorldServer world, Pos target) {
            super(world);
            this.target = target;
        }

        @Override
        public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
            entity.setLocationAndAngles(target.x, target.y, target.z, target.yaw, target.pitch);
            entity.motionX = entity.motionY = entity.motionZ = 0.0D;
        }

        @Override
        public boolean placeInExistingPortal(Entity entity, double x, double y, double z, float yaw) {
            placeInPortal(entity, x, y, z, yaw);
            return true;
        }

        @Override
        public boolean makePortal(Entity entity) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long time) {}
    }
}
