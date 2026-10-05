package com.voxelgame.world.entity;

import com.voxelgame.audio.AudioManager;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.player.Player;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;
import org.joml.Vector3f;

/**
 * [GP-045] Passive animals: cow, pig, chicken, sheep, deer, fox, bear and
 * parrot.
 *
 * They wander around during the day, flee when the player comes too close,
 * can be bred with the right food (a baby appears at the midpoint of the
 * pair) and grow from baby to adult over time. Meat drops are cooked when
 * the animal died in fire or lava.
 *
 * [ANM] Animal system v2:
 * - per-type stats (bears are tough, deer are skittish and fast);
 * - bears are hostile: patrol / chase / attack with knockback;
 * - foxes hunt chickens;
 * - chickens lay eggs;
 * - sheep can be sheared (wool regrows);
 * - cows can be milked with a bucket;
 * - parrots can be tamed with wheat and then follow the player;
 * - animals float in water instead of drowning and avoid cliff edges;
 * - herd behaviour: animals drift toward the nearest mate;
 * - animals follow a player who holds their favourite food;
 * - feeding a baby speeds up its growth.
 */
public class Animal {

    // [PERF] Скретч-AABB для физики (без аллокаций на тик)
    private final float[] bbScratch = new float[6];

    public enum AnimalType {
        COW, PIG, CHICKEN, SHEEP, DEER, FOX, BEAR, PARROT
    }

    // --- Per-type stats [ANM] ---

    private static float maxHp(AnimalType t) {
        return t == AnimalType.BEAR ? 20.0f : 10.0f;
    }

    private static float fleeRange(AnimalType t) {
        switch (t) {
            case DEER: return 12.0f;
            case FOX:  return 8.0f;
            default:   return 6.0f;
        }
    }

    private static float fleeSpeed(AnimalType t) {
        switch (t) {
            case DEER: return 3.4f;
            case FOX:  return 3.0f;
            default:   return 2.6f;
        }
    }

    private static float chaseSpeed(AnimalType t) {
        switch (t) {
            case BEAR: return 1.9f;
            case FOX:  return 3.2f;
            default:   return 2.0f;
        }
    }

    // Movement
    private static final float WANDER_SPEED = 0.9f;
    private static final float GRAVITY = 28.0f;
    private static final float MAX_FALL_SPEED = 78.4f;

    // Body: 0.7 wide, animal heights vary per type
    private static final float HALF_WIDTH = 0.35f;
    private static final float STEP_UP = 1.0f;

    // Baby growth: 3 minutes to adulthood
    private static final float GROW_TIME = 180.0f;
    // Breeding cooldown between two feedings
    private static final float BREED_COOLDOWN = 30.0f;
    // Fire damage: 1 hp every 2 seconds spent in fire/lava
    private static final float FIRE_TICK = 2.0f;

    // [ANM] Bear combat
    private static final float BEAR_AGGRO_RANGE = 10.0f;
    private static final float BEAR_DEAGGRO_RANGE = 18.0f;
    private static final float BEAR_ATTACK_RANGE = 2.2f;
    private static final float BEAR_ATTACK_DURATION = 0.35f;
    private static final float BEAR_ATTACK_COOLDOWN = 1.2f;
    private static final float BEAR_DAMAGE = 3.0f;
    private static final float BEAR_GRUDGE = 5.0f;

    // [ANM] Fox hunting
    private static final float FOX_HUNT_RANGE = 10.0f;
    private static final float FOX_ATTACK_RANGE = 1.6f;
    private static final float FOX_ATTACK_COOLDOWN = 0.8f;
    private static final float FOX_BITE_DAMAGE = 2.0f;

    // [ANM] Chicken egg laying: 5-7 minutes, only on the ground
    private static final float EGG_MIN = 300.0f;
    private static final float EGG_MAX = 420.0f;

    // [ANM] Sheep wool regrows 2 minutes after shearing
    private static final float WOOL_REGROW = 120.0f;

    // [ANM] Feeding a baby speeds growth by a minute
    private static final float FEED_GROWTH_BOOST = 60.0f;

    // [ANM] The player holding food lures animals within this range
    private static final float FOOD_LURE_RANGE = 6.0f;

