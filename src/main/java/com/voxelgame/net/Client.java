package com.voxelgame.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Client-side connection to the co-op server.
 *
 * Runs a background reader thread that dispatches server messages to the
 * {@link Listener}. Sending is done from the caller's thread via a
 * synchronized writer, so the game loop can push player state without
 * blocking on network I/O.
 */
public class Client {

    /** Callbacks for events arriving from the server. */
    public interface Listener {
        void onJoined(int myId, long seed, int structures, float spawnX, float spawnY, float spawnZ, double time);
        void onPlayerJoin(int id, String name);
        void onPlayerLeave(int id);
        void onPlayerState(int id, float x, float y, float z, float yaw, float pitch,
                           float health, int selected, boolean walking, boolean sneak);
        void onBlockChange(int x, int y, int z, byte id);
        void onChat(int id, String name, String message);
        void onServerMsg(String message);
        void onDisconnect(String reason);
    }

    private final String host;
    private final int port;
    private final String name;
    private final Listener listener;

    private Socket socket;
    private PrintWriter out;
    private Thread readerThread;
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private int myId = -1;

    public Client(String host, int port, String name, Listener listener) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.listener = listener;
    }

    public synchronized void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        running = true;
        connected = true;
        readerThread = new Thread(this::readLoop, "coop-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        send("JOIN " + Net.fields("name", name));
    }

    public boolean isConnected() { return connected; }
    public int getMyId() { return myId; }

    public synchronized void send(String line) {
        if (out == null) return;
        out.println(line);
    }

    public void sendPlayerState(float x, float y, float z, float yaw, float pitch,
                                float health, int selected, boolean walking, boolean sneak) {
        send("STATE " + Net.fields("x", fmt(x), "y", fmt(y), "z", fmt(z),
            "yaw", fmt(yaw), "pitch", fmt(pitch), "health", Math.round(health),
            "selected", selected, "walk", walking ? 1 : 0, "sneak", sneak ? 1 : 0));
    }

    public void sendBlockChange(int x, int y, int z, byte id) {
        send("BLOCK " + Net.fields("x", x, "y", y, "z", z, "id", id & 0xFF));
    }

    public void sendChat(String message) {
        send("CHAT " + Net.fields("msg", message));
    }

    public synchronized void disconnect() {
        running = false;
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        out = null;
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = in.readLine()) != null) {
                handle(line);
            }
        } catch (IOException e) {
            if (running) {
                connected = false;
                listener.onDisconnect("Соединение с сервером потеряно: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    private void handle(String line) {
        int sp = line.indexOf(' ');
        String type = sp < 0 ? line : line.substring(0, sp);
        Map<String, String> m = Net.parse(sp < 0 ? "" : line.substring(sp + 1));

        switch (type) {
            case "WELCOME" -> {
                myId = Net.getInt(m, "id", -1);
                listener.onJoined(myId, Net.getLong(m, "seed", 0), Net.getInt(m, "structures", 1),
                    Net.getFloat(m, "spawnX", 0), Net.getFloat(m, "spawnY", 70),
                    Net.getFloat(m, "spawnZ", 0), Net.getFloat(m, "time", 0.25f));
            }
            case "PLAYER_JOIN" -> listener.onPlayerJoin(Net.getInt(m, "id", -1), Net.getString(m, "name", "?"));
            case "PLAYER_LEAVE" -> listener.onPlayerLeave(Net.getInt(m, "id", -1));
            case "STATE" -> listener.onPlayerState(Net.getInt(m, "id", -1),
                Net.getFloat(m, "x", 0), Net.getFloat(m, "y", 0), Net.getFloat(m, "z", 0),
                Net.getFloat(m, "yaw", 0), Net.getFloat(m, "pitch", 0),
                Net.getFloat(m, "health", 20), Net.getInt(m, "selected", 0),
                Net.getInt(m, "walk", 0) == 1, Net.getInt(m, "sneak", 0) == 1);
            case "BLOCK" -> listener.onBlockChange(Net.getInt(m, "x", 0), Net.getInt(m, "y", 0),
                Net.getInt(m, "z", 0), (byte) Net.getInt(m, "id", 0));
            case "CHAT" -> listener.onChat(Net.getInt(m, "id", -1),
                Net.getString(m, "name", "?"), Net.getString(m, "msg", ""));
            case "TIME" -> {/* time is applied via WELCOME/periodic; handled by caller if desired */}
            case "SERVER_MSG" -> listener.onServerMsg(Net.getString(m, "msg", ""));
            default -> {/* ignore unknown */}
        }
    }
}
