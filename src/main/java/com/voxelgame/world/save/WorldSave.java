package com.voxelgame.world.save;

import com.voxelgame.world.Chunk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * On-disk world storage: saves/&lt;name&gt;/
 *
 *   meta.json      name, seed, game mode, timestamps, player state
 *   chunks/X_Z.dat gzip-compressed block array
 *
 * Only the block array is written. Light and the height map are derived
 * state and are recomputed on load, which keeps a chunk file at a few
 * hundred bytes for typical terrain instead of three times the size.
 *
 * Chunks are only written if they were modified, so an untouched world
 * costs nothing: terrain generation is deterministic from the seed, so an
 * absent file simply means "regenerate this one".
 */
public class WorldSave {

    /** Immutable snapshot of a modified chunk for background saving. */
    public static final class ChunkSnapshot {
        public final int cx, cz;
        public final short[] blocks;
        public final int[][] doors, crops, water;
        // Each container: {lx, ly, lz, typeOrdinal, serializedData}
        public final Object[][] containers;

        public ChunkSnapshot(int cx, int cz, short[] blocks,
                int[][] doors, int[][] crops, int[][] water, Object[][] containers) {
            this.cx = cx; this.cz = cz; this.blocks = blocks;
            this.doors = doors; this.crops = crops; this.water = water;
            this.containers = containers;
        }
    }

    private static final Path ROOT = Paths.get("saves");
    private static final int FORMAT_VERSION = 6;

    private final Path dir;
    private final WorldMeta meta;

    public WorldSave(WorldMeta meta) {
        this.meta = meta;
        this.dir = ROOT.resolve(sanitise(meta.folderName));
    }

