package cz.dmuzik.dimensioncontrol;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ServerConfigurationManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cz.dmuzik.dimensioncontrol.asm.HookStatus;

@Mod(
    modid = DimensionControl.MODID,
    name = "Dimension Control",
    version = DimensionControl.VERSION,
    acceptableRemoteVersions = "*")
public class DimensionControl {

    public static final String MODID = "dimensioncontrol";
    public static final String VERSION = "1.1.0";
    public static final Logger LOG = LogManager.getLogger("DimensionControl");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        DCConfig.init(event.getSuggestedConfigurationFile());
        FMLCommonHandler.instance().bus().register(new PlayerTracker());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandDimAccess());

        // Make sure both patched classes are loaded so the status below is accurate
        LOG.debug("Hooked classes: {}, {}", EntityPlayerMP.class.getName(), ServerConfigurationManager.class.getName());
        DCConfig.validate();
        if (AdminNotices.hooksActive()) {
            LOG.info("Dimension access hooks are active");
        } else {
            LOG.error(
                "Dimension access hooks missing (travelToDimension: {}, transferPlayerToDimension: {}) - "
                    + "is the jar in the mods folder and not unpacked? Only the fallback check will work.",
                HookStatus.travelToDimensionPatched,
                HookStatus.transferPlayerToDimensionPatched);
        }
        for (DimensionRule rule : DCConfig.getRules().values()) {
            LOG.info(
                "Dimension {} ({}) requires {}{}{}",
                rule.dimensionId,
                rule.dimensionName,
                rule.describeItem(),
                rule.unlockAfterFirstEntry ? ", unlocked permanently after first entry" : "",
                rule.consumeItem ? ", item consumed on entry" : "");
        }
        if (DCConfig.opsBypass) {
            LOG.info("opsBypass=true: operators are not checked");
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        UnlockData.clearCache();
        PlayerTracker.clear();
        AccessManager.clear();
    }
}
