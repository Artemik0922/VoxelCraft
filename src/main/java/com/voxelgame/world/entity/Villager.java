package com.voxelgame.world.entity;

import com.voxelgame.audio.AudioManager;
import com.voxelgame.economy.TradeOffer;
import com.voxelgame.economy.TradeRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.player.Player;
import com.voxelgame.world.BlockType;
import com.voxelgame.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Villager (Р¶РёС‚РµР»СЊ РґРµСЂРµРІРЅРё) вЂ” РјРёСЂРЅС‹Р№ РјРѕР± СЃ СЂР°СЃРїРёСЃР°РЅРёРµРј РґРЅСЏ РєР°Рє РІ Minecraft.
 *
 * Р Р°СЃРїРёСЃР°РЅРёРµ (РІСЂРµРјСЏ 0-24000, 0 = 06:00):
 *   0-8000     (06:00-14:00) вЂ” WORK: РёРґСѓС‚ РЅР° СЂР°Р±РѕС‡РµРµ РјРµСЃС‚Рѕ Рё СЂР°Р±РѕС‚Р°СЋС‚
 *   8000-12000 (14:00-18:00) вЂ” РѕС‚РґС‹С…: Р±СЂРѕРґСЏС‚ РїРѕ РґРµСЂРµРІРЅРµ, РѕР±С‰Р°СЋС‚СЃСЏ
 *   12000-14000 (18:00-20:00) вЂ” РёРґСѓС‚ РґРѕРјРѕР№
 *   14000-24000 (20:00-06:00) вЂ” SLEEP: СЃРїСЏС‚ РґРѕРјР° (Р»РµР¶Р°С‚ РЅР° Р·РµРјР»Рµ)
 *
 * РџРѕРІРµРґРµРЅРёРµ:
 *   - РЎРјРѕС‚СЂСЏС‚ РЅР° РёРіСЂРѕРєР° РіРѕР»РѕРІРѕР№, РєРѕРіРґР° РѕРЅ СЂСЏРґРѕРј (РєР°Рє РІ MC)
 *   - РћС‚С…РѕРґСЏС‚ РЅР° С€Р°Рі, РµСЃР»Рё РёРіСЂРѕРє РїРѕРґРѕС€С‘Р» РІРїР»РѕС‚РЅСѓСЋ, РЅРѕ РќР• СѓР±РµРіР°СЋС‚
 *   - РЈР±РµРіР°СЋС‚ С‚РѕР»СЊРєРѕ РµСЃР»Рё РёС… СѓРґР°СЂРёР»Рё РёР»Рё СЂСЏРґРѕРј РјРѕРЅСЃС‚СЂС‹ (Р±РµРіСѓС‚ РґРѕРјРѕР№ РїСЂСЏС‚Р°С‚СЊСЃСЏ)
 *   - РћР±С‰Р°СЋС‚СЃСЏ: РїРѕРґС…РѕРґСЏС‚ Рє РґСЂСѓРіРѕРјСѓ Р¶РёС‚РµР»СЋ Рё СЂР°Р·РіРѕРІР°СЂРёРІР°СЋС‚
 *   - РџР»Р°РІРЅС‹Рµ РїРѕРІРѕСЂРѕС‚С‹, РЅРµ РїР°РґР°СЋС‚ СЃ РѕР±СЂС‹РІРѕРІ, РЅРµ Р·Р°СЃС‚СЂРµРІР°СЋС‚ Сѓ СЃС‚РµРЅ
 */
public class Villager {

    // [PERF] Скретч-AABB для физики: раньше new float[6] дважды за тик на сущность
    private final float[] bbScratch = new float[6];

    /** РџСЂРѕС„РµСЃСЃРёСЏ Р¶РёС‚РµР»СЏ (РїРѕСЂСЏРґРѕРє = РїРѕСЂСЏРґРѕРє С‚Р°Р№Р»РѕРІ РІ Р°С‚Р»Р°СЃРµ!). */
    public enum Profession {
        FARMER, LIBRARIAN, BLACKSMITH, BUTCHER, PRIEST,
        FISHERMAN, FLETCHER, LEATHERWORKER, SHEPHERD,
        TOOLSMITH, ARMORER, WEAPONSMITH, CARTOGRAPHER,
        CLERIC, MASON, NITWIT, WARRIOR
    }

    /** РЎРѕСЃС‚РѕСЏРЅРёРµ AI. */
    public enum State {
        IDLE, WANDER, WORK, SOCIAL, SLEEP, FLEE, GUARD, STORE
    }

    // === РљРѕРЅСЃС‚Р°РЅС‚С‹ ===
    private static final float MAX_HP = 20.0f;
    private static final float WALK_SPEED = 1.0f;
    private static final float RUN_SPEED = 2.5f;
    private static final float GRAVITY = 28.0f;
    private static final float WANDER_RADIUS = 12.0f;
    private static final float SOCIAL_ARRIVE = 1.6f;
    private static final float MAX_FALL_SPEED = 78.4f;
    /** Р”РёСЃС‚Р°РЅС†РёСЏ, СЃ РєРѕС‚РѕСЂРѕР№ Р¶РёС‚РµР»СЊ РїРѕРІРѕСЂР°С‡РёРІР°РµС‚ РіРѕР»РѕРІСѓ Рє РёРіСЂРѕРєСѓ. */
    private static final float LOOK_RANGE = 10.0f;
    /** Р•СЃР»Рё РёРіСЂРѕРє РїРѕРґРѕС€С‘Р» Р±Р»РёР¶Рµ вЂ” Р¶РёС‚РµР»СЊ РѕС‚СЃС‚СѓРїР°РµС‚ РЅР° С€Р°Рі. */
    private static final float BACK_AWAY_RANGE = 1.3f;
    /** РњРѕРЅСЃС‚СЂ Р±Р»РёР¶Рµ СЌС‚РѕР№ РґРёСЃС‚Р°РЅС†РёРё вЂ” Р¶РёС‚РµР»СЊ Р±РµР¶РёС‚ РґРѕРјРѕР№. */
    private static final float MOB_FLEE_RANGE = 14.0f;
    /** РЎРєРѕСЂРѕСЃС‚СЊ РїР»Р°РІРЅРѕРіРѕ РїРѕРІРѕСЂРѕС‚Р°, СЂР°Рґ/СЃ. */
    private static final float TURN_RATE = 5.0f;

    // === Р’РѕРёРЅ (WARRIOR) ===
    /** Р”Р°Р»СЊРЅРѕСЃС‚СЊ, СЃ РєРѕС‚РѕСЂРѕР№ РІРѕРёРЅ Р·Р°РјРµС‡Р°РµС‚ РјРѕРЅСЃС‚СЂР°. */
    private static final float DETECT_RANGE = 24.0f;
    /** Р”РёСЃС‚Р°РЅС†РёСЏ СѓРґР°СЂР° РјРµС‡РѕРј. */
    private static final float ATTACK_RANGE = 2.0f;
    private static final float ATTACK_COOLDOWN = 1.4f;
    private static final float ATTACK_DURATION = 0.45f;
    private static final float ATTACK_DAMAGE = 6.0f;
    /** Р Р°РґРёСѓСЃ РїР°С‚СЂСѓР»СЏ РІРѕРёРЅР° РІРѕРєСЂСѓРі СЂР°Р±РѕС‡РµРіРѕ РјРµСЃС‚Р°. */
    private static final float GUARD_RADIUS = 14.0f;
    /** Р Р°РґРёСѓСЃ РїРѕРёСЃРєР° "РјР°С‚РµСЂРёР°Р»Р°" РґР»СЏ СЂР°Р±РѕС‚С‹ (С„РµСЂРјРµСЂ вЂ” РіСЂСЏРґРєРё, СЂС‹Р±Р°Рє вЂ” РІРѕРґР°). */
    private static final float WORK_SCAN_RANGE = 22.0f;
    // === [CR] Р¤РµСЂРјРµСЂ (FARMER) ===
    /** Р¤РµСЂРјС‹ РґРµСЂРµРІРЅРё СЃС‚РѕСЏС‚ РІ 24-32 Р±Р»РѕРєР°С… РѕС‚ С†РµРЅС‚СЂР° вЂ” РёС‰РµРј С€РёСЂРµ. */
    private static final float FARM_SCAN_RANGE = 40.0f;
    /** РЎР»РѕС‚С‹ РёРЅРІРµРЅС‚Р°СЂСЏ С„РµСЂРјРµСЂР° (СѓСЂРѕР¶Р°Р№ СЃРєР»Р°РґС‹РІР°РµС‚СЃСЏ РІ СЃС‚РѕРїРєРё РїРѕ 64). */
    private static final int FARMER_INVENTORY_SIZE = 9;
    /** РџСЂРё СЌС‚РѕРј РєРѕР»РёС‡РµСЃС‚РІРµ СѓСЂРѕР¶Р°СЏ С„РµСЂРјРµСЂ РёРґС‘С‚ РЅР° СЃРєР»Р°Рґ (в‰€80% РѕС‚ 9x64). */
    private static final int STORE_THRESHOLD = 460;
    /** РЎС‚РѕР»СЊРєРѕ РµРґРёРЅРёС† СѓСЂРѕР¶Р°СЏ РїСЂРµРІСЂР°С‰Р°РµС‚СЃСЏ РІ РѕРґРёРЅ С‚СЋРє СЃРµРЅР°. */
    private static final int HAY_BALE_COST = 9;
    /** РўСЋРєРё СЃРєР»Р°РґС‹РІР°СЋС‚СЃСЏ СЃС‚РѕР»Р±РёРєР°РјРё РґРѕ СЌС‚РѕР№ РІС‹СЃРѕС‚С‹. */
    private static final int BALE_MAX_STACK = 3;
    /** Р”РёСЃС‚Р°РЅС†РёСЏ, СЃ РєРѕС‚РѕСЂРѕР№ С„РµСЂРјРµСЂ СЃС‡РёС‚Р°РµС‚СЃСЏ РґРѕС€РµРґС€РёРј РґРѕ СЃРєР»Р°РґР°. */
    private static final float STORE_ARRIVE = 2.2f;

    // AABB-РєРѕР»Р»РёР·РёСЏ (РєР°Рє Сѓ Zoloy)
    private static final float HALF_WIDTH = 0.3f;
    private static final float HEIGHT = 1.8f;
    private static final float STEP_UP = 1.0f;
    /** РџСЂС‹Р¶РѕРє С‡РµСЂРµР· РїСЂРµРїСЏС‚СЃС‚РІРёРµ: РІС‹СЃРѕС‚Р° ~1 Р±Р»РѕРє, + Р°РІС‚Рѕ-С€Р°Рі = 2 Р±Р»РѕРєР°. */
    private static final float JUMP_SPEED = 7.8f;
    private static final float JUMP_COOLDOWN = 1.2f;

