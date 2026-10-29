package cz.dmuzik.dimensioncontrol;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameData;

/** Access requirement for one dimension, loaded from a config category. */
public class DimensionRule {

    public static final int ANY_META = -1;

    public final String key;
    public final int dimensionId;
    public final String dimensionName;
    public final String itemId;
    public final int meta;
    public final String itemDisplayName;
    public final String message;
    public final boolean unlockAfterFirstEntry;

    private Item item;
    private boolean resolved;

    public DimensionRule(String key, int dimensionId, String dimensionName, String itemId, int meta,
        String itemDisplayName, String message, boolean unlockAfterFirstEntry) {
        this.key = key;
        this.dimensionId = dimensionId;
        this.dimensionName = dimensionName;
        this.itemId = itemId;
        this.meta = meta;
        this.itemDisplayName = itemDisplayName;
        this.message = message;
        this.unlockAfterFirstEntry = unlockAfterFirstEntry;
    }

    /** Looks the item up in the registry. Has to run after all mods registered their items. */
    public boolean resolve() {
        resolved = true;
        item = GameData.getItemRegistry().containsKey(itemId) ? (Item) GameData.getItemRegistry().getObject(itemId)
            : null;
        return item != null;
    }

    public Item getItem() {
        if (!resolved) {
            resolve();
        }
        return item;
    }

    public boolean matches(ItemStack stack) {
        Item required = getItem();
        if (required == null || stack == null || stack.getItem() != required) {
            return false;
        }
        return meta == ANY_META || stack.getItemDamage() == meta;
    }

    /** A stack used only for showing the item name in chat. */
    public ItemStack displayStack() {
        Item required = getItem();
        return required == null ? null : new ItemStack(required, 1, meta == ANY_META ? 0 : meta);
    }

    public String describeItem() {
        return meta == ANY_META ? itemId : itemId + " (meta " + meta + ")";
    }
}
