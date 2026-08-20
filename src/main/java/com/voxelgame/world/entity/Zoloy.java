package com.voxelgame.world.entity;

import com.voxelgame.audio.AudioManager;
import com.voxelgame.player.Player;
import com.voxelgame.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Zoloy (Пепельный прислужник) — hostile mob with patrol/chase/attack AI.
 *
 * States: PATROL (wander to random point) -> CHASE (pursue player within 16 blocks)
 * -> ATTACK (strike within 1.5 blocks). Aggro hysteresis: de-aggros at >24 blocks.
 * HP = 30, damage = 4, attack cooldown = 1.2s.
 *
 * [GP-042] Physics runs against block AABBs like the player: axis-by-axis
 * movement with collision resolution and 1-block step-up.
 * [GP-036] Chasing uses A* pathfinding over the block grid so mobs no longer
 * get stuck on walls; the path is replanned every 0.5s or when the target
 * moves significantly.
 * [GP-038] Taking damage applies knockback and flashes the mob red.
 */
public class Zoloy {

    public enum State { PATROL, CHASE, ATTACK }

    // Stats
    private static final float MAX_HP = 30.0f;
    private static final float DAMAGE = 4.0f;
    private static final float ATTACK_COOLDOWN = 1.2f;
    private static final float ATTACK_DURATION = 0.3f;

    // Movement
    private static final float PATROL_SPEED = 1.2f;
    private static final float CHASE_SPEED = 2.3f;
    private static final float GRAVITY = 28.0f;
    private static final float MAX_FALL_SPEED = 78.4f;

    // Body: 0.6 wide, 1.8 tall (matches the player, so both squeeze through
    // the same gaps and stand on the same ledges)
    private static final float HALF_WIDTH = 0.3f;
    private static final float HEIGHT = 1.8f;
    private static final float STEP_UP = 1.0f;

    // Jumping: ~1.09 blocks of height, so jump + auto-step clears 2-block
    // obstacles and 2-deep pits, like the player
    private static final float JUMP_SPEED = 7.8f;
    private static final float JUMP_COOLDOWN = 1.2f;

    // AI ranges
    private static final float AGGRO_RANGE = 16.0f;
    private static final float DEAGGRO_RANGE = 24.0f;
    private static final float ATTACK_RANGE = 1.5f;

    // Pathfinding
    private static final float REPLAN_INTERVAL = 0.5f;
    private static final float REPLAN_TARGET_MOVED = 1.5f;
    private static final int MAX_PATH_STEPS = 128;

    private final World world;
    private final Vector3f position;
    private final Vector3f velocity = new Vector3f();
    private float yaw;

    /** Pushed into an obstacle higher than the auto-step (jump trigger). */
    private boolean blockedByObstacle = false;
    private float jumpCooldown = 0;
    /** Direction the mob is pushing into the obstacle (normalized at use). */
    private float pushDirX = 0, pushDirZ = 0;

    private float hp = MAX_HP;
    private boolean dead = false;
    private State state = State.PATROL;
    private boolean debugTpose = false;
    private boolean onGround = false;

    // Animation
    private float walkPhase = 0;
    private float attackTimer = 0;
    private float attackCooldown = 0;
    private float limbSwingAmount = 0;

    // [GP-038] Red flash while hurt
    private float hurtTimer = 0;
    private static final float HURT_DURATION = 0.25f;

    // [GP-044] Ambient voice: growls while idle, snarls when hostile
    private float idleSoundTimer = 3.0f + (float) (Math.random() * 5.0f);
    private boolean aggroSoundPlayed = false;

    // Patrol target
    private final Vector3f patrolTarget = new Vector3f();
    private float patrolRetargetTimer = 0;
    private boolean hasPatrolTarget = false;

    // [GP-036] A* path: block coordinates of the waypoints ahead
    private final List<int[]> path = new ArrayList<>();
    private int pathIndex = 0;
    private float replanTimer = 0;
    private final Vector3f lastTargetPos = new Vector3f(Float.MAX_VALUE);

    public Zoloy(World world, float x, float y, float z) {
        this.world = world;
        this.position = new Vector3f(x, y, z);
        this.yaw = (float) (Math.random() * Math.PI * 2);
    }

    public void update(float dt, Player player) {
        if (dead) return;

        if (attackCooldown > 0) attackCooldown -= dt;
        if (hurtTimer > 0) hurtTimer -= dt;

        Vector3f playerPos = player.getPosition();
        float dx = playerPos.x - position.x;
        float dz = playerPos.z - position.z;
        float distToPlayer = (float) Math.sqrt(dx * dx + dz * dz);

        // [GP-044] Occasional idle growl, only heard near the player
        idleSoundTimer -= dt;
        if (idleSoundTimer <= 0 && distToPlayer < 18.0f) {
            AudioManager.play("sounds/zoloy", 0.9f + (float) Math.random() * 0.3f, 0.30f);
            idleSoundTimer = 6.0f + (float) (Math.random() * 8.0f);
        }

        // --- State machine with hysteresis ---
        switch (state) {
            case PATROL:
                if (distToPlayer < AGGRO_RANGE) {
                    state = State.CHASE;
                    path.clear();
                    // [GP-044] Snarl when the mob first notices the player
                    if (!aggroSoundPlayed) {
                        aggroSoundPlayed = true;
                        AudioManager.play("sounds/zoloy/5", 1.0f, 0.45f);
                    }
                }
                break;
            case CHASE:
                if (distToPlayer < ATTACK_RANGE && attackCooldown <= 0) {
                    state = State.ATTACK;
                    attackTimer = 0;
                    // [GP-044] Lunge snarl on each strike
                    AudioManager.play("sounds/zoloy/5", 1.0f + (float) Math.random() * 0.2f, 0.5f);
                } else if (distToPlayer > DEAGGRO_RANGE) {
                    state = State.PATROL;
                    hasPatrolTarget = false;
                    path.clear();
                    aggroSoundPlayed = false;
                }
                break;
            case ATTACK:
                attackTimer += dt;
                if (attackTimer >= ATTACK_DURATION) {
                    if (distToPlayer < ATTACK_RANGE + 0.5f) {
                        // [GP-037] Proper death cause on the death screen
                        player.takeDamage((int) DAMAGE, Player.DeathCause.MOB);
                        // [GP-025] Blow shoves the player away from the mob
                        float kx = playerPos.x - position.x;
                        float kz = playerPos.z - position.z;
                        float klen = (float) Math.sqrt(kx * kx + kz * kz);
                        if (klen > 0.001f) {
                            org.joml.Vector3f pv = player.getVelocity();
                            pv.add((kx / klen) * 4.0f, 2.5f, (kz / klen) * 4.0f);
                        }
                    }
                    attackCooldown = ATTACK_COOLDOWN;
                    state = State.CHASE;
                }
                break;
        }

        // --- Movement ---
        switch (state) {
            case PATROL:
                updatePatrol(dt);
                break;
            case CHASE:
                // Лицо модели смотрит в -Z, поэтому к цели — atan2(-dx,-dz)
                yaw = (float) Math.atan2(-dx, -dz);
                if (distToPlayer > ATTACK_RANGE * 0.8f) {
                    chasePlayer(playerPos, dt);
                } else {
                    velocity.x = 0;
                    velocity.z = 0;
                }
                break;
            case ATTACK:
                // Face player during attack
                yaw = (float) Math.atan2(-dx, -dz);
                velocity.x = 0;
                velocity.z = 0;
                break;
        }

        // --- Physics (gravity + block collision) ---
        applyPhysics(dt);

        // --- Animation ---
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        walkPhase += dt * (7.0f + speed * 2.0f);
        limbSwingAmount = Math.min(speed / CHASE_SPEED, 1.0f);
    }

    private void updatePatrol(float dt) {
        patrolRetargetTimer -= dt;
        if (!hasPatrolTarget || patrolRetargetTimer <= 0) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = 3.0f + (float) Math.random() * 5.0f;
            patrolTarget.set(
                position.x + (float) Math.cos(angle) * dist,
                position.y,
                position.z + (float) Math.sin(angle) * dist
            );
            hasPatrolTarget = true;
            patrolRetargetTimer = 3.0f + (float) Math.random() * 4.0f;
        }

        float tx = patrolTarget.x - position.x;
        float tz = patrolTarget.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);
        if (dist > 0.5f) {
            yaw = (float) Math.atan2(-tx, -tz);
            moveToward(patrolTarget, PATROL_SPEED, dt);
        } else {
            hasPatrolTarget = false;
        }
    }

    // ------------------------------------------------------------------
    // [GP-036] A* chase
    // ------------------------------------------------------------------

    private void chasePlayer(Vector3f playerPos, float dt) {
        // Replan when the path ran out, is stale, or the target wandered off
        replanTimer -= dt;
        float moved = Math.abs(playerPos.x - lastTargetPos.x)
                    + Math.abs(playerPos.z - lastTargetPos.z);
        boolean pathDone = pathIndex >= path.size();
        if (pathDone || replanTimer <= 0 || moved > REPLAN_TARGET_MOVED) {
            replanTimer = REPLAN_INTERVAL;
            lastTargetPos.set(playerPos);
            findPath((int) Math.floor(position.x), (int) Math.floor(position.z),
                     (int) Math.floor(playerPos.x), (int) Math.floor(playerPos.z));
        }

        if (pathIndex >= path.size()) {
            // No path: walk straight at the player rather than standing still
            moveToward(playerPos, CHASE_SPEED, dt);
            return;
        }

        int[] wp = path.get(pathIndex);
        float wx = wp[0] + 0.5f;
        float wz = wp[1] + 0.5f;
        float dx = wx - position.x;
        float dz = wz - position.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.45f) {
            pathIndex++;
            return;
        }

        yaw = (float) Math.atan2(-dx, -dz);
        velocity.x = (dx / dist) * CHASE_SPEED;
        velocity.z = (dz / dist) * CHASE_SPEED;
    }

    /**
     * A* over a 2D grid of blocks. A cell is walkable when the mob's head
     * clears it, the step up is at most one block and the drop is not a
     * cliff edge. Diagonal moves require both orthogonal neighbours to be
     * walkable, so the mob never cuts corners through walls.
     */
    private void findPath(int sx, int sz, int tx, int tz) {
        path.clear();
        pathIndex = 0;

        if (!isWalkable(sx, sz)) return;

        // Trivial case: adjacent to the target
        if (Math.abs(sx - tx) + Math.abs(sz - tz) <= 1) {
            path.add(new int[]{tx, tz});
            return;
        }

        Map<Long, int[]> cameFrom = new HashMap<>();
        Map<Long, Float> gScore = new HashMap<>();
        PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a[2]));
        Set<Long> closed = new HashSet<>();

        long startKey = key(sx, sz);
        gScore.put(startKey, 0.0f);
        open.add(new double[]{sx, sz, heuristic(sx, sz, tx, tz)});

        long targetKey = key(tx, tz);
        boolean found = false;
        int steps = 0;

        int[][] dirs = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        while (!open.isEmpty() && steps++ < MAX_PATH_STEPS) {
            double[] cur = open.poll();
            int cx = (int) cur[0];
            int cz = (int) cur[1];
            long ck = key(cx, cz);
            if (closed.contains(ck)) continue;
            if (ck == targetKey) { found = true; break; }
            closed.add(ck);

            float g = gScore.getOrDefault(ck, Float.MAX_VALUE);
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                long nk = key(nx, nz);
                if (closed.contains(nk)) continue;

                boolean diagonal = d[0] != 0 && d[1] != 0;
                if (diagonal && (!isWalkable(cx + d[0], cz) || !isWalkable(cx, cz + d[1]))) {
                    continue;
                }
                if (!isWalkable(nx, nz)) continue;

                float cost = g + (diagonal ? 1.414f : 1.0f);
                if (cost < gScore.getOrDefault(nk, Float.MAX_VALUE)) {
                    gScore.put(nk, cost);
                    cameFrom.put(nk, new int[]{cx, cz});
                    open.add(new double[]{nx, nz, cost + heuristic(nx, nz, tx, tz)});
                }
            }
        }

        if (!found) return;

        // Rebuild the path from the target back to the start
        List<int[]> rev = new ArrayList<>();
        long ck = targetKey;
        while (cameFrom.containsKey(ck)) {
            int[] parent = cameFrom.get(ck);
            rev.add(new int[]{(int) (ck >> 32), (int) (ck & 0xFFFFFFFFL)});
            ck = key(parent[0], parent[1]);
            if (rev.size() > MAX_PATH_STEPS) break;
        }
        for (int i = rev.size() - 1; i >= 0; i--) {
            path.add(rev.get(i));
        }
    }

    private float heuristic(int ax, int az, int bx, int bz) {
        return Math.abs(ax - bx) + Math.abs(az - bz);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * A cell is walkable when it has a floor, head room for the mob, and the
     * step up from the current cell is at most two blocks (jump + auto-step
     * clears 2-block climbs and 2-deep pits). Drops of up to 3 blocks are
     * allowed; deeper cliffs are treated as blocked so the mob walks around
     * instead of falling into pits it cannot climb out of.
     */
    private boolean isWalkable(int x, int z) {
        int groundY = world.getGroundHeight(x, z);
        if (groundY <= 0) return false;

        int standY = Math.max(groundY, (int) Math.floor(position.y) - 1);

        // Step up at most 2 blocks from the current stand height
        if (groundY > standY + 2) return false;
        // No cliff edges: refuse drops deeper than 3 blocks
        if (groundY < standY - 3) return false;

        // Head room: two free cells above the floor
        if (world.isSolid(x, groundY + 1, z)) return false;
        if (world.isSolid(x, groundY + 2, z)) return false;
        return !world.isSolid(x, groundY, z);
    }

    private void moveToward(Vector3f target, float speed, float dt) {
        float dx = target.x - position.x;
        float dz = target.z - position.z;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01f) return;
        velocity.x = (dx / len) * speed;
        velocity.z = (dz / len) * speed;
    }

    // ------------------------------------------------------------------
    // [GP-042] Physics: gravity + AABB collision
    // ------------------------------------------------------------------

    private void applyPhysics(float dt) {
        onGround = false;
        blockedByObstacle = false;
        pushDirX = 0;
        pushDirZ = 0;

        velocity.y -= GRAVITY * dt;
        if (velocity.y < -MAX_FALL_SPEED) velocity.y = -MAX_FALL_SPEED;

        moveAxis(velocity.x * dt, 0);
        moveAxis(0, velocity.z * dt);

        // Vertical: fall until something solid is underfoot
        position.y += velocity.y * dt;
        resolveVertical();

        // Pushed into an obstacle higher than the auto-step: jump like the
        // player, so 2-block walls and 2-deep pits are passable
        if (blockedByObstacle && onGround && jumpCooldown <= 0 && jumpClearAhead()) {
            velocity.y = JUMP_SPEED;
            jumpCooldown = JUMP_COOLDOWN;
        }
        if (jumpCooldown > 0) jumpCooldown -= dt;

        // Falling through the world is fatal for mobs too
        if (position.y < -64) {
            hp = 0;
            dead = true;
        }
    }

    /** Move along one horizontal axis, resolving collisions with blocks. */
    private void moveAxis(float dx, float dz) {
        if (dx == 0 && dz == 0) return;
        position.x += dx;
        position.z += dz;

        float minX = position.x - HALF_WIDTH;
        float maxX = position.x + HALF_WIDTH;
        float minZ = position.z - HALF_WIDTH;
        float maxZ = position.z + HALF_WIDTH;
        float minY = position.y;
        float maxY = position.y + HEIGHT;

        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(maxX);
        int z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(maxZ);
        int y0 = (int) Math.floor(minY), y1 = (int) Math.floor(maxY);

        // [GP-002] Collide against per-block AABBs, so slabs only block
        // their lower half.
        float[] bb = new float[6];
        float blockMin = Float.MAX_VALUE;
        float blockMax = -Float.MAX_VALUE;
        float blockTop = -Float.MAX_VALUE;
        boolean blocked = false;
        for (int bx = x0; bx <= x1 && !blocked; bx++) {
            for (int bz = z0; bz <= z1 && !blocked; bz++) {
                for (int by = y0; by <= y1 && !blocked; by++) {
                    if (!solidAabb(bx, by, bz, bb)) continue;
                    if (maxX > bb[0] && minX < bb[3]
                        && maxY > bb[1] && minY < bb[4]
                        && maxZ > bb[2] && minZ < bb[5]) {
                        blocked = true;
                        blockTop = bb[4];
                        if (dx > 0) {
                            blockMin = bb[0];
                        } else if (dx < 0) {
                            blockMax = bb[3];
                        } else if (dz > 0) {
                            blockMin = bb[2];
                        } else if (dz < 0) {
                            blockMax = bb[5];
                        }
                    }
                }
            }
        }

        if (!blocked) return;

        // Push back to the edge of the blocking AABB, not the cell edge
        if (dx > 0) {
            position.x = blockMin - HALF_WIDTH - 0.001f;
        } else if (dx < 0) {
            position.x = blockMax + HALF_WIDTH + 0.001f;
        } else if (dz > 0) {
            position.z = blockMin - HALF_WIDTH - 0.001f;
        } else if (dz < 0) {
            position.z = blockMax + HALF_WIDTH + 0.001f;
        }

        // Step up: 0.5 for a slab, 1.0 for a full block
        float rise = stepUpAmount(dx, dz, blockTop);
        if (rise > 0.001f) {
            pushDirX = dx;
            pushDirZ = dz;
            position.y += rise;
        } else {
            blockedByObstacle = true;
            pushDirX = dx;
            pushDirZ = dz;
            // Keep pushing while rising so the auto-step catches mid-jump
            if (velocity.y < 1.0f) {
                velocity.x = 0;
                velocity.z = 0;
            }
        }
    }

    /** Rise needed to climb the blocking block, or 0 when impossible. */
    private float stepUpAmount(float dx, float dz, float blockTop) {
        float rise = blockTop - position.y;
        if (rise <= 0.001f || rise > STEP_UP + 0.001f) return 0;

        int cx = (int) Math.floor(position.x + (dx > 0.001f ? HALF_WIDTH + 0.1f : dx < -0.001f ? -HALF_WIDTH - 0.1f : 0));
        int cz = (int) Math.floor(position.z + (dz > 0.001f ? HALF_WIDTH + 0.1f : dz < -0.001f ? -HALF_WIDTH - 0.1f : 0));

        // Headroom: every cell the raised body occupies must be free
        int top = (int) Math.floor(position.y + HEIGHT + rise + 0.01f);
        for (int y = (int) Math.floor(position.y + HEIGHT); y <= top; y++) {
            if (isBlockSolid(cx, y, cz)) return 0;
        }
        return rise;
    }

    /** Landing cell at feet+2 must be air, so 3-high walls are not climbed. */
    private boolean jumpClearAhead() {
        float len = (float) Math.sqrt(pushDirX * pushDirX + pushDirZ * pushDirZ);
        if (len < 0.001f) return false;
        int fx = (int) Math.floor(position.x + (pushDirX / len) * (HALF_WIDTH + 0.1f));
        int fz = (int) Math.floor(position.z + (pushDirZ / len) * (HALF_WIDTH + 0.1f));
        int landing = (int) Math.floor(position.y + 2.0f);
        return !isBlockSolid(fx, landing, fz);
    }

    private void resolveVertical() {
        float minX = position.x - HALF_WIDTH;
        float maxX = position.x + HALF_WIDTH;
        float minZ = position.z - HALF_WIDTH;
        float maxZ = position.z + HALF_WIDTH;

        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(maxX);
        int z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(maxZ);
        int y0 = (int) Math.floor(position.y);
        int y1 = (int) Math.floor(position.y + HEIGHT);

        // [GP-002] AABB-aware: land on slab tops (y+0.5), bump heads on
        // the lowest AABB bottom.
        float[] bb = new float[6];
        if (velocity.y <= 0) {
            // Falling: land on the highest AABB top the body overlaps
            float landY = -Float.MAX_VALUE;
            for (int bx = x0; bx <= x1; bx++) {
                for (int bz = z0; bz <= z1; bz++) {
                    for (int by = y0; by <= y1; by++) {
                        if (!solidAabb(bx, by, bz, bb)) continue;
                        if (bb[4] > position.y && bb[1] < position.y + HEIGHT
                            && maxX > bb[0] && minX < bb[3]
                            && maxZ > bb[2] && minZ < bb[5]) {
                            landY = Math.max(landY, bb[4]);
                        }
                    }
                }
            }
            // Auto-step: standing at the edge of an obstacle, land on its top
            // when it is within STEP_UP — otherwise the feet slowly sink,
            // falling speed builds up and the mob slips back down.
            if (landY == -Float.MAX_VALUE && (pushDirX != 0 || pushDirZ != 0)) {
                float dirLen = (float) Math.sqrt(pushDirX * pushDirX + pushDirZ * pushDirZ);
                if (dirLen > 0.001f) {
                    int fx = (int) Math.floor(
                        position.x + (pushDirX / dirLen) * (HALF_WIDTH + 0.1f));
                    int fz = (int) Math.floor(
                        position.z + (pushDirZ / dirLen) * (HALF_WIDTH + 0.1f));
                    for (int fy = y0; fy <= y1; fy++) {
                        if (!solidAabb(fx, fy, fz, bb)) continue;
                        if (position.y < bb[4] && position.y + STEP_UP + 0.001f >= bb[4]) {
                            landY = Math.max(landY, bb[4]);
                        }
                    }
                }
            }
            if (landY != -Float.MAX_VALUE) {
                position.y = landY;
                velocity.y = 0;
                onGround = true;
            }
        } else {
            // Rising: bump the head against the lowest AABB bottom
            float ceilY = Float.MAX_VALUE;
            for (int bx = x0; bx <= x1; bx++) {
                for (int bz = z0; bz <= z1; bz++) {
                    for (int by = y0; by <= y1; by++) {
                        if (!solidAabb(bx, by, bz, bb)) continue;
                        if (bb[1] < position.y + HEIGHT && bb[4] > position.y
                            && maxX > bb[0] && minX < bb[3]
                            && maxZ > bb[2] && minZ < bb[5]) {
                            ceilY = Math.min(ceilY, bb[1]);
                        }
                    }
                }
            }
            if (ceilY != Float.MAX_VALUE) {
                position.y = ceilY - HEIGHT - 0.001f;
                velocity.y = 0;
            }
        }
    }

    /** Solid AABB at a cell: full cell below the world, per-block AABBs above. */
    private boolean solidAabb(int x, int y, int z, float[] bb) {
        if (y < 0) {
            bb[0] = x; bb[1] = y; bb[2] = z;
            bb[3] = x + 1; bb[4] = y + 1; bb[5] = z + 1;
            return true;
        }
        if (y >= 256) return false;
        return world.getBlockAabb(x, y, z, bb) != null;
    }

    private boolean isBlockSolid(int x, int y, int z) {
        if (y < 0 || y >= 256) return y < 0;
        return world.isSolid(x, y, z);
    }

    public float getAttackPhase() {
        if (state == State.ATTACK) {
            return Math.min(attackTimer / ATTACK_DURATION, 1.0f);
        }
        return 0;
    }

    // --- Getters ---
    public Vector3f getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getWalkPhase() { return walkPhase; }
    public float getLimbSwingAmount() { return limbSwingAmount; }
    public float getHp() { return hp; }
    public float getMaxHp() { return MAX_HP; }
    public boolean isDead() { return dead; }
    public State getState() { return state; }
    public boolean isDebugTpose() { return debugTpose; }
    public void setDebugTpose(boolean v) { debugTpose = v; }
    /** [GP-038] 0..1 red flash intensity while hurt. */
    public float getHurtProgress() {
        return Math.max(0, Math.min(1.0f, hurtTimer / HURT_DURATION));
    }

    /**
     * [GP-038] Damage with knockback and a hurt flash. The knockback is
     * applied in the direction the hit came from.
     */
    public void takeDamage(float dmg, Vector3f fromDir) {
        if (dead) return;
        hp -= dmg;
        hurtTimer = HURT_DURATION;
        // [GP-044] Pain growl (hurt = clip 3)
        AudioManager.play("sounds/zoloy/3", 0.95f + (float) Math.random() * 0.2f, 0.5f);

        // Knockback pushes the mob away from the attacker
        float len = (float) Math.sqrt(fromDir.x * fromDir.x + fromDir.z * fromDir.z);
        if (len > 0.001f) {
            float strength = 6.5f;
            velocity.x += (fromDir.x / len) * strength;
            velocity.z += (fromDir.z / len) * strength;
            velocity.y = 5.0f;
        }

        if (hp <= 0) {
            hp = 0;
            dead = true;
            // [GP-044] Dying groan (death = clip 4)
            AudioManager.play("sounds/zoloy/4", 1.0f, 0.55f);
        }
    }

    /** Legacy damage without knockback, kept for non-player sources. */
    public void takeDamage(float dmg) {
        if (dead) return;
        hp -= dmg;
        hurtTimer = HURT_DURATION;
        AudioManager.play("sounds/zoloy/3", 0.95f + (float) Math.random() * 0.2f, 0.5f);
        if (hp <= 0) {
            hp = 0;
            dead = true;
            AudioManager.play("sounds/zoloy/4", 1.0f, 0.55f);
        }
    }
}