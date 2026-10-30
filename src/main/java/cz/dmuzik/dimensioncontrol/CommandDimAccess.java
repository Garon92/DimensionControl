package cz.dmuzik.dimensioncontrol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.registry.GameData;

/**
 * /dimaccess rules | dims | check &lt;player&gt; [dim] | list &lt;player&gt; | grant &lt;player&gt; &lt;dim&gt; | revoke &lt;player&gt; &lt;dim|all&gt; | reload
 */
public class CommandDimAccess extends CommandBase {

    private static final String USAGE = "/dimaccess <rules | dims | check <hráč> [dim] | list <hráč> | grant <hráč> <dim> | revoke <hráč> <dim|all> | reload>";

    @Override
    public String getCommandName() {
        return "dimaccess";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            throw new WrongUsageException(USAGE);
        }
        String sub = args[0].toLowerCase();
        if ("rules".equals(sub)) {
            listRules(sender);
        } else if ("dims".equals(sub)) {
            listDimensions(sender);
        } else if ("reload".equals(sub)) {
            DCConfig.reload();
            send(sender, "§aConfig znovu načten, pravidel: " + DCConfig.getRules().size());
            AdminNotices.sendProblems(sender);
        } else if ("check".equals(sub) && (args.length == 2 || args.length == 3)) {
            check(sender, getPlayer(sender, args[1]), args.length == 3 ? parseInt(sender, args[2]) : null);
        } else if ("list".equals(sub) && args.length == 2) {
            GameProfile profile = profile(args[1]);
            Set<Integer> dims = UnlockData.getUnlocked(profile.getId());
            send(sender, "§e" + profile.getName() + "§r má odemčeno: " + (dims.isEmpty() ? "nic" : describe(dims)));
        } else if ("grant".equals(sub) && args.length == 3) {
            GameProfile profile = profile(args[1]);
            int dim = parseInt(sender, args[2]);
            boolean changed = UnlockData.unlock(profile.getId(), dim);
            send(sender, changed ? "§aDimenze " + describe(dim) + " odemčena pro " + profile.getName()
                : "§e" + profile.getName() + " už má dimenzi " + describe(dim) + " odemčenou");
        } else if ("revoke".equals(sub) && args.length == 3) {
            GameProfile profile = profile(args[1]);
            if ("all".equalsIgnoreCase(args[2])) {
                int count = UnlockData.revokeAll(profile.getId());
                send(sender, "§aHráči " + profile.getName() + " odebráno odemčení: " + count);
            } else {
                int dim = parseInt(sender, args[2]);
                boolean changed = UnlockData.revoke(profile.getId(), dim);
                send(sender, changed ? "§aHráči " + profile.getName() + " odebráno odemčení dimenze " + describe(dim)
                    : "§e" + profile.getName() + " dimenzi " + describe(dim) + " odemčenou neměl");
            }
        } else {
            throw new WrongUsageException(USAGE);
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "rules", "dims", "check", "list", "grant", "revoke", "reload");
        }
        String sub = args[0].toLowerCase();
        boolean takesPlayer = "list".equals(sub) || "grant".equals(sub) || "revoke".equals(sub) || "check".equals(sub);
        if (args.length == 2 && takesPlayer) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 3 && ("grant".equals(sub) || "revoke".equals(sub) || "check".equals(sub))) {
            List<String> options = new ArrayList<String>();
            for (int dim : DCConfig.getRules().keySet()) {
                options.add(String.valueOf(dim));
            }
            if ("revoke".equals(sub)) {
                options.add("all");
            }
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
        }
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 1;
    }

    private static void listRules(ICommandSender sender) {
        send(
            sender,
            "§7Hlavní kontrola: " + (AdminNotices.hooksActive() ? "§aaktivní" : "§cNEAKTIVNÍ")
                + "§7, OP obchvat: " + (DCConfig.opsBypass ? "§eano" : "ne")
                + "§7, creative obchvat: " + (DCConfig.creativeBypass ? "§eano" : "ne"));
        AdminNotices.sendProblems(sender);
        if (DCConfig.getRules().isEmpty()) {
            send(sender, "§eŽádná dimenze není omezená.");
            return;
        }
        for (DimensionRule rule : DCConfig.getRules().values()) {
            send(
                sender,
                "§e" + rule.dimensionId + " §r(" + rule.dimensionName + "): " + rule.describeItem()
                    + (rule.getItem() == null ? " §c[neznámý předmět]§r" : "")
                    + ", trvalé odemčení: " + (rule.unlockAfterFirstEntry ? "ano" : "ne"));
        }
        if (DCConfig.getRule(0) == null && DCConfig.getRules().containsKey(0)) {
            send(sender, "§7Pravidlo pro Overworld se ignoruje (alwaysAllowOverworld=true).");
        }
    }

    /** Shows, without changing anything, whether the player would get into each restricted dimension and why. */
    private static void check(ICommandSender sender, EntityPlayerMP player, Integer onlyDim) {
        ItemStack held = player.getCurrentEquippedItem();
        String heldText = held == null ? "nic"
            : GameData.getItemRegistry().getNameForObject(held.getItem()) + " (meta " + held.getItemDamage() + ")";
        send(sender, "§eHráč " + player.getCommandSenderName() + "§r drží: " + heldText);
        if (onlyDim != null && DCConfig.getRule(onlyDim) == null) {
            send(sender, "§e" + onlyDim + "§r: §aPOVOLENO§r - dimenze není omezená");
            return;
        }
        for (DimensionRule rule : DCConfig.getRules().values()) {
            if (onlyDim != null && rule.dimensionId != onlyDim) {
                continue;
            }
            String head = "§e" + rule.dimensionId + "§r (" + rule.dimensionName + "): ";
            switch (AccessManager.evaluate(player, rule.dimensionId)) {
                case UNRESTRICTED:
                    send(sender, head + "§aPOVOLENO§r - neomezeno (alwaysAllowOverworld=true)");
                    break;
                case BYPASS:
                    send(sender, head + "§aPOVOLENO§r - obchvat: " + AccessManager.bypassReason(player));
                    break;
                case UNLOCKED:
                    send(sender, head + "§aPOVOLENO§r - trvale odemčeno");
                    break;
                case HOLDING_ITEM:
                    send(sender, head + "§aPOVOLENO§r - drží požadovaný předmět");
                    break;
                default:
                    send(
                        sender,
                        head + "§cZAMÍTNUTO§r - potřebuje " + rule.describeItem()
                            + (rule.getItem() == null ? " §c(ten předmět neexistuje, oprav config)" : ""));
            }
        }
    }

    /** All registered dimensions with their names, to find the IDs mods use (some pick them at startup). */
    private static void listDimensions(ICommandSender sender) {
        Integer[] ids = DimensionManager.getStaticDimensionIDs();
        Arrays.sort(ids);
        for (int id : ids) {
            String name;
            WorldServer world = DimensionManager.getWorld(id);
            try {
                name = world != null ? world.provider.getDimensionName()
                    : DimensionManager.createProviderFor(id).getDimensionName();
            } catch (Throwable t) {
                name = "?";
            }
            DimensionRule rule = DCConfig.getRule(id);
            send(sender, "§e" + id + "§r " + name + (rule == null ? " §7(bez omezení)" : " §c→ " + rule.describeItem()));
        }
    }

    /** Online players first, then the server's name cache so offline players can be managed too. */
    private static GameProfile profile(String name) {
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP online = server.getConfigurationManager().func_152612_a(name);
        if (online != null) {
            return online.getGameProfile();
        }
        GameProfile cached = server.func_152358_ax().func_152655_a(name);
        if (cached == null || cached.getId() == null) {
            throw new CommandException("Hráč " + name + " nebyl nalezen (nikdy nebyl na serveru?)");
        }
        return cached;
    }

    private static String describe(Set<Integer> dims) {
        StringBuilder sb = new StringBuilder();
        for (int dim : dims) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(describe(dim));
        }
        return sb.toString();
    }

    private static String describe(int dim) {
        DimensionRule rule = DCConfig.getRules().get(dim);
        return rule == null ? String.valueOf(dim) : dim + " (" + rule.dimensionName + ")";
    }

    private static void send(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
