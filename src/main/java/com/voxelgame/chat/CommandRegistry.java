package com.voxelgame.chat;

import com.voxelgame.chat.commands.Command;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for chat commands. Commands register themselves by name and are
 * dispatched via {@link #execute(String)}.
 */
public class CommandRegistry {

    private static final Map<String, Command> commands = new HashMap<>();

    /** Register a command. */
    public static void register(Command cmd) {
        commands.put(cmd.getName().toLowerCase(), cmd);
    }

    /**
     * Execute a command line (including the leading '/').
     *
     * @param input full command line e.g. "/spawn zoloy 10 20 30"
     * @return result message to display, or null for silent success
     */
    public static String execute(String input) {
        if (input == null || !input.startsWith("/")) return null;

        String[] parts = input.substring(1).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return "Неизвестная команда. Введите /help";
        }

        String name = parts[0].toLowerCase();
        Command cmd = commands.get(name);
        if (cmd == null) {
            return "Неизвестная команда '" + name + "'. Введите /help";
        }

        String[] args;
        if (parts.length > 1) {
            args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, parts.length - 1);
        } else {
            args = new String[0];
        }

        try {
            return cmd.execute(args);
        } catch (IllegalArgumentException e) {
            return "Ошибка: " + e.getMessage() + "\nИспользование: " + cmd.getUsage();
        } catch (Exception e) {
            return "Ошибка выполнения: " + e.getMessage();
        }
    }

    /** Get all registered commands (for /help). */
    public static Map<String, Command> getCommands() {
        return commands;
    }
}
