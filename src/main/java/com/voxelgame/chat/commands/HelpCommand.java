package com.voxelgame.chat.commands;

import com.voxelgame.chat.CommandRegistry;

import java.util.Map;

/**
 * /help — list all registered commands with descriptions.
 */
public class HelpCommand implements Command {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getUsage() {
        return "/help";
    }

    @Override
    public String getDescription() {
        return "показать список команд";
    }

    @Override
    public String execute(String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Команды ---\n");
        for (Map.Entry<String, Command> e : CommandRegistry.getCommands().entrySet()) {
            sb.append(e.getValue().getUsage()).append(" — ")
              .append(e.getValue().getDescription()).append("\n");
        }
        // Remove trailing newline
        return sb.toString().trim();
    }
}