    private final World world;
    private final Vector3f position;
    private final Vector3f velocity = new Vector3f();
    private float yaw;
    private float pitch;
    /** Р¦РµР»РµРІРѕР№ РїРѕРІРѕСЂРѕС‚ (РїР»Р°РІРЅРѕ РїРѕРґРІРѕРґРёРј yaw Рє РЅРµРјСѓ). */
    private float desiredYaw;
    /** РљСѓРґР° РЅР°РїСЂР°РІР»РµРЅР° РіРѕР»РѕРІР° (РјРёСЂ). */
    private float lookYaw;
    /** РќР°РєР»РѕРЅ РіРѕР»РѕРІС‹ РІРІРµСЂС…/РІРЅРёР·. */
    private float lookPitch;

    // === РЎС‚Р°С‚С‹ ===
    private float hp = MAX_HP;
    private boolean dead = false;
    private final Profession profession;
    private final boolean isBaby;
    private State state = State.IDLE;
    private String biome = "plains";

    // === РђРЅРёРјР°С†РёСЏ ===
    private float walkPhase = 0;
    private float limbSwingAmount = 0;
    private float headBob = 0;
    private float talkTimer = 0;
    private boolean isTalking = false;

    // === AI С‚Р°Р№РјРµСЂС‹ ===
    private float stateTimer = 0;
    private float idleTimer = 0;
    private float sleepTimer = 0;
    private float workTimer = 0;
    private float socialTimer = 0;
    private float fleeTimer = 0;
    private boolean fleeToHome = false;

    // === РџР°РЅРёРєР° РѕС‚ Р°СЃС‚РµСЂРѕРёРґР° ===
    /** РЎРєРѕР»СЊРєРѕ РµС‰С‘ СЃРµРєСѓРЅРґ Р±РµР¶Р°С‚СЊ РѕС‚ РёСЃС‚РѕС‡РЅРёРєР° РїР°РЅРёРєРё. */
    private float panicTimer = 0;
    private float panicRetarget = 0;
    private final Vector3f panicSource = new Vector3f();
    private final Vector3f panicTarget = new Vector3f();
    private boolean onGround = false;

    // === Р¦РµР»Рё РґРІРёР¶РµРЅРёСЏ ===
    private final Vector3f moveTarget = new Vector3f();
    private boolean hasTarget = false;
    private float stuckTimer = 0;
    private float cliffCheckTimer = 0;
    /** РЈРїРµСЂР»РёСЃСЊ РІ СЃС‚РµРЅСѓ Р±РµР· РІРѕР·РјРѕР¶РЅРѕСЃС‚Рё С€Р°РіР° вЂ” СЂР°Р·СЂРµС€С‘РЅ РїСЂС‹Р¶РѕРє. */
    private boolean blockedByObstacle = false;
    private float jumpCooldown = 0;
    /** РќР°РїСЂР°РІР»РµРЅРёРµ, РІ РєРѕС‚РѕСЂРѕРј РґР°РІРёРј РЅР° РїСЂРµРїСЏС‚СЃС‚РІРёРµ (РґР»СЏ РїСЂС‹Р¶РєР°). */
    private float pushDirX = 0, pushDirZ = 0;

    /** РџРѕР·РёС†РёСЏ РґРѕРјР° (РіРґРµ СЃРїРёС‚ РЅРѕС‡СЊСЋ). */
    private final Vector3f homePos = new Vector3f();
    private boolean hasHome = false;

    /** РџРѕР·РёС†РёСЏ СЂР°Р±РѕС‡РµРіРѕ РјРµСЃС‚Р° (Р·РґР°РЅРёРµ РІ РґРµСЂРµРІРЅРµ). */
    private final Vector3f workPos = new Vector3f();
    private boolean hasWork = false;

    /** РЎРѕР±РµСЃРµРґРЅРёРє РґР»СЏ SOCIAL. */
    private Villager socialBuddy = null;

    // === Р’РѕРёРЅ (WARRIOR) ===
    /** РўРµРєСѓС‰Р°СЏ С†РµР»СЊ Р°С‚Р°РєРё (Zoloy) РёР»Рё null. */
    private Zoloy attackTarget = null;
    private float attackTimer = 0;
    private float attackCooldown = 0;
    private float guardTimer = 0;

    // === Р Р°Р±РѕС‚Р° РїРѕ РїСЂРѕС„РµСЃСЃРёРё ===
    /** РљСѓРґР° РёРґС‚Рё СЂР°Р±РѕС‚Р°С‚СЊ: Сѓ С„РµСЂРјРµСЂР° вЂ” РіСЂСЏРґРєР°, Сѓ СЂС‹Р±Р°РєР° вЂ” Р±РµСЂРµРі Рё С‚.Рї. */
    private final Vector3f workTarget = new Vector3f();
    private boolean hasWorkTarget = false;

    // === [CR] Р¤РµСЂРјРµСЂ (FARMER) ===
    /** Y-РєРѕРѕСЂРґРёРЅР°С‚Р° Р±Р»РѕРєР°-РіСЂСЏРґРєРё (workTarget.y вЂ” СЌС‚Рѕ РЅРѕРіРё Р¶РёС‚РµР»СЏ). */
    private int workTargetBlockY = -1;
    /** РЈСЂРѕР¶Р°Р№ РІ РёРЅРІРµРЅС‚Р°СЂРµ: СЃС‚РѕРїРєРё РєСѓР»СЊС‚СѓСЂ (WHEAT/CARROT/POTATO). */
    private final ItemStack[] farmerInventory = new ItemStack[FARMER_INVENTORY_SIZE];
    /** РЎРєР»Р°Рґ (Р°РјР±Р°СЂ): РєСѓРґР° СЃРЅРѕСЃРёС‚СЊ СѓСЂРѕР¶Р°Р№ Рё РєР»Р°СЃС‚СЊ С‚СЋРєРё СЃРµРЅР°. */
    private final Vector3f storagePos = new Vector3f();
    private boolean hasStorage = false;
    /** РўР°Р№РјРµСЂ РІС‹РіСЂСѓР·РєРё СѓСЂРѕР¶Р°СЏ РЅР° СЃРєР»Р°РґРµ. */
    private float storeTimer = 0;
    /** [PERF] Грядки деревни (x,y,z), собранные при генерации, — вместо тяжёлого скана мира. */
    private List<int[]> farmCrops = null;
    /** Кулдаун резервного скана мира, когда по списку ничего не нашлось. */
    private float wideScanCooldown = 0;
    /** [PERF] Кэш сканирования угроз — раз в 0.25 с вместо каждого тика. */
    private float threatScanTimer = 0;
    private Zoloy cachedThreat = null;

    /** РРЅРґРµРєСЃ С‚РµРєСЃС‚СѓСЂС‹ РІ Р°С‚Р»Р°СЃРµ (0-15 РґР»СЏ РїСЂРѕС„РµСЃСЃРёР№). */
    private final int textureIndex;

    /** Р’Р°СЂРёР°РЅС‚ СЃРєРёРЅР° (0 РёР»Рё 1) вЂ” РЅРѕРјРµСЂ СЃС‚СЂРѕРєРё РІ HD-Р°С‚Р»Р°СЃРµ 16384x2048. */
    private final int variant;

    // === [ECO] РўРѕСЂРіРѕРІР»СЏ ===
    /** РћСЃС‚Р°РІС€РёРµСЃСЏ РїРѕСЃС‚Р°РІРєРё РїРѕ РёРЅРґРµРєСЃР°Рј РїСЂРµРґР»РѕР¶РµРЅРёР№ РїСЂРѕС„РµСЃСЃРёРё (0 = РїРѕРІР°СЂ СЂР°СЃРїСЂРѕРґР°РЅ). */
    private int[] tradeStock;
    /** РўР°Р№РјРµСЂ РїРѕРїРѕР»РЅРµРЅРёСЏ Р°СЃСЃРѕСЂС‚РёРјРµРЅС‚Р° (~1 РёРіСЂРѕРІР°СЏ РјРёРЅСѓС‚Р°). */
    private float restockCooldown = 0;

    /** [ECO] РРіСЂРѕРє вЂ” Р“РµСЂРѕР№ РґРµСЂРµРІРЅРё: РІРѕРёРЅС‹ РїРµСЂРµСЃС‚Р°СЋС‚ РѕС…СЂР°РЅСЏС‚СЊ РІР°С€Р°С‚ Рё СЃРѕРїСЂРѕРІРѕР¶РґР°СЋС‚ РёРіСЂРѕРєР°. */
    public static boolean heroPresent = false;

    public Villager(World world, float x, float y, float z, Profession profession) {
        this(world, x, y, z, profession, false);
    }

    public Villager(World world, float x, float y, float z, Profession profession, boolean isBaby) {
        this.world = world;
        this.position = new Vector3f(x, y, z);
        this.homePos.set(x, y, z);
        this.hasHome = true;
        this.profession = profession;
        this.isBaby = isBaby;
        this.yaw = (float) (Math.random() * Math.PI * 2);
        this.desiredYaw = this.yaw;
        this.lookYaw = this.yaw;
        this.textureIndex = profession.ordinal();
        this.variant = Math.random() < 0.5 ? 0 : 1;
        // Р’РѕРёРЅ РєСЂРµРїС‡Рµ РѕР±С‹С‡РЅРѕРіРѕ Р¶РёС‚РµР»СЏ Рё РЅРµ СЃРїРёС‚ РїРѕ СЂР°СЃРїРёСЃР°РЅРёСЋ
        if (profession == Profession.WARRIOR) {
            this.hp = 45.0f;
        }
        this.stateTimer = 2.0f + (float) Math.random() * 3.0f;
        initTradeStock();
        this.restockCooldown = 60.0f;
    }

