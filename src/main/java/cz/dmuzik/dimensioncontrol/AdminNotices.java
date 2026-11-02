package cz.dmuzik.dimensioncontrol;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import cz.dmuzik.dimensioncontrol.asm.HookStatus;

/** Makes setup mistakes visible in chat instead of only in the server log. */
public final class AdminNotices {

    private AdminNotices() {}

    public static boolean hooksActive() {
        return HookStatus.travelToDimensionPatched && HookStatus.transferPlayerToDimensionPatched;
    }

    /** Operators get the list of problems when they log in, so a broken config does not go unnoticed. */
    public static void onLogin(EntityPlayerMP player) {
        if (DCConfig.showAdminNotices && AccessManager.isOp(player)
            && (!DCConfig.getProblems().isEmpty() || !hooksActive())) {
            send(player, "§6[DimensionControl] §eNastavení má problémy:");
            sendProblems(player);
        }
    }

    /** Hook status (only when something is wrong) and config problems; returns how many lines were sent. */
    public static int sendProblems(ICommandSender sender) {
        int lines = 0;
        if (!hooksActive()) {
            send(
                sender,
                "§c- Hlavní kontrola není aktivní (coremod se nenačetl), funguje jen záložní kontrola. "
                    + "Je jar přímo ve složce mods a nerozbalený?");
            lines++;
        }
        List<String> problems = DCConfig.getProblems();
        for (String problem : problems) {
            send(sender, "§c- " + problem);
            lines++;
        }
        return lines;
    }

    static void send(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
