package cz.dmuzik.dimensioncontrol;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Achievement;
import net.minecraftforge.event.entity.player.AchievementEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Mods award their "you entered the dimension" achievements around the teleport without checking that it happened,
 * and the Aether hands out gifts on its achievement. A blocked player must get none of that.
 * <p>
 * Awards after the blocked teleport are simply cancelled. Awards before it (Twilight Forest, Glacia) cannot know yet,
 * so a new achievement earned while standing in a portal waits for the end of the tick: dropped if the player was
 * blocked in that tick, handed out in the original order if not. The mods award it again on the next attempt.
 */
public class AchievementGuard {

    private static final List<Pending> pending = new ArrayList<Pending>();
    private static boolean releasing;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAchievement(AchievementEvent event) {
        if (releasing || !(event.entityPlayer instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        if (!AccessManager.isRealPlayer(player)) {
            return;
        }
        if (PlayerTracker.wasBlockedThisTick(player)) {
            event.setCanceled(true);
        } else if (!player.func_147099_x().hasAchievementUnlocked(event.achievement)
            && (hasPending(player) || PlayerTracker.isInPortal(player))) {
                // Once one waits, the later ones must too: they may need it as their parent achievement
                pending.add(new Pending(player, event.achievement));
                event.setCanceled(true);
            }
    }

    /** Has to run before PlayerTracker forgets who was blocked in this tick. */
    static void endOfTick(MinecraftServer server) {
        if (pending.isEmpty()) {
            return;
        }
        List<Pending> due = new ArrayList<Pending>(pending);
        pending.clear();
        releasing = true;
        try {
            for (Pending p : due) {
                boolean online = server.getConfigurationManager().playerEntityList.contains(p.player);
                if (online && !PlayerTracker.wasBlockedThisTick(p.player)) {
                    p.player.triggerAchievement(p.achievement);
                }
            }
        } finally {
            releasing = false;
        }
    }

    static void clear() {
        pending.clear();
    }

    private static boolean hasPending(EntityPlayerMP player) {
        for (Pending p : pending) {
            if (p.player == player) {
                return true;
            }
        }
        return false;
    }

    private static final class Pending {

        final EntityPlayerMP player;
        final Achievement achievement;

        Pending(EntityPlayerMP player, Achievement achievement) {
            this.player = player;
            this.achievement = achievement;
        }
    }
}