    /**
     * РћР±РЅРѕРІР»РµРЅРёРµ AI Рё С„РёР·РёРєРё.
     *
     * @param dt        РґРµР»СЊС‚Р° РІСЂРµРјРµРЅРё
     * @param player    РёРіСЂРѕРє (РІР·РіР»СЏРґ, РѕС‚СЃС‚СѓРї, РїРѕР±РµРі РїРѕСЃР»Рµ СѓРґР°СЂР°)
     * @param timeOfDay РІСЂРµРјСЏ СЃСѓС‚РѕРє 0-24000 (0=6:00, 6000=12:00, 12000=18:00, 18000=0:00)
     */
    public void update(float dt, Player player, int timeOfDay) {
        if (dead) return;
        if (dt > 0.1f) dt = 0.1f;

        Vector3f playerPos = player.getPosition();
        float dx = playerPos.x - position.x;
        float dz = playerPos.z - position.z;
        float distToPlayer = (float) Math.sqrt(dx * dx + dz * dz);

        // === РЈРјРµРЅСЊС€РёС‚СЊ С‚Р°Р№РјРµСЂС‹ ===
        stateTimer -= dt;
        if (idleTimer > 0) idleTimer -= dt;
        if (talkTimer > 0) {
            talkTimer -= dt;
            if (talkTimer <= 0) isTalking = false;
        }
        if (fleeTimer > 0) fleeTimer -= dt;
        if (wideScanCooldown > 0) wideScanCooldown -= dt;

        // === [ECO] РџРѕРїРѕР»РЅРµРЅРёРµ Р°СЃСЃРѕСЂС‚РёРјРµРЅС‚Р° Р¶РёС‚РµР»СЏ ===
        if (restockCooldown > 0) {
            restockCooldown -= dt;
            if (restockCooldown <= 0) {
                restock();
                restockCooldown = 60.0f;
            }
        }

        // === РЈРіСЂРѕР·Р°: РјРѕРЅСЃС‚СЂС‹ СЂСЏРґРѕРј ===
        // [PERF] Скан всех мобов не каждый тик — кэш на 0.25 с (мёртвый кэш сбрасывается)
        Zoloy threat;
        if (threatScanTimer > 0) {
            threatScanTimer -= dt;
            threat = (cachedThreat != null && !cachedThreat.isDead()) ? cachedThreat : null;
        } else {
            threatScanTimer = 0.25f;
            threat = findNearestMob(MOB_FLEE_RANGE);
            cachedThreat = threat;
        }
        boolean mobThreat = threat != null;

        // === РњР°С€РёРЅР° СЃРѕСЃС‚РѕСЏРЅРёР№ ===
        if (state == State.FLEE) {
            updateFlee(dt, threat, playerPos, distToPlayer);
        } else if (profession == Profession.WARRIOR) {
            // Р’РѕРёРЅ РЅРµ СЃРїРёС‚ Рё РЅРµ СѓР±РµРіР°РµС‚ РѕС‚ РјРѕРЅСЃС‚СЂРѕРІ вЂ” РѕС…СЂР°РЅСЏРµС‚ РґРµСЂРµРІРЅСЋ
            if (state != State.GUARD) {
                state = State.GUARD;
                stateTimer = 0;
                hasTarget = false;
                socialBuddy = null;
                isTalking = false;
            }
            updateGuard(dt, playerPos);
        } else if (mobThreat && state != State.SLEEP && !isBaby) {
            // РњРѕРЅСЃС‚СЂС‹ СЂСЏРґРѕРј вЂ” Р±РµР¶РёРј РґРѕРјРѕР№ Рё РїСЂСЏС‡РµРјСЃСЏ
            state = State.FLEE;
            fleeToHome = true;
            fleeTimer = 30.0f;
            hasTarget = false;
            socialBuddy = null;
            isTalking = false;
        } else {
            // === Р Р°СЃРїРёСЃР°РЅРёРµ РґРЅСЏ ===
            boolean night = timeOfDay >= 14000;  // 20:00-06:00
            boolean evening = timeOfDay >= 12000; // 18:00-20:00
            boolean workTime = timeOfDay < 8000; // 06:00-14:00

            if (night) {
                if (state != State.SLEEP) {
                    state = State.SLEEP;
                    stateTimer = 0;
                    hasTarget = false;
                    socialBuddy = null;
                    isTalking = false;
                }
                updateSleep(dt);
            } else if (state == State.SLEEP) {
                // РџСЂРѕСЃРЅСѓР»РёСЃСЊ
                state = State.IDLE;
                stateTimer = 1.0f + (float) Math.random() * 2.0f;
                isTalking = false;
            } else if (evening) {
                // Р’РµС‡РµСЂРѕРј РёРґСѓС‚ РґРѕРјРѕР№
                if (state == State.WORK || state == State.SOCIAL || state == State.STORE) {
                    state = State.WANDER;
                    hasTarget = false;
                    socialBuddy = null;
                }
                updateEvening(dt);
            } else {
                if (stateTimer <= 0) {
                    transitionState(workTime);
                }
                switch (state) {
                    case IDLE:
                        updateIdle(dt);
                        break;
                    case WANDER:
                        updateWander(dt);
                        break;
                    case WORK:
                        updateWork(dt);
                        break;
                    case STORE:
                        updateStore(dt);
                        break;
                    case SOCIAL:
                        updateSocial(dt);
                        break;
                    default:
                        break;
                }
            }
        }

        // === РРіСЂРѕРє РїРѕРґРѕС€С‘Р» РІРїР»РѕС‚РЅСѓСЋ вЂ” РѕС‚С…РѕРґРёРј РЅР° С€Р°Рі (РЅРµ Р±РµР¶РёРј) ===
        if (distToPlayer < BACK_AWAY_RANGE && state != State.FLEE
                && state != State.SLEEP && !isBaby) {
            float len = Math.max(distToPlayer, 0.01f);
            velocity.x = -(dx / len) * WALK_SPEED * 0.45f;
            velocity.z = -(dz / len) * WALK_SPEED * 0.45f;
            desiredYaw = (float) Math.atan2(dx, dz); // СЃРјРѕС‚СЂРёРј РЅР° РёРіСЂРѕРєР°
        }

        // === РџР»Р°РІРЅС‹Р№ РїРѕРІРѕСЂРѕС‚ ===
        turnToward(desiredYaw, dt);

        // === Р¤РёР·РёРєР° ===
        applyPhysics(dt);

        // === Р Р°Р±РѕС‚Р° СЃ РјРёСЂРѕРј РєР°Рє Сѓ РёРіСЂРѕРєР° ===
        // Р•СЃР»Рё Р¶РёС‚РµР»СЏ Р·Р°РјСѓСЂРѕРІР°Р»Рё Р±Р»РѕРєР°РјРё вЂ” РІС‹С‚Р°Р»РєРёРІР°РµРј РЅР°СЂСѓР¶Сѓ, РєР°Рє РёРіСЂРѕРєР°
        if (world != null) {
            world.pushOutOfBlocks(position);
        }

        // === РђРЅРёРјР°С†РёСЏ ===
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        walkPhase += dt * (5.0f + speed * 3.0f);
        limbSwingAmount = Math.min(speed / WALK_SPEED, 1.0f);
        headBob = (float) Math.sin(walkPhase * 0.5f) * 0.1f * limbSwingAmount;

        // === Р’Р·РіР»СЏРґ РЅР° РёРіСЂРѕРєР° ===
        updateLook(dt, playerPos, distToPlayer);

        // РЎР»СѓС‡Р°Р№РЅР°СЏ "СЂРµС‡СЊ"
        if (Math.random() < 0.002 && state != State.SLEEP && state != State.FLEE) {
            isTalking = true;
            talkTimer = 0.5f + (float) Math.random() * 0.5f;
            // [GP-044] A soft hum while "talking"
            AudioManager.play("sounds/villager",
                1.0f + (float) (Math.random() - 0.5f) * 0.15f, 0.30f);
        }
    }

    /** Р”РЅРµРІРЅРѕР№ РїРµСЂРµС…РѕРґ: СЂР°Р±РѕС‚Р° / РїСЂРѕРіСѓР»РєР° / РѕР±С‰РµРЅРёРµ / РѕС‚РґС‹С…. */
    private void transitionState(boolean workTime) {
        float r = (float) Math.random();
        if (workTime && hasWork && r < 0.75f) {
            // РЈС‚СЂРѕ вЂ” РёРґСѓС‚ РЅР° СЂР°Р±РѕС‡РµРµ РјРµСЃС‚Рѕ
            state = State.WORK;
            stateTimer = 6.0f + (float) Math.random() * 6.0f;
            hasTarget = false;
            stuckTimer = 0;
        } else if (r < 0.45f) {
            state = State.WANDER;
            pickWanderTarget();
            stateTimer = 4.0f + (float) Math.random() * 4.0f;
        } else if (r < 0.72f) {
            state = State.SOCIAL;
            stateTimer = 3.0f + (float) Math.random() * 3.0f;
            hasTarget = false;
            socialBuddy = null;
        } else {
            state = State.IDLE;
            stateTimer = 2.0f + (float) Math.random() * 3.0f;
        }
    }

    private void updateIdle(float dt) {
        velocity.x *= 0.8f;
        velocity.z *= 0.8f;

        // РћСЃРјР°С‚СЂРёРІР°СЋС‚СЃСЏ
        if (Math.random() < 0.02f) {
            desiredYaw += (float) (Math.random() - 0.5) * 1.5f;
        }
    }

    private void updateWander(float dt) {
        if (!hasTarget) {
            pickWanderTarget();
            stuckTimer = 0;
        }

        float tx = moveTarget.x - position.x;
        float tz = moveTarget.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);

        if (dist < 0.6f) {
            hasTarget = false;
            state = State.IDLE;
            stateTimer = 1.0f + (float) Math.random() * 2.0f;
            velocity.x = 0;
            velocity.z = 0;
            return;
        }

        // РќРµ РїР°РґР°РµРј СЃ РѕР±СЂС‹РІР° Рё РЅРµ Р·Р°СЃС‚СЂРµРІР°РµРј Сѓ СЃС‚РµРЅ
        stuckTimer += dt;
        if (dangerAhead(dt) || stuckTimer > 2.5f) {
            pickWanderTarget();
            stuckTimer = 0;
            return;
        }

