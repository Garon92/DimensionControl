package cz.dmuzik.dimensioncontrol.asm;

/** Records which hooks were injected, so the mod can report it at server start. */
public final class HookStatus {

    public static volatile boolean travelToDimensionPatched;
    public static volatile boolean transferPlayerToDimensionPatched;

    private HookStatus() {}
}
