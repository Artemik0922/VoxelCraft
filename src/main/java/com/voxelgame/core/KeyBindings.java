package com.voxelgame.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rebindable controls.
 *
 * Gameplay asks this class which key an action uses rather than testing
 * GLFW constants directly, so a rebind takes effect immediately with no
 * further plumbing.
 */
public class KeyBindings {

    /** One action the player can bind. */
    public enum Action {
        FORWARD("key.forward", GLFW_KEY_W),
        BACK("key.back", GLFW_KEY_S),
        LEFT("key.left", GLFW_KEY_A),
        RIGHT("key.right", GLFW_KEY_D),
        JUMP("key.jump", GLFW_KEY_SPACE),
        SNEAK("key.sneak", GLFW_KEY_LEFT_SHIFT),
        SPRINT("key.sprint", GLFW_KEY_LEFT_CONTROL),
        INVENTORY("key.inventory", GLFW_KEY_E),
        DROP("key.drop", GLFW_KEY_Q);

        public final String translationKey;
        public final int defaultKey;

        Action(String translationKey, int defaultKey) {
            this.translationKey = translationKey;
            this.defaultKey = defaultKey;
        }
    }

    private final Map<Action, Integer> bindings = new LinkedHashMap<>();

    public KeyBindings() {
        resetAll();
    }

    public int get(Action action) {
        return bindings.getOrDefault(action, action.defaultKey);
    }

    public void set(Action action, int key) {
        bindings.put(action, key);
    }

    public void resetAll() {
        for (Action a : Action.values()) bindings.put(a, a.defaultKey);
    }

    public void reset(Action action) {
        bindings.put(action, action.defaultKey);
    }

    /** Another action already using this key, or null. */
    public Action conflictFor(Action action, int key) {
        if (key == GLFW_KEY_UNKNOWN) return null;
        for (Map.Entry<Action, Integer> e : bindings.entrySet()) {
            if (e.getKey() != action && e.getValue() == key) return e.getKey();
        }
        return null;
    }

    // ------------------------------------------------------------------

    public void load(Properties p) {
        for (Action a : Action.values()) {
            String v = p.getProperty("key." + a.name());
            if (v == null) continue;
            try {
                bindings.put(a, Integer.parseInt(v.trim()));
            } catch (NumberFormatException ignored) {}
        }
    }

    public void save(Properties p) {
        for (Action a : Action.values()) {
            p.setProperty("key." + a.name(), Integer.toString(get(a)));
        }
    }

    // ------------------------------------------------------------------

    /** Human-readable key name for the controls list. */
    public static String keyName(int key) {
        if (key == GLFW_KEY_UNKNOWN) return Language.tr("key.none");

        String named = switch (key) {
            case GLFW_KEY_SPACE -> "Space";
            case GLFW_KEY_LEFT_SHIFT -> "L. Shift";
            case GLFW_KEY_RIGHT_SHIFT -> "R. Shift";
            case GLFW_KEY_LEFT_CONTROL -> "L. Ctrl";
            case GLFW_KEY_RIGHT_CONTROL -> "R. Ctrl";
            case GLFW_KEY_LEFT_ALT -> "L. Alt";
            case GLFW_KEY_RIGHT_ALT -> "R. Alt";
            case GLFW_KEY_TAB -> "Tab";
            case GLFW_KEY_ENTER -> "Enter";
            case GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW_KEY_ESCAPE -> "Esc";
            case GLFW_KEY_UP -> "Up";
            case GLFW_KEY_DOWN -> "Down";
            case GLFW_KEY_LEFT -> "Left";
            case GLFW_KEY_RIGHT -> "Right";
            default -> null;
        };
        if (named != null) return named;

        if (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25) {
            return "F" + (key - GLFW_KEY_F1 + 1);
        }

        // Printable keys can report a layout-specific name, but only once
        // GLFW is initialised - fall back to the physical key
        try {
            String s = glfwGetKeyName(key, 0);
            if (s != null && !s.isBlank()) return s.toUpperCase();
        } catch (IllegalStateException ignored) {
            // GLFW not initialised; use the ASCII name below
        }

        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + key - GLFW_KEY_A));
        }
        if (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) {
            return String.valueOf((char) ('0' + key - GLFW_KEY_0));
        }
        return "#" + key;
    }
}