    // Model scale per type (renderer multiplies by the growth scale)
    private static final float SCALE_COW = 0.9f;
    private static final float SCALE_PIG = 0.8f;
    private static final float SCALE_CHICKEN = 0.45f;
    private static final float SCALE_SHEEP = 0.85f;
    private static final float SCALE_DEER = 0.95f;
    private static final float SCALE_FOX = 0.5f;
    private static final float SCALE_BEAR = 1.4f;
    private static final float SCALE_PARROT = 0.35f;

    /** [ANM] Behaviour state: wander, chase a player (bear), strike (bear),
     *  hunt a chicken (fox). */
    private enum State { PATROL, CHASE, ATTACK, HUNT }

    private final World world;
    private final AnimalType type;
    private final Vector3f position;
    private final Vector3f velocity = new Vector3f();
    private float yaw;

    private float hp;
    private boolean dead = false;
    private boolean onGround = false;

    // Life cycle
    private boolean baby = false;
    private float age = 0;          // seconds since birth
    private float loveCooldown = 0; // seconds until breedable again

    // Fire
    private boolean burning = false;
    private float fireTimer = 0;

    // Animation
    private float walkPhase = 0;
    private float limbSwingAmount = 0;
    private float hurtTimer = 0;
    private static final float HURT_DURATION = 0.25f;

    // Wander target
    private final Vector3f wanderTarget = new Vector3f();
    private float retargetTimer = 0;
    private boolean hasWanderTarget = false;

    // Ambient sound
    private float idleSoundTimer = 4.0f + (float) (Math.random() * 6.0f);

    // [ANM] State machine
    private State state = State.PATROL;
    private float attackTimer = 0;
    private float attackCooldown = 0;
    private float aggroTimer = 0;           // bear stays angry after a hit
    private Animal huntTarget = null;       // fox's chicken
    private float huntRetargetTimer = 0;
    private float huntIdleTimer = 0;        // pause after a successful kill

    // [ANM] Type-specific life
    private float eggTimer = 0;             // chicken: seconds to next egg
    private boolean sheared = false;        // sheep: wool harvested
    private float woolRegrowTimer = 0;
    private boolean tamed = false;          // parrot: follows the player
    private float followRetargetTimer = 0;

    public Animal(World world, AnimalType type, float x, float y, float z) {
        this.world = world;
        this.type = type;
        this.position = new Vector3f(x, y, z);
        this.yaw = (float) (Math.random() * Math.PI * 2);
        this.hp = maxHp(type);
        if (type == AnimalType.CHICKEN) {
            eggTimer = EGG_MIN + (float) (Math.random() * (EGG_MAX - EGG_MIN));
        }
    }

    public void update(float dt, Player player) {
        if (dead) return;

        if (hurtTimer > 0) hurtTimer -= dt;
        if (loveCooldown > 0) loveCooldown -= dt;
        if (attackCooldown > 0) attackCooldown -= dt;
        if (aggroTimer > 0) aggroTimer -= dt;
        if (huntRetargetTimer > 0) huntRetargetTimer -= dt;
        if (huntIdleTimer > 0) huntIdleTimer -= dt;
        if (followRetargetTimer > 0) followRetargetTimer -= dt;

        // Baby grows up over time
        if (baby) {
            age += dt;
            if (age >= GROW_TIME) baby = false;
        }

        // [ANM] Sheared sheep regrow their wool
        if (sheared) {
            woolRegrowTimer -= dt;
            if (woolRegrowTimer <= 0) sheared = false;
        }

        // Burning: standing in fire or lava hurts and keeps the "cooked" flag
        updateBurning(dt);

        // [ANM] Chickens lay eggs on a timer
        if (type == AnimalType.CHICKEN) {
            eggTimer -= dt;
            if (eggTimer <= 0) {
                eggTimer = EGG_MIN + (float) (Math.random() * (EGG_MAX - EGG_MIN));
                layEgg();
            }
        }

        // Ambient voice, only heard nearby
        idleSoundTimer -= dt;
        if (idleSoundTimer <= 0 && distTo(player) < 16.0f) {
            playIdleSound();
            idleSoundTimer = 7.0f + (float) (Math.random() * 8.0f);
        }

        // --- Behaviour ---
        updateState(dt, player);

        // --- Physics (gravity + block collision) ---
        applyPhysics(dt);

        // --- Animation ---
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        walkPhase += dt * (6.0f + speed * 2.0f);
        limbSwingAmount = Math.min(speed / fleeSpeed(type), 1.0f);

        // Falling out of the world is fatal
        if (position.y < -64) {
            hp = 0;
            dead = true;
        }
    }

    // ------------------------------------------------------------------
    // Behaviour [ANM]
    // ------------------------------------------------------------------

    private void updateState(float dt, Player player) {
        float d = distTo(player);
        boolean lured = !player.isCreative() && playerHoldsFood(player) && d < FOOD_LURE_RANGE;

        // [ANM] A tamed parrot hops after the player
        if (tamed) {
            followPlayer(dt, player);
            return;
        }

        // [ANM] Bears are hostile
        if (type == AnimalType.BEAR) {
            updateBear(dt, player, d);
            return;
        }

        // [ANM] Foxes hunt chickens when one is near
        if (type == AnimalType.FOX && updateFox(dt)) return;

        if (d < fleeRange(type) && !player.isCreative() && !lured) {
            fleeFrom(player);
        } else if (lured) {
            // [ANM] A player offering food draws the animal closer
            moveToward(player.getPosition(), WANDER_SPEED * 1.3f);
        } else {
            updateWander(dt);
        }
    }

    /** [ANM] Bear AI: patrol, chase, strike. Direct pursuit, no pathfinding. */
    private void updateBear(float dt, Player player, float d) {
        if (state != State.ATTACK && d < BEAR_AGGRO_RANGE && !player.isCreative()) {
            state = State.CHASE;
            aggroTimer = BEAR_GRUDGE;
        }

        switch (state) {
            case CHASE:
                if (d < BEAR_ATTACK_RANGE && attackCooldown <= 0) {
                    state = State.ATTACK;
                    attackTimer = 0;
                    AudioManager.play("sounds/animal/bear", 1.0f, 0.5f);
                } else if (aggroTimer <= 0 && d > BEAR_DEAGGRO_RANGE) {
                    state = State.PATROL;
                    hasWanderTarget = false;
                } else {
                    moveToward(player.getPosition(), chaseSpeed(type));
                }
                break;
            case ATTACK:
                attackTimer += dt;
                if (attackTimer >= BEAR_ATTACK_DURATION) {
                    if (d < BEAR_ATTACK_RANGE + 0.5f) {
                        player.takeDamage((int) BEAR_DAMAGE, Player.DeathCause.MOB);
                        // Blow shoves the player away from the bear
                        Vector3f pp = player.getPosition();
                        float kx = pp.x - position.x;
                        float kz = pp.z - position.z;
                        float klen = (float) Math.sqrt(kx * kx + kz * kz);
                        if (klen > 0.001f) {
                            Vector3f pv = player.getVelocity();
                            pv.add((kx / klen) * 4.0f, 2.0f, (kz / klen) * 4.0f);
                        }
                    }
                    attackCooldown = BEAR_ATTACK_COOLDOWN;
                    state = State.CHASE;
                }
                break;
            default:
                updateWander(dt);
                break;
        }
    }

    /** [ANM] Fox AI: stalk the nearest chicken and bite it. */
    private boolean updateFox(float dt) {
        if (huntIdleTimer > 0) {
            updateWander(dt);
            return true;
        }

        if (huntTarget == null || huntTarget.isDead() || huntRetargetTimer <= 0) {
            huntRetargetTimer = 0.5f;
            huntTarget = findNearestChicken();
        }
        if (huntTarget == null) return false; // no chickens: normal behaviour

        float d = distTo(huntTarget.getPosition());
        if (d > FOX_HUNT_RANGE) {
            huntTarget = null;
            return false;
        }

        if (d < FOX_ATTACK_RANGE) {
            if (attackCooldown <= 0) {
                attackCooldown = FOX_ATTACK_COOLDOWN;
                Vector3f from = new Vector3f(huntTarget.getPosition()).sub(position);
                huntTarget.takeDamage(FOX_BITE_DAMAGE, from);
                AudioManager.play("sounds/animal/fox", 1.0f, 0.5f);
                if (huntTarget.isDead()) {
                    huntIdleTimer = 3.0f;
                    huntTarget = null;
                }
            }
            stop();
        } else {
            moveToward(huntTarget.getPosition(), chaseSpeed(type));
        }
        return true;
    }

    private Animal findNearestChicken() {
        Animal best = null;
        float bestD = FOX_HUNT_RANGE * FOX_HUNT_RANGE;
        for (Animal a : world.getAnimals()) {
            if (a == this || a.dead || a.type != AnimalType.CHICKEN) continue;
            float dx = a.position.x - position.x;
            float dz = a.position.z - position.z;
            float dd = dx * dx + dz * dz;
            if (dd < bestD) {
                bestD = dd;
                best = a;
            }
        }
        return best;
    }

    /** [ANM] Tamed parrots hop toward the player and perch nearby. */
    private void followPlayer(float dt, Player player) {
        Vector3f p = player.getPosition();
        float d = distTo(player);
        if (d > 5.0f) {
            moveToward(p, 1.8f);
        } else if (d < 2.5f && onGround && Math.random() < dt * 0.4f) {
            // Happy hops close to the player
            velocity.y = 3.2f;
            AudioManager.play("sounds/animal/parrot", 0.9f, 0.25f);
        }
    }

    /** [ANM] Is the player holding this animal's favourite food? */
    private boolean playerHoldsFood(Player player) {
        return isFoodFor(type, player.getInventory().getSelectedItem());
    }

    /**
     * [ANM] The food that starts breeding (and lures) each animal type.
     * Bears do not breed.
     */
    public static boolean isFoodFor(AnimalType type, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        switch (type) {
            case COW:
            case SHEEP:
            case CHICKEN:
                return stack.isItem() && stack.getItem() == ItemRegistry.WHEAT;
            case PIG:
                return stack.isBlock()
                    && (stack.getBlockType() == BlockType.CARROT
                        || stack.getBlockType() == BlockType.POTATO);
            case DEER:
                return (stack.isItem() && stack.getItem() == ItemRegistry.APPLE_ITEM)
                    || (stack.isBlock() && stack.getBlockType() == BlockType.APPLE);
            case FOX:
                return stack.isItem() && stack.getItem() == ItemRegistry.RAW_CHICKEN;
            default:
                return false;
        }
    }

    /** [ANM] Stand still (used by attacking fox and tamed parrot). */
    private void stop() {
        velocity.x = 0;
        velocity.z = 0;
    }

    /** Move toward a world position at a given speed, avoiding cliff edges. */
    private void moveToward(Vector3f target, float speed) {
        float tx = target.x - position.x;
        float tz = target.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);
        if (dist < 0.3f) {
            stop();
            return;
        }
        float dx = tx / dist;
        float dz = tz / dist;
        // [ANM] Cliff avoidance: do not walk off an edge
        if (!groundAhead(dx, dz)) {
            stop();
            return;
        }
        // [GP-043] Do not step into lava
        if (lavaAhead(dx, dz)) {
            stop();
            return;
        }
        yaw = (float) Math.atan2(-tx, -tz);
        velocity.x = dx * speed;
        velocity.z = dz * speed;
    }

    /** [ANM] Is there ground 1-2 blocks below the point 1.5 blocks ahead? */
    private boolean groundAhead(float dx, float dz) {
        float fx = position.x + dx * 1.5f;
        float fz = position.z + dz * 1.5f;
        int fx0 = (int) Math.floor(fx);
        int fz0 = (int) Math.floor(fz);
        int by = (int) Math.floor(position.y);
        for (int dy = 1; dy <= 2; dy++) {
            if (world.isSolid(fx0, by - dy, fz0)) return true;
        }
        return false;
    }

    /** [GP-043] Is the block the animal is about to step into lava? */
    private boolean lavaAhead(float dx, float dz) {
        float fx = position.x + dx * 1.2f;
        float fz = position.z + dz * 1.2f;
        int by = (int) Math.floor(position.y);
        return world.getBlock((int) Math.floor(fx), by, (int) Math.floor(fz)) == BlockType.LAVA.id
            || world.getBlock((int) Math.floor(fx), by - 1, (int) Math.floor(fz)) == BlockType.LAVA.id;
    }

    private float distTo(Player player) {
        return distTo(player.getPosition());
    }

    private float distTo(Vector3f p) {
        float dx = p.x - position.x;
        float dz = p.z - position.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private void fleeFrom(Player player) {
        Vector3f p = player.getPosition();
        float dx = position.x - p.x;
        float dz = position.z - p.z;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < 0.01f) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        yaw = (float) Math.atan2(-dx, -dz);
        velocity.x = (dx / d) * fleeSpeed(type);
        velocity.z = (dz / d) * fleeSpeed(type);
    }

    private void updateWander(float dt) {
        retargetTimer -= dt;
        if (!hasWanderTarget || retargetTimer <= 0) {
            // [ANM] Herding: drift toward the nearest mate within 8 blocks
            Animal mate = findNearestMate();
            if (mate != null) {
                wanderTarget.set(mate.position);
                hasWanderTarget = true;
                retargetTimer = 2.0f;
            } else {
                float angle = (float) (Math.random() * Math.PI * 2);
                float dist = 2.0f + (float) Math.random() * 5.0f;
                wanderTarget.set(
                    position.x + (float) Math.cos(angle) * dist,
                    position.y,
                    position.z + (float) Math.sin(angle) * dist
                );
                hasWanderTarget = true;
                retargetTimer = 4.0f + (float) (Math.random() * 5.0f);
            }
        }

        float tx = wanderTarget.x - position.x;
        float tz = wanderTarget.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);
        if (dist > 0.5f) {
            moveToward(wanderTarget, WANDER_SPEED);
        } else {
            hasWanderTarget = false;
            stop();
        }
    }

    /** [ANM] Nearest adult mate of the same type within 8 blocks. */
    private Animal findNearestMate() {
        Animal best = null;
        float bestD = 8.0f * 8.0f;
        for (Animal a : world.getAnimals()) {
            if (a == this || a.dead || a.baby || a.type != type) continue;
            float dx = a.position.x - position.x;
            float dz = a.position.z - position.z;
            float dd = dx * dx + dz * dz;
            if (dd < bestD) {
                bestD = dd;
                best = a;
            }
        }
        return best;
    }

    /** [ANM] Drop an egg beside a grounded, dry chicken. */
    private void layEgg() {
        if (!onGround) return;
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        if (world.getBlock(bx, by, bz) == BlockType.WATER.id) return;
        world.spawnDrop(position, BlockType.ITEM_EGG, 1);
    }

    private void updateBurning(float dt) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        boolean inFire = world.getBlock(bx, by, bz) == BlockType.FIRE.id
            || world.getBlock(bx, by, bz) == BlockType.LAVA.id;
        if (inFire) {
            burning = true;
            fireTimer += dt;
            if (fireTimer >= FIRE_TICK) {
                fireTimer = 0;
                hp -= 1;
                hurtTimer = HURT_DURATION;
                if (hp <= 0) {
                    hp = 0;
                    dead = true;
                }
            }
        } else {
            fireTimer = 0;
        }
    }

    /**
     * [GP-045] Feed the animal: both parents enter a breeding cooldown and a
     * baby of the same type appears at the midpoint. Requires a ready adult
     * partner of the same type within 8 blocks.
     */
    public boolean tryBreed(World w, Player player) {
        if (dead || baby || loveCooldown > 0) return false;

        Animal partner = null;
        for (Animal other : w.getAnimals()) {
            if (other == this || other.dead || other.baby || other.type != type
                    || other.loveCooldown > 0) continue;
            float dx = other.position.x - position.x;
            float dz = other.position.z - position.z;
            if (dx * dx + dz * dz < 8.0f * 8.0f) {
                partner = other;
                break;
            }
        }
        if (partner == null) return false;

        loveCooldown = BREED_COOLDOWN;
        partner.loveCooldown = BREED_COOLDOWN;

        float mx = (position.x + partner.position.x) * 0.5f;
        float mz = (position.z + partner.position.z) * 0.5f;
        int groundY = w.getGroundHeight((int) mx, (int) mz);
        w.spawnAnimalBaby(type, mx, groundY + 0.01f, mz);

        AudioManager.play("sounds/animal/" + type.name().toLowerCase(), 1.0f, 0.4f);
        return true;
    }

    /** [ANM] Feed a baby: a minute closer to adulthood. */
    public boolean feedBaby() {
        if (!baby) return false;
        age = Math.min(age + FEED_GROWTH_BOOST, GROW_TIME);
        AudioManager.play("sounds/animal/" + type.name().toLowerCase(), 1.0f, 0.4f);
        return true;
    }

    /** [ANM] Shear a sheep: yields wool once, then regrows. */
    public boolean shear() {
        if (sheared || type != AnimalType.SHEEP) return false;
        sheared = true;
        woolRegrowTimer = WOOL_REGROW;
        return true;
    }

    /** [ANM] Tame a parrot with wheat. */
    public boolean tryTame(ItemStack food) {
        if (tamed || type != AnimalType.PARROT) return false;
        if (food == null || !(food.isItem() && food.getItem() == ItemRegistry.WHEAT)) return false;
        tamed = true;
        AudioManager.play("sounds/animal/parrot", 1.0f, 0.4f);
        return true;
    }

    private void playIdleSound() {
        String folder = "sounds/animal/" + type.name().toLowerCase();
        AudioManager.play(folder, 0.95f + (float) Math.random() * 0.2f, 0.3f);
    }

    // ------------------------------------------------------------------
    // Physics: gravity + AABB collision (same model as Zoloy)
    // ------------------------------------------------------------------

    private void applyPhysics(float dt) {
        onGround = false;

        // [ANM] Animals float in water instead of sinking
        boolean inWater = isInWater();
        if (inWater) {
            velocity.y += 12.0f * dt;
            if (velocity.y > 2.0f) velocity.y = 2.0f;
            if (velocity.y < -2.0f) velocity.y = -2.0f;
        } else {
            velocity.y -= GRAVITY * dt;
            if (velocity.y < -MAX_FALL_SPEED) velocity.y = -MAX_FALL_SPEED;
        }

        float drag = inWater ? 0.6f : 1.0f;
        moveAxis(velocity.x * drag * dt, 0);
        moveAxis(0, velocity.z * drag * dt);

        // Vertical: fall until something solid is underfoot
        position.y += velocity.y * dt;
        resolveVertical();
    }

    /** [ANM] Is the middle of the animal's body in water? */
    private boolean isInWater() {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y + height() * 0.5f);
        int bz = (int) Math.floor(position.z);
        return world.getBlock(bx, by, bz) == BlockType.WATER.id;
    }

    private void moveAxis(float dx, float dz) {
        if (dx == 0 && dz == 0) return;
        position.x += dx;
        position.z += dz;

        float minX = position.x - HALF_WIDTH;
        float maxX = position.x + HALF_WIDTH;
        float minZ = position.z - HALF_WIDTH;
        float maxZ = position.z + HALF_WIDTH;
        float minY = position.y;
        float maxY = position.y + height();

        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(maxX);
        int z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(maxZ);
        int y0 = (int) Math.floor(minY), y1 = (int) Math.floor(maxY);

        float[] bb = bbScratch;
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
            position.y += rise;
        }
    }

    private float stepUpAmount(float dx, float dz, float blockTop) {
        float rise = blockTop - position.y;
        if (rise <= 0.001f || rise > STEP_UP + 0.001f) return 0;

        int cx = (int) Math.floor(position.x + (dx > 0.001f ? HALF_WIDTH + 0.1f : dx < -0.001f ? -HALF_WIDTH - 0.1f : 0));
        int cz = (int) Math.floor(position.z + (dz > 0.001f ? HALF_WIDTH + 0.1f : dz < -0.001f ? -HALF_WIDTH - 0.1f : 0));

        int top = (int) Math.floor(position.y + height() + rise + 0.01f);
        for (int y = (int) Math.floor(position.y + height()); y <= top; y++) {
            if (isBlockSolid(cx, y, cz)) return 0;
        }
        return rise;
    }

    private void resolveVertical() {
        float minX = position.x - HALF_WIDTH;
        float maxX = position.x + HALF_WIDTH;
        float minZ = position.z - HALF_WIDTH;
        float maxZ = position.z + HALF_WIDTH;

        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(maxX);
        int z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(maxZ);
        int y0 = (int) Math.floor(position.y);
        int y1 = (int) Math.floor(position.y + height());

        float[] bb = bbScratch;
        if (velocity.y <= 0) {
            // Falling: land on the highest AABB top the body overlaps
            float landY = -Float.MAX_VALUE;
            for (int bx = x0; bx <= x1; bx++) {
                for (int bz = z0; bz <= z1; bz++) {
                    for (int by = y0; by <= y1; by++) {
                        if (!solidAabb(bx, by, bz, bb)) continue;
                        if (bb[4] > position.y && bb[1] < position.y + height()
                            && maxX > bb[0] && minX < bb[3]
                            && maxZ > bb[2] && minZ < bb[5]) {
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
                        if (bb[1] < position.y + height() && bb[4] > position.y
                            && maxX > bb[0] && minX < bb[3]
                            && maxZ > bb[2] && minZ < bb[5]) {
                            ceilY = Math.min(ceilY, bb[1]);
                        }
                    }
                }
            }
            if (ceilY != Float.MAX_VALUE) {
                position.y = ceilY - height() - 0.001f;
                velocity.y = 0;
            }
        }
    }

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

    /** Body height in blocks: 1.1 for quadrupeds, 0.6 for chickens and
     *  parrots, 0.8 for the sitting fox, 1.2 for the bear. */
    private float height() {
        switch (type) {
            case CHICKEN:
            case PARROT:    return 0.6f;
            case FOX:       return 0.8f;
            case BEAR:      return 1.2f;
            default:        return 1.1f;
        }
    }

    // --- Damage ---

    /**
     * [GP-038] Damage with knockback and a hurt flash, like Zoloy.
     */
    public void takeDamage(float dmg, Vector3f fromDir) {
        if (dead) return;
        hp -= dmg;
        hurtTimer = HURT_DURATION;
        playHurtSound();

        // [ANM] Hitting a bear enrages it
        if (type == AnimalType.BEAR) {
            state = State.CHASE;
            aggroTimer = BEAR_GRUDGE;
        }

        float len = (float) Math.sqrt(fromDir.x * fromDir.x + fromDir.z * fromDir.z);
        if (len > 0.001f) {
            float strength = 5.5f;
            velocity.x += (fromDir.x / len) * strength;
            velocity.z += (fromDir.z / len) * strength;
            velocity.y = 4.0f;
        }

        if (hp <= 0) {
            hp = 0;
            dead = true;
            // Deeper, slower clip for the dying gasp
            AudioManager.play("sounds/animal/" + type.name().toLowerCase(),
                0.6f + (float) Math.random() * 0.2f, 0.55f);
        }
    }

    private void playHurtSound() {
        String folder = "sounds/animal/" + type.name().toLowerCase();
        AudioManager.play(folder, 1.0f + (float) Math.random() * 0.2f, 0.5f);
    }

    // --- Getters / setters ---

    public Vector3f getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getWalkPhase() { return walkPhase; }
    public float getLimbSwingAmount() { return limbSwingAmount; }
    public float getHp() { return hp; }
    public float getMaxHp() { return maxHp(type); }
    public boolean isDead() { return dead; }
    public AnimalType getType() { return type; }
    public boolean isBaby() { return baby; }
    public void setBaby(boolean v) { baby = v; }
    public boolean isBurning() { return burning; }
    public boolean isSheared() { return sheared; }
    public boolean isTamed() { return tamed; }

    /** [ANM] Test hook: make the next egg appear almost immediately. */
    public void forceEggSoon() {
        if (type == AnimalType.CHICKEN) eggTimer = 0.5f;
    }

    /** 0..1 red flash intensity while hurt. */
    public float getHurtProgress() {
        return Math.max(0, Math.min(1.0f, hurtTimer / HURT_DURATION));
    }

    /** Model scale for the renderer (babies are small and grow). */
    public float getModelScale() {
        float base;
        switch (type) {
            case COW:     base = SCALE_COW; break;
            case PIG:     base = SCALE_PIG; break;
            case CHICKEN: base = SCALE_CHICKEN; break;
            case DEER:    base = SCALE_DEER; break;
            case FOX:     base = SCALE_FOX; break;
            case BEAR:    base = SCALE_BEAR; break;
            case PARROT:  base = SCALE_PARROT; break;
            default:      base = SCALE_SHEEP; break;
        }
        if (!baby) return base;
        float progress = Math.min(age / GROW_TIME, 1.0f);
        return base * (0.55f + 0.45f * progress);
    }

    public boolean isOnGround() { return onGround; }
}