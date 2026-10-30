package cz.dmuzik.dimensioncontrol;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

/** Decides whether a player may enter a dimension. All calls happen on the server thread. */
public final class AccessManager {

    public enum Decision {
        UNRESTRICTED,
        BYPASS,
        UNLOCKED,
        HOLDING_ITEM,
        DENIED
    }

    private static final Map<UUID, Integer> lastMessageTick = new HashMap<UUID, Integer>();
    private static final Map<UUID, Integer> lastBypassNoticeTick = new HashMap<UUID, Integer>();
    private static int bypassDepth;

    private AccessManager() {}

    /**
     * Called by the hooks before a transfer starts. Returning false cancels it.
     */
    public static boolean allowTransfer(EntityPlayerMP player, int targetDim) {
        if (bypassDepth > 0 || !isRealPlayer(player) || player.dimension == targetDim) {
            return true;
        }
        Decision decision = decide(player, targetDim);
        if (decision == Decision.DENIED) {
            onDenied(player, DCConfig.getRule(targetDim), true);
            return false;
        }
        if (decision == Decision.BYPASS) {
            onBypass(player, DCConfig.getRule(targetDim));
        }
        PlayerTracker.approve(player, targetDim);
        return true;
    }

    /**
     * Called by the fallback check when a player showed up in another dimension without passing the hooks.
     * Returning false means the player has to be sent back.
     */
    public static boolean allowArrival(EntityPlayerMP player, int dimension) {
        Decision decision = decide(player, dimension);
        if (decision == Decision.DENIED) {
            onDenied(player, DCConfig.getRule(dimension), false);
            return false;
        }
        if (decision == Decision.BYPASS) {
            onBypass(player, DCConfig.getRule(dimension));
        }
        return true;
    }

    /** Evaluates access without side effects (used by /dimaccess check). */
    public static Decision evaluate(EntityPlayerMP player, int dimension) {
        DimensionRule rule = DCConfig.getRule(dimension);
        if (rule == null) {
            return Decision.UNRESTRICTED;
        }
        if (bypassReason(player) != null) {
            return Decision.BYPASS;
        }
        if (rule.unlockAfterFirstEntry && UnlockData.isUnlocked(player.getUniqueID(), dimension)) {
            return Decision.UNLOCKED;
        }
        return rule.matches(player.getCurrentEquippedItem()) ? Decision.HOLDING_ITEM : Decision.DENIED;
    }

    /** Evaluates access and, when the item check passes for an unlockable rule, grants the permanent unlock. */
    public static Decision decide(EntityPlayerMP player, int dimension) {
        Decision decision = evaluate(player, dimension);
        if (decision != Decision.HOLDING_ITEM) {
            return decision;
        }
        DimensionRule rule = DCConfig.getRule(dimension);
        if (rule.unlockAfterFirstEntry && UnlockData.unlock(player.getUniqueID(), dimension)) {
            DimensionControl.LOG
                .info("{} unlocked dimension {} ({})", player.getCommandSenderName(), dimension, rule.dimensionName);
            if (!DCConfig.unlockMessage.isEmpty()) {
                player.addChatMessage(Messages.format(DCConfig.unlockMessage, rule, player));
            }
        }
        return Decision.HOLDING_ITEM;
    }

    /** Why the player skips the checks, or null when they do not. */
    public static String bypassReason(EntityPlayerMP player) {
        if (DCConfig.creativeBypass && player.capabilities.isCreativeMode) {
            return "creative (creativeBypass=true)";
        }
        if (DCConfig.opsBypass && isOp(player)) {
            return "OP (opsBypass=true)";
        }
        return null;
    }

    public static boolean isOp(EntityPlayerMP player) {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.getConfigurationManager() != null
            && server.getConfigurationManager().func_152596_g(player.getGameProfile());
    }

    public static boolean isRealPlayer(EntityPlayerMP player) {
        return player != null && !(player instanceof FakePlayer) && player.playerNetServerHandler != null
            && player.worldObj != null && !player.worldObj.isRemote;
    }

    /** Runs a transfer of our own (sending a player back) without it being checked again. */
    public static void runUnchecked(Runnable action) {
        bypassDepth++;
        try {
            action.run();
        } finally {
            bypassDepth--;
        }
    }

    public static void forget(UUID id) {
        lastMessageTick.remove(id);
        lastBypassNoticeTick.remove(id);
    }

    public static void clear() {
        lastMessageTick.clear();
        lastBypassNoticeTick.clear();
    }

    /** Tells the player they only got in because of a bypass, so an admin testing the mod is not misled. */
    private static void onBypass(EntityPlayerMP player, DimensionRule rule) {
        int now = MinecraftServer.getServer().getTickCounter();
        Integer last = lastBypassNoticeTick.get(player.getUniqueID());
        if (last != null && now - last < DCConfig.messageCooldownTicks && now >= last) {
            return;
        }
        lastBypassNoticeTick.put(player.getUniqueID(), now);
        player.addChatMessage(
            Messages.format(
                "&7[DimensionControl] Do &e%dim% &7tě pouští jen obchvat pro " + bypassReason(player)
                    + ". Ostatní hráči potřebují &e%item%&7.",
                rule,
                player));
    }

    private static void onDenied(EntityPlayerMP player, DimensionRule rule, boolean beforeTransfer) {
        int now = MinecraftServer.getServer().getTickCounter();
        UUID id = player.getUniqueID();
        Integer last = lastMessageTick.get(id);
        if (!rule.message.isEmpty() && (last == null || now - last >= DCConfig.messageCooldownTicks || now < last)) {
            lastMessageTick.put(id, now);
            player.addChatMessage(Messages.format(rule.message, rule, player));
        }
        if (DCConfig.logBlocked) {
            DimensionControl.LOG.info(
                "Blocked {} from entering dimension {} ({}){}",
                player.getCommandSenderName(),
                rule.dimensionId,
                rule.dimensionName,
                beforeTransfer ? "" : " - sent back by the fallback check");
        }
        player.timeUntilPortal = Math.max(player.timeUntilPortal, DCConfig.portalCooldownTicks);
        if (beforeTransfer) {
            PlayerTracker.onBlocked(player);
        }
    }
}
