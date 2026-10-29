package cz.dmuzik.dimensioncontrol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

/**
 * Permanent per-player dimension unlocks, saved in world/data/DimensionControl.dat.
 * Kept per world instead of in player NBT so admins can manage offline players too.
 */
public class UnlockData extends WorldSavedData {

    public static final String NAME = "DimensionControl";

    private static UnlockData instance;

    private final Map<UUID, Set<Integer>> unlocked = new HashMap<UUID, Set<Integer>>();

    public UnlockData(String name) {
        super(name);
    }

    public static boolean isUnlocked(UUID player, int dimension) {
        UnlockData data = get();
        if (data == null) {
            return false;
        }
        Set<Integer> dims = data.unlocked.get(player);
        return dims != null && dims.contains(dimension);
    }

    /** Returns true when the dimension was not unlocked before. */
    public static boolean unlock(UUID player, int dimension) {
        UnlockData data = get();
        if (data == null) {
            return false;
        }
        Set<Integer> dims = data.unlocked.get(player);
        if (dims == null) {
            dims = new TreeSet<Integer>();
            data.unlocked.put(player, dims);
        }
        if (!dims.add(dimension)) {
            return false;
        }
        data.markDirty();
        return true;
    }

    /** Returns true when the dimension was unlocked before. */
    public static boolean revoke(UUID player, int dimension) {
        UnlockData data = get();
        if (data == null) {
            return false;
        }
        Set<Integer> dims = data.unlocked.get(player);
        if (dims == null || !dims.remove(dimension)) {
            return false;
        }
        if (dims.isEmpty()) {
            data.unlocked.remove(player);
        }
        data.markDirty();
        return true;
    }

    /** Returns how many unlocks were removed. */
    public static int revokeAll(UUID player) {
        UnlockData data = get();
        if (data == null) {
            return 0;
        }
        Set<Integer> dims = data.unlocked.remove(player);
        if (dims == null) {
            return 0;
        }
        data.markDirty();
        return dims.size();
    }

    public static Set<Integer> getUnlocked(UUID player) {
        UnlockData data = get();
        Set<Integer> dims = data == null ? null : data.unlocked.get(player);
        return dims == null ? Collections.<Integer>emptySet() : Collections.unmodifiableSet(dims);
    }

    public static void clearCache() {
        instance = null;
    }

    private static UnlockData get() {
        if (instance != null) {
            return instance;
        }
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null && MinecraftServer.getServer() != null) {
            overworld = MinecraftServer.getServer().worldServerForDimension(0);
        }
        if (overworld == null) {
            return null;
        }
        UnlockData data = (UnlockData) overworld.loadItemData(UnlockData.class, NAME);
        if (data == null) {
            data = new UnlockData(NAME);
            overworld.setItemData(NAME, data);
        }
        instance = data;
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        unlocked.clear();
        NBTTagList players = tag.getTagList("players", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound entry = players.getCompoundTagAt(i);
            UUID id;
            try {
                id = UUID.fromString(entry.getString("uuid"));
            } catch (IllegalArgumentException e) {
                DimensionControl.LOG.warn("Skipping unlock entry with invalid UUID '{}'", entry.getString("uuid"));
                continue;
            }
            Set<Integer> dims = new TreeSet<Integer>();
            for (int dim : entry.getIntArray("dims")) {
                dims.add(dim);
            }
            if (!dims.isEmpty()) {
                unlocked.put(id, dims);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList players = new NBTTagList();
        for (Map.Entry<UUID, Set<Integer>> e : unlocked.entrySet()) {
            int[] dims = new int[e.getValue().size()];
            int i = 0;
            for (int dim : e.getValue()) {
                dims[i++] = dim;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", e.getKey().toString());
            entry.setIntArray("dims", dims);
            players.appendTag(entry);
        }
        tag.setTag("players", players);
    }
}