    public WorldMeta getMeta() { return meta; }
    public Path getDirectory() { return dir; }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    /** Every readable world, newest first. */
    public static List<WorldMeta> listWorlds() {
        List<WorldMeta> out = new ArrayList<>();
        if (!Files.isDirectory(ROOT)) return out;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ROOT)) {
            for (Path p : stream) {
                if (!Files.isDirectory(p)) continue;
                WorldMeta m = WorldMeta.read(p.resolve("meta.json"));
                if (m != null) {
                    m.folderName = p.getFileName().toString();
                    out.add(m);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not list saves: " + e.getMessage());
        }

        out.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        return out;
    }

    public static boolean exists(String folderName) {
        return Files.isDirectory(ROOT.resolve(sanitise(folderName)));
    }

    /** Turn a display name into a folder name that is safe on any platform. */
    public static String sanitise(String name) {
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (s.isEmpty()) s = "world";
        return s;
    }

    /** Append a suffix until the name is free, as the real game does. */
    public static String uniqueFolderName(String desired) {
        String base = sanitise(desired);
        String candidate = base;
        int n = 1;
        while (exists(candidate)) {
            candidate = base + " (" + (++n) + ")";
        }
        return candidate;
    }

    /**
     * Mark the world as dead (hardcore loss). Writes the meta with the dead
     * flag so the world list can grey it out.
     */
    public void markDead() {
        meta.dead = true;
        saveMeta();
    }

    public static boolean delete(String folderName) {
        Path p = ROOT.resolve(sanitise(folderName));
        if (!Files.isDirectory(p)) return false;

        try (var walk = Files.walk(p)) {
            // Deepest entries first, so directories are empty when removed
            walk.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.delete(f);
                } catch (IOException e) {
                    System.err.println("Could not delete " + f + ": " + e.getMessage());
                }
            });
            return true;
        } catch (IOException e) {
            System.err.println("Could not delete world: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------

    public void saveMeta() {
        try {
            Files.createDirectories(dir);
            meta.lastPlayed = System.currentTimeMillis();
            meta.write(dir.resolve("meta.json"));
        } catch (IOException e) {
            System.err.println("Could not save world metadata: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Backups
    // ------------------------------------------------------------------

    /** How many rolling backups to keep per world. */
    private static final int MAX_BACKUPS = 3;
    /** Skip backing up when the last one was this recent. */
    private static final long BACKUP_MIN_INTERVAL_MS = 5 * 60 * 1000L;

    private long lastBackupTime = 0;

    /**
     * [GP-082] Snapshot every stored chunk file into backups/&lt;timestamp&gt;/.
     * Chunk files are tiny (a few hundred bytes each, gzipped), so a full
     * world backup costs less than a megabyte. The oldest backups are
     * pruned so at most {@link #MAX_BACKUPS} remain.
     */
    public void createBackup() {
        long now = System.currentTimeMillis();
        if (now - lastBackupTime < BACKUP_MIN_INTERVAL_MS) return;
        lastBackupTime = now;

        Path chunksDir = dir.resolve("chunks");
        if (!Files.isDirectory(chunksDir)) return;

        try {
            Path backupDir = dir.resolve("backups").resolve("backup_" + now);
            Files.createDirectories(backupDir);

            try (var walk = Files.walk(chunksDir)) {
                walk.filter(Files::isRegularFile).forEach(f -> {
                    try {
                        Files.copy(f, backupDir.resolve(f.getFileName()));
                    } catch (IOException e) {
                        System.err.println("Backup: could not copy " + f + ": " + e.getMessage());
                    }
                });
            }

            // Prune: keep only the newest MAX_BACKUPS
            Path backupsRoot = dir.resolve("backups");
            List<Path> old = new ArrayList<>();
            try (var s = Files.list(backupsRoot)) {
                s.filter(Files::isDirectory).forEach(old::add);
            }
            old.sort(Comparator.comparing(p -> p.getFileName().toString()));
            while (old.size() > MAX_BACKUPS) {
                deleteRecursive(old.remove(0));
            }

            System.out.println("Backup saved (" + old.size() + " kept)");
        } catch (IOException e) {
            System.err.println("Could not create backup: " + e.getMessage());
        }
    }

    private static void deleteRecursive(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.delete(f);
                } catch (IOException e) {
                    // best effort
                }
            });
        } catch (IOException e) {
            System.err.println("Could not prune backup " + root + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Chunks
    // ------------------------------------------------------------------

    private Path chunkPath(int cx, int cz) {
        return dir.resolve("chunks").resolve(cx + "_" + cz + ".dat");
    }

    public boolean hasChunk(int cx, int cz) {
        return Files.isRegularFile(chunkPath(cx, cz));
    }

    /**
     * Write a chunk's blocks. Called only for chunks the player changed.
     *
     * @param containers containers inside this chunk: {x, y, z, ContainerData}
     */
    public void saveChunk(Chunk chunk, java.util.List<Object[]> containers) {
        Path p = chunkPath(chunk.getChunkX(), chunk.getChunkZ());
        try {
            Files.createDirectories(p.getParent());

            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(
                        new GZIPOutputStream(Files.newOutputStream(tmp))))) {

                out.writeInt(FORMAT_VERSION);
                out.writeInt(chunk.getChunkX());
                out.writeInt(chunk.getChunkZ());

                // [BASE] Format 6 stores blocks as 16-bit ids (0..511).
                short[] blocks = chunk.getBlocks();
                out.writeInt(blocks.length);
                for (short s : blocks) {
                    out.writeShort(s);
                }

                // [SD] Sliding-door state, keyed by the bottom cell.
                java.util.List<int[]> doors = chunk.doorMetaEntries();
                out.writeInt(doors.size());
                for (int[] d : doors) {
                    out.writeByte(d[0]);
                    out.writeByte(d[1]);
                    out.writeByte(d[2]);
                    out.writeByte(d[3]);
                }

                // [CR] Crop growth stages (format 3).
                java.util.List<int[]> crops = chunk.cropMetaEntries();
                out.writeInt(crops.size());
                for (int[] c : crops) {
                    out.writeByte(c[0]);
                    out.writeByte(c[1]);
                    out.writeByte(c[2]);
                    out.writeByte(c[3]);
                }

                // [WQ] Flowing-water levels (format 5).
                java.util.List<int[]> water = chunk.waterMetaEntries();
                out.writeInt(water.size());
                for (int[] w : water) {
                    out.writeByte(w[0]);
                    out.writeByte(w[1]);
                    out.writeByte(w[2]);
                    out.writeByte(w[3]);
                }

                // [CF] Container contents and furnace state (format 4).
                out.writeInt(containers == null ? 0 : containers.size());
                if (containers != null) {
                    for (Object[] c : containers) {
                        out.writeByte((Integer) c[0] & 15);
                        out.writeByte((Integer) c[1]);
                        out.writeByte((Integer) c[2] & 15);
                        com.voxelgame.world.container.ContainerData cd =
                            (com.voxelgame.world.container.ContainerData) c[3];
                        out.writeByte(cd.type.ordinal());
                        out.writeUTF(cd.serialize());
                    }
                }
            }
            // Atomic replace: prevents corruption if the process crashes mid-write
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not save chunk " + chunk.getChunkX()
                + "," + chunk.getChunkZ() + ": " + e.getMessage());
        }
    }

    /**
     * Read a chunk previously written by {@link #saveChunk}.
     *
     * @return the chunk, or null when it was never modified and should be
     *         regenerated from the seed instead
     */
    public Chunk loadChunk(int cx, int cz) {
        Path p = chunkPath(cx, cz);
        if (!Files.isRegularFile(p)) return null;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(p))))) {

            int version = in.readInt();
            if (version < 1 || version > FORMAT_VERSION) {
                System.err.println("Skipping chunk with format " + version);
                return null;
            }

            int fx = in.readInt();
            int fz = in.readInt();
            if (fx != cx || fz != cz) {
                System.err.println("Chunk file coordinate mismatch, ignoring");
                return null;
            }

            int length = in.readInt();
            if (length != Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE) {
                System.err.println("Corrupt chunk data at " + cx + "," + cz
                    + ": expected " + (Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE)
                    + " blocks, got " + length);
                return null;
            }
            Chunk chunk = new Chunk(cx, cz);
            if (version >= 6) {
                // [BASE] Format 6: 16-bit block ids (extended base blocks)
                short[] blocks = new short[length];
                for (int i = 0; i < length; i++) {
                    blocks[i] = in.readShort();
                }
                chunk.loadBlocks(blocks);
            } else {
                // Formats 1-5: 8-bit block ids
                byte[] blocks = new byte[length];
                in.readFully(blocks);
                chunk.loadBlocks(blocks);
            }

            // [SD] Door meta appeared in format 2; older saves have none.
            if (version >= 2) {
                int doorCount = in.readInt();
                for (int i = 0; i < doorCount; i++) {
                    int dx = in.readByte() & 0xFF;
                    int dy = in.readByte() & 0xFF;
                    int dz = in.readByte() & 0xFF;
                    byte meta = in.readByte();
                    chunk.loadDoorMeta(dx, dy, dz, meta);
                }
            }

            // [CR] Crop growth stages appeared in format 3.
            if (version >= 3) {
                int cropCount = in.readInt();
                for (int i = 0; i < cropCount; i++) {
                    int cx2 = in.readByte() & 0xFF;
                    int cy2 = in.readByte() & 0xFF;
                    int cz2 = in.readByte() & 0xFF;
                    int stage = in.readByte() & 0xFF;
                    chunk.loadCropMeta(cx2, cy2, cz2, stage);
                }
            }

                        // [WQ] Flowing-water levels appeared in format 5.
            if (version >= 5) {
                int waterCount = in.readInt();
                for (int i = 0; i < waterCount; i++) {
                    int wx = in.readByte() & 0xFF;
                    int wy = in.readByte() & 0xFF;
                    int wz = in.readByte() & 0xFF;
                    int level = in.readByte() & 0xFF;
                    chunk.loadWaterMeta(wx, wy, wz, level);
                }
            }

            // [CF] Container contents and furnace state appeared in format 4.
            if (version >= 4) {
                int containerCount = in.readInt();
                for (int i = 0; i < containerCount; i++) {
                    int lx = in.readByte() & 0xFF;
                    int ly = in.readByte() & 0xFF;
                    int lz = in.readByte() & 0xFF;
                    int typeOrdinal = in.readByte() & 0xFF;
                    chunk.loadContainerEntry(lx, ly, lz, typeOrdinal, in.readUTF());
                }
            }
            return chunk;

        } catch (IOException e) {
            System.err.println("Could not load chunk " + cx + "," + cz
                + ": " + e.getMessage());
            return null;
        }
    }

    /** Number of chunk files on disk, for the world list. */
    public int countStoredChunks() {
        Path c = dir.resolve("chunks");
        if (!Files.isDirectory(c)) return 0;
        try (var s = Files.list(c)) {
            return (int) s.count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Write a list of chunk snapshots to disk atomically.
     * Called from the background save thread.
     *
     * @return number of chunks written
     */
    public int writeSnapshots(java.util.List<ChunkSnapshot> snapshots) {
        int written = 0;
        for (ChunkSnapshot snap : snapshots) {
            Path p = chunkPath(snap.cx, snap.cz);
            try {
                Files.createDirectories(p.getParent());
                Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
                try (DataOutputStream out = new DataOutputStream(
                        new BufferedOutputStream(
                            new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                    out.writeInt(FORMAT_VERSION);
                    out.writeInt(snap.cx);
                    out.writeInt(snap.cz);
                    out.writeInt(snap.blocks.length);
                    for (short b : snap.blocks) out.writeShort(b);
                    out.writeInt(snap.doors.length);
                    for (int[] d : snap.doors) { out.writeByte(d[0]); out.writeByte(d[1]); out.writeByte(d[2]); out.writeByte(d[3]); }
                    out.writeInt(snap.crops.length);
                    for (int[] c2 : snap.crops) { out.writeByte(c2[0]); out.writeByte(c2[1]); out.writeByte(c2[2]); out.writeByte(c2[3]); }
                    out.writeInt(snap.water.length);
                    for (int[] w : snap.water) { out.writeByte(w[0]); out.writeByte(w[1]); out.writeByte(w[2]); out.writeByte(w[3]); }
                    out.writeInt(snap.containers.length);
                    for (Object[] c2 : snap.containers) {
                        out.writeByte(((Integer) c2[0]) & 15);
                        out.writeByte((Integer) c2[1]);
                        out.writeByte(((Integer) c2[2]) & 15);
                        out.writeByte((Integer) c2[3]); // typeOrdinal
                        out.writeUTF((String) c2[4]);    // serialized
                    }
                }
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
                written++;
            } catch (IOException e) {
                System.err.println("Could not write snapshot " + snap.cx + "," + snap.cz + ": " + e.getMessage());
            }
        }
        return written;
    }
}
