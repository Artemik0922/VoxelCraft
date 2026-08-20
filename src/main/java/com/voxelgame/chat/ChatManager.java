package com.voxelgame.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the chat input buffer, cursor position, command history, and the
 * message tape (recent messages that fade out over time).
 */
public class ChatManager {

    private static final int MAX_BUFFER = 100;
    private static final int MAX_HISTORY = 50;
    private static final int TAPE_SIZE = 12;
    private static final float MESSAGE_LIFETIME = 10.0f;
    private static final float FADE_DURATION = 2.0f;

    // Input buffer
    private final StringBuilder buffer = new StringBuilder();
    private int cursor = 0;

    // Command history (most recent at end)
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    // Message tape
    private final List<Message> tape = new ArrayList<>();

    // State
    private boolean open = false;

    /** A single message in the tape. */
    private static class Message {
        String text;
        int color;
        float age = 0;

        Message(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    // ------------------------------------------------------------------
    // Open / close
    // ------------------------------------------------------------------

    /** Open chat. If prefix is not null, it is pre-inserted into the buffer. */
    public void open(String prefix) {
        open = true;
        buffer.setLength(0);
        cursor = 0;
        if (prefix != null) {
            buffer.append(prefix);
            cursor = prefix.length();
        }
        historyIndex = -1;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

    /** Insert a Unicode character at the cursor. */
    public void charTyped(char c) {
        if (buffer.length() >= MAX_BUFFER) return;
        if (cursor < 0) cursor = 0;
        if (cursor > buffer.length()) cursor = buffer.length();
        buffer.insert(cursor, c);
        cursor++;
    }

    /** Backspace deletes the character before the cursor. */
    public void backspace() {
        if (cursor > 0 && cursor <= buffer.length()) {
            buffer.deleteCharAt(cursor - 1);
            cursor--;
        }
    }

    /** Delete key removes the character at the cursor. */
    public void delete() {
        if (cursor < buffer.length()) {
            buffer.deleteCharAt(cursor);
        }
    }

    public void moveCursorLeft() {
        if (cursor > 0) cursor--;
    }

    public void moveCursorRight() {
        if (cursor < buffer.length()) cursor++;
    }

    public void moveCursorHome() {
        cursor = 0;
    }

    public void moveCursorEnd() {
        cursor = buffer.length();
    }

    /** Navigate history: -1 = older, +1 = newer. */
    public void historyNavigate(int dir) {
        if (history.isEmpty()) return;
        if (historyIndex == -1) {
            historyIndex = history.size() + dir;
        } else {
            historyIndex += dir;
        }
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex >= history.size()) {
            historyIndex = -1;
            buffer.setLength(0);
            cursor = 0;
            return;
        }
        buffer.setLength(0);
        buffer.append(history.get(historyIndex));
        cursor = buffer.length();
    }

    /** Paste text from clipboard into buffer at cursor. */
    public void paste(String text) {
        if (text == null) return;
        for (char c : text.toCharArray()) {
            if (buffer.length() >= MAX_BUFFER) break;
            if (c >= 32) {
                buffer.insert(cursor, c);
                cursor++;
            }
        }
    }

    // ------------------------------------------------------------------
    // Send / command processing
    // ------------------------------------------------------------------

    /**
     * Send the current buffer. Adds to history, processes as command or chat
     * message, then clears the buffer.
     *
     * @return true if the chat should stay open (never), false to close
     */
    public boolean send() {
        String text = buffer.toString().trim();
        if (text.isEmpty()) {
            buffer.setLength(0);
            cursor = 0;
            return false;
        }

        // Add to history
        history.add(text);
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }

        // In co-op, route plain chat to the server which relays it to everyone
        if (messageSink != null && !text.startsWith("/")) {
            messageSink.send(text);
            buffer.setLength(0);
            cursor = 0;
            return false;
        }

        // Echo to tape
        addMessage("<" + "Player" + "> " + text, 0xFFFFFFFF);

        // Process command
        if (text.startsWith("/")) {
            System.out.println("[Chat] Executing command: " + text);
            String result = CommandRegistry.execute(text);
            if (result != null) {
                addMessage(result, 0xFFFFAA00);
            }
        }

        buffer.setLength(0);
        cursor = 0;
        return false;
    }

    // ------------------------------------------------------------------
    // Co-op message sink
    // ------------------------------------------------------------------

    /** Forwards typed chat (non-command) to an external consumer, e.g. a
     *  co-op server, which relays it to all players. When set, the message is
     *  NOT echoed locally (the relay comes back through {@link #addMessage}). */
    public interface MessageSink { void send(String text); }

    private MessageSink messageSink;

    public void setMessageSink(MessageSink sink) { this.messageSink = sink; }

    // ------------------------------------------------------------------
    // Message tape
    // ------------------------------------------------------------------

    /** Add a message to the tape (system messages, command results, etc). */
    public void addMessage(String text, int color) {
        tape.add(new Message(text, color));
        while (tape.size() > TAPE_SIZE) {
            tape.remove(0);
        }
    }

    /** Update message ages. Removes expired messages. */
    public void update(float dt) {
        tape.removeIf(m -> {
            m.age += dt;
            return m.age >= MESSAGE_LIFETIME;
        });
    }

    /**
     * Render the message tape and input line.
     *
     * @param ui     the UIRenderer
     * @param font   the FontRenderer
     * @param width  screen width in GUI pixels
     * @param height screen height in GUI pixels
     * @param time   current time for cursor blink
     */
    public void render(com.voxelgame.ui.UIRenderer ui, com.voxelgame.ui.FontRenderer font,
                       float width, float height, float time) {
        float lineHeight = 9;
        float inputHeight = 12;
        float tapeTop = height - inputHeight - 4;

        // --- Message tape (bottom to top) ---
        float y = tapeTop - lineHeight;
        for (int i = tape.size() - 1; i >= 0; i--) {
            Message m = tape.get(i);
            float alpha = 1.0f;
            float remaining = MESSAGE_LIFETIME - m.age;
            if (remaining < FADE_DURATION) {
                alpha = Math.max(0, remaining / FADE_DURATION);
            }
            if (alpha <= 0) continue;

            int color = m.color;
            int a = (int) (alpha * 255);
            int argb = (a << 24) | (color & 0xFFFFFF);

            // Background
            int bgAlpha = (int) (alpha * 128);
            ui.fillRect(4, y - 1, font.width(m.text) + 4, lineHeight, (bgAlpha << 24));

            font.draw(ui, m.text, 6, y, argb);
            y -= lineHeight;
        }

        // --- Input line ---
        if (open) {
            // Background bar
            ui.fillRect(0, height - inputHeight, width, inputHeight, 0x80000000);

            String display = "> " + buffer.toString();
            font.draw(ui, display, 4, height - inputHeight + 2, 0xFFFFFFFF);

            // Cursor (blink period ~0.5s)
            boolean cursorVisible = ((int) (time * 2)) % 2 == 0;
            if (cursorVisible) {
                int cx = 4 + font.width("> " + buffer.substring(0, cursor));
                ui.fillRect(cx, height - inputHeight + 2, 1, lineHeight, 0xFFFFFFFF);
            }
        }
    }

    // --- Getters ---
    public String getBuffer() { return buffer.toString(); }
    public int getCursor() { return cursor; }
}
