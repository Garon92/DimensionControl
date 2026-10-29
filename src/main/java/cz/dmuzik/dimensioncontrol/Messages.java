package cz.dmuzik.dimensioncontrol;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Builds chat messages from config templates. &-codes become formatting, %item% becomes a translation
 * component so every client sees the item name in its own language.
 */
public final class Messages {

    private static final String ITEM = "%item%";

    private Messages() {}

    public static IChatComponent format(String template, DimensionRule rule, EntityPlayer player) {
        String text = colorize(template).replace("%dim%", rule.dimensionName)
            .replace("%player%", player.getCommandSenderName());

        IChatComponent root = new ChatComponentText("");
        String activeCodes = "";
        int start = 0;
        int index;
        while ((index = text.indexOf(ITEM, start)) >= 0) {
            String part = text.substring(start, index);
            root.appendSibling(new ChatComponentText(activeCodes + part));
            activeCodes = trackCodes(activeCodes, part);
            root.appendSibling(itemComponent(rule, activeCodes));
            start = index + ITEM.length();
        }
        root.appendSibling(new ChatComponentText(activeCodes + text.substring(start)));
        return root;
    }

    public static String colorize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(text.charAt(i + 1)) >= 0) {
                out.append('§').append(Character.toLowerCase(text.charAt(i + 1)));
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static IChatComponent itemComponent(DimensionRule rule, String activeCodes) {
        IChatComponent component;
        ItemStack stack = rule.displayStack();
        if (rule.itemDisplayName != null && !rule.itemDisplayName.isEmpty()) {
            component = new ChatComponentText(colorize(rule.itemDisplayName));
        } else if (stack != null) {
            component = new ChatComponentTranslation(stack.getUnlocalizedName() + ".name");
        } else {
            component = new ChatComponentText(rule.itemId);
        }
        component.setChatStyle(styleOf(activeCodes));
        return component;
    }

    /** Formatting codes still active at the end of {@code text}, starting from {@code codes}. */
    private static String trackCodes(String codes, String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '§') {
                continue;
            }
            char code = text.charAt(i + 1);
            if (code == 'r') {
                codes = "";
            } else if ("0123456789abcdef".indexOf(code) >= 0) {
                // A colour resets all formatting before it
                codes = "§" + code;
            } else if ("klmno".indexOf(code) >= 0) {
                codes = codes + "§" + code;
            }
            i++;
        }
        return codes;
    }

    private static ChatStyle styleOf(String codes) {
        ChatStyle style = new ChatStyle();
        for (int i = 0; i + 1 < codes.length(); i += 2) {
            String code = codes.substring(i, i + 2);
            for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
                if (!formatting.toString().equals(code)) {
                    continue;
                }
                if (formatting.isColor()) {
                    style.setColor(formatting);
                } else if (formatting == EnumChatFormatting.BOLD) {
                    style.setBold(true);
                } else if (formatting == EnumChatFormatting.ITALIC) {
                    style.setItalic(true);
                } else if (formatting == EnumChatFormatting.UNDERLINE) {
                    style.setUnderlined(true);
                } else if (formatting == EnumChatFormatting.STRIKETHROUGH) {
                    style.setStrikethrough(true);
                } else if (formatting == EnumChatFormatting.OBFUSCATED) {
                    style.setObfuscated(true);
                }
            }
        }
        return style;
    }
}
