package cz.dmuzik.dctest;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameData;

/**
 * Test-only helper. /dctest moves players the ways dimension mods do, so DimensionControl can be tested
 * without a game client:
 * transfer = ServerConfigurationManager.transferPlayerToDimension with a mod teleporter (Twilight Forest style),
 * travel = EntityPlayerMP.travelToDimension (items/blocks calling it directly),
 * manual = hand-written transfer that never calls either method (Galacticraft style),
 * runic = travelToDimension followed by an unconditional setPositionAndUpdate(7, 3, 7) (what Runic Dungeons'
 * portal does),
 * endportal = builds a 3x3 End portal over lava, where = prints a player's state to the log,
 * mods = prints the mod list (so a test client can claim the same mods), blocks = registry names matching a filter,
 * place = sets a block without neighbour updates (mod portals would break without their frame),
 * hold = puts an item straight into the held slot (/give drops it as an entity, which some mods interfere with).
 */
@Mod(modid = "dctest", name = "DimensionControl Test Helper", version = "1.0.0", acceptableRemoteVersions = "*")
public class DCTestMod {

    static final Logger LOG = LogManager.getLogger("DCTest");

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBase() {

            @Override
            public String getCommandName() {
                return "dctest";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/dctest <transfer|travel|manual|runic> <player> <dim> | where <player> | endportal <x> <y> <z>";
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 2;
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                if (args.length == 1 && "mods".equals(args[0])) {
                    StringBuilder sb = new StringBuilder();
                    for (ModContainer mod : Loader.instance().getActiveModList()) {
                        sb.append(mod.getModId()).append('=').append(mod.getVersion()).append('|');
                    }
                    LOG.info("DCTEST mods {}", sb);
                    return;
                }
                if (args.length == 2 && "blocks".equals(args[0])) {
                    for (Object key : GameData.getBlockRegistry().getKeys()) {
                        Block block = GameData.getBlockRegistry().getObject((String) key);
                        String cls = block.getClass().getName();
                        if (((String) key).toLowerCase().contains(args[1]) || cls.toLowerCase().contains(args[1])) {
                            LOG.info("DCTEST block {} {}", key, cls);
                        }
                    }
                    return;
                }
                if ((args.length == 5 || args.length == 6) && "place".equals(args[0])) {
                    Block block = GameData.getBlockRegistry().getObject(args[4]);
                    int meta = args.length == 6 ? parseInt(sender, args[5]) : 0;
                    MinecraftServer.getServer().worldServerForDimension(0).setBlock(
                        parseInt(sender, args[1]),
                        parseInt(sender, args[2]),
                        parseInt(sender, args[3]),
                        block,
                        meta,
                        2);
                    LOG.info("DCTEST placed {}:{} at {} {} {}", args[4], meta, args[1], args[2], args[3]);
                    return;
                }
                if ((args.length == 3 || args.length == 4) && "hold".equals(args[0])) {
                    EntityPlayerMP player = getPlayer(sender, args[1]);
                    Item item = GameData.getItemRegistry().getObject(args[2]);
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, new ItemStack(item, args.length == 4 ? parseInt(sender, args[3]) : 1));
                    player.inventoryContainer.detectAndSendChanges();
                    LOG.info("DCTEST hold {} {}", player.getCommandSenderName(), args[2]);
                    return;
                }
                if (args.length == 4 && "endportal".equals(args[0])) {
                    buildEndPortal(
                        sender,
                        parseInt(sender, args[1]),
                        parseInt(sender, args[2]),
                        parseInt(sender, args[3]));
                    return;
                }
                if (args.length < 2) {
                    throw new WrongUsageException(getCommandUsage(sender));
                }
                EntityPlayerMP player = getPlayer(sender, args[1]);
                if ("where".equals(args[0])) {
                    report(player);
                    return;
                }
                if (args.length != 3) {
                    throw new WrongUsageException(getCommandUsage(sender));
                }
                int dim = parseInt(sender, args[2]);
                int before = player.dimension;
                if ("transfer".equals(args[0])) {
                    WorldServer target = MinecraftServer.getServer().worldServerForDimension(dim);
                    MinecraftServer.getServer().getConfigurationManager()
                        .transferPlayerToDimension(player, dim, new ModTeleporter(target));
                } else if ("travel".equals(args[0])) {
                    player.travelToDimension(dim);
                } else if ("runic".equals(args[0])) {
                    player.travelToDimension(dim);
                    player.setPositionAndUpdate(7.0D, 3.0D, 7.0D);
                } else if ("manual".equals(args[0])) {
                    manualTransfer(player, dim);
                } else {
                    throw new WrongUsageException(getCommandUsage(sender));
                }
                LOG.info("DCTEST {} {} {} -> {} (now in {})", args[0], player.getCommandSenderName(), before, dim,
                    player.dimension);
            }
        });
    }

    static void report(EntityPlayerMP p) {
        LOG.info(
            "DCTEST where {} dim={} x={} y={} z={} hp={} portalCooldown={}",
            p.getCommandSenderName(),
            p.dimension,
            String.format("%.2f", p.posX),
            String.format("%.2f", p.posY),
            String.format("%.2f", p.posZ),
            p.getHealth(),
            p.timeUntilPortal);
    }

    /** 3x3 End portal at y with lava right below it, like the stronghold portal room. */
    static void buildEndPortal(ICommandSender sender, int x, int y, int z) {
        WorldServer world = MinecraftServer.getServer().worldServerForDimension(0);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                world.setBlock(x + dx, y - 1, z + dz, inner ? Blocks.lava : Blocks.stonebrick, 0, 2);
                if (inner) {
                    world.setBlock(x + dx, y, z + dz, Blocks.end_portal, 0, 2);
                } else if (!corner) {
                    world.setBlock(x + dx, y, z + dz, Blocks.end_portal_frame, 4, 2);
                }
            }
        }
        sender.addChatMessage(new ChatComponentText("End portal built"));
        LOG.info("DCTEST endportal built at {} {} {}", x, y, z);
    }

    /** Moves the player without ever calling travelToDimension or transferPlayerToDimension. */
    static void manualTransfer(EntityPlayerMP p, int dim) {
        MinecraftServer server = MinecraftServer.getServer();
        ServerConfigurationManager scm = server.getConfigurationManager();
        WorldServer oldWorld = (WorldServer) p.worldObj;
        WorldServer newWorld = server.worldServerForDimension(dim);
        int oldDim = p.dimension;

        p.dimension = dim;
        p.playerNetServerHandler.sendPacket(
            new S07PacketRespawn(
                dim,
                newWorld.difficultySetting,
                newWorld.getWorldInfo().getTerrainType(),
                p.theItemInWorldManager.getGameType()));
        oldWorld.removePlayerEntityDangerously(p);
        p.isDead = false;

        double x = p.posX;
        double z = p.posZ;
        double y = prepareLanding(newWorld, x, z);
        p.setLocationAndAngles(x, y, z, p.rotationYaw, p.rotationPitch);
        newWorld.spawnEntityInWorld(p);
        newWorld.updateEntityWithOptionalForce(p, false);
        p.setWorld(newWorld);
        scm.func_72375_a(p, oldWorld);
        p.playerNetServerHandler.setPlayerLocation(x, y, z, p.rotationYaw, p.rotationPitch);
        p.theItemInWorldManager.setWorld(newWorld);
        scm.updateTimeAndWeatherForPlayer(p, newWorld);
        scm.syncPlayerInventory(p);
        FMLCommonHandler.instance().firePlayerChangedDimensionEvent(p, oldDim, dim);
    }

    /** Small obsidian platform with an air pocket at y=100, so tests do not depend on terrain. */
    static double prepareLanding(WorldServer world, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int y = 100;
        world.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(bx + dx, y - 1, bz + dz, Blocks.obsidian);
                world.setBlockToAir(bx + dx, y, bz + dz);
                world.setBlockToAir(bx + dx, y + 1, bz + dz);
            }
        }
        return y;
    }

    /** A mod-style teleporter: own placement logic, no vanilla portal search or creation. */
    static class ModTeleporter extends Teleporter {

        private final WorldServer world;

        ModTeleporter(WorldServer world) {
            super(world);
            this.world = world;
        }

        @Override
        public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
            double landY = prepareLanding(world, entity.posX, entity.posZ);
            entity.setLocationAndAngles(entity.posX, landY, entity.posZ, entity.rotationYaw, entity.rotationPitch);
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
