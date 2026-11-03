package cz.dmuzik.dimensioncontrol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

import com.google.common.io.Files;

/**
 * Checks a hand-edited config before Forge gets to read it. Forge renames a file it cannot parse and starts with an
 * empty one, which would silently drop every rule, and it replaces a value it cannot read with the default. Neither
 * may happen to a file that decides who gets in, so a file failing this check is left alone and not loaded.
 */
final class ConfigCheck {

    private static final int MAX_ERRORS = 5;

    private ConfigCheck() {}

    /** Returns what is wrong with the file, empty when it is safe to load. */
    static List<String> check(File file) {
        List<String> errors = new ArrayList<String>();
        try {
            scanLines(file, errors);
            if (errors.isEmpty()) {
                parseWithForge(file, errors);
            }
        } catch (IOException e) {
            errors.add("soubor nejde přečíst: " + e.getMessage());
        }
        return errors;
    }

    /** Tracks blocks and typed values itself: Forge misses a missing }, and its line numbers can be far off. */
    private static void scanLines(File file, List<String> errors) throws IOException {
        Deque<String> blocks = new ArrayDeque<String>();
        Set<String> rules = new HashSet<String>();
        boolean inList = false;
        int lineNumber = 0;
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null && errors.size() < MAX_ERRORS) {
                lineNumber++;
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#") || text.startsWith("~")) {
                    continue;
                }
                if (inList) {
                    inList = !text.equals(">");
                    continue;
                }
                // Everything after the first = is a value, whatever characters it contains
                int equals = line.indexOf('=');
                if (equals >= 0) {
                    checkValue(line.substring(0, equals).trim(), line.substring(equals + 1), lineNumber, errors);
                } else if (text.endsWith("<")) {
                    inList = true;
                } else if (text.equals("}")) {
                    if (blocks.isEmpty()) {
                        errors.add("řádek " + lineNumber + ": } navíc");
                    } else {
                        blocks.pop();
                    }
                } else if (text.endsWith("{")) {
                    String name = text.substring(0, text.length() - 1).trim();
                    if (name.isEmpty()) {
                        errors.add("řádek " + lineNumber + ": { bez názvu bloku, patří na řádek s názvem");
                        name = "?";
                    }
                    boolean isRule = blocks.size() == 1 && DCConfig.CATEGORY_DIMENSIONS.equals(blocks.peek());
                    if (isRule && !rules.add(name)) {
                        errors.add(
                            "řádek " + lineNumber + ": blok '" + name
                                + "' je v souboru dvakrát, zkopírovaný blok je potřeba přejmenovat");
                    }
                    blocks.push(name);
                }
            }
        } finally {
            reader.close();
        }
        if (!blocks.isEmpty() && errors.isEmpty()) {
            errors.add("na konci souboru chybí } u bloku '" + blocks.peek() + "'");
        }
    }

    private static void checkValue(String key, String value, int lineNumber, List<String> errors) {
        if (key.startsWith("I:")) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException e) {
                errors.add("řádek " + lineNumber + ": '" + key + "=" + value + "' není celé číslo");
            }
        } else if (key.startsWith("B:") && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            errors.add("řádek " + lineNumber + ": '" + key + "=" + value + "' musí být true nebo false");
        }
    }

    /** Runs Forge's own parser on a copy, so anything else it would choke on is caught before it renames the file. */
    private static void parseWithForge(File file, List<String> errors) throws IOException {
        File copy = File.createTempFile("dimensioncontrol", ".cfg");
        try {
            Configuration probe = new Configuration(copy);
            Files.copy(file, copy);
            probe.load();
        } catch (RuntimeException e) {
            errors.add("Forge soubor nepřečetl: " + String.valueOf(e.getMessage()).replaceAll("'[^']*:(\\d+)'", "(řádek $1)"));
        } finally {
            copy.delete();
        }
    }
}
