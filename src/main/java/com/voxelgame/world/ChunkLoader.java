package com.voxelgame.world;

import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background chunk generation.
 *
 * Terrain generation is a pure function of (seed, coordinate), so it can run
 * on a worker pool with no locking. Finished chunks are handed back through a
 * queue and published on the main thread, where a small budget per frame
 * keeps the hand-off from becoming its own stall.
 *
 * Doing this inline cost roughly 285 ms whenever the player crossed a chunk
 * boundary at render distance 8 - about 17 dropped frames.
 */
public class ChunkLoader {

    /** Chunks published per frame. Bounded so a burst cannot spike a frame. */
    private static final int PUBLISH_BUDGET = 2;

    private final ExecutorService pool;
    private final int workerCount;

    /** Coordinates currently being generated, so work is never duplicated. */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    private final ConcurrentLinkedQueue<Chunk> completed = new ConcurrentLinkedQueue<>();

    private final AtomicInteger generatedTotal = new AtomicInteger();

    private volatile boolean running = true;

    public ChunkLoader() {
        // Leave a core for the render thread; one worker is enough on 2-core
        workerCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        pool = Executors.newFixedThreadPool(workerCount, r -> {
            Thread t = new Thread(r, "chunk-worker");
            t.setDaemon(true);
            // Never let generation starve rendering
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        System.out.println("Chunk loader: " + workerCount + " worker thread(s)");
    }

    public int getWorkerCount() { return workerCount; }
    public int getPendingCount() { return inFlight.size(); }
    public int getReadyCount() { return completed.size(); }
    public int getGeneratedTotal() { return generatedTotal.get(); }

    /**
     * Queue a chunk for generation unless it is already loaded or in flight.
     */
    public void request(int cx, int cz, com.voxelgame.world.generator.TerrainGenerator generator) {
        if (!running) return;

        long key = Chunk.key(cx, cz);
        if (!inFlight.add(key)) return;

        pool.execute(() -> {
            try {
                Chunk chunk = new Chunk(cx, cz);
                generator.generate(chunk);
                generator.generateStructures(chunk);
                completed.add(chunk);
                generatedTotal.incrementAndGet();
            } catch (Throwable t) {
                System.err.println("Chunk generation failed at " + cx + "," + cz + ": " + t);
                inFlight.remove(key);
            }
        });
    }

    /**
     * Move finished chunks into the world. Main thread only.
     *
     * @return chunks published this frame
     */
    public List<Chunk> collect(Map<Long, Chunk> target) {
        List<Chunk> published = new ArrayList<>(PUBLISH_BUDGET);

        for (int i = 0; i < PUBLISH_BUDGET; i++) {
            Chunk chunk = completed.poll();
            if (chunk == null) break;

            long key = Chunk.key(chunk.getChunkX(), chunk.getChunkZ());
            inFlight.remove(key);

            // The player may have moved away while this was generating
            target.putIfAbsent(key, chunk);
            published.add(chunk);
        }

        return published;
    }

    /** Forget a pending request, e.g. when the chunk drifts out of range. */
    public void cancel(int cx, int cz) {
        inFlight.remove(Chunk.key(cx, cz));
    }

    public void shutdown() {
        running = false;
        pool.shutdownNow();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
