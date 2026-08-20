package com.voxelgame.net;

import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;
import com.voxelgame.world.save.WorldMeta;
import com.voxelgame.world.save.WorldSave;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Headless co-op server.
 *
 * Hosts a pure-Java {@link World} (no rendering) that is authoritative for
 * block state and the save file. It relays player states and chat between
 * clients and broadcasts block edits so every connected client's locally
 * generated world stays consistent.
 *
 * Mobs/villagers and water flow are simulated per-client for the MVP; only
 * players and block edits are synchronised.
 */
public class GameServer {

    /** Simple console logger. */
    public interface Logger { void log(String msg); }

    private final int port;
    private final int maxPlayers;
    private final Logger logger;
    private final World world;
    private final WorldSave save;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<Integer, Vector3f> lastPos = new ConcurrentHashMap<>();
    private final Set<Integer> usedIds = new HashSet<>();
    private int nextId = 1;

    private double lastFlush = 0;
    private double lastTime = 0;
    private final double dayTime = 0.25;

    public GameServer(int port, long seed, int maxPlayers, Logger logger) {
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.logger = logger;

        // Reuse a persisted server world so block edits survive restarts.
        Path metaFile = Paths.get("saves", "coop_server", "meta.json");
        WorldMeta meta = WorldMeta.read(metaFile);
        if (meta == null) {
            meta = new WorldMeta("Co-op Server", seed, WorldMeta.GameMode.SURVIVAL, true);
            meta.folderName = "coop_server";
        }
        save = new WorldSave(meta);
        save.saveMeta();

        world = new World(meta.seed);
        world.setSave(save);
        world.setStructuresEnabled(meta.generateStructures);

        // Preload the spawn area so first placements always land.
        world.preloadChunks(new Vector3f(8.5f, 70, 8.5f), 3);
    }

    public long getSeed() { return world.getSeed(); }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 16, InetAddress.getByName("0.0.0.0"));
        running = true;
        logger.log("Кооп-сервер запущен на порту " + port
            + ", сид мира: " + world.getSeed() + ", слотов: " + maxPlayers);

        Thread ticker = new Thread(this::tickLoop, "coop-server-tick");
        ticker.setDaemon(true);
        ticker.start();

        while (running) {
            try {
                Socket s = serverSocket.accept();
                if (clients.size() >= maxPlayers) {
                    try (PrintWriter w = new PrintWriter(
                            new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
                        w.println("SERVER_MSG msg=Сервер заполнен");
                    }
                    s.close();
                    continue;
                }
                new ClientHandler(s).start();
            } catch (IOException e) {
                if (running) logger.log("Ошибка accept: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        for (ClientHandler h : clients.values()) h.close();
        world.cleanup();
    }

    private void tickLoop() {
        while (running) {
            try { Thread.sleep(100); } catch (InterruptedException e) { return; }

            // Keep chunks streamed around every connected player.
            for (Vector3f p : lastPos.values()) {
                world.update(p);
            }

            double now = System.currentTimeMillis() / 1000.0;
            if (now - lastFlush > 30.0) {
                lastFlush = now;
                int n = world.flushToDisk();
                if (n > 0) logger.log("Сервер сохранил " + n + " чанков");
            }
            if (now - lastTime > 5.0) {
                lastTime = now;
                broadcast("TIME " + Net.fields("time", dayTime));
            }
        }
    }

    private synchronized int allocateId() {
        while (usedIds.contains(nextId)) nextId++;
        int id = nextId++;
        usedIds.add(id);
        return id;
    }

    /** Apply a block edit authoritatively and relay it to every other client. */
    private void applyBlockEdit(ClientHandler from, int x, int y, int z, byte id) {
        ensureChunk(x, z);
        world.setBlock(x, y, z, id);
        broadcastExcept(from, "BLOCK " + Net.fields("x", x, "y", y, "z", z, "id", id & 0xFF));
    }

    /** Ensure the chunk owning this column exists (generate synchronously). */
    private void ensureChunk(int x, int z) {
        if (world.getChunkAt(x, z) != null) return;
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        if (world.getChunk(cx, cz) != null) return;
        Chunk c = new Chunk(cx, cz);
        world.getGenerator().generate(c);
        world.getChunks().put(Chunk.key(cx, cz), c);
    }

    private void broadcast(String line) {
        for (ClientHandler h : clients.values()) h.send(line);
    }

    private void broadcastExcept(ClientHandler except, String line) {
        for (ClientHandler h : clients.values()) {
            if (h != except) h.send(line);
        }
    }

    /** One connected player's socket + reader thread. */
    private class ClientHandler implements Runnable {
        final Socket socket;
        final PrintWriter out;
        final BufferedReader in;
        final Thread thread;
        int id = -1;
        String name = "Player";
        final Vector3f pos = new Vector3f(8.5f, 72, 8.5f);

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            socket.setTcpNoDelay(true);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            thread = new Thread(this, "coop-client-" + socket.getPort());
            thread.setDaemon(true);
        }

        void start() { thread.start(); }
        void send(String line) { out.println(line); }
        void close() { try { socket.close(); } catch (IOException ignored) {} }

        @Override
        public void run() {
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                    handle(line);
                }
            } catch (IOException ignored) {
            } finally {
                onLeave();
            }
        }

        private void handle(String line) {
            int sp = line.indexOf(' ');
            String type = sp < 0 ? line : line.substring(0, sp);
            Map<String, String> m = Net.parse(sp < 0 ? "" : line.substring(sp + 1));

            switch (type) {
                case "JOIN" -> {
                    name = Net.getString(m, "name", "Player");
                    id = allocateId();
                    clients.put(id, this);
                    lastPos.put(id, pos);
                    pos.set(8.5f, 70, 8.5f);
                    ensureChunk(8, 8);
                    pos.y = world.getGroundHeight(8, 8) + 2.0f;
                    send("WELCOME " + Net.fields("id", id, "seed", world.getSeed(),
                        "structures", world.getGenerator().isStructuresEnabled() ? 1 : 0,
                        "spawnX", pos.x, "spawnY", pos.y, "spawnZ", pos.z, "time", dayTime));
                    // Tell the newcomer about everyone already here.
                    for (ClientHandler other : clients.values()) {
                        if (other.id != -1 && other.id != id) {
                            send("PLAYER_JOIN " + Net.fields("id", other.id, "name", other.name));
                        }
                    }
                    // Tell everyone about this join.
                    broadcast("PLAYER_JOIN " + Net.fields("id", id, "name", name));
                    broadcast("SERVER_MSG msg=" + name + " зашёл в игру");
                    logger.log(name + " подключился (id=" + id + ")");
                }
                case "STATE" -> {
                    pos.set(Net.getFloat(m, "x", pos.x), Net.getFloat(m, "y", pos.y), Net.getFloat(m, "z", pos.z));
                    lastPos.put(id, pos);
                    // Relay with the sender's id so clients can map the state
                    broadcastExcept(this, "STATE id=" + id + " " + line.substring(sp + 1));
                }
                case "BLOCK" -> applyBlockEdit(this,
                    Net.getInt(m, "x", 0), Net.getInt(m, "y", 0), Net.getInt(m, "z", 0),
                    (byte) Net.getInt(m, "id", 0));
                case "CHAT" -> {
                    String msg = Net.getString(m, "msg", "");
                    broadcast("CHAT " + Net.fields("id", id, "name", name, "msg", msg));
                }
                default -> {}
            }
        }

        private void onLeave() {
            if (id == -1) return;
            clients.remove(id);
            usedIds.remove(id);
            lastPos.remove(id);
            broadcast("PLAYER_LEAVE " + Net.fields("id", id));
            broadcast("SERVER_MSG msg=" + name + " вышел из игры");
            logger.log(name + " отключился (id=" + id + ")");
            close();
        }
    }
}
