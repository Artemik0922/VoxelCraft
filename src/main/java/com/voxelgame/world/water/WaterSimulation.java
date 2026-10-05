package com.voxelgame.world.water;

import com.voxelgame.world.BlockType;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.World;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft-style water physics.
 *
 * Every water cell carries a level 0-7. Level 0 is a source (ocean, bucket
 * placement, a falling column) and never drains; higher levels spread away
 * from sources, rising by 1 per horizontal block. Water flows straight down
 * first, then spreads sideways across solid ground or over water.
 *
 * Two event-driven passes, both drained by {@link #processSpread()}:
 * <ul>
 *   <li>SPREAD - water pours into empty cells (placement, digging through
 *       a wall next to water).</li>
 *   <li>REFLOW - after a water cell disappears (broken, or replaced by a
 *       placed block) the connected flowing-water component around the
 *       change is re-leveled. Levels are recomputed as distances from the
 *       static source cells (anchors) that border the component; cells with
 *       no source path within 7 blocks drain, air cells next to the water
 *       get refilled when a source can still reach them. This is a
 *       multi-source Bellman-Ford pass, so a wall placed across a pool
 *       drains the cut-off side instead of leaving a self-consistent
 *       "false plateau" of levels.</li>
 * </ul>
 * Both are bounded per call so a hostile setup can never stall the game.
 */
public class WaterSimulation {

    private static final int MAX_LEVEL = 7;

    private final World world;
    private final ArrayDeque<WaterNode> spreadQueue = new ArrayDeque<>();

    /** Upper bound for one processSpread() call. */
    private static final int UPDATE_LIMIT = 1 << 16;

    /** Largest flowing-water component re-leveled in one reflow pass. */
    private static final int COMPONENT_LIMIT = 4096;

    private static final int[][] DIRS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIRS6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                                          {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static class WaterNode {
        int x, y, z, level;
        WaterNode(int x, int y, int z, int level) {
            this.x = x; this.y = y; this.z = z; this.level = level;
        }
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x << 42) | ((long) y << 21) | (z & 0x1FFFFF);
    }

    public WaterSimulation(World world) {
        this.world = world;
    }

    /**
     * Add a water source at the given position (level 0, immortal).
     */
    public void addSource(int x, int y, int z) {
        spreadQueue.add(new WaterNode(x, y, z, 0));
    }

    /**
     * Add water with a specific level; used to re-trigger spreading from an
     * existing cell (e.g. a wall next to a pool was dug out).
     */
    public void addWater(int x, int y, int z, int level) {
        if (level >= 0 && level <= MAX_LEVEL) {
            spreadQueue.add(new WaterNode(x, y, z, level));
        }
    }

    /**
     * Process all pending water spreading (reflow runs synchronously).
     */
    public void processSpread() {
        // [PERF] Массовый залив: сотни setBlock — свет одним проходом в конце
        world.beginBatchEdits();
        try {
            int budget = UPDATE_LIMIT;
            while (!spreadQueue.isEmpty() && budget-- > 0) {
                WaterNode node = spreadQueue.poll();
                spreadFrom(node.x, node.y, node.z, node.level);
            }
        } finally {
            world.endBatchEdits();
        }
    }

    /**
     * A cell stopped being water (broken or replaced by a placed block):
     * re-level the surrounding component and let surviving water pour into
     * the gap it left.
     */
    public void removeWater(int x, int y, int z) {
        reflow(x, y, z);
        pourFromNeighbors(x, y, z);
    }

    /**
     * A non-water block was removed: any water touching the gap pours into
     * it (digging through a wall lets the pool flow out).
     */
    public void pourFromNeighbors(int x, int y, int z) {
        for (int[] d : DIRS6) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (world.getBlock(nx, ny, nz) == BlockType.WATER.id) {
                spreadQueue.add(new WaterNode(nx, ny, nz, world.getWaterLevel(nx, ny, nz)));
            }
        }
    }

    /**
     * Spread water from a position. Flows straight down first (the falling
     * column keeps its level); otherwise spreads horizontally, one level
     * higher per block, only over solid ground or existing water.
     */
    private void spreadFrom(int x, int y, int z, int level) {
        if (y <= 0) return;

        int below = world.getBlock(x, y - 1, z);
        if (below == BlockType.AIR.id) {
            world.setBlock(x, y - 1, z, BlockType.WATER.id);
            world.setWaterLevel(x, y - 1, z, 0); // falling columns are level 0
            spreadQueue.add(new WaterNode(x, y - 1, z, level));
            return;
        }

        if (level >= MAX_LEVEL) return;

        int newLevel = level + 1;
        for (int[] d : DIRS4) {
            int nx = x + d[0];
            int nz = z + d[1];

            int neighbor = world.getBlock(nx, y, nz);
            if (neighbor == BlockType.AIR.id) {
                int neighborBelow = world.getBlock(nx, y - 1, nz);
                if (neighborBelow != BlockType.AIR.id) {
                    world.setBlock(nx, y, nz, BlockType.WATER.id);
                    world.setWaterLevel(nx, y, nz, newLevel);
                    spreadQueue.add(new WaterNode(nx, y, nz, newLevel));
                }
            } else if (neighbor == BlockType.WATER.id) {
                int old = world.getWaterLevel(nx, y, nz);
                if (old > newLevel) {
                    // A closer source reached an already-flowing cell:
                    // adopt the shorter distance and keep spreading.
                    world.setWaterLevel(nx, y, nz, newLevel);
                    spreadQueue.add(new WaterNode(nx, y, nz, newLevel));
                }
            }
        }
    }

    /**
     * Re-level the connected flowing-water component around a change.
     *
     * Step 1: flood-fill the component from the change's water neighbours,
     * stopping at static (source) water cells. Step 2: Bellman-Ford
     * distance pass from every static cell bordering the component (an
     * anchor, level 0), with the falling rules: a cell under water keeps
     * its level (0), a cell above water feeds it 0. Cells farther than
     * MAX_LEVEL from any anchor drain; the rest are re-leveled. Finally an
     * air cell left by the change is refilled when an anchor can reach it
     * within MAX_LEVEL.
     */
    private void reflow(int x, int y, int z) {
        // [PERF] Рефлоу правит до 4096 ячеек — свет одним проходом в конце
        world.beginBatchEdits();
        try {
            reflowInner(x, y, z);
        } finally {
            world.endBatchEdits();
        }
    }

    private void reflowInner(int x, int y, int z) {
        // --- Gather the component and mark its static anchors ---
        Map<Long, Integer> index = new HashMap<>();
        java.util.List<int[]> component = new java.util.ArrayList<>();
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        Map<Long, Boolean> anchored = new HashMap<>();

        for (int[] d : DIRS6) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (world.getBlock(nx, ny, nz) == BlockType.WATER.id) {
                frontier.add(new int[]{nx, ny, nz});
            }
        }

        while (!frontier.isEmpty() && component.size() < COMPONENT_LIMIT) {
            int[] c = frontier.poll();
            long k = cellKey(c[0], c[1], c[2]);
            if (index.containsKey(k)) continue;
            if (!world.hasWaterMeta(c[0], c[1], c[2])) {
                anchored.put(k, Boolean.TRUE); // static source: boundary
                continue;
            }
            index.put(k, component.size());
            component.add(c);
            for (int[] d : DIRS6) {
                int nx = c[0] + d[0];
                int ny = c[1] + d[1];
                int nz = c[2] + d[2];
                if (world.getBlock(nx, ny, nz) == BlockType.WATER.id
                    && !index.containsKey(cellKey(nx, ny, nz))) {
                    frontier.add(new int[]{nx, ny, nz});
                }
            }
        }
        if (component.isEmpty()) {
            // Everything around the change was static source water: the
            // gap simply refills at the level of its nearest source.
            refillGap(x, y, z, index, anchored, new int[0]);
            return;
        }

        // --- Multi-source distance pass from the anchors ---
        int[] lv = new int[component.size()];
        java.util.Arrays.fill(lv, MAX_LEVEL + 1);

        // Anchor level: 0 for static water, their stored level for flowing
        // cells left outside the component cap (assumed still valid).
        for (int pass = 0; pass <= MAX_LEVEL; pass++) {
            boolean changed = false;
            for (int i = 0; i < component.size(); i++) {
                int[] c = component.get(i);
                int best = MAX_LEVEL + 1;

                for (int[] d : DIRS4) {
                    int nx = c[0] + d[0];
                    int nz = c[2] + d[1];
                    if (world.getBlock(nx, c[1], nz) != BlockType.WATER.id) continue;
                    int l = levelOf(nx, c[1], nz, index, anchored, lv);
                    if (l < MAX_LEVEL) best = Math.min(best, l + 1);
                }
                if (c[1] > 0 && world.getBlock(c[0], c[1] - 1, c[2]) == BlockType.WATER.id) {
                    best = Math.min(best, levelOf(c[0], c[1] - 1, c[2], index, anchored, lv));
                }
                if (c[1] + 1 < Chunk.HEIGHT
                    && world.getBlock(c[0], c[1] + 1, c[2]) == BlockType.WATER.id) {
                    best = 0; // under a column of water
                }
                if (best < lv[i]) {
                    lv[i] = best;
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // --- Apply: drain or re-level ---
        for (int i = 0; i < component.size(); i++) {
            int[] c = component.get(i);
            if (lv[i] > MAX_LEVEL) {
                if (world.getBlock(c[0], c[1], c[2]) == BlockType.WATER.id) {
                    world.setBlock(c[0], c[1], c[2], BlockType.AIR.id);
                    world.setWaterLevel(c[0], c[1], c[2], 0);
                }
            } else if (lv[i] != world.getWaterLevel(c[0], c[1], c[2])) {
                world.setWaterLevel(c[0], c[1], c[2], lv[i]);
            }
        }

        // --- Refill the gap the change left, if a source still reaches it ---
        refillGap(x, y, z, index, anchored, lv);
    }

    private void refillGap(int x, int y, int z, Map<Long, Integer> index,
                           Map<Long, Boolean> anchored, int[] lv) {
        if (world.getBlock(x, y, z) != BlockType.AIR.id) return;
        int best = bestLevelFor(x, y, z, index, anchored, lv);
        if (best <= MAX_LEVEL) {
            world.setBlock(x, y, z, BlockType.WATER.id);
            world.setWaterLevel(x, y, z, best);
        }
    }

    /**
     * Current level of a water cell: in-component cells use their fresh
     * distance, static cells are 0, out-of-cap cells keep their stored
     * level. Returns MAX_LEVEL + 1 for non-water cells.
     */
    private int levelOf(int x, int y, int z, Map<Long, Integer> index,
                        Map<Long, Boolean> anchored, int[] lv) {
        Integer i = index.get(cellKey(x, y, z));
        if (i != null) return lv[i];
        if (anchored.containsKey(cellKey(x, y, z))) return 0;
        if (world.getBlock(x, y, z) == BlockType.WATER.id) {
            return world.getWaterLevel(x, y, z);
        }
        return MAX_LEVEL + 1;
    }

    /** Best achievable level for an air cell: 8 = no water can reach it. */
    private int bestLevelFor(int x, int y, int z, Map<Long, Integer> index,
                             Map<Long, Boolean> anchored, int[] lv) {
        int best = MAX_LEVEL + 1;
        for (int[] d : DIRS4) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (world.getBlock(nx, y, nz) == BlockType.WATER.id) {
                best = Math.min(best, levelOf(nx, y, nz, index, anchored, lv) + 1);
            }
        }
        if (y > 0 && world.getBlock(x, y - 1, z) == BlockType.WATER.id) {
            best = Math.min(best, levelOf(x, y - 1, z, index, anchored, lv));
        }
        if (y + 1 < Chunk.HEIGHT && world.getBlock(x, y + 1, z) == BlockType.WATER.id) {
            best = 0; // under a column of water
        }
        return best;
    }

    /**
     * Check if a position is a valid water container (has floor and walls).
     */
    public static boolean isContainer(World world, int x, int y, int z) {
        int below = world.getBlock(x, y - 1, z);
        return below != BlockType.AIR.id && below != BlockType.WATER.id;
    }
}