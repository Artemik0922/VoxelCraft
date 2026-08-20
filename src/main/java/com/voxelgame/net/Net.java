package com.voxelgame.net;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for the local co-op network protocol.
 *
 * The wire format is a simple line-based text protocol over TCP:
 *
 *   TYPE k=v k=v k=v ...
 *
 * where TYPE is the message kind and the rest are space-separated fields.
 * Keeping it text makes the stream trivially debuggable (you can watch it
 * with netcat) and avoids any serialization dependency beyond the JDK.
 */
public final class Net {

    /** Default LAN port for the co-op server. */
    public static final int DEFAULT_PORT = 25565;

    private Net() {}

    /**
     * Build a "k=v k=v ..." line from an even-length array of key/value pairs.
     */
    public static String fields(Object... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(kv[i]).append('=').append(String.valueOf(kv[i + 1]));
        }
        return sb.toString();
    }

    /** Parse a "k=v k=v ..." line into an ordered map. */
    public static Map<String, String> parse(String line) {
        Map<String, String> out = new LinkedHashMap<>();
        if (line == null) return out;
        for (String token : line.split("\\s+")) {
            int eq = token.indexOf('=');
            if (eq <= 0) continue;
            out.put(token.substring(0, eq), token.substring(eq + 1));
        }
        return out;
    }

    public static int getInt(Map<String, String> m, String key, int def) {
        String v = m.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public static long getLong(Map<String, String> m, String key, long def) {
        String v = m.get(key);
        if (v == null) return def;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public static float getFloat(Map<String, String> m, String key, float def) {
        String v = m.get(key);
        if (v == null) return def;
        try { return Float.parseFloat(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    public static String getString(Map<String, String> m, String key, String def) {
        String v = m.get(key);
        return v == null ? def : v;
    }
}
