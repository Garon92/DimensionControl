package cz.dmuzik.dimensioncontrol;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.config.Configuration;

public final class DCConfig {

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_DIMENSIONS = "dimensions";

    public static boolean opsBypass = false;
    public static boolean creativeBypass = false;
    public static boolean alwaysAllowOverworld = true;
    public static int messageCooldownTicks = 100;
    public static int portalCooldownTicks = 40;
    public static boolean pushBackFromPortal = true;
    public static boolean fallbackCheck = true;
    public static boolean logBlocked = false;
    public static String unlockMessage = "";

    private static final Map<String, Object[]> DEFAULT_RULES = defaultRules();

    private static Configuration config;
    private static volatile Map<Integer, DimensionRule> rules = Collections.emptyMap();
    /** Problems found while reading the file (wrong item format, duplicate dimensions). */
    private static List<String> loadProblems = new ArrayList<String>();
    /** Everything wrong with the current config, shown to admins in chat. */
    private static volatile List<String> problems = Collections.emptyList();

    private DCConfig() {}

    public static void init(File file) {
        boolean firstRun = !file.exists();
        config = new Configuration(file);
        load(firstRun);
    }

    /** Re-reads the file and validates it again. Returns problems worth showing to an admin. */
    public static synchronized List<String> reload() {
        load(false);
        return validate();
    }

    /**
     * Resolves items and checks that the dimensions exist. Must run once all mods registered their items and
     * dimensions (server start).
     */
    public static List<String> validate() {
        List<String> found = new ArrayList<String>(loadProblems);
        for (DimensionRule rule : rules.values()) {
            if (!rule.resolve()) {
                found.add(
                    "Pravidlo '" + rule.key + "' (dimenze " + rule.dimensionId + "): neznámý předmět '" + rule.itemId
                        + "' - s předmětem se tam nikdo nedostane, dokud se to neopraví");
            }
            if (!DimensionManager.isDimensionRegistered(rule.dimensionId)) {
                found.add(
                    "Pravidlo '" + rule.key + "': dimenze " + rule.dimensionId
                        + " na serveru neexistuje - pravidlo nic nehlídá (správná ID: /dimaccess dims)");
            }
        }
        for (String problem : found) {
            DimensionControl.LOG.error("Config problem: {}", problem);
        }
        problems = Collections.unmodifiableList(found);
        return problems;
    }

    public static List<String> getProblems() {
        return problems;
    }

    /** The rule that applies to entering the dimension, or null when the dimension is not restricted. */
    public static DimensionRule getRule(int dimension) {
        if (dimension == 0 && alwaysAllowOverworld) {
            return null;
        }
        return rules.get(dimension);
    }

    public static Map<Integer, DimensionRule> getRules() {
        return rules;
    }

    private static synchronized void load(boolean firstRun) {
        config.load();

        config.setCategoryComment(
            CATEGORY_GENERAL,
            "Obecné nastavení. Barvy ve zprávách se píšou přes & (např. &c červená, &e žlutá, &a zelená).");
        opsBypass = config.getBoolean(
            "opsBypass",
            CATEGORY_GENERAL,
            false,
            "Operátoři (OP) můžou do všech dimenzí bez předmětu. Pozor při testování: s true se kontrola\n"
                + "netýká ani tebe (a v singleplayeru se zapnutými cheaty jsi OP).");
        creativeBypass = config.getBoolean(
            "creativeBypass",
            CATEGORY_GENERAL,
            false,
            "Hráči v creative módu můžou do všech dimenzí bez předmětu.");
        alwaysAllowOverworld = config.getBoolean(
            "alwaysAllowOverworld",
            CATEGORY_GENERAL,
            true,
            "Návrat do Overworldu (dimenze 0) se nikdy neomezuje, i kdyby pro něj existovalo pravidlo.");
        messageCooldownTicks = config.getInt(
            "messageCooldownTicks",
            CATEGORY_GENERAL,
            100,
            0,
            72000,
            "Nejkratší rozestup mezi chybovými zprávami jednomu hráči (20 ticků = 1 sekunda).");
        portalCooldownTicks = config.getInt(
            "portalCooldownTicks",
            CATEGORY_GENERAL,
            40,
            0,
            72000,
            "Po zablokování se hráči nastaví tento cooldown portálu, aby se teleport nezkoušel každý tick.\n"
                + "Vanilla portál pak funguje až poté, co hráč stál tuto dobu mimo něj.");
        pushBackFromPortal = config.getBoolean(
            "pushBackFromPortal",
            CATEGORY_GENERAL,
            true,
            "Zablokovaného hráče, který stojí v portálu, vrátit na poslední bezpečné místo před portálem.\n"
                + "Důležité hlavně pro End portál, pod kterým bývá láva.");
        fallbackCheck = config.getBoolean(
            "fallbackCheck",
            CATEGORY_GENERAL,
            true,
            "Záložní kontrola každý tick: pokud se hráč dostane do omezené dimenze způsobem, který obešel hlavní\n"
                + "kontrolu (mod s vlastním teleportem), vrátí ho zpět tam, odkud přišel.");
        logBlocked = config.getBoolean(
            "logBlockedAttempts",
            CATEGORY_GENERAL,
            false,
            "Zapisovat každý zablokovaný pokus do logu serveru.");
        unlockMessage = config.getString(
            "unlockMessage",
            CATEGORY_GENERAL,
            "&aDimenze &e%dim% &aje pro tebe nyní trvale odemčena.",
            "Zpráva po prvním úspěšném vstupu u pravidel s unlockAfterFirstEntry=true. Prázdné = žádná zpráva.\n"
                + "Proměnné: %dim% (název dimenze), %item% (předmět), %player% (hráč).");

        config.setCategoryComment(
            CATEGORY_DIMENSIONS,
            "Každá podkategorie je jedno pravidlo pro jednu dimenzi. Novou přidáš zkopírováním celého bloku\n"
                + "(např. nether { ... }) a změnou jeho názvu a hodnot. Dimenze bez pravidla nejsou omezené.");
        if (firstRun) {
            addDefaultRules();
        }

        loadProblems = new ArrayList<String>();
        Map<Integer, DimensionRule> loaded = new LinkedHashMap<Integer, DimensionRule>();
        for (String category : new TreeSet<String>(config.getCategoryNames())) {
            if (!category.startsWith(CATEGORY_DIMENSIONS + Configuration.CATEGORY_SPLITTER)) {
                continue;
            }
            DimensionRule rule = readRule(category);
            if (rule == null) {
                continue;
            }
            DimensionRule previous = loaded.put(rule.dimensionId, rule);
            if (previous != null) {
                loadProblems.add(
                    "Pravidla '" + previous.key + "' a '" + rule.key + "' hlídají stejnou dimenzi "
                        + rule.dimensionId + ", platí jen '" + rule.key + "'");
            }
        }
        rules = Collections.unmodifiableMap(loaded);

        if (config.hasChanged()) {
            config.save();
        }
        DimensionControl.LOG.info("Loaded {} dimension rule(s)", rules.size());
    }

