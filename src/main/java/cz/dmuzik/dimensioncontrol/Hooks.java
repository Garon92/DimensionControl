package cz.dmuzik.dimensioncontrol;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Called from code injected by {@link cz.dmuzik.dimensioncontrol.asm.DCTransformer}.
 * Returning false cancels the dimension change.
 */
public final class Hooks {

    private static boolean errorLogged;

    private Hooks() {}

    public static boolean onTravelToDimension(EntityPlayerMP player, int dimension) {
        return safeCheck(player, dimension);
    }

    public static boolean onTransferPlayerToDimension(EntityPlayerMP player, int dimension) {
        return safeCheck(player, dimension);
    }

    private static boolean safeCheck(EntityPlayerMP player, int dimension) {
        try {
            return AccessManager.allowTransfer(player, dimension);
        } catch (Throwable t) {
            // Never break teleporting for the whole server because of a bug in this mod
            if (!errorLogged) {
                errorLogged = true;
                DimensionControl.LOG.error("Dimension access check failed, letting the transfer through", t);
            }
            return true;
        }
    }
}