        desiredYaw = (float) Math.atan2(-tx, -tz); // Р»РёС†Рѕ Рє С†РµР»Рё
        moveToward(moveTarget, getWalkSpeed(), dt);
    }

    private void updateWork(float dt) {
        // [CR] Р¤РµСЂРјРµСЂ СЂР°Р±РѕС‚Р°РµС‚ РїРѕ-СЃРІРѕРµРјСѓ: СЃРѕР±РёСЂР°РµС‚ СѓСЂРѕР¶Р°Р№, СЃР°Р¶Р°РµС‚, РЅРѕСЃРёС‚ РЅР° СЃРєР»Р°Рґ
        if (profession == Profession.FARMER) {
            updateFarmerWork(dt);
            return;
        }
        if (!hasWork) {
            updateWander(dt);
            return;
        }

        // РЎРЅР°С‡Р°Р»Р° РЅР°С€Р»Рё С†РµР»СЊ, РїРѕС‚РѕРј "СЂР°Р±РѕС‚Р°РµРј" РЅР°Рґ РЅРµР№ workTimer СЃРµРєСѓРЅРґ
        if (!hasWorkTarget) {
            findWorkTarget();
            workTimer = 6.0f + (float) Math.random() * 8.0f;
        }

        float tx = workTarget.x - position.x;
        float tz = workTarget.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);

        if (dist > 1.2f) {
            // РРґСѓС‚ Рє С†РµР»Рё (СЂР°Р±РѕС‡РµРµ РјРµСЃС‚Рѕ, РіСЂСЏРґРєР° РёР»Рё Р±РµСЂРµРі)
            desiredYaw = (float) Math.atan2(-tx, -tz);
            moveToward(workTarget, getWalkSpeed() * 0.85f, dt);
            stuckTimer += dt;
            if (stuckTimer > 3.5f) {
                // РќРµ РґРѕС€Р»Рё вЂ” СЃРјРµРЅРёРј С†РµР»СЊ (РёР»Рё РїСЂРѕСЃС‚Рѕ Р±СЂРѕРґСЏС‚)
                hasWorkTarget = false;
                stuckTimer = 0;
            }
        } else {
            // РќР° РјРµСЃС‚Рµ вЂ” "СЂР°Р±РѕС‚Р°СЋС‚": СЂСѓРєРё РІ РґРµР»Рµ, РёР·СЂРµРґРєР° РєРѕСЂРѕС‚РєР°СЏ СЂРµС‡СЊ
            velocity.x *= 0.85f;
            velocity.z *= 0.85f;
            desiredYaw = (float) Math.atan2(-tx, -tz);
            workTimer -= dt;
            limbSwingAmount = 0.30f; // Р°РЅРёРјР°С†РёСЏ СЂР°Р±РѕС‚С‹ СЂСѓРєР°РјРё
            if (Math.random() < 0.012f) {
                isTalking = true;
                talkTimer = 0.4f + (float) Math.random() * 0.3f;
            }
            if (workTimer <= 0) {
                hasWorkTarget = false; // РёС‰РµРј СЃР»РµРґСѓСЋС‰РµРµ РјРµСЃС‚Рѕ
            }
            stuckTimer = 0;
        }
    }

    /**
     * РљСѓРґР° РёРґС‚Рё СЂР°Р±РѕС‚Р°С‚СЊ РїРѕ РїСЂРѕС„РµСЃСЃРёРё: С„РµСЂРјРµСЂ вЂ” Рє РіСЂСЏРґРєР°Рј СЃ СѓСЂРѕР¶Р°РµРј,
     * СЂС‹Р±Р°Рє вЂ” Рє РІРѕРґРµ, РѕСЃС‚Р°Р»СЊРЅС‹Рµ вЂ” РЅР° СЂР°Р±РѕС‡РµРµ РјРµСЃС‚Рѕ Сѓ Р·РґР°РЅРёСЏ.
     */
    private void findWorkTarget() {
        hasWorkTarget = true;
        if (profession == Profession.FARMER) {
            int[] pos = scanBlock(new BlockType[]{
                    BlockType.WHEAT, BlockType.CARROT, BlockType.POTATO},
                    WORK_SCAN_RANGE);
            if (pos != null) {
                workTarget.set(pos[0] + 0.5f, position.y, pos[2] + 0.5f);
                return;
            }
        } else if (profession == Profession.FISHERMAN) {
            int[] pos = scanBlock(BlockType.WATER, WORK_SCAN_RANGE);
            if (pos != null) {
                // Р’СЃС‚Р°С‘Рј РЅР° Р±РµСЂРµРіСѓ СЂСЏРґРѕРј СЃ РІРѕРґРѕР№, Р»РёС†РѕРј Рє РЅРµР№
                workTarget.set(pos[0] + 0.5f, position.y, pos[2] + 0.5f);
                return;
            }
        }
        workTarget.set(workPos);
    }

    // === [CR] Р¤РµСЂРјРµСЂ: РїРѕР»РµРІРѕР№ С†РёРєР» Рё СЃРєР»Р°Рґ ===

    /**
     * РџРѕР»РµРІР°СЏ СЂР°Р±РѕС‚Р° С„РµСЂРјРµСЂР°: С…РѕРґРёС‚ РїРѕ РіСЂСЏРґРєР°Рј, СЃРѕР±РёСЂР°РµС‚ СЃРїРµР»С‹Рµ РєСѓР»СЊС‚СѓСЂС‹
     * РІ РёРЅРІРµРЅС‚Р°СЂСЊ Рё СЃСЂР°Р·Сѓ СЃР°Р¶Р°РµС‚ РЅРѕРІСѓСЋ (СЃРµРјРµРЅР° Р±РµСЃРєРѕРЅРµС‡РЅС‹Рµ). РљРѕРіРґР° СѓСЂРѕР¶Р°СЏ
     * РЅР°Р±СЂР°Р»РѕСЃСЊ РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ вЂ” РЅРµСЃС‘С‚ РµРіРѕ РЅР° СЃРєР»Р°Рґ (СЃРѕСЃС‚РѕСЏРЅРёРµ STORE).
     */
    private void updateFarmerWork(float dt) {
        if (!hasWork) {
            updateWander(dt);
            return;
        }

        // РРЅРІРµРЅС‚Р°СЂСЊ Р·Р°РїРѕР»РЅРµРЅ вЂ” СЃРєР»Р°РґСЃРєРѕР№ РїРѕС…РѕРґ
        if (farmerIsFull()) {
            if (hasStorage) {
                state = State.STORE;
                hasTarget = false;
                hasWorkTarget = false;
                stuckTimer = 0;
                storeTimer = 1.5f;
                updateStore(dt);
            } else {
                // РЎРєР»Р°РґР° РЅРµС‚ вЂ” РЅРµ РЅР°РєР°РїР»РёРІР°РµРј, РїСЂРѕСЃС‚Рѕ СЃС‚РѕРёРј Сѓ СЂР°Р±РѕС‡РµРіРѕ РјРµСЃС‚Р°
                workTarget.set(workPos);
                hasWorkTarget = true;
                workTargetBlockY = -1;
                velocity.x *= 0.85f;
                velocity.z *= 0.85f;
                limbSwingAmount = 0.30f;
            }
            return;
        }

        if (!hasWorkTarget) {
            findFarmerWorkTarget();
            workTimer = 4.0f + (float) Math.random() * 4.0f;
        }

        float tx = workTarget.x - position.x;
        float tz = workTarget.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);

        if (dist > 1.2f) {
            desiredYaw = (float) Math.atan2(-tx, -tz);
            moveToward(workTarget, getWalkSpeed() * 0.85f, dt);
            stuckTimer += dt;
            if (stuckTimer > 3.5f) {
                hasWorkTarget = false;
                stuckTimer = 0;
            }
        } else {
            velocity.x *= 0.85f;
            velocity.z *= 0.85f;
            desiredYaw = (float) Math.atan2(-tx, -tz);
            workTimer -= dt;
            limbSwingAmount = 0.30f; // Р°РЅРёРјР°С†РёСЏ СЂР°Р±РѕС‚С‹ СЂСѓРєР°РјРё
            if (Math.random() < 0.012f) {
                isTalking = true;
                talkTimer = 0.4f + (float) Math.random() * 0.3f;
            }
            if (workTimer <= 0) {
                workOnCrop();
                hasWorkTarget = false;
            }
            stuckTimer = 0;
        }
    }

    /** РљСѓРґР° РёРґС‚Рё: СЃРЅР°С‡Р°Р»Р° СЃРїРµР»С‹Рµ РіСЂСЏРґРєРё, РїРѕС‚РѕРј РјРѕР»РѕРґС‹Рµ, РёРЅР°С‡Рµ СЂР°Р±РѕС‡РµРµ РјРµСЃС‚Рѕ. */
    private void findFarmerWorkTarget() {
        hasWorkTarget = true;
        // [PERF] Основной путь — список грядок деревни (~сотни проверок вместо ~20 тысяч)
        int[] pos = scanCropList(true);
        if (pos == null) pos = scanCropList(false);
        if (pos != null) {
            workTarget.set(pos[0] + 0.5f, position.y, pos[2] + 0.5f);
            workTargetBlockY = pos[1];
            return;
        }
        // Резервный скан мира — не чаще раза в 8 секунд (чужие/игроковые грядки)
        if (wideScanCooldown <= 0) {
            wideScanCooldown = 8.0f;
            pos = scanCrop(true);
            if (pos == null) pos = scanCrop(false);
            if (pos != null) {
                workTarget.set(pos[0] + 0.5f, position.y, pos[2] + 0.5f);
                workTargetBlockY = pos[1];
                return;
            }
        }
        workTarget.set(workPos);
        workTargetBlockY = -1;
    }

    /**
     * [PERF] Ближайшая грядка нужной зрелости по списку деревни из VillageGenerator.
     * Снесённые игроком культуры просто пропускаются — список не требует обслуживания.
     */
    private int[] scanCropList(boolean mature) {
        if (farmCrops == null) return null;
        float bestSq = FARM_SCAN_RANGE * FARM_SCAN_RANGE;
        int[] best = null;
        for (int i = 0, n = farmCrops.size(); i < n; i++) {
            int[] p = farmCrops.get(i);
            int id = world.getBlock(p[0], p[1], p[2]);
            boolean crop = id == BlockType.WHEAT.id
                        || id == BlockType.CARROT.id
                        || id == BlockType.POTATO.id;
            if (!crop) continue;
            int stage = world.getCropStage(p[0], p[1], p[2]);
            if (stage < 0) {
                stage = com.voxelgame.rendering.TextureAtlas.stageOf(id, p[0], p[1], p[2]);
            }
            if ((stage >= 7) != mature) continue;
            float dx = p[0] - position.x;
            float dz = p[2] - position.z;
            float d = dx * dx + dz * dz;
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    /** Р‘Р»РёР¶Р°Р№С€Р°СЏ РіСЂСЏРґРєР° РЅСѓР¶РЅРѕР№ Р·СЂРµР»РѕСЃС‚Рё (СЃРїРµР»Р°СЏ/РЅРµСЃРїРµР»Р°СЏ) РІ СЂР°РґРёСѓСЃРµ С„РµСЂРјС‹. */
    private int[] scanCrop(boolean mature) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        float bestSq = FARM_SCAN_RANGE * FARM_SCAN_RANGE;
        int[] best = null;
        int step = 2;
        for (int x = (int) (bx - FARM_SCAN_RANGE); x <= bx + FARM_SCAN_RANGE; x += step) {
            for (int z = (int) (bz - FARM_SCAN_RANGE); z <= bz + FARM_SCAN_RANGE; z += step) {
                for (int y = by - 1; y <= by + 4; y++) {
                    int id = world.getBlock(x, y, z);
                    boolean crop = id == BlockType.WHEAT.id
                                || id == BlockType.CARROT.id
                                || id == BlockType.POTATO.id;
                    if (!crop) continue;
                    int stage = world.getCropStage(x, y, z);
                    if (stage < 0) {
                        stage = com.voxelgame.rendering.TextureAtlas.stageOf(id, x, y, z);
                    }
                    if ((stage >= 7) != mature) continue;
                    float dx = x - position.x;
                    float dz = z - position.z;
                    float d = dx * dx + dz * dz;
                    if (d < bestSq) {
                        bestSq = d;
                        best = new int[]{x, y, z};
                    }
                }
            }
        }
        return best;
    }

    /**
     * Р”РµР№СЃС‚РІРёРµ РЅР° РіСЂСЏРґРєРµ: СЃРїРµР»СѓСЋ РєСѓР»СЊС‚СѓСЂСѓ СЃРѕР±СЂР°С‚СЊ РІ РёРЅРІРµРЅС‚Р°СЂСЊ Рё СЃСЂР°Р·Сѓ
     * РїРѕСЃР°РґРёС‚СЊ РЅРѕРІСѓСЋ (СЃС‚Р°РґРёСЏ 0); РјРѕР»РѕРґСѓСЋ вЂ” СЃР»РµРіРєР° СѓСЃРєРѕСЂРёС‚СЊ СЂРѕСЃС‚ (РїСЂРѕРїРѕР»РєР°).
     */
    private void workOnCrop() {
        int bx = (int) Math.floor(workTarget.x);
        int bz = (int) Math.floor(workTarget.z);
        if (workTargetBlockY < 0) return;
        int id = world.getBlock(bx, workTargetBlockY, bz);
        boolean crop = id == BlockType.WHEAT.id
                    || id == BlockType.CARROT.id
                    || id == BlockType.POTATO.id;
        if (!crop) return;

        int stage = world.getCropStage(bx, workTargetBlockY, bz);
        if (stage < 0) {
            stage = com.voxelgame.rendering.TextureAtlas.stageOf(id, bx, workTargetBlockY, bz);
        }

        if (stage >= 7) {
            // РЎР±РѕСЂ СѓСЂРѕР¶Р°СЏ РІ РёРЅРІРµРЅС‚Р°СЂСЊ + РјРіРЅРѕРІРµРЅРЅР°СЏ РїРѕСЃР°РґРєР° (СЃРµРјРµРЅР° Р±РµСЃРєРѕРЅРµС‡РЅС‹Рµ)
            BlockType harvest = BlockType.fromId(id);
            world.setBlock(bx, workTargetBlockY, bz, (byte) 0);
            addCropToInventory(harvest);
            world.setBlock(bx, workTargetBlockY, bz, id);
            world.setCropStage(bx, workTargetBlockY, bz, 0);
        } else {
            // РџСЂРѕРїРѕР»РєР°: СѓС…РѕРґ Р·Р° РјРѕР»РѕРґС‹РјРё СЂР°СЃС‚РµРЅРёСЏРјРё СѓСЃРєРѕСЂСЏРµС‚ РёС… СЂРѕСЃС‚
            if (Math.random() < 0.5) {
                world.setCropStage(bx, workTargetBlockY, bz, stage + 1);
            }
        }
    }

    /** РџРѕР»РѕР¶РёС‚СЊ РѕРґРёРЅ СЃРѕР±СЂР°РЅРЅС‹Р№ СѓСЂРѕР¶Р°Р№ РІ СЃРІРѕР±РѕРґРЅС‹Р№ СЃР»РѕС‚. */
    private void addCropToInventory(BlockType crop) {
        for (ItemStack s : farmerInventory) {
            if (s != null && !s.isEmpty() && s.getBlockType() == crop && s.canAdd(1)) {
                s.add(1);
                return;
            }
        }
        for (int i = 0; i < farmerInventory.length; i++) {
            if (farmerInventory[i] == null || farmerInventory[i].isEmpty()) {
                farmerInventory[i] = new ItemStack(crop, 1);
                return;
            }
        }
    }

    /** Р’СЃРµРіРѕ РµРґРёРЅРёС† СѓСЂРѕР¶Р°СЏ РІ РёРЅРІРµРЅС‚Р°СЂРµ. */
    private int farmerCropCount() {
        int n = 0;
        for (ItemStack s : farmerInventory) {
            if (s != null) n += s.getCount();
        }
        return n;
    }

    /** РРЅРІРµРЅС‚Р°СЂСЊ Р·Р°РїРѕР»РЅРµРЅ РЅР°СЃС‚РѕР»СЊРєРѕ, С‡С‚Рѕ РїРѕСЂР° РЅРµСЃС‚Рё РЅР° СЃРєР»Р°Рґ. */
    private boolean farmerIsFull() {
        return farmerCropCount() >= STORE_THRESHOLD;
    }

    /** РЈР±СЂР°С‚СЊ amount РµРґРёРЅРёС† СѓСЂРѕР¶Р°СЏ (РїРѕСЃР»Рµ РїСЂРµРІСЂР°С‰РµРЅРёСЏ РІ С‚СЋРєРё СЃРµРЅР°). */
    private void removeCropsFromInventory(int amount) {
        for (ItemStack s : farmerInventory) {
            if (amount <= 0) break;
            if (s == null || s.isEmpty()) continue;
            int take = Math.min(s.getCount(), amount);
            s.add(-take);
            amount -= take;
        }
    }

    /**
     * РЎРєР»Р°РґСЃРєРѕР№ РїРѕС…РѕРґ: С„РµСЂРјРµСЂ РёРґС‘С‚ Рє Р°РјР±Р°СЂСѓ Рё РІС‹РіСЂСѓР¶Р°РµС‚ СѓСЂРѕР¶Р°Р№ вЂ” РєР°Р¶РґС‹Рµ
     * 9 РµРґРёРЅРёС† СЃС‚Р°РЅРѕРІСЏС‚СЃСЏ Р±Р»РѕРєРѕРј С‚СЋРєР° СЃРµРЅР° (HAY_BALE), РєРѕС‚РѕСЂС‹Р№ СЃС‚Р°РІРёС‚СЃСЏ
     * РЅР° РїРѕР» Р°РјР±Р°СЂР°. РљРѕРіРґР° РёРЅРІРµРЅС‚Р°СЂСЊ РѕРїСѓСЃС‚РѕС€С‘РЅ вЂ” РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ Рє СЂР°Р±РѕС‚Рµ.
     */
    private void updateStore(float dt) {
        if (!hasStorage) {
            state = State.WORK;
            hasWorkTarget = false;
            updateFarmerWork(dt);
            return;
        }

        float tx = storagePos.x - position.x;
        float tz = storagePos.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);

        if (dist > STORE_ARRIVE) {
            desiredYaw = (float) Math.atan2(-tx, -tz);
            moveToward(storagePos, getWalkSpeed() * 1.05f, dt);
            stuckTimer += dt;
            if (stuckTimer > 6.0f) {
                // РќРµ РґРѕС€Р»Рё (СЃС‚РµРЅР°/РѕР±СЂС‹РІ) вЂ” РІРµСЂРЅС‘РјСЃСЏ Рє РїРѕР»СЋ
                state = State.WORK;
                hasWorkTarget = false;
                stuckTimer = 0;
            }
        } else {
            velocity.x *= 0.85f;
            velocity.z *= 0.85f;
            limbSwingAmount = 0.30f;
            storeTimer -= dt;
            if (storeTimer <= 0) {
                depositHarvest();
                storeTimer = 2.0f + (float) Math.random() * 2.0f;
                if (!farmerIsFull()) {
                    state = State.WORK;
                    hasWorkTarget = false;
                    stuckTimer = 0;
                }
            }
        }
    }

    /** РџСЂРµРІСЂР°С‚РёС‚СЊ СѓСЂРѕР¶Р°Р№ РІ С‚СЋРєРё СЃРµРЅР° РЅР° РїРѕР»Сѓ Р°РјР±Р°СЂР°. */
    private void depositHarvest() {
        int count = farmerCropCount();
        if (count < HAY_BALE_COST) return;
        int bales = count / HAY_BALE_COST;

        int sx = (int) Math.floor(storagePos.x);
        int sy = (int) Math.floor(storagePos.y);
        int sz = (int) Math.floor(storagePos.z);

        int placed = 0;
        for (int dx = -4; dx <= 4 && placed < bales; dx++) {
            for (int dz = -4; dz <= 4 && placed < bales; dz++) {
                for (int dy = 0; dy < BALE_MAX_STACK && placed < bales; dy++) {
                    int x = sx + dx, y = sy + dy, z = sz + dz;
                    if (world.getBlock(x, y, z) != 0) continue;
                    int below = world.getBlock(x, y - 1, z);
                    boolean solid = below != 0
                            && below != BlockType.WATER.id
                            && below != BlockType.LAVA.id;
                    if (!solid) break; // СЃС‚РѕР»Р±РёРє РѕР±РѕСЂРІР°Р»СЃСЏ вЂ” РІС‹С€Рµ СЃС‚Р°РІРёС‚СЊ РЅРµРєСѓРґР°
                    world.setBlock(x, y, z, BlockType.HAY_BALE.id);
                    placed++;
                }
            }
        }
        removeCropsFromInventory(placed * HAY_BALE_COST);
    }

    /** [CR] РќР°Р·РЅР°С‡РёС‚СЊ С„РµСЂРјРµСЂСѓ СЃРєР»Р°Рґ (Р°РјР±Р°СЂ РґРµСЂРµРІРЅРё). */
    public void setStorage(float x, float y, float z) {
        storagePos.set(x, y, z);
        hasStorage = true;
    }

    /** [PERF] Назначить фермеру список грядок деревни (собирает VillageGenerator). */
    public void setFarmCrops(List<int[]> crops) {
        this.farmCrops = (crops == null || crops.isEmpty()) ? null : crops;
    }

    /** [CR] РЎРєРѕР»СЊРєРѕ РІСЃРµРіРѕ РµРґРёРЅРёС† СѓСЂРѕР¶Р°СЏ Сѓ С„РµСЂРјРµСЂР° РІ РёРЅРІРµРЅС‚Р°СЂРµ. */
    public int getFarmerCropCount() {
        return farmerCropCount();
    }

    /** РџРѕРёСЃРє Р±Р»РёР¶Р°Р№С€РµРіРѕ Р±Р»РѕРєР° РѕРґРЅРѕРіРѕ С‚РёРїР° РІРѕРєСЂСѓРі Р¶РёС‚РµР»СЏ. */
    private int[] scanBlock(BlockType type, float range) {
        return scanBlock(new BlockType[]{type}, range);
    }

    /** РџРѕРёСЃРє Р±Р»РёР¶Р°Р№С€РµРіРѕ Р±Р»РѕРєР° РѕРґРЅРѕРіРѕ РёР· С‚РёРїРѕРІ РІ РєСѓР±Рµ РІРѕРєСЂСѓРі Р¶РёС‚РµР»СЏ. */
    private int[] scanBlock(BlockType[] types, float range) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y);
        int bz = (int) Math.floor(position.z);
        float bestSq = range * range;
        int[] best = null;
        int step = 2;
        for (int x = (int) (bx - range); x <= bx + range; x += step) {
            for (int z = (int) (bz - range); z <= bz + range; z += step) {
                for (int y = by - 1; y <= by + 4; y++) {
                    int id = world.getBlock(x, y, z);
                    boolean hit = false;
                    for (BlockType t : types) {
                        if (id == t.id) {
                            hit = true;
                            break;
                        }
                    }
                    if (!hit) continue;
                    float dx = x - position.x;
                    float dz = z - position.z;
                    float d = dx * dx + dz * dz;
                    if (d < bestSq) {
                        bestSq = d;
                        best = new int[]{x, y, z};
                    }
                }
            }
        }
        return best;
    }

    /**
     * Р’РѕРёРЅ: РїР°С‚СЂСѓР»РёСЂСѓРµС‚ Сѓ СЂР°Р±РѕС‡РµРіРѕ РјРµСЃС‚Р° (С†РµРЅС‚СЂ РґРµСЂРµРІРЅРё), Р° РєРѕРіРґР° СЂСЏРґРѕРј
     * РјРѕРЅСЃС‚СЂ вЂ” Р±РµР¶РёС‚ Рє РЅРµРјСѓ Рё СЂСѓР±РёС‚ РјРµС‡РѕРј.
     */