    private static DimensionRule readRule(String category) {
        String key = category.substring(CATEGORY_DIMENSIONS.length() + 1);
        Object[] defaults = DEFAULT_RULES.containsKey(key) ? DEFAULT_RULES.get(key) : new Object[] { 0, key, "" };
        boolean enabled = config.getBoolean("enabled", category, true, "Pravidlo je aktivní.");
        int dimensionId = config.getInt(
            "dimensionId",
            category,
            (Integer) defaults[0],
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            "ID dimenze, do které se omezuje vstup.");
        String dimensionName = config
            .getString("dimensionName", category, (String) defaults[1], "Název dimenze pro zprávy v chatu (%dim%).");
        String item = config.getString(
            "item",
            category,
            (String) defaults[2],
            "Předmět, který musí hráč držet v ruce, ve tvaru modid:item (např. minecraft:ender_eye).");
        int meta = config.getInt(
            "meta",
            category,
            DimensionRule.ANY_META,
            DimensionRule.ANY_META,
            Short.MAX_VALUE,
            "Metadata (damage) předmětu. -1 = libovolná.");
        String itemName = config.getString(
            "itemName",
            category,
            "",
            "Vlastní název předmětu pro %item%. Prázdné = název podle jazyka klienta.");
        String message = config.getString(
            "message",
            category,
            "&cDo dimenze &e%dim% &cse dostaneš jen s předmětem &e%item% &cv ruce.",
            "Chybová zpráva při zablokování. Proměnné: %dim%, %item%, %player%.");
        boolean unlock = config.getBoolean(
            "unlockAfterFirstEntry",
            category,
            true,
            "Po prvním úspěšném vstupu s předmětem si hráč dimenzi trvale odemkne a předmět už nepotřebuje.\n"
                + "Odemčení přežije smrt i restart serveru. Spravuje se příkazem /dimaccess.");

        if (!enabled) {
            return null;
        }
        if (item.trim().isEmpty() || item.indexOf(':') < 0) {
            loadProblems.add("Pravidlo '" + key + "': předmět '" + item + "' není ve tvaru modid:item - pravidlo se ignoruje");
            return null;
        }
        return new DimensionRule(key, dimensionId, dimensionName, item.trim(), meta, itemName, message, unlock);
    }

    private static void addDefaultRules() {
        for (String key : DEFAULT_RULES.keySet()) {
            config.getCategory(CATEGORY_DIMENSIONS + Configuration.CATEGORY_SPLITTER + key);
        }
    }

    /** Example rules written on the first run: dimension id, display name, item. */
    private static Map<String, Object[]> defaultRules() {
        Map<String, Object[]> defaults = new LinkedHashMap<String, Object[]>();
        defaults.put("nether", new Object[] { -1, "Nether", "minecraft:flint_and_steel" });
        defaults.put("end", new Object[] { 1, "The End", "minecraft:ender_eye" });
        return defaults;
    }
}
