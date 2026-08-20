package com.voxelgame.debug;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * File-based command console for unattended testing.
 *
 * An external tool (a script, an AI agent, a CI job) appends one command
 * per line to {@code debug_commands.txt}. On every tick this handler picks
 * up new lines, executes them on the caller's thread through an
 * {@link Executor} and appends trace lines to {@code debug_results.txt}:
 *
 *   [OUT]   <line emitted by the command>
 *   [OK]    <command that succeeded>
 *   [ERROR] <command>: <message>
 *
 * Lines starting with '#' or ';' and blank lines are ignored. Rewriting or
 * truncating the command file resynchronises the read offset automatically.
 */
public final class DebugCommandHandler {

    /** Executes one parsed command line on the caller (game) thread. */
    public interface Executor {
        void execute(String[] tokens, Consumer<String> out) throws Exception;
    }

    private static final Path IN_FILE = Paths.get("debug_commands.txt");
    private static final Path OUT_FILE = Paths.get("debug_results.txt");

    private final Executor executor;
    private final List<String> queue = new ArrayList<>();
    private long readOffset = -1;

    public DebugCommandHandler(Executor executor) {
        this.executor = executor;
        append("debug console ready (in: " + IN_FILE.getFileName()
            + ", out: " + OUT_FILE.getFileName() + ")");
    }

    /** Polls the command file and drains the queue. Call once per tick. */
    public void tick() {
        pollInbox();
        while (!queue.isEmpty()) {
            String raw = queue.remove(0);
            try {
                executor.execute(raw.split("\\s+"), this::append);
                append("[OK] " + raw);
                System.out.println("[Debug] OK: " + raw);
            } catch (Exception e) {
                append("[ERROR] " + raw + " -> " + describe(e));
                System.out.println("[Debug] ERROR: " + raw + " -> " + describe(e));
            }
        }
    }

    public boolean hasPending() {
        return !queue.isEmpty();
    }

    /** Appends a trace line to the results file and mirrors it to stdout. */
    public void append(String line) {
        System.out.println("[Debug] " + line);
        synchronized (this) {
            try {
                Files.writeString(OUT_FILE, line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (java.io.IOException e) {
                System.err.println("[Debug] failed to write " + OUT_FILE + ": " + e.getMessage());
            }
        }
    }

    private void pollInbox() {
        try {
            long size = Files.exists(IN_FILE) ? Files.size(IN_FILE) : 0L;
            if (readOffset < 0) {
                readOffset = 0;
                return; // first sight: don't replay commands left over from an older run
            }
            if (size < readOffset) {
                readOffset = size; // file was rewritten or truncated
                queue.clear();
                return;
            }
            if (size <= readOffset) return;

            byte[] buf = new byte[(int) (size - readOffset)];
            try (FileInputStream fis = new FileInputStream(IN_FILE.toFile())) {
                int got = 0;
                while (got < buf.length) {
                    int r = fis.read(buf, got, buf.length - got);
                    if (r < 0) break;
                    got += r;
                }
            }
            readOffset = size;

            for (String line : new String(buf, StandardCharsets.UTF_8).split("\r?\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                char first = t.charAt(0);
                if (first == '#' || first == ';') continue;
                queue.add(t);
            }
        } catch (java.io.IOException e) {
            // The file may be momentarily locked while it is being written.
            // Nothing to do: the next tick re-reads from the last offset.
        }
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}