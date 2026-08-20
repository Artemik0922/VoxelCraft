package com.voxelgame.chat.commands;

/**
 * Base interface for chat commands.
 */
public interface Command {

    /** Command name without '/' (e.g. "spawn"). */
    String getName();

    /** Usage string e.g. "/spawn <mob> [count] [x y z]". */
    String getUsage();

    /** Short description for /help. */
    String getDescription();

    /**
     * Execute the command.
     *
     * @param args arguments after the command name
     * @return message to display (or null for silent)
     * @throws IllegalArgumentException if arguments are invalid
     */
    String execute(String[] args);
}