private void updateGuard(float dt, Vector3f playerPos) {
        if (attackCooldown > 0) attackCooldown -= dt;
        if (attackTimer > 0) attackTimer -= dt;

        if (attackTarget == null || attackTarget.isDead()) {
            attackTarget = findNearestMob(DETECT_RANGE);
        }

        if (attackTarget != null) {
            Vector3f mpos = attackTarget.getPosition();
            float tx = mpos.x - position.x;
            float tz = mpos.z - position.z;
            float dist = (float) Math.sqrt(tx * tx + tz * tz);
            desiredYaw = (float) Math.atan2(-tx, -tz);
            if (dist > ATTACK_RANGE) {
                // Р‘РµРіРѕРј Рє С†РµР»Рё
                moveToward(mpos, getWalkSpeed() * 2.2f, dt);
                if (dangerAhead(dt)) attackTarget = null;
            } else {
                // Р’СЃС‚Р°Р»Рё Рё Р±СЊС‘Рј РјРµС‡РѕРј
                velocity.x *= 0.7f;
                velocity.z *= 0.7f;
                if (attackCooldown <= 0) {
                    attackCooldown = ATTACK_COOLDOWN;
                    attackTimer = ATTACK_DURATION;
                    float len = Math.max(dist, 0.01f);
                    attackTarget.takeDamage(ATTACK_DAMAGE,
                        new Vector3f(-tx / len, 0, -tz / len));
                }
            }
            return;
        }

        // РњРѕРЅСЃС‚СЂРѕРІ РЅРµС‚ вЂ” РїР°С‚СЂРѕР»РёСЂСѓРµРј: РІРѕР·Р»Рµ РёРіСЂРѕРєР°-Р“РµСЂРѕСЏ РёР»Рё РѕРєРѕР»Рѕ СЂР°Р±РѕС‡РµРіРѕ РјРµСЃС‚Р°.
        if (!hasTarget) {
            pickGuardPost(playerPos);
        }
        float tx = moveTarget.x - position.x;
        float tz = moveTarget.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);
        if (dist < 0.8f) {
            velocity.x *= 0.8f;
            velocity.z *= 0.8f;
            guardTimer -= dt;
            if (guardTimer <= 0) {
                pickGuardPost(playerPos); // СЃР»РµРґСѓСЋС‰Р°СЏ С‚РѕС‡РєР° РїР°С‚СЂСѓР»СЏ
            }
        } else {
            desiredYaw = (float) Math.atan2(-tx, -tz);
            moveToward(moveTarget, getWalkSpeed() * 0.9f, dt);
        }
    }

    /** СЂР°Р±РѕС‡РµРіРѕ РјРµСЃС‚Р° (С†РµРЅС‚СЂР° РґРµСЂРµРІРЅРё) РёР»Рё РёРіСЂРѕРєР°-Р“РµСЂРѕСЏ. */
    private void pickGuardPost(Vector3f playerPos) {
        boolean escort = profession == Profession.WARRIOR && heroPresent
            && playerPos != null
            && (float) Math.sqrt(
                (playerPos.x - position.x) * (playerPos.x - position.x)
                + (playerPos.z - position.z) * (playerPos.z - position.z)) < 64.0f;
        float baseX = escort
            ? playerPos.x
            : (hasWork ? workPos.x : (hasHome ? homePos.x : position.x));
float baseZ = escort
            ? playerPos.z
            : (hasWork ? workPos.z : (hasHome ? homePos.z : position.z));
        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = 2.0f + (float) Math.random() * GUARD_RADIUS;
            float tx = baseX + (float) Math.cos(angle) * dist;
            float tz = baseZ + (float) Math.sin(angle) * dist;
            int g = world.getGroundHeight((int) Math.floor(tx), (int) Math.floor(tz));
            if (g < 1) continue;                         // РІРѕРґР°/РїСѓСЃС‚РѕС‚Р°
            if (Math.abs(g - position.y) > 2.5f) continue; // СЃР»РёС€РєРѕРј РєСЂСѓС‚Рѕ
            moveTarget.set(tx, position.y, tz);
            hasTarget = true;
            guardTimer = 3.0f + (float) Math.random() * 5.0f;
            return;
        }
        moveTarget.set(baseX + 0.5f, position.y, baseZ + 0.5f);
        hasTarget = true;
        guardTimer = 3.0f;
    }

    private void updateSocial(float dt) {
        if (socialBuddy == null || socialBuddy.isDead()
                || socialBuddy.isSleeping()
                || socialBuddy.getState() == State.FLEE) {
            socialBuddy = null;
            hasTarget = false;
        }

        if (!hasTarget || socialBuddy == null) {
            pickSocialTarget();
            if (!hasTarget) {
                // РќРёРєРѕРіРѕ СЂСЏРґРѕРј вЂ” РїСЂРѕСЃС‚Рѕ Р±СЂРѕРґСЏС‚
                state = State.WANDER;
                pickWanderTarget();
                stateTimer = 4.0f;
                return;
            }
        }

        Vector3f buddyPos = socialBuddy.getPosition();
        float tx = buddyPos.x - position.x;
        float tz = buddyPos.z - position.z;
        float dist = (float) Math.sqrt(tx * tx + tz * tz);

        if (dist < SOCIAL_ARRIVE) {
            // РџСЂРёС€Р»Рё вЂ” СЂР°Р·РіРѕРІР°СЂРёРІР°СЋС‚, РіР»СЏРґСЏ РґСЂСѓРі РЅР° РґСЂСѓРіР°
            velocity.x *= 0.85f;
            velocity.z *= 0.85f;
            desiredYaw = (float) Math.atan2(-tx, -tz);
            isTalking = true;
            talkTimer = 1.0f;
            // РЎРѕР±РµСЃРµРґРЅРёРє С‚РѕР¶Рµ "РіРѕРІРѕСЂРёС‚", РµСЃР»Рё РѕРЅ РЅРµ Р·Р°РЅСЏС‚
            if (socialBuddy.getState() == State.IDLE && !socialBuddy.isTalking()) {
                socialBuddy.talkAWhile();
            }
        } else {
            // РРґСѓС‚ Рє СЃРѕР±РµСЃРµРґРЅРёРєСѓ (С‚РѕС‚ РјРѕР¶РµС‚ РґРІРёРіР°С‚СЊСЃСЏ)
            desiredYaw = (float) Math.atan2(-tx, -tz);
            moveToward(buddyPos, getWalkSpeed() * 0.75f, dt);
            moveTarget.set(buddyPos);
        }
    }

    private void updateEvening(float dt) {
        if (hasHome) {
            float tx = homePos.x - position.x;
            float tz = homePos.z - position.z;
            float dist = (float) Math.sqrt(tx * tx + tz * tz);
            if (dist > 1.0f) {
                desiredYaw = (float) Math.atan2(-tx, -tz);
                moveToward(homePos, getWalkSpeed() * 0.8f, dt);
            } else {
                // Р”РѕРјР° вЂ” Р¶РґСѓС‚ РЅРѕС‡Рё
                velocity.x *= 0.85f;
                velocity.z *= 0.85f;
                state = State.IDLE;
                stateTimer = 1.0f;
            }
        } else {
            updateIdle(dt);
        }
    }

    private void updateSleep(float dt) {
        if (hasHome) {
            float tx = homePos.x - position.x;
            float tz = homePos.z - position.z;
            float dist = (float) Math.sqrt(tx * tx + tz * tz);
            if (dist > 0.7f) {
                desiredYaw = (float) Math.atan2(-tx, -tz);
                moveToward(homePos, getWalkSpeed() * 0.55f, dt);
            } else {
                // Р”РѕС€Р»Рё вЂ” Р»РµРіР»Рё
                velocity.x = 0;
                velocity.z = 0;
            }
        } else {
            velocity.x = 0;
            velocity.z = 0;
        }
    }

    private void updateFlee(float dt, Zoloy threat, Vector3f playerPos, float distToPlayer) {
        if (panicTimer > 0) {
            updatePanic(dt);
            return;
        }
        if (fleeToHome && hasHome) {
            // Р‘РµР¶РёРј РґРѕРјРѕР№ РїСЂСЏС‚Р°С‚СЊСЃСЏ
            float tx = homePos.x - position.x;
            float tz = homePos.z - position.z;
            float distHome = (float) Math.sqrt(tx * tx + tz * tz);
            if (distHome > 0.8f) {
                desiredYaw = (float) Math.atan2(-tx, -tz);
                moveToward(homePos, RUN_SPEED, dt);
            } else {
                // Р”РѕРјР° вЂ” Р·Р°С‚Р°РёР»РёСЃСЊ, Р¶РґС‘Рј РїРѕРєР° СѓРіСЂРѕР·Р° СѓР№РґС‘С‚
                velocity.x = 0;
                velocity.z = 0;
            }
            if (threat == null && distHome < 0.8f) {
                state = State.IDLE;
                stateTimer = 1.0f;
                fleeToHome = false;
            }
        } else {
            // РЈР±РµРіР°РµРј РѕС‚ РёРіСЂРѕРєР° (РїРѕСЃР»Рµ СѓРґР°СЂР°)
            if (distToPlayer < 24.0f && fleeTimer > 0) {
                float ax = position.x - playerPos.x;
                float az = position.z - playerPos.z;
                float alen = (float) Math.sqrt(ax * ax + az * az);
                if (alen < 0.01f) return;
                velocity.x = (ax / alen) * RUN_SPEED;
                velocity.z = (az / alen) * RUN_SPEED;
                desiredYaw = (float) Math.atan2(-ax, -az); // СЃРјРѕС‚СЂРёРј РїРѕ С…РѕРґСѓ Р±РµРіР°
            } else {
                state = State.IDLE;
                stateTimer = 1.0f;
                fleeToHome = false;
            }
        }
    }

    /** РџР°РЅРёРєР°: Р±РµР¶РёРј РѕС‚ РёСЃС‚РѕС‡РЅРёРєР°, РїРµСЂРёРѕРґРёС‡РµСЃРєРё РјРµРЅСЏСЏ РЅР°РїСЂР°РІР»РµРЅРёРµ. */
    private void updatePanic(float dt) {
        panicTimer -= dt;
        panicRetarget -= dt;
        float dx = panicTarget.x - position.x;
        float dz = panicTarget.z - position.z;
        if (!hasTarget || panicRetarget <= 0 || dx * dx + dz * dz < 2.5f) {
            pickPanicTarget();
        }
        moveToward(panicTarget, RUN_SPEED, dt);
        // РСЃРїСѓРіР°РЅРЅС‹Р№ РїРѕРґСЃРєРѕРє РЅР° Р±РµРіСѓ
        if (onGround && Math.random() < 0.06f) {
            velocity.y = 3.6f;
        }
        if (panicTimer <= 0) {
            state = State.IDLE;
            stateTimer = 1.0f;
            hasTarget = false;
        }
    }

    /** РЎР»СѓС‡Р°Р№РЅР°СЏ С‚РѕС‡РєР° РїРѕРґР°Р»СЊС€Рµ РѕС‚ РёСЃС‚РѕС‡РЅРёРєР° РїР°РЅРёРєРё. */
    private void pickPanicTarget() {
        float ax = position.x - panicSource.x;
        float az = position.z - panicSource.z;
        float alen = Math.max((float) Math.sqrt(ax * ax + az * az), 0.01f);
        float angle = (float) Math.atan2(az, ax) + (float) (Math.random() - 0.5f) * 1.6f;
        float dist = 9.0f + (float) Math.random() * 11.0f;
        panicTarget.set(
            position.x + (float) Math.cos(angle) * dist,
            position.y,
            position.z + (float) Math.sin(angle) * dist);
        hasTarget = true;
    }

    private void pickWanderTarget() {
        // Р¦РµР»Рё СЂСЏРґРѕРј СЃ РґРѕРјРѕРј, С‡С‚РѕР±С‹ Р¶РёС‚РµР»Рё РґРµСЂР¶Р°Р»РёСЃСЊ РґРµСЂРµРІРЅРё
        float baseX = hasHome ? homePos.x : position.x;
        float baseZ = hasHome ? homePos.z : position.z;
        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = 2.0f + (float) Math.random() * WANDER_RADIUS;
            float tx = baseX + (float) Math.cos(angle) * dist;
            float tz = baseZ + (float) Math.sin(angle) * dist;
            int g = world.getGroundHeight((int) Math.floor(tx), (int) Math.floor(tz));
            if (g < 1) continue;                         // РІРѕРґР°/РїСѓСЃС‚РѕС‚Р°
            if (Math.abs(g - position.y) > 2.5f) continue; // СЃР»РёС€РєРѕРј РєСЂСѓС‚Рѕ
            // [GP-043] Never wander onto a lava surface
            if (world.getBlock((int) Math.floor(tx), g, (int) Math.floor(tz)) == BlockType.LAVA.id) continue;
            moveTarget.set(tx, position.y, tz);
            hasTarget = true;
            return;
        }
        // Р—Р°РїР°СЃРЅРѕР№ РІР°СЂРёР°РЅС‚ вЂ” СЂСЏРґРѕРј СЃ С‚РµРєСѓС‰РµР№ РїРѕР·РёС†РёРµР№
        moveTarget.set(
            position.x + (float) (Math.random() - 0.5) * 4.0f,
            position.y,
            position.z + (float) (Math.random() - 0.5) * 4.0f
        );
        hasTarget = true;
    }

    private void pickSocialTarget() {
        socialBuddy = null;
        hasTarget = false;
        Villager best = null;
        float bestDistSq = 18.0f * 18.0f;
        for (Villager v : world.getVillagers()) {
            if (v == this || v.isDead() || v.isSleeping()
                    || v.getState() == State.FLEE) continue;
            float dx = v.getPosition().x - position.x;
            float dz = v.getPosition().z - position.z;
            float d = dx * dx + dz * dz;
            if (d < bestDistSq) {
                bestDistSq = d;
                best = v;
            }
        }
        if (best != null) {
            socialBuddy = best;
            moveTarget.set(best.getPosition());
            hasTarget = true;
        }
    }

    /** РћР±СЂС‹РІ РІРїРµСЂРµРґРё РїРѕ С…РѕРґСѓ РґРІРёР¶РµРЅРёСЏ (РЅРµ С‡Р°С‰Рµ СЂР°Р·Р° РІ 0.5СЃ). */
    private boolean dangerAhead(float dt) {
        if (cliffCheckTimer > 0) {
            cliffCheckTimer -= dt;
            return false;
        }
        cliffCheckTimer = 0.5f;
        float vx = velocity.x;
        float vz = velocity.z;
        float len = (float) Math.sqrt(vx * vx + vz * vz);
        if (len < 0.05f) return false;
        float px = position.x + (vx / len) * 1.5f;
        float pz = position.z + (vz / len) * 1.5f;
        int g = world.getGroundHeight((int) Math.floor(px), (int) Math.floor(pz));
        // [GP-043] Lava counts as danger: never step into a lava surface
        if (world.getBlock((int) Math.floor(px), g, (int) Math.floor(pz)) == BlockType.LAVA.id) {
            return true;
        }
        return position.y - g > 2.0f;
    }

    /** РџР»Р°РІРЅС‹Р№ РїРѕРІРѕСЂРѕС‚ Рє С†РµР»РµРІРѕР№ РѕСЂРёРµРЅС‚Р°С†РёРё. */
    private void turnToward(float target, float dt) {
        float diff = target - yaw;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        float maxTurn = TURN_RATE * dt;
        if (diff > maxTurn) diff = maxTurn;
        else if (diff < -maxTurn) diff = -maxTurn;
        yaw += diff;
    }

    /** Р“РѕР»РѕРІР° РїРѕРІРѕСЂР°С‡РёРІР°РµС‚СЃСЏ Рє РёРіСЂРѕРєСѓ, РєРѕРіРґР° РѕРЅ СЂСЏРґРѕРј. */
    private void updateLook(float dt, Vector3f playerPos, float distToPlayer) {
        if (distToPlayer < LOOK_RANGE && state != State.SLEEP) {
            float dx = playerPos.x - position.x;
            float dz = playerPos.z - position.z;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001f) {
                float step = Math.min(1.0f, dt * 3.0f);
                float targetYaw = (float) Math.atan2(-dx, -dz);
                float diff = targetYaw - lookYaw;
                while (diff > Math.PI) diff -= 2 * Math.PI;
                while (diff < -Math.PI) diff += 2 * Math.PI;
                lookYaw += diff * step;

                float dy = (playerPos.y + 1.6f) - (position.y + 1.6f);
                float targetPitch = (float) Math.atan2(dy, len);
                if (targetPitch > 0.6f) targetPitch = 0.6f;
                if (targetPitch < -0.6f) targetPitch = -0.6f;
                lookPitch += (targetPitch - lookPitch) * step;
            }
        } else {
            // РЎРјРѕС‚СЂРёРј РїРѕ С…РѕРґСѓ РґРІРёР¶РµРЅРёСЏ
            float diff = desiredYaw - lookYaw;
            while (diff > Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;
            lookYaw += diff * Math.min(1.0f, dt * 2.0f);
            lookPitch *= Math.max(0.0f, 1.0f - dt * 3.0f);
        }
    }

    /** Р‘Р»РёР¶Р°Р№С€РёР№ РјРѕРЅСЃС‚СЂ РІ СЂР°РґРёСѓСЃРµ, РёР»Рё null. */
    private Zoloy findNearestMob(float range) {
        Zoloy best = null;
        float bestSq = range * range;
        for (Zoloy m : world.getMobs()) {
            if (m.isDead()) continue;
            float dx = m.getPosition().x - position.x;
            float dz = m.getPosition().z - position.z;
            float d = dx * dx + dz * dz;
            if (d < bestSq) {
                bestSq = d;
                best = m;
            }
        }
        return best;
    }

    private void moveToward(Vector3f target, float speed, float dt) {
        float dx = target.x - position.x;
        float dz = target.z - position.z;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01f) return;
        // РџР»Р°РІРЅРѕРµ С‚РѕСЂРјРѕР¶РµРЅРёРµ РїСЂРё РїСЂРёР±Р»РёР¶РµРЅРёРё Рє С†РµР»Рё
        float arrive = Math.min(1.0f, len / 1.5f);
        velocity.x = (dx / len) * speed * arrive;
        velocity.z = (dz / len) * speed * arrive;
    }

    private float getWalkSpeed() {
        return isBaby ? 1.35f : WALK_SPEED;
    }

    private void applyPhysics(float dt) {
        onGround = false;
        blockedByObstacle = false;
        pushDirX = 0;
        pushDirZ = 0;
        velocity.y -= GRAVITY * dt;
        if (velocity.y < -MAX_FALL_SPEED) velocity.y = -MAX_FALL_SPEED;

        moveAxis(velocity.x * dt, 0);
        moveAxis(0, velocity.z * dt);

        position.y += velocity.y * dt;
        resolveVertical();

        // РЈРїРµСЂР»РёСЃСЊ РІ РїСЂРµРїСЏС‚СЃС‚РІРёРµ РІС‹С€Рµ С€Р°РіР° вЂ” РїСЂС‹РіР°РµРј, РєР°Рє РёРіСЂРѕРє
        if (blockedByObstacle && onGround && jumpCooldown <= 0 && jumpClearAhead()) {
            velocity.y = JUMP_SPEED;
            jumpCooldown = JUMP_COOLDOWN;
        }
        if (jumpCooldown > 0) jumpCooldown -= dt;

        if (position.y < -64) {
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
            if (velocity.y < 1.0f) {
                // РќР° Р·РµРјР»Рµ вЂ” СЃС‚РѕРёРј; РІ РїСЂС‹Р¶РєРµ вЂ” РїСЂРѕРґРѕР»Р¶Р°РµРј РґР°РІРёС‚СЊ, С‡С‚РѕР±С‹
                // Р°РІС‚Рѕ-С€Р°Рі РїРѕРґС…РІР°С‚РёР» РЅР°СЃ РЅР° СЃРµСЂРµРґРёРЅРµ РїРѕРґСЉС‘РјР°.
                velocity.x = 0;
                velocity.z = 0;
            }
        }
    }

    /**
     * РџСЂС‹Р¶РѕРє РѕРїСЂР°РІРґР°РЅ, РµСЃР»Рё РїРѕСЃР»Рµ РїСЂС‹Р¶РєР° (~1 Р±Р»РѕРє) Рё Р°РІС‚Рѕ-С€Р°РіР° (~1 Р±Р»РѕРє)
     * РЅРѕРіРё РІСЃС‚Р°РЅСѓС‚ РЅР° РІРµСЂС€РёРЅСѓ РїСЂРµРїСЏС‚СЃС‚РІРёСЏ: РєР»РµС‚РєР° РІРїРµСЂРµРґРё РЅР° РІС‹СЃРѕС‚Рµ feet+2
     * РґРѕР»Р¶РЅР° Р±С‹С‚СЊ СЃРІРѕР±РѕРґРЅР° (СЃС‚РµРЅС‹ РІС‹С€Рµ 2 Р±Р»РѕРєРѕРІ РЅРµ Р»РµР·РµРј).
     */
    private boolean jumpClearAhead() {
        float vx = pushDirX, vz = pushDirZ;
        float len = (float) Math.sqrt(vx * vx + vz * vz);
        if (len < 0.05f) return false;
        float px = position.x + (vx / len) * (HALF_WIDTH + 0.1f);
        float pz = position.z + (vz / len) * (HALF_WIDTH + 0.1f);
        int cx = (int) Math.floor(px);
        int cz = (int) Math.floor(pz);
        int top = (int) Math.floor(position.y + 2.0f);
        return !isBlockSolid(cx, top, cz);
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

    private void resolveVertical() {
        float minX = position.x - HALF_WIDTH;
        float maxX = position.x + HALF_WIDTH;
        float minZ = position.z - HALF_WIDTH;
        float maxZ = position.z + HALF_WIDTH;

        int x0 = (int) Math.floor(minX), x1 = (int) Math.floor(maxX);
        int z0 = (int) Math.floor(minZ), z1 = (int) Math.floor(maxZ);
        int y0 = (int) Math.floor(position.y);
        int y1 = (int) Math.floor(position.y + HEIGHT);

        float[] bb = bbScratch;
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
            // [CF] РђРІС‚Рѕ-С€Р°Рі: СЃС‚РѕСЏ РЅР° РєСЂР°СЋ РїСЂРµРїСЏС‚СЃС‚РІРёСЏ, РїСЂРёР·РµРјР»СЏРµРјСЃСЏ РЅР° РµРіРѕ
            // РІРµСЂС…, РµСЃР»Рё РѕРЅ РЅРµ РІС‹С€Рµ STEP_UP вЂ” РёРЅР°С‡Рµ РЅРѕРіРё РјРµРґР»РµРЅРЅРѕ С‚РѕРЅСѓС‚,
            // СЃРєРѕСЂРѕСЃС‚СЊ РїР°РґРµРЅРёСЏ РєРѕРїРёС‚СЃСЏ Рё Р¶РёС‚РµР»СЊ СЃРІР°Р»РёРІР°РµС‚СЃСЏ РѕР±СЂР°С‚РЅРѕ.
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

    // === Getters ===
    public Vector3f getPosition() { return position; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getWalkPhase() { return walkPhase; }
    public float getLimbSwingAmount() { return limbSwingAmount; }
    public float getHeadBob() { return headBob; }
    public float getLookYaw() { return lookYaw; }
    public float getLookPitch() { return lookPitch; }
    public float getHp() { return hp; }
    public float getMaxHp() { return MAX_HP; }
    public boolean isDead() { return dead; }
    public Profession getProfession() { return profession; }
    public boolean isBaby() { return isBaby; }

    // === [ECO] РўРѕСЂРіРѕРІР»СЏ ===

    /** РўРѕРІР°СЂС‹ РїСЂРѕС„РµСЃСЃРёРё (РїСѓСЃС‚Рѕ Сѓ NITWIT). */
    public TradeOffer[] getOffers() {
        return TradeRegistry.offersFor(profession);
    }

    /** РЎРєРѕР»СЊРєРѕ СЂР°Р· РµС‰С‘ РІ РЅР°Р»РёС‡РёРё РїСЂРµРґР»РѕР¶РµРЅРёРµ {@code idx} (0 = СЂР°СЃРїСЂРѕРґР°РЅРѕ). */
    public int stockOf(int idx) {
        if (tradeStock == null || idx < 0 || idx >= tradeStock.length) return 0;
        return tradeStock[idx];
    }

    /** РЎРїРёСЃР°С‚СЊ РѕРґРЅСѓ РїРѕСЃС‚Р°РІРєСѓ; false вЂ” С‚РѕРІР°СЂ СѓР¶Рµ СЂР°СЃРїСЂРѕРґР°РЅ. */
    public boolean useOffer(int idx) {
        if (tradeStock == null || idx < 0 || idx >= tradeStock.length) return false;
        if (tradeStock[idx] <= 0) return false;
        tradeStock[idx]--;
        return true;
    }

    /** РџРѕР»РЅРѕРµ РїРѕРїРѕР»РЅРµРЅРёРµ РІСЃРµРіРѕ Р°СЃСЃРѕСЂС‚РёРјРµРЅС‚Р° (РїРѕСЃР»Рµ СЃРЅР°/С‚Р°Р№РјРµСЂСѓ). */
    public void restock() {
        TradeOffer[] offers = getOffers();
        if (tradeStock == null || tradeStock.length != offers.length) initTradeStock();
        for (int i = 0; i < offers.length; i++) tradeStock[i] = offers[i].maxUses;
    }

    private void initTradeStock() {
        TradeOffer[] offers = getOffers();
        tradeStock = new int[offers.length];
        for (int i = 0; i < offers.length; i++) tradeStock[i] = offers[i].maxUses;
    }
    public State getState() { return state; }
    public boolean isTalking() { return isTalking; }
    public float getTalkTimer() { return talkTimer; }
    public int getTextureIndex() { return textureIndex; }
    public int getVariant() { return variant; }
    /** 0..1 С„Р°Р·Р° Р·Р°РјР°С…Р° РјРµС‡РѕРј (РґР»СЏ Р°РЅРёРјР°С†РёРё РІРѕРёРЅР°). */
    public float getAttackPhase() {
        return Math.max(0, Math.min(1.0f, attackTimer / ATTACK_DURATION));
    }
    public String getBiome() { return biome; }
    public boolean isSleeping() { return state == State.SLEEP; }

    public void setBiome(String biome) { this.biome = biome; }
    public void setHome(float x, float y, float z) {
        homePos.set(x, y, z);
        hasHome = true;
    }
    public void setWorkstation(float x, float y, float z) {
        workPos.set(x, y, z);
        hasWork = true;
    }
    /** РџРѕРїСЂРѕСЃРёС‚СЊ Р¶РёС‚РµР»СЏ "РіРѕРІРѕСЂРёС‚СЊ" (РґР»СЏ РѕР±С‰РµРЅРёСЏ СЃ СЃРѕР±РµСЃРµРґРЅРёРєРѕРј). */
    public void talkAWhile() {
        if (state == State.IDLE) {
            isTalking = true;
            talkTimer = 1.0f;
        }
    }

    /** [AST] РђСЃС‚РµСЂРѕРёРґ СЂСЏРґРѕРј: РїР°РЅРёРєР° вЂ” Р±РµР¶РёРј РѕС‚ РёСЃС‚РѕС‡РЅРёРєР°, РїРѕРєР° С‚Р°Р№РјРµСЂ РёРґС‘С‚. */
    public void panic(Vector3f source, float duration) {
        if (dead || profession == Profession.WARRIOR || state == State.SLEEP) return;
        state = State.FLEE;
        panicSource.set(source);
        panicTimer = duration;
        panicRetarget = 0;
        fleeToHome = false;
        hasTarget = false;
        socialBuddy = null;
        isTalking = false;
    }

    public void takeDamage(float dmg) {
        takeDamage(dmg, null);
    }

    /** [GP-025] Damage with knockback and the hit-away fleeing behaviour. */
    public void takeDamage(float dmg, Vector3f fromDir) {
        hp -= dmg;
        // [0.7] Getting hit sends the villager running
        if (!dead) {
            // [GP-025] Knockback pushes the villager away from the attacker
            if (fromDir != null) {
                float len = (float) Math.sqrt(fromDir.x * fromDir.x + fromDir.z * fromDir.z);
                if (len > 0.001f) {
                    float strength = 5.0f;
                    velocity.x += (fromDir.x / len) * strength;
                    velocity.z += (fromDir.z / len) * strength;
                    velocity.y = 5.0f;
                }
            }
            state = State.FLEE;
            fleeTimer = 3.0f;
            fleeToHome = false;
            hasTarget = false;
            socialBuddy = null;
        }
        // [GP-044] Startled squeak when hurt (hurt = clip 2)
        AudioManager.play("sounds/villager/2",
            0.9f + (float) Math.random() * 0.25f, 0.45f);
        if (hp <= 0) {
            hp = 0;
            dead = true;
            AudioManager.play("sounds/villager/2", 0.75f, 0.5f);
        }
    }
}