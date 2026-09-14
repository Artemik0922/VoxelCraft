package com.voxelgame.player;

import com.voxelgame.item.BlockHarvest;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.Item;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.ToolTier;
import com.voxelgame.item.ToolType;
import com.voxelgame.item.StatusEffect;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;
import com.voxelgame.world.Chunk;
import com.voxelgame.world.save.WorldMeta;
import org.joml.*;

/**
 * Player entity with physics, health, hunger, and inventory
 */
public class Player {
    // Position and movement
    private Vector3f position;
    private Vector3f velocity;
    private World world;

    // [BED] Respawn point set by sleeping in a bed (null = world spawn)
    private org.joml.Vector3i bedSpawn = null;
    
    // Player dimensions
    private float width = 0.6f;
    private float height = 1.8f;
    private float eyeHeight = 1.6f;
    
    // State
    private boolean onGround = false;
    private boolean sprinting = false;
    private boolean swimming = false;
    private boolean flying = false;
    private WorldMeta.GameMode gameMode = WorldMeta.GameMode.SURVIVAL;
    
    // Health and hunger (Minecraft-style)
    private int health = 20;
    private int maxHealth = 20;
    private int hunger = 20;

    /** [GP-003][GP-004] Accumulated energy spent by sprinting/swimming. */
    private float exhaustion = 0;
    /** Exhaustion per second while sprinting: ~1 hunger per 7 s of sprint. */
    private static final float EXHAUSTION_SPRINT = 0.14f;
    /** Exhaustion per second while swimming: ~1 hunger per 100 s of swim. */
    private static final float EXHAUSTION_SWIM = 0.01f;
    private int maxHunger = 20;
    private float saturation = 5.0f;
    private float healthRegenTimer = 0;
    /** [BASE] Seconds between campfire heals. */
    private int campfireRegenTimer = 0;
    private int damageTimer = 0;
    
    // Air supply while underwater (vanilla: 15 s submerged, then damage)
    float air = 300;
    float maxAir = 300;
    /** Air points drained per second while the head is underwater. */
    private static final float AIR_DRAIN_PER_SECOND = 20f;
    /** Air points restored per second once the head clears the surface. */
    private static final float AIR_REGEN_PER_SECOND = 120f;
    private float drownTimer = 0;
    /** True when the eye itself sits inside a water block. */
    private boolean headUnderwater = false;
    
    // [GP-028] Fire/lava damage timers
    private boolean inFire = false;
    private boolean inLava = false;
    private int fireDamageTimer = 0;     // Ticks while in fire (damage every 0.5s = 30 ticks)
    private int lavaDamageTimer = 0;     // Ticks while in lava (damage every 0.5s = 30 ticks)
    private int burningTimer = 0;        // Ticks burning after leaving fire (3 seconds = 180 ticks)
    private int burnDamageTimer = 0;     // Ticks for post-fire burn damage (1 damage/sec = 60 ticks)
    
    /** Check if player is currently on fire (for rendering). */
    public boolean isOnFire() { return burningTimer > 0 || inFire || inLava; }
    /** Get burning progress (0-1) for particle effects. */
    public float getFireProgress() { return java.lang.Math.min(1.0f, burningTimer / 180.0f); }
    
    // Eating: hold right-click for 1.5s to eat the held item
    private int eatTimer = 0;
    private boolean eating = false;
    
    // Inventory
    private Inventory inventory;

    // [POT] Active status effects, keyed by effect type.
    private final java.util.Map<StatusEffect, ActiveEffect> activeEffects = new java.util.HashMap<>();

    /** One active effect instance with its remaining time and power. */
    public static final class ActiveEffect {
        public final StatusEffect type;
        public int power;
        public int remainingTicks;

        ActiveEffect(StatusEffect type, int power, int remainingTicks) {
            this.type = type;
            this.power = power;
            this.remainingTicks = remainingTicks;
        }
    }

    /** Apply a potion effect, refreshing an existing one with the stronger level. */
    public void applyPotion(StatusEffect effect, int power, int durationTicks) {
        ActiveEffect existing = activeEffects.get(effect);
        if (existing != null) {
            existing.power = java.lang.Math.max(existing.power, power);
            existing.remainingTicks = java.lang.Math.max(existing.remainingTicks, durationTicks);
        } else {
            activeEffects.put(effect, new ActiveEffect(effect, power, durationTicks));
        }
    }

    public boolean hasEffect(StatusEffect effect) {
        ActiveEffect e = activeEffects.get(effect);
        return e != null && e.remainingTicks > 0;
    }

    public int getEffectLevel(StatusEffect effect) {
        ActiveEffect e = activeEffects.get(effect);
        return e != null && e.remainingTicks > 0 ? e.power : 0;
    }

    public java.util.Map<StatusEffect, ActiveEffect> getActiveEffects() {
        return activeEffects;
    }

    /** Clear every active effect (e.g. milk, respawn). */
    public void clearEffects() {
        activeEffects.clear();
    }

    /** [UI-016] The survival inventory's 2x2 crafting grid; persists across
     *  screen opens so placed ingredients are never lost on close. */
    private final ItemStack[] craftingGrid = new ItemStack[4];

    /** The survival crafting grid, 4 cells (row-major, 2x2). */
    public ItemStack[] getCraftingGrid() { return craftingGrid; }
    
    public enum DeathCause { FELL, DROWN, STARVE, LAVA, FIRE, CACTUS, EXPLOSION, MOB, VOID, GENERIC }
    
    /** Reached when health hits zero; the owner decides how to display it. */
    public interface DeathCallback {
        void onDeath(DeathCause cause);
    }
    
    private DeathCallback deathCallback;
    public void setDeathCallback(DeathCallback cb) { this.deathCallback = cb; }
    
    // Invincibility frames after taking damage [GP-027]
    private float invincibleTimer = 0;
    private static final float INVINCIBLE_DURATION = 0.5f; // 0.5 seconds of invincibility
    
    public boolean isInvincible() { return invincibleTimer > 0; }
    public float getInvincibleProgress() { return invincibleTimer / INVINCIBLE_DURATION; }
    
    // Physics constants
    private static final float WALK_SPEED = 4.3f;
    private static final float SPRINT_SPEED = 5.6f;
    private static final float SWIM_SPEED = 2.0f;
    private static final float GRAVITY = 28.0f;
    private static final float JUMP_FORCE = 8.5f;
    private static final float SPRINT_JUMP_FORCE = 10.0f;
    private static final float MAX_FALL_SPEED = 78.4f;
    private static final float FRICTION = 0.85f;
    private static final float AIR_FRICTION = 0.95f;

    // [SPACE] Gravity multiplier: space planets pull much weaker.
    private float gravityScale = 1.0f;

    public void setGravityScale(float scale) { this.gravityScale = java.lang.Math.max(0.05f, scale); }
    public float getGravityScale() { return gravityScale; }
    
    // Fall damage
    private float fallDistance = 0;
    private float highestPosition = 0;
    
    public Player(Vector3f startPos, World world) {
        this.position = new Vector3f(startPos);
        this.velocity = new Vector3f();
        this.world = world;
        this.inventory = new Inventory();
        this.highestPosition = startPos.y;
        for (int i = 0; i < craftingGrid.length; i++) {
            craftingGrid[i] = new ItemStack(BlockType.AIR, 0);
        }
    }
    
    public void update(double dt) {
        // Clamp delta time to prevent physics explosions
        if (dt > 0.1) dt = 0.1;
        if (dt <= 0) dt = 0.016;
        
        float fdt = (float) dt;
        
        // Update swimming state
        updateSwimming();
        
        // [POT] Tick active status effects (regen, timers)
        tickStatusEffects();
        
        // [GP-028][GP-072] Check fire/lava contact and apply tick damage
        updateFireAndLavaDamage(fdt);
        
        // [GP-031] Check cactus contact damage
        updateCactusDamage(fdt);
        
        // Apply gravity вЂ” creative fliers ignore it
        if (!flying) applyGravity(fdt);
        
        // Apply movement
        applyMovement(fdt);
        
        // Update fall distance for damage calculation (never in creative)
        if (gameMode != WorldMeta.GameMode.CREATIVE) {
            updateFallDistance();
        }
        
        // Hunger and health only matter in survival-like modes
        if (gameMode != WorldMeta.GameMode.CREATIVE) {
            updateHungerAndHealth(fdt);
        }
        
        // Update damage timer
        if (damageTimer > 0) damageTimer--;
        
        // [GP-007] Safety: kill player if fallen through world (void damage)
        if (position.y < -64) {
            // Instant death from falling into the void
            health = 0;
            if (deathCallback != null) {
                deathCallback.onDeath(DeathCause.VOID);
            }
        }
        
        // [GP-027] Update invincibility timer
        if (invincibleTimer > 0) {
            invincibleTimer -= fdt;
        }
        
        // Check if player is stuck in a block and push them out
        if (world != null && collides(position)) {
            world.pushOutOfBlocks(position);
        }
    }

    private int regenTickCounter = 0;

    /**
     * [POT] Advance status-effect timers each tick and apply the
     * regeneration effect every 2 seconds.
     */
    private void tickStatusEffects() {
        if (activeEffects.isEmpty()) return;

        if (hasEffect(StatusEffect.REGENERATION)) {
            regenTickCounter++;
            // Heal 1 HP every 40 ticks (2 s); higher power heals faster
            int interval = 40 - (getEffectLevel(StatusEffect.REGENERATION) - 1) * 10;
            if (interval < 15) interval = 15;
            if (regenTickCounter >= interval) {
                regenTickCounter = 0;
                heal(1);
            }
        } else {
            regenTickCounter = 0;
        }

        activeEffects.values().removeIf(e -> {
            e.remainingTicks--;
            return e.remainingTicks <= 0;
        });
    }
    
    private void updateSwimming() {
        // Check if player is in water
        int blockX = (int) java.lang.Math.floor(position.x);
        int blockY = (int) java.lang.Math.floor(position.y);
        int blockZ = (int) java.lang.Math.floor(position.z);
        swimming = world.getBlock(blockX, blockY, blockZ) == 45; // WATER

        // Air only matters when the eye itself sits inside a water block:
        // wading or swimming with the head above the surface is free
        int eyeY = (int) java.lang.Math.floor(position.y + eyeHeight);
        headUnderwater = world.getBlock(blockX, eyeY, blockZ) == BlockType.WATER.id;
    }
    
    /**
     * [GP-028] Fire damage: 1 damage every 0.5s while in fire.
     * [GP-072] Lava damage: 4 damage every 0.5s while in lava.
     * After leaving fire: burn for 3 seconds (1 damage/sec).
     * After leaving lava: burn for 5 seconds (1 damage/sec).
     */
    private void updateFireAndLavaDamage(float dt) {
        if (gameMode == WorldMeta.GameMode.CREATIVE) return;
        
        int bx = (int) java.lang.Math.floor(position.x);
        int by = (int) java.lang.Math.floor(position.y);
        int bz = (int) java.lang.Math.floor(position.z);
        int blockFeet = world.getBlock(bx, by, bz);
        int blockBody = world.getBlock(bx, by + 1, bz);

        boolean touchingFire = isFireBlock(blockFeet) || isFireBlock(blockBody);
        // Fire on the four sides hurts too, like a campfire
        if (!touchingFire) {
            touchingFire = isFireBlock(world.getBlock(bx + 1, by, bz))
                || isFireBlock(world.getBlock(bx - 1, by, bz))
                || isFireBlock(world.getBlock(bx, by, bz + 1))
                || isFireBlock(world.getBlock(bx, by, bz - 1))
                || isFireBlock(world.getBlock(bx + 1, by + 1, bz))
                || isFireBlock(world.getBlock(bx - 1, by + 1, bz))
                || isFireBlock(world.getBlock(bx, by + 1, bz + 1))
                || isFireBlock(world.getBlock(bx, by + 1, bz - 1));
        }
        boolean touchingLava = (blockFeet == BlockType.LAVA.id || blockBody == BlockType.LAVA.id);
        
        inFire = touchingFire;
        inLava = touchingLava;

        // [POT] Fire resistance: no burn, damage or afterburn while active
        if (hasEffect(StatusEffect.FIRE_RESISTANCE)) {
            burningTimer = 0;
            burnDamageTimer = 0;
            fireDamageTimer = 0;
            lavaDamageTimer = 0;
            return;
        }
        
        // Water extinguishes fire
        if (swimming) {
            burningTimer = 0;
            burnDamageTimer = 0;
            fireDamageTimer = 0;
            lavaDamageTimer = 0;
            return;
        }
        
        // Lava damage: 4 HP every 0.5 seconds (30 ticks at 60fps)
        if (touchingLava) {
            lavaDamageTimer++;
            burningTimer = 300; // 5 seconds of burning after leaving lava
            if (lavaDamageTimer >= 30) {
                lavaDamageTimer = 0;
                takeDamage(4, DeathCause.LAVA);
            }
        } else {
            lavaDamageTimer = 0;
        }
        
        // Fire damage: 1 HP every 0.5 seconds
        if (touchingFire) {
            fireDamageTimer++;
            burningTimer = java.lang.Math.max(burningTimer, 180); // 3 seconds of burning
            if (fireDamageTimer >= 30) {
                fireDamageTimer = 0;
                takeDamage(1, DeathCause.FIRE);
            }
        } else {
            fireDamageTimer = 0;
        }
        
        // Post-fire burning: 1 damage per second (60 ticks)
        if (!touchingFire && !touchingLava && burningTimer > 0) {
            burningTimer--;
            burnDamageTimer++;
            if (burnDamageTimer >= 60) {
                burnDamageTimer = 0;
                takeDamage(1, DeathCause.FIRE);
            }
        }
    }
    
    /** Check if a block ID represents fire or a fire-like block. */
    private boolean isFireBlock(int blockId) {
        return blockId == BlockType.FIRE.id;
    }
    
    // [GP-031] Cactus damage timer
    private int cactusDamageTimer = 0;
    
    /**
     * [GP-031] Check if player is touching a cactus and apply damage.
     * Cactus deals 1 damage every 0.5 seconds and pushes the player away.
     */
    public void updateCactusDamage(float dt) {
        if (gameMode == WorldMeta.GameMode.CREATIVE) return;
        
        // Check blocks around player feet and body for cactus
        int px = (int) java.lang.Math.floor(position.x);
        int py = (int) java.lang.Math.floor(position.y);
        int pz = (int) java.lang.Math.floor(position.z);
        
        boolean touchingCactus = false;
        int cactusX = 0, cactusZ = 0;
        
        // Check adjacent blocks (cactus has thorns on sides)
        int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        for (int[] off : offsets) {
            int cx = px + off[0];
            int cz = pz + off[1];
            for (int dy = 0; dy <= 1; dy++) {
                if (world.getBlock(cx, py + dy, cz) == BlockType.CACTUS.id) {
                    touchingCactus = true;
                    cactusX = cx;
                    cactusZ = cz;
                    break;
                }
            }
            if (touchingCactus) break;
        }
        
        if (touchingCactus) {
            cactusDamageTimer++;
            // Damage every 0.5 seconds (30 ticks at 60fps)
            if (cactusDamageTimer >= 30) {
                cactusDamageTimer = 0;
                takeDamage(1, DeathCause.CACTUS);
                
                // Knockback away from cactus
                float dx = position.x - cactusX;
                float dz = position.z - cactusZ;
                float len = (float) java.lang.Math.sqrt(dx * dx + dz * dz);
                if (len > 0.01f) {
                    velocity.x += (dx / len) * 0.4f;
                    velocity.z += (dz / len) * 0.4f;
                }
            }
        } else {
            cactusDamageTimer = 0;
        }
    }
    
    // [GP-006] Sneaking state
    private boolean sneaking = false;
    
    /** Toggle or set sneaking state. */
    public void setSneaking(boolean sneaking) { this.sneaking = sneaking; }
    public boolean isSneaking() { return sneaking; }
    
    /**
     * [GP-006] Check if player is about to fall off an edge while sneaking.
     * Returns true if the player should NOT fall (sneaking prevents edge falls).
     */
    public boolean shouldPreventEdgeFall() {
        if (!sneaking || !onGround) return false;
        
        // Check if there's ground ahead in movement direction
        float halfW = width / 2.0f;
        int checkY = (int) java.lang.Math.floor(position.y) - 1;
        
        // Check 4 points around the player's base
        int[][] edgeChecks = {
            {(int) java.lang.Math.floor(position.x + halfW + 0.1f), (int) java.lang.Math.floor(position.z)},
            {(int) java.lang.Math.floor(position.x - halfW - 0.1f), (int) java.lang.Math.floor(position.z)},
            {(int) java.lang.Math.floor(position.x), (int) java.lang.Math.floor(position.z + halfW + 0.1f)},
            {(int) java.lang.Math.floor(position.x), (int) java.lang.Math.floor(position.z - halfW - 0.1f)}
        };
        
        for (int[] check : edgeChecks) {
            if (!world.isSolid(check[0], checkY, check[1])) {
                // This edge has no ground below - player is at a ledge
                // Check if player is significantly over the edge
                return true;
            }
        }
        return false;
    }
    
    /** Get movement speed multiplier when sneaking (30% of normal). */
    public float getSneakSpeedMultiplier() {
        return sneaking ? 0.3f : 1.0f;
    }
    
    private void applyGravity(float dt) {
        if (swimming) {
            // Swimming physics - slower gravity
            if (velocity.y < -1.0f) {
                velocity.y += GRAVITY * 0.3f * dt;
            } else {
                velocity.y *= 0.9f;
            }
        } else if (inLava) {
            // [GP-072] Lava physics - very slow gravity, high drag
            velocity.y -= GRAVITY * 0.15f * dt;
            velocity.y *= 0.5f; // Extreme drag in lava
        } else {
            // Normal gravity
            velocity.y -= GRAVITY * dt * gravityScale;
        }
        
        if (velocity.y < -MAX_FALL_SPEED) {
            velocity.y = -MAX_FALL_SPEED;
        }
    }
    
        /**
     * [WQ] Push the player along the water level gradient. The gradient
     * points from the source (level 0, highest surface) toward the edge
     * (level 7, thinnest film) - i.e. downstream.
     */
    private void applyFlowPush(float dt) {
        if (world == null) return;
        int bx = (int) java.lang.Math.floor(position.x);
        int by = (int) java.lang.Math.floor(position.y);
        int bz = (int) java.lang.Math.floor(position.z);
        if (world.getWaterLevel(bx, by, bz) < 1) return; // static water: calm

        float gx = 0, gz = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int l = world.getWaterLevel(bx + d[0], by, bz + d[1]);
            if (l > 1) {
                gx += d[0] * (l - 1);
                gz += d[1] * (l - 1);
            }
        }
        float len = (float) java.lang.Math.sqrt(gx * gx + gz * gz);
        if (len > 0.001f) {
            float strength = 0.35f * java.lang.Math.min(1.0f, len / 2.0f);
            velocity.x += gx / len * strength * dt * 60.0f;
            velocity.z += gz / len * strength * dt * 60.0f;
        }
    }

    private void applyMovement(float dt) {
        // Calculate horizontal movement
        float moveX = velocity.x * dt;
        float moveY = velocity.y * dt;
        float moveZ = velocity.z * dt;
        
        // Move one axis at a time for proper collision
        moveAxis(moveX, 0, 0);
        moveAxis(0, moveY, 0);
        moveAxis(0, 0, moveZ);
        
        // Apply friction
        float friction = onGround ? FRICTION : AIR_FRICTION;
        velocity.x *= friction;
        velocity.z *= friction;
        
        // Swimming friction
        if (swimming) {
            velocity.x *= 0.8f;
            velocity.z *= 0.8f;
        }

        // [WQ] Flowing water carries the player toward the thicker film:
        // the push follows the level gradient (downstream), so a stream
        // sweeps the player along and pools stay calm.
        if (swimming) {
            applyFlowPush(dt);
        }
        
        // [GP-072] Lava severely slows movement
        if (inLava) {
            velocity.x *= 0.25f;
            velocity.z *= 0.25f;
        }
    }
    
    private void moveAxis(float dx, float dy, float dz) {
        if (dx == 0 && dy == 0 && dz == 0) return;
        
        Vector3f newPos = new Vector3f(position);
        newPos.x += dx;
        newPos.y += dy;
        newPos.z += dz;
        
        if (!collides(newPos)) {
            position.set(newPos);
            if (dy != 0) onGround = false;
        } else {
            if (dy < 0) {
                // Falling - snap to ground, or bounce off a slime block
                position.y = findGroundBelow();
                if (blockBelowIsSlime()) {
                    // Bounce: reflect the (capped) fall speed back upward.
                    // onGround=true lets updateFallDistance() reset the fall
                    // distance with a zero damage multiplier before the next
                    // frame launches the player back into the air.
                    float fall = java.lang.Math.max(0.0f, -velocity.y);
                    velocity.y = java.lang.Math.min(fall, 14.0f) * 0.8f + 2.0f;
                    onGround = true;
                } else {
                    velocity.y = 0;
                    onGround = true;
                }
            } else if (dy > 0) {
                // Hit ceiling
                velocity.y = 0;
            } else {
                // Horizontal collision
                velocity.x = 0;
                velocity.z = 0;
            }
        }
    }
    
    private float findGroundBelow() {
        int minX = (int) java.lang.Math.floor(position.x - width / 2);
        int maxX = (int) java.lang.Math.floor(position.x + width / 2);
        int minZ = (int) java.lang.Math.floor(position.z - width / 2);
        int maxZ = (int) java.lang.Math.floor(position.z + width / 2);
        int startY = (int) java.lang.Math.floor(position.y - 0.01f);

        float highestGround = -100;

        // [GP-002] AABB-aware: a slab's top is at y+0.5, so the player
        // snaps onto slabs instead of falling through their cell.
        // collides() guarantees the player is never inside an AABB, so the
        // first solid found scanning down is always the highest ground.
        float[] bb = new float[6];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = startY; y >= 0; y--) {
                    if (world.getBlockAabb(x, y, z, bb) != null) {
                        float topOfBlock = bb[4];
                        if (topOfBlock > highestGround) {
                            highestGround = topOfBlock;
                        }
                        break;
                    }
                }
            }
        }

        return highestGround > -100 ? highestGround : position.y;
    }
    
    private boolean collides(Vector3f pos) {
        int minX = (int) java.lang.Math.floor(pos.x - width / 2);
        int maxX = (int) java.lang.Math.floor(pos.x + width / 2);
        int minY = (int) java.lang.Math.floor(pos.y);
        int maxY = (int) java.lang.Math.floor(pos.y + height - 0.01f);
        int minZ = (int) java.lang.Math.floor(pos.z - width / 2);
        int maxZ = (int) java.lang.Math.floor(pos.z + width / 2);

        // [GP-002] Collide against per-block AABBs, so slabs only block
        // their lower half. The tmp array is reused for every cell.
        float[] bb = new float[6];
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (y < 0 || y >= Chunk.HEIGHT) continue;
                    if (world.getBlockAabb(x, y, z, bb) != null
                        && pos.x + width / 2 > bb[0] && pos.x - width / 2 < bb[3]
                        && pos.y + height > bb[1] && pos.y < bb[4]
                        && pos.z + width / 2 > bb[2] && pos.z - width / 2 < bb[5]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private void updateFallDistance() {
        // [GP-008] Reset fall distance when entering water (water absorbs fall damage)
        if (swimming) {
            fallDistance = 0;
            highestPosition = position.y;
            return;
        }
        
        if (onGround) {
            if (fallDistance > 3) {
                // [GP-009] Check if landing on special blocks that reduce fall damage
                float damageMultiplier = getLandingDamageMultiplier();
                int damage = (int) ((fallDistance - 3) * damageMultiplier);
                
                if (damage > 0) {
                    takeDamage(damage);
                    if (health <= 0 && deathCallback != null) {
                        health = 0;
                        deathCallback.onDeath(DeathCause.FELL);
                    }
                }
            }
            fallDistance = 0;
            highestPosition = position.y;
        } else if (position.y < highestPosition) {
            fallDistance = highestPosition - position.y;
        }
    }
    
    /**
     * [GP-009] Get fall damage multiplier based on what block the player lands on.
     * Hay bale: 0.2x damage, Slime block: 0x damage, Normal: 1.0x
     */
    private float getLandingDamageMultiplier() {
        int bx = (int) java.lang.Math.floor(position.x);
        int by = (int) java.lang.Math.floor(position.y) - 1;
        int bz = (int) java.lang.Math.floor(position.z);
        
        int blockBelow = world.getBlock(bx, by, bz);
        // [GP-009] Hay bales soften landings; slime blocks absorb all damage
        if (blockBelow == BlockType.HAY_BALE.id) return 0.2f;
        if (blockBelow == BlockType.SLIME_BLOCK.id) return 0.0f;
        return 1.0f;
    }

    /** True when the block under the player's feet is a slime block. */
    private boolean blockBelowIsSlime() {
        if (world == null) return false;
        int bx = (int) java.lang.Math.floor(position.x);
        int by = (int) java.lang.Math.floor(position.y - 0.1f);
        int bz = (int) java.lang.Math.floor(position.z);
        return world.getBlock(bx, by, bz) == BlockType.SLIME_BLOCK.id;
    }
    
    private void updateHungerAndHealth(float dt) {
        // Vanilla hunger never drains while idle: only real activity
        // (sprinting, swimming, jumping, healing) spends food.

        // [GP-003][GP-004] Sprinting and swimming burn exhaustion, which
        // drains saturation first, then hunger. Only while actually moving.
        float hSpeed = (float) java.lang.Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        boolean moving = hSpeed > 0.4f;
        if (sprinting && moving) {
            exhaustion += EXHAUSTION_SPRINT * dt;
        }
        if (swimming && moving) {
            exhaustion += EXHAUSTION_SWIM * dt;
        }
        if (exhaustion >= 1.0f) {
            int lost = (int) exhaustion;
            exhaustion -= lost;
            for (int i = 0; i < lost && hunger > 0; i++) {
                if (saturation > 0) {
                    saturation--;
                } else {
                    hunger--;
                }
            }
        }

        // Health regeneration when hunger is high: 1 HP every 4 s,
        // costing 1.5 food points (vanilla exhaustion conversion)
        if (hunger >= 18 && health < maxHealth) {
            healthRegenTimer += dt;
            if (healthRegenTimer >= 4.0f) {
                healthRegenTimer = 0;
                health = java.lang.Math.min(health + 1, maxHealth);
                exhaustion += 1.5f;
            }
        } else {
            healthRegenTimer = 0;
        }

        // [BASE] A campfire warms the survivor: +1 HP every ~4 seconds
        // while standing within two blocks of one (and not at full health).
        if (health < maxHealth && gameMode != WorldMeta.GameMode.CREATIVE && world != null) {
            campfireRegenTimer++;
            if (campfireRegenTimer >= 240) {
                campfireRegenTimer = 0;
                Vector3f p = getPosition();
                int cx = (int) java.lang.Math.floor(p.x), cy = (int) java.lang.Math.floor(p.y), cz = (int) java.lang.Math.floor(p.z);
                if (nearCampfire(cx, cy, cz)) {
                    health = java.lang.Math.min(health + 1, maxHealth);
                }
            }
        } else {
            campfireRegenTimer = 0;
        }

        // Starvation damage (skip on peaceful): 1 HP every 4 s at zero hunger
        if (hunger <= 0 && gameMode != WorldMeta.GameMode.CREATIVE) {
            healthRegenTimer += dt;
            if (healthRegenTimer >= 4.0f) {
                healthRegenTimer = 0;
                takeDamage(1);
                if (health <= 0 && deathCallback != null) {
                    health = 0;
                    deathCallback.onDeath(DeathCause.STARVE);
                }
            }
        }

        // Drowning: air only drains while the EYE is submerged - wading or
        // floating with the head above water costs nothing (vanilla rule)
        if (headUnderwater) {
            air = java.lang.Math.max(0f, air - AIR_DRAIN_PER_SECOND * dt);
        } else if (air < maxAir) {
            air = java.lang.Math.min(maxAir, air + AIR_REGEN_PER_SECOND * dt);
        }

        if (air <= 0) {
            drownTimer += dt;
            if (drownTimer >= 1.0f) { // 1 heart of damage per second
                drownTimer = 0;
                takeDamage(2);
                if (health <= 0 && deathCallback != null) {
                    health = 0;
                    deathCallback.onDeath(DeathCause.DROWN);
                }
            }
        } else {
            drownTimer = 0;
        }
        
        // Eating progresses while eating is armed and hunger isn't full
        if (eating && hunger < maxHunger) {
            eatTimer++;
            if (eatTimer >= 90) { // 1.5s at 60fps
                eatTimer = 0;
                // Eat the food item from hotbar
                ItemStack food = getFoodItem();
                if (!food.isEmpty()) {
                    if (food.isItem() && food.getItem() != null && food.getItem().isFood()) {
                        Item item = food.getItem();
                        eat(item.foodValue, item.saturation);
                        food.remove(1);
                    } else if (food.isBlock() && food.getBlockType() == BlockType.APPLE) {
                        eat(4, 2.0f); // Apple restores 4 hunger + 2 saturation
                        food.remove(1);
                    }
                }
                eating = false;
            }
        } else {
            eatTimer = 0;
        }
    }
    
    /** Begin eating (called while right-click is held in survival). */
    public void startEating() {
        if (hunger < maxHunger && hasFood()) {
            eating = true;
        }
    }
    
    public void stopEating() {
        eating = false;
        eatTimer = 0;
    }
    
    public boolean isEating() { return eating; }
    public float getEatProgress() {
        return eating ? eatTimer / 90.0f : 0;
    }
    
    private boolean hasFood() {
        // Check if player has any edible item
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            ItemStack item = inventory.getHotbarItem(i);
            if (item.isItem() && item.getItem() != null && item.getItem().isFood()) {
                return true;
            }
            if (item.isBlock() && item.getBlockType() == BlockType.APPLE) {
                return true;
            }
        }
        return false;
    }

    /** Get the food item from hotbar (returns first edible found) */
    private ItemStack getFoodItem() {
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            ItemStack item = inventory.getHotbarItem(i);
            if (item.isItem() && item.getItem() != null && item.getItem().isFood()) {
                return item;
            }
            if (item.isBlock() && item.getBlockType() == BlockType.APPLE) {
                return item;
            }
        }
        return new ItemStack(BlockType.AIR, 0);
    }
    
    public void takeDamage(int amount) {
        takeDamage(amount, DeathCause.GENERIC);
    }
    
    /**
     * [GP-027] Take damage with invincibility frames.
     * During invincibility period, no damage is applied.
     */
    public void takeDamage(int amount, DeathCause cause) {
        // [GP-027] Invincibility frames - ignore damage during recovery
        if (invincibleTimer > 0) return;
        if (damageTimer > 0) return; // Legacy damage cooldown
        if (gameMode == WorldMeta.GameMode.CREATIVE) return; // Invulnerable

        // Armor reduces damage (each point = 4% reduction, max 80%)
        int armor = inventory.getTotalArmorPoints();
        float reduction = java.lang.Math.min(armor * 0.04f, 0.80f);
        int reducedAmount = java.lang.Math.max(1, (int) (amount * (1.0f - reduction)));

        health -= reducedAmount;
        damageTimer = 20; // 1 second cooldown
        
        // [GP-027] Start invincibility frames
        invincibleTimer = INVINCIBLE_DURATION;

        if (health <= 0) {
            health = 0;
            if (deathCallback != null) deathCallback.onDeath(cause);
        }
    }
    
    /**
     * Respawn the player with full health at a safe position.
     * [BED] If the player slept in a bed and it still stands, they wake
     * there; otherwise the world spawn is used and the bed is forgotten.
     */
    public void respawn() {
        health = maxHealth;
        hunger = maxHunger;
        saturation = 5.0f;
        fallDistance = 0;
        invincibleTimer = 0;
        damageTimer = 0;
        air = maxAir;
        velocity.set(0, 0, 0);
        
        if (bedSpawn != null && world != null) {
            int id = world.getBlock(bedSpawn.x, bedSpawn.y, bedSpawn.z);
            if (id == BlockType.BED.id || id == BlockType.BED_HEAD.id) {
                position.set(bedSpawn.x + 0.5f, bedSpawn.y + 0.7f, bedSpawn.z + 0.5f);
                return;
            }
            bedSpawn = null;
        }
        
        // Find safe spawn position
        if (world != null) {
            org.joml.Vector3f safePos = world.findSafeSpawnPosition(
                (int) position.x, (int) position.z);
            position.set(safePos);
        }
    }

    /** [BED] Remember a bed as the respawn point (null clears it). */
    public void setBedSpawn(org.joml.Vector3i spawn) {
        bedSpawn = spawn;
    }
    
    public void heal(int amount) {
        health = java.lang.Math.min(health + amount, maxHealth);
    }
    
    public void eat(int hungerAmount, float saturationAmount) {
        hunger = java.lang.Math.min(hunger + hungerAmount, maxHunger);
        saturation = java.lang.Math.min(saturation + saturationAmount, hunger);
    }
    
    private void handleDeath() {
        // Respawn at world spawn
        health = maxHealth;
        hunger = maxHunger;
        saturation = 5;
        fallDistance = 0;
        
        int groundY = world.getGroundHeight((int) position.x, (int) position.z);
        position.set(position.x, groundY + 5, position.z);
        velocity.set(0, 0, 0);
    }
    
    public void jump() {
        if (onGround && !swimming) {
            velocity.y = sprinting ? SPRINT_JUMP_FORCE : JUMP_FORCE;
            onGround = false;

            // Jumping costs hunger (vanilla: 0.05 exhaustion, 0.2 sprint-jump)
            exhaustion += sprinting ? 0.05f : 0.0125f;
        } else if (swimming) {
            // Swim up
            velocity.y = 2.0f;
        }
    }

    /** Called while space is held during creative flight. */
    public void flyAscend() {
        if (flying) {
            velocity.y = 6.0f;
        }
    }

    /** Called when space is released during creative flight вЂ” player hovers. */
    public void flyHover() {
        if (flying) {
            velocity.y = 0.0f;
        }
    }

    public void descend() {
        if (flying) {
            velocity.y = -6.0f;
        }
    }
    
    /** Toggle creative flight (double-jump). */
    public void toggleFlying() {
        flying = !flying;
        if (flying) {
            velocity.y = 0;
        }
    }
    
    /** Force-flying setter, used by the rocket ride while travelling up. */
    public void setFlying(boolean on) {
        flying = on;
        if (flying) {
            velocity.y = 0;
        }
    }
    
    public boolean isFlying() { return flying; }
    
    public WorldMeta.GameMode getGameMode() { return gameMode; }
    public void setGameMode(WorldMeta.GameMode mode) { this.gameMode = mode; }
    public boolean isCreative() { return gameMode == WorldMeta.GameMode.CREATIVE; }
    public boolean isHardcore() { return gameMode == WorldMeta.GameMode.HARDCORE; }
    
    public void setHorizontalVelocity(float x, float z) {
        // Sneaking slows movement [GP-006]
        float sneakMult = getSneakSpeedMultiplier();
        // [POT] Speed effect boosts movement (+20% per level)
        float speedMult = getPotionSpeedMultiplier();
        velocity.x = x * sneakMult * speedMult;
        velocity.z = z * sneakMult * speedMult;
    }

    /** [POT] Movement multiplier from the Speed effect: 1 + 0.2 * level. */
    private float getPotionSpeedMultiplier() {
        return 1.0f + 0.2f * getEffectLevel(StatusEffect.SPEED);
    }

    /** [POT] Melee damage multiplier from the Strength effect. */
    public float getStrengthMultiplier() {
        return 1.0f + 0.5f * getEffectLevel(StatusEffect.STRENGTH);
    }


    public void setVerticalVelocity(float y) {
        velocity.y = y;
    }
    
    private float getSprintSpeed() {
        if (swimming) return SWIM_SPEED;
        return sprinting ? SPRINT_SPEED : WALK_SPEED;
    }

    /** [BASE] True when a campfire burns within two blocks of the cell. */
    private boolean nearCampfire(int cx, int cy, int cz) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (world.getBlock(cx + dx, cy + dy, cz + dz) == BlockType.CAMPFIRE.id) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }
    
    // Getters and setters
    public Vector3f getPosition() { return position; }
    public Vector3f getVelocity() { return velocity; }
    public void setWorld(World world) { this.world = world; }
    public boolean isOnGround() { return onGround; }
    public boolean isSwimming() { return swimming; }
    public boolean isSprinting() { return sprinting; }

    /**
     * [GP-010] Ground blocks modify movement speed: ice is slippery and
     * fast, soul sand drags you down. Returns 1.0 on normal ground.
     */
    public float getGroundSpeedMultiplier() {
        if (!onGround) return 1.0f;
        int bx = (int) java.lang.Math.floor(position.x);
        int by = (int) java.lang.Math.floor(position.y - 0.5f);
        int bz = (int) java.lang.Math.floor(position.z);
        int below = world.getBlock(bx, by, bz);
        if (below == BlockType.ICE.id || below == BlockType.BLUE_ICE.id) return 1.30f;
        if (below == BlockType.SOUL_SAND.id) return 0.55f;
        return 1.0f;
    }
    public int getHealth() { return health; }
    public void setHealth(int h) { health = java.lang.Math.max(0, java.lang.Math.min(h, maxHealth)); if (health <= 0 && deathCallback != null) deathCallback.onDeath(DeathCause.GENERIC); }
    public int getMaxHealth() { return maxHealth; }
    public int getHunger() { return hunger; }
    public int getMaxHunger() { return maxHunger; }
    public float getSaturation() { return saturation; }
    public int getAir() { return (int) air; }
    public int getMaxAir() { return (int) maxAir; }
    public Inventory getInventory() { return inventory; }
    public float getEyeHeight() { return eyeHeight; }

    // --- Mining ---
    /** Get mining speed multiplier from held item for a given block */
    public float getMiningSpeed(BlockType block) {
        ItemStack held = inventory.getSelectedItem();
        if (held.isItem() && held.getItem() != null && held.getItem().isTool()) {
            return held.getItem().tier.miningSpeed;
        }
        return 1.0f; // Fist
    }

    /**
     * [GP-054] Effective mining speed multiplier for a block, respecting tool
     * kind and tier. Free blocks (dirt, logs, plants) accept any tool at its
     * listed speed. Requiring a tool mines at full speed only with the right
     * kind AND tier; otherwise the block crumbles at a crawl, exactly like
     * vanilla's "wrong tool" slow dig.
     */
    public float getBlockMiningMultiplier(BlockType block) {
        ItemStack held = inventory.getSelectedItem();
        boolean hasTool = held.isItem() && held.getItem() != null && held.getItem().isTool();

        BlockHarvest.Requirement req = BlockHarvest.get(block);
        if (req == null || req.tool == ToolType.NONE) {
            return hasTool ? held.getItem().tier.miningSpeed : 1.0f;
        }

        ToolTier tier = getHeldToolTier();
        ToolType tool = getHeldToolType();
        boolean okTool = tool == req.tool;
        boolean okTier = req.minTier == null
            || (tier != null && tier.harvestLevel >= req.minTier.harvestLevel);
        if (okTool && okTier) {
            return hasTool ? held.getItem().tier.miningSpeed : 1.0f;
        }

        // Wrong tool kind or too-low tier: ~3x slower than bare hands
        return 0.3f;
    }

    /** Get tool tier of held item (null if not a tool) */
    public ToolTier getHeldToolTier() {
        ItemStack held = inventory.getSelectedItem();
        if (held.isItem() && held.getItem() != null) {
            return held.getItem().tier;
        }
        return null;
    }

    /** Get tool type of held item */
    public ToolType getHeldToolType() {
        ItemStack held = inventory.getSelectedItem();
        if (held.isItem() && held.getItem() != null) {
            return held.getItem().toolType;
        }
        return ToolType.NONE;
    }

    // --- [ENCH] Held-tool enchantment helpers ---

    /** Efficiency level of the held tool (0 when absent). */
    public int getHeldEfficiency() {
        ItemStack held = inventory.getSelectedItem();
        if (held.isItem() && held.getItem() != null) {
            return held.getEnchantLevel(com.voxelgame.item.Enchantment.EFFICIENCY);
        }
        return 0;
    }

    /** True when the held tool has Silk Touch. */
    public boolean hasSilkTouch() {
        ItemStack held = inventory.getSelectedItem();
        if (held.isItem() && held.getItem() != null) {
            return held.getEnchantLevel(com.voxelgame.item.Enchantment.SILK_TOUCH) > 0;
        }
        return false;
    }

    /** Fortune-boosted drop count for an ore drop (1 by default). */
    public int fortuneCount(BlockType dropType) {
        ItemStack held = inventory.getSelectedItem();
        int fortune = 0;
        if (held.isItem() && held.getItem() != null) {
            fortune = held.getEnchantLevel(com.voxelgame.item.Enchantment.FORTUNE);
        }
        if (fortune <= 0) return 1;
        int extra = 0;
        for (int i = 0; i < fortune; i++) {
            if (java.lang.Math.random() < 0.5) extra++;
        }
        return 1 + extra;
    }

    /** Damage the held tool (call after mining a block) */
    public void damageHeldTool() {
        damageHeldTool(2);
    }

    /** Damage the held tool by a custom amount (swords lose 1 per hit). */
    public void damageHeldTool(int amount) {
        ItemStack held = inventory.getSelectedItem();
        if (held.isItem() && held.getItem() != null && held.getItem().isTool()) {
            // [ENCH] Unbreaking: chance to skip durability loss
            int unbreaking = held.getEnchantLevel(com.voxelgame.item.Enchantment.UNBREAKING);
            if (unbreaking > 0 && java.lang.Math.random() < unbreaking / (unbreaking + 1.0f)) {
                return;
            }
            held.damage(amount);
            // Tool broke?
            if (held.getDurability() <= 0) {
                held.clear();
            }
        }
    }

    // --- [ENCH] Experience ---

    private int xpTotal = 0;
    private int xpLevel = 0;
    private int xpProgress = 0;

    /** Experience needed to reach the next level (capped at 30). */
    public int getXpToNext() {
        return 7 + xpLevel * 2;
    }

    public int getXpTotal() { return xpTotal; }
    public int getXpLevel() { return xpLevel; }
    public int getXpProgress() { return xpProgress; }

    /** Add experience, leveling up repeatedly until the cap. */
    public void addXp(int amount) {
        if (amount <= 0) return;
        xpTotal += amount;
        while (xpProgress + amount >= getXpToNext() && xpLevel < 30) {
            int needed = getXpToNext() - xpProgress;
            amount -= needed;
            xpProgress = 0;
            xpLevel++;
        }
        if (xpLevel >= 30) {
            xpProgress = 0;
            return;
        }
        xpProgress += amount;
    }

    /** Consume experience; returns false when the player is too poor.
     * One level costs 30 XP вЂ” the enchanting table's pricing unit. */
    public boolean spendXp(int amount) {
        int cost = amount * 30;
        int available = xpProgress + xpLevel * 30;
        if (available < cost) return false;
        int remaining = cost;
        int take = java.lang.Math.min(remaining, xpProgress);
        xpProgress -= take;
        remaining -= take;
        while (remaining > 0 && xpLevel > 0) {
            xpLevel--;
            remaining -= 30;
        }
        return true;
    }

}
