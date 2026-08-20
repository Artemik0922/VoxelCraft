package com.voxelgame.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translation lookup.
 *
 * Strings live in assets/lang/&lt;code&gt;.json, loaded from the classpath and
 * overridable by a file of the same name on disk. Everything the interface
 * displays goes through {@link #tr}; a missing key falls back to English and
 * then to the key itself, so a gap shows up as a visible identifier rather
 * than blank space.
 *
 * The JSON here is a flat string-to-string map, which needs no parser
 * library - worth avoiding a dependency for.
 */
public final class Language {

    public static final String DEFAULT = "ru_ru";

    private static final Map<String, String> current = new LinkedHashMap<>();
    private static final Map<String, String> fallback = new LinkedHashMap<>();

    private static String currentCode = DEFAULT;

    /** Languages offered in the selection screen. */
    public record Entry(String code, String name, String region) {}

    private static final Entry[] AVAILABLE = {
        new Entry("ru_ru", "Русский", "Россия"),
        new Entry("en_us", "English", "US")
    };

    private Language() {}

    public static Entry[] available() { return AVAILABLE; }

    public static String getCode() { return currentCode; }

    public static String getDisplayName() {
        for (Entry e : AVAILABLE) {
            if (e.code.equals(currentCode)) return e.name;
        }
        return currentCode;
    }

    /**
     * Load a language, keeping English in memory as the fallback.
     */
    public static void load(String code) {
        if (fallback.isEmpty()) {
            read("en_us", fallback);
        }

        current.clear();
        if (!"en_us".equals(code)) {
            read(code, current);
        }
        currentCode = code;

        System.out.println("Language: " + code
            + " (" + (current.isEmpty() ? fallback.size() : current.size()) + " strings)");
    }

    /** Translate a key. */
    public static String tr(String key) {
        String v = current.get(key);
        if (v != null) return v;
        v = fallback.get(key);
        if (v != null) return v;
        return key;
    }

    /** Translate with positional {@code %s} style arguments. */
    public static String tr(String key, Object... args) {
        try {
            return String.format(tr(key), args);
        } catch (Exception e) {
            return tr(key);
        }
    }

    public static boolean has(String key) {
        return current.containsKey(key) || fallback.containsKey(key);
    }

    // ------------------------------------------------------------------

    private static void read(String code, Map<String, String> into) {
        String text = readSource(code);
        if (text == null) {
            System.err.println("Missing language file: " + code);
            return;
        }
        parseFlatJson(text, into);
    }

    private static String readSource(String code) {
        // A file on disk wins, so translations can be edited without rebuilding
        Path onDisk = Paths.get("assets", "lang", code + ".json");
        if (Files.isRegularFile(onDisk)) {
            try {
                return Files.readString(onDisk, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Could not read " + onDisk + ": " + e.getMessage());
            }
        }

        try (InputStream in = Language.class.getClassLoader()
                .getResourceAsStream("assets/lang/" + code + ".json")) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Minimal reader for a flat {@code {"key": "value"}} object.
     *
     * Handles the escapes that appear in translation files and ignores
     * comments and whitespace. A full JSON parser would be overkill here.
     */
    private static void parseFlatJson(String text, Map<String, String> into) {
        int i = 0;
        int n = text.length();

        while (i < n) {
            // Find the start of a key
            while (i < n && text.charAt(i) != '"') i++;
            if (i >= n) break;

            StringBuilder key = new StringBuilder();
            i = readString(text, i + 1, key);
            if (i < 0) break;

            while (i < n && text.charAt(i) != ':') i++;
            i++;

            while (i < n && text.charAt(i) != '"') {
                // A non-string value means this is not a flat entry; skip it
                if (text.charAt(i) == ',' || text.charAt(i) == '}') break;
                i++;
            }
            if (i >= n || text.charAt(i) != '"') continue;

            StringBuilder value = new StringBuilder();
            i = readString(text, i + 1, value);
            if (i < 0) break;

            into.put(key.toString(), value.toString());
        }
    }

    /** Read a quoted string starting at {@code i}; returns the index after it. */
    private static int readString(String text, int i, StringBuilder out) {
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char esc = text.charAt(i + 1);
                switch (esc) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case 'u' -> {
                        if (i + 5 < n) {
                            try {
                                out.append((char) Integer.parseInt(
                                    text.substring(i + 2, i + 6), 16));
                            } catch (NumberFormatException ignored) {}
                            i += 4;
                        }
                    }
                    default -> out.append(esc);
                }
                i += 2;
                continue;
            }
            if (c == '"') return i + 1;
            out.append(c);
            i++;
        }
        return -1;
    }
}
