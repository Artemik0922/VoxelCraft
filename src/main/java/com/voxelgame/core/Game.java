package com.voxelgame.core;

import com.voxelgame.rendering.*;
import com.voxelgame.rendering.BlockOutline;
import com.voxelgame.world.*;
import com.voxelgame.player.Player;
import com.voxelgame.physics.*;
import com.voxelgame.item.Inventory;
import com.voxelgame.item.Item;
import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.ToolType;
import com.voxelgame.item.ToolTier;
import com.voxelgame.item.BlockHarvest;
import com.voxelgame.world.entity.AsteroidEntity;
import com.voxelgame.world.entity.Animal;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.TntEntity;
import com.voxelgame.world.entity.Villager;
import com.voxelgame.world.entity.Zoloy;
import com.voxelgame.chat.CommandRegistry;
import com.voxelgame.chat.commands.HelpCommand;
import com.voxelgame.chat.commands.SpawnCommand;
import com.voxelgame.chat.commands.TpCommand;
import com.voxelgame.chat.commands.GamemodeCommand;
import com.voxelgame.chat.commands.GiveCommand;
import com.voxelgame.chat.commands.TimeCommand;
import com.voxelgame.chat.commands.KillCommand;
import com.voxelgame.chat.commands.WeatherCommand;
import com.voxelgame.chat.commands.ConnectCommand;
import com.voxelgame.chat.commands.VillageCommand;
import com.voxelgame.chat.commands.AsteroidCommand;
import com.voxelgame.net.*;
import com.voxelgame.world.biome.BiomeDiscovery;
import com.voxelgame.world.biome.BiomeDistributionTest;
import com.voxelgame.world.biome.BiomeRegistry;
import com.voxelgame.world.biome.BiomeData;
import com.voxelgame.rendering.texture.TextureBootstrap;
import com.voxelgame.ui.GameHud;
import com.voxelgame.ui.toast.ToastManager;
import com.voxelgame.ui.FontRenderer;
import com.voxelgame.ui.UIRenderer;
import com.voxelgame.ui.GuiAssets;
import com.voxelgame.ui.MenuTheme;
import com.voxelgame.ui.screen.*;
import com.voxelgame.ui.screen.ChestScreen;
import com.voxelgame.ui.screen.FurnaceScreen;
import com.voxelgame.rendering.model.*;
import com.voxelgame.world.save.*;
import com.voxelgame.world.container.ContainerData;
import com.voxelgame.world.block.DoorBlock;
import com.voxelgame.debug.DebugCommandHandler;
import com.voxelgame.audio.AudioManager;
import com.voxelgame.achievement.AchievementRegistry;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.joml.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Main game class - handles window, game loop, input, and rendering
 */
public class Game {
    /** Declared first: several fields below read their defaults from it. */
    private final Settings settings = Settings.load();
    
    private long window;
    private int width = 1280;
    private int height = 720;
    private String title = "VoxelCraft";
    
    // Systems
    private Shader shader;
    private Camera camera;
    private World world;
    private Player player;
    private GameHud hud;
    private Renderer renderer;
    private com.voxelgame.render.MobRenderer mobRenderer;
    private com.voxelgame.render.VillagerRenderer villagerRenderer;
    private com.voxelgame.render.AnimalRenderer animalRenderer;
    private com.voxelgame.render.RemotePlayerRenderer remotePlayerRenderer;
    private Skybox skybox;
    private CloudLayer cloudLayer;
    private ShadowMap shadowMap;
    private PostProcess postProcess;
    private boolean shadowsEnabled = settings.shadows;
    /** [OPT] Tracks the cached shadow pass: last chunk remesh seen + on/off edge. */
    private long lastShadowRemesh = -1;
    private boolean wasCastingShadows = false;
    private DayNightCycle dayNight;
    private ParticleSystem particles;

    // [AST] Countdown to the next random meteor event (seconds)
    private float asteroidEventTimer = 120.0f;
    private static final float ASTEROID_EVENT_MIN_INTERVAL = 60.0f;
    private static final float ASTEROID_EVENT_MAX_INTERVAL = 120.0f;
    private static final float ASTEROID_EVENT_CHANCE = 0.30f;
    private PanoramaCamera panorama;
    private final KeyBindings keyBindings = settings.keyBindings;
    private WorldSave currentSave;
    private double lastAutosave = 0;
    /** Seconds between automatic saves. */
    private static final double AUTOSAVE_INTERVAL = 30.0;
    private SkinTexture skin;
    private HeldItemRenderer heldItem;
    private PlayerBodyRenderer playerBodyRenderer;
    private BlockOutline blockOutline;
    private CrackOverlay crackOverlay;
    
    // Third-person view: 0 = first person, 1 = behind the player, 2 = front
    private int viewMode = 0;
    private float limbSwing = 0;
    private float limbSwingAmount = 0;
    
    // Local co-op
    private Client netClient;
    private boolean inMultiplayer = false;
    private String playerName = "Player";
    private final java.util.List<com.voxelgame.net.RemotePlayer> remotePlayers = new java.util.ArrayList<>();
    private final java.util.Map<Integer, com.voxelgame.net.RemotePlayer> remoteById = new java.util.HashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> netEvents =
        new java.util.concurrent.ConcurrentLinkedQueue<>();
    private float netStateTimer = 0f;
    private static final float NET_STATE_INTERVAL = 0.05f;
    private com.voxelgame.ui.screen.MultiplayerScreen multiplayerScreen;
    
    // Interface
    private UIRenderer ui;
    private FontRenderer font;
    private GuiAssets uiTextures;
    private ScreenManager screens;
    /** Tracks ScreenManager.revision() to re-apply cursor after fades. */
    private int lastScreensRevision = -1;
    
    public static final String VERSION = "VoxelCraft 0.4 (LWJGL3)";
    // F7 toggles back-face culling. With correct winding the image must look
    // identical either way - any face that disappears means bad winding.
    private boolean cullingEnabled = true;
    
    // Sky / sun tint shared by the clear color, fog and block lighting
    private static final Vector3f SKY_COLOR = new Vector3f(0.529f, 0.808f, 0.922f);
    private static final Vector3f SUN_COLOR = new Vector3f(1.0f, 0.98f, 0.94f);
    private static final Vector3f SUN_DIRECTION = new Vector3f(0.35f, -0.86f, 0.37f).normalize();

    
    // Game settings
    private int renderDistance = settings.renderDistance;
    private float eyeHeight = 1.6f;
    private float fov = settings.fov;
    
// Timing
    private double deltaTime;
    private double lastFrame;
    private int frameCount = 0;
    private float fps = 60;
    private float fpsTimer = 0;
    /** Fixed timestep: one world tick = 1/60 s */
    private static final double TICK_RATE = 1.0 / 60.0;
    /** Accumulates wall-clock seconds for fixed-timestep world updates */
    private double tickAccumulator = 0;
    /** Background thread for non-blocking autosave */
    private static final java.util.concurrent.ExecutorService saveExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "world-save");
            t.setDaemon(true);
            return t;
        });
    
    // Input state
    private boolean mouseCaptured = false;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private double lastSpacePress = -1.0;
    private ItemStack mouseItem = new ItemStack(BlockType.AIR, 0);

    // Keyboard modifiers tracked globally so inventory screens can offer
    // shift-click quick move and Ctrl+Q whole-stack drops
    private static boolean shiftKeyDown = false;
    private static boolean ctrlKeyDown = false;
    public static boolean isShiftDown() { return shiftKeyDown; }
    public static boolean isCtrlDown() { return ctrlKeyDown; }
    private final ToastManager toastManager = new ToastManager();
    private BiomeDiscovery biomeDiscovery = null;
    private String lastBiomeId = null;
    
    // File-based command console for unattended testing
    private DebugCommandHandler debugConsole;
    
    // Block interaction
    private float blockBreakProgress = 0;
    private Vector3i breakingBlock = null;
    private Raycast.Hit targetedBlock = null;

    // [BED] Sleep state: fade to black, skip the night, wake at sunrise
    private boolean sleeping = false;
    private boolean waking = false;
    private float sleepFade = 0;
    private static final float SLEEP_FADE_TIME = 0.8f;
    private Vector3i bedSpawn = null;
    
    // Game state
    private boolean paused = false;
    private String currentWeather = "clear";

    // [WX] Automatic weather: timer drives clear/rain/thunder/snow switches,
    // thunder strikes spawn lightning, overcast dims the sky and the fog.
    private float weatherChangeTimer = 90.0f; // first change after ~90s
    private float lightningTimer = 6.0f;
    private float overcast = 0.0f;      // smoothed sky dimming 0..1
    private float lightningFlash = 0.0f; // 0..1, decays each frame

    // [SPACE] Rocket ride and dimension switching
    private boolean spaceMode = false;
    private boolean rocketRiding = false;
    private float rocketProgress = 0f;
    private int rocketPadX, rocketPadY, rocketPadZ;
    private final org.joml.Vector3f rocketReturnPos = new org.joml.Vector3f();
    private float preRocketFov = 70f;
    private static final float ROCKET_THRUST_MIN = 12f;
    private static final float ROCKET_THRUST_MAX = 36f;
    private static final float ROCKET_LAUNCH_Y = 248f;
    private static final float SPACE_GRAVITY_SCALE = 0.30f;

    // Chat / commands
    private final com.voxelgame.chat.ChatManager chat = new com.voxelgame.chat.ChatManager();
    
    public void run() {
        MenuTheme.setThemeIndex(settings.menuTheme);
        init();
        loop();
        cleanup();
    }
    
    private void init() {
        // Initialize GLFW
        if (!glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        
        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4); // 4x MSAA
        glfwWindowHint(GLFW_DEPTH_BITS, 24);
        glfwWindowHint(GLFW_STENCIL_BITS, 8);
        
        // Create window
        window = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        
        // Center window
        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vidmode.width() - width) / 2, (vidmode.height() - height) / 2);
        
        // Set callbacks
        setupCallbacks();
        
        // Make context current
        glfwMakeContextCurrent(window);
        glfwSwapInterval(settings.vsync ? 1 : 0);
        
        // Initialize OpenGL
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        // 4x MSAA - the framebuffer was requested via GLFW_SAMPLES above
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        // Chunk meshes are wound counter-clockwise seen from outside the block
        glFrontFace(GL_CCW);
        // Opaque world pass must not blend
        glDisable(GL_BLEND);
        // Minecraft sky color - must match the fog color in block.frag
        glClearColor(SKY_COLOR.x, SKY_COLOR.y, SKY_COLOR.z, 1.0f);
        
        System.out.println("OpenGL Version: " + glGetString(GL_VERSION));
        System.out.println("GPU: " + glGetString(GL_RENDERER));
        System.out.println("MSAA: " + glGetInteger(GL_SAMPLES) + "x samples, "
            + glGetInteger(GL_SAMPLE_BUFFERS) + " sample buffer(s)");
        
        // Initialize game systems. This opens the main menu, which decides
        // the cursor mode via applyCursorMode() - do not override it here.
        initGameSystems();
        
        System.out.println("Game initialized successfully!");
    }
    
    private void setupCallbacks() {
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            if (w <= 0 || h <= 0) return; // minimised
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
            
            if (camera != null) camera.resize(w, h);
            if (postProcess != null) postProcess.resize(w, h);
            if (ui != null) {
                ui.resize(w, h);
                screens.resize(ui.getWidth(), ui.getHeight());
            }
        });
        
        glfwSetCursorPosCallback(window, this::mouseCallback);
        glfwSetMouseButtonCallback(window, this::mouseButtonCallback);
        glfwSetScrollCallback(window, this::scrollCallback);
        glfwSetKeyCallback(window, this::keyCallback);
        
        // Typed text arrives here rather than through key events, so the
        // keyboard layout is honoured and Cyrillic can be entered
        glfwSetCharCallback(window, (win, codepoint) -> {
            Screen top = screens.current();
            if (top instanceof CreateWorldScreen c) {
                c.charTyped((char) codepoint);
            } else if (top instanceof com.voxelgame.ui.screen.MultiplayerScreen m) {
                m.charTyped((char) codepoint);
            } else if (top instanceof com.voxelgame.ui.screen.CreativeInventoryScreen ci) {
                ci.charTyped((char) codepoint);
            }
            if (chat.isOpen()) {
                chat.charTyped((char) codepoint);
            }
        });
    }
    
    private void initGameSystems() {
        // Load translations first: every screen reads strings during layout
        Language.load(settings.language);

        // Load biomes from JSON
        BiomeRegistry.load();

        // Initialize texture registry for new biome blocks
        TextureBootstrap.init();

        // Validate biome distribution
        BiomeDistributionTest.testMultipleSeeds();
        
        shader = new Shader("shaders/block.vert", "shaders/block.frag");
        
        // A placeholder world backs the menu panorama until the player picks
        // a save. It is replaced wholesale by activateWorld().
        world = new World(settings.resolveSeed());
        world.setRenderDistance(renderDistance);
        renderer = new Renderer(settings.resourcePack);
        mobRenderer = new com.voxelgame.render.MobRenderer();
        villagerRenderer = new com.voxelgame.render.VillagerRenderer();
        animalRenderer = new com.voxelgame.render.AnimalRenderer();
        remotePlayerRenderer = new com.voxelgame.render.RemotePlayerRenderer();
        skybox = new Skybox();
        cloudLayer = new CloudLayer();
        shadowMap = new ShadowMap();
        // [PP] Post-processing stack (bloom/FXAA) sized to the current window
        postProcess = new PostProcess(width, height);
        dayNight = new DayNightCycle();
        dayNight.setDayLengthSeconds(settings.dayLengthMinutes * 60.0);
        particles = new ParticleSystem();
        panorama = new PanoramaCamera();
        skin = new SkinTexture();
        heldItem = new HeldItemRenderer(skin);
        heldItem.getTuning().load(settings);
        playerBodyRenderer = new PlayerBodyRenderer(skin);
        blockOutline = new BlockOutline();
        crackOverlay = new CrackOverlay();

        // Audio (footsteps). Missing or broken OpenAL just disables sound.
        AudioManager.init();
        AudioManager.setSoundVolume(settings.soundVolume);
        
        // Apply persisted graphics options before the first mesh is built
        cloudLayer.setEnabled(settings.clouds);
        ChunkMeshBuilder.setAmbientOcclusionEnabled(settings.ambientOcclusion);
        ChunkMeshBuilder.setFancyGraphics(settings.fancyGraphics);
        
        // Spawn position
        float spawnX = 8.5f;
        float spawnZ = 8.5f;
        
        // Pre-load chunks
        world.preloadChunks(new Vector3f(spawnX, 70, spawnZ), renderDistance);
        
// Find ground level
        int groundY = world.getGroundHeight((int) spawnX, (int) spawnZ);
        // [WG] A sea-heavy world may place the spawn underwater: surface the player
        if (groundY < Chunk.SEA_LEVEL) groundY = Chunk.SEA_LEVEL;
        float spawnY = groundY + 2.0f;
        
        System.out.println("Spawn: x=" + spawnX + " y=" + spawnY + " z=" + spawnZ + " (ground: " + groundY + ")");
        
        // Create player and camera
        player = new Player(new Vector3f(spawnX, spawnY, spawnZ), world);
        camera = new Camera(new Vector3f(spawnX, spawnY + eyeHeight, spawnZ), width, height, fov);
        
        // Orbit the menu panorama around the spawn point
        panorama.setAnchor(spawnX, spawnY, spawnZ);
        
        // Interface layer
        hud = new GameHud(renderer.getTextureAtlas());
        ui = new UIRenderer();
        font = new FontRenderer();
        uiTextures = new GuiAssets();
        screens = new ScreenManager();
        ui.setGuiScaleSetting(settings.guiScale);
        ui.resize(width, height);
        screens.resize(ui.getWidth(), ui.getHeight());

        // Register chat commands
        CommandRegistry.register(new HelpCommand());
        CommandRegistry.register(new SpawnCommand());
        CommandRegistry.register(new TpCommand());
        CommandRegistry.register(new GamemodeCommand());
        CommandRegistry.register(new GiveCommand());
        CommandRegistry.register(new TimeCommand());
        CommandRegistry.register(new KillCommand());
        CommandRegistry.register(new WeatherCommand());
        CommandRegistry.register(new ConnectCommand());
        CommandRegistry.register(new VillageCommand());
        CommandRegistry.register(new AsteroidCommand());
        ConnectCommand.game = this;

        // Initialize achievement system
        AchievementRegistry.setToastManager(toastManager);

        // File-based command console: lets an external tool drive the game
        // (create worlds, teleport, take screenshots) via debug_commands.txt
        debugConsole = new DebugCommandHandler(this::executeDebugCommand);

        openMainMenu();
    }
    
    // ------------------------------------------------------------------
    // Screen navigation
    // ------------------------------------------------------------------
    
    private void openMainMenu() {
        // Stop autosaving into a world we are no longer playing
        currentSave = null;
        lastBiomeId = null;
        
        screens.open(new MainMenuScreen(new MainMenuScreen.Callbacks() {
            @Override public void onSingleplayer() { openWorldList(); }
            @Override public void onMultiplayer() { openMultiplayerScreen(); }
            @Override public void onOptions() { openOptions(false); }
            @Override public void onLanguage() { openLanguage(false); }
            @Override public void onQuit() { glfwSetWindowShouldClose(window, true); }
            @Override public String versionString() { return VERSION; }
            @Override public int menuTheme() { return settings.menuTheme; }
        }));
        applyCursorMode();
    }
    
    private void openMultiplayerScreen() {
        multiplayerScreen = new com.voxelgame.ui.screen.MultiplayerScreen(
            new com.voxelgame.ui.screen.MultiplayerScreen.Callbacks() {
                @Override public void onConnect(String name, String host, int port) {
                    playerName = name;
                    connectToServer(host, port);
                }
                @Override public void onBack() { openMainMenu(); }
            });
        screens.open(multiplayerScreen);
        applyCursorMode();
    }
    
    /** F6 puts the arrow keys in charge of the view model placement. */
    private boolean handTuningMode = false;
    
    public boolean isHandTuningMode() { return handTuningMode; }
    
    /**
     * @return true when the key was consumed by tuning
     */
    private boolean handleHandTuningKey(int key, int mods) {
        HandTuning t = heldItem.getTuning();
        
        // Shift makes every adjustment ten times finer
        float step = ((mods & GLFW_MOD_SHIFT) != 0) ? 0.005f : 0.05f;
        float angle = ((mods & GLFW_MOD_SHIFT) != 0) ? 1.0f : 5.0f;
        
        switch (key) {
            case GLFW_KEY_LEFT:      t.nudgeX(-step); return true;
            case GLFW_KEY_RIGHT:     t.nudgeX(step);  return true;
            case GLFW_KEY_UP:        t.nudgeY(step);  return true;
            case GLFW_KEY_DOWN:      t.nudgeY(-step); return true;
            case GLFW_KEY_PAGE_UP:   t.nudgeZ(step);  return true;
            case GLFW_KEY_PAGE_DOWN: t.nudgeZ(-step); return true;
            
            case GLFW_KEY_EQUAL:
            case GLFW_KEY_KP_ADD:      t.nudgeScale(0.02f);  return true;
            case GLFW_KEY_MINUS:
            case GLFW_KEY_KP_SUBTRACT: t.nudgeScale(-0.02f); return true;
            
            case GLFW_KEY_R: t.nudgeRotA(angle);  return true;
            case GLFW_KEY_F: t.nudgeRotA(-angle); return true;
            case GLFW_KEY_T: t.nudgeRotB(angle);  return true;
            case GLFW_KEY_G: t.nudgeRotB(-angle); return true;
            
            case GLFW_KEY_TAB: t.toggleTarget(); return true;
            default: return false;
        }
    }
    
    /** True while the title screen is showing its orbiting world backdrop. */
    private boolean isPanoramaActive() {
        return screens.current() instanceof MainMenuScreen && panorama.isAnchored();
    }

    /** Light for loose block entities: dims with the day like everything else. */
    private float entityLightLevel() {
        return 0.15f + 0.85f * dayNight.getDaylight();
    }
    
    private void openPauseMenu() {
        screens.open(new PauseScreen(new PauseScreen.Callbacks() {
            @Override public void onResume() { closeScreens(); }
            @Override public void onOptions() { openOptions(true); }
            @Override public void onQuitToTitle() {
                saveWorld();
                settings.save();
                openMainMenu();
            }
        }));
        applyCursorMode();
    }
    
    private void openCreativeInventory() {
        if (player.isCreative()) {
            CreativeInventoryScreen screen = new CreativeInventoryScreen(
                new CreativeInventoryScreen.Callbacks() {
                @Override public Inventory inventory() { return player.getInventory(); }

                @Override public void onPickBlock(BlockType block) {
                    player.getInventory().setHotbarItem(
                        player.getInventory().getSelectedSlot(), block, 64);
                }

                @Override public void onSelectHotbarSlot(int index) {
                    player.getInventory().setSelectedSlot(index);
                }

                @Override public ItemStack mouseItem() { return mouseItem; }
                @Override public void onMouseItemChanged(ItemStack stack) { mouseItem = stack; }
            }, renderer.getTextureAtlas());
            // Creative destroys the cursor stack instead of dropping it
            screen.onClose(() -> mouseItem.clear());
            screens.open(screen);
        } else {
            openSurvivalInventory();
        }
        applyCursorMode();
    }
    
    private void openSurvivalInventory() {
        SurvivalInventoryScreen screen = new SurvivalInventoryScreen(
            new SurvivalInventoryScreen.Callbacks() {
                @Override public Inventory inventory() { return player.getInventory(); }
                @Override public ItemStack mouseItem() { return mouseItem; }
                @Override public void onMouseItemChanged(ItemStack stack) { mouseItem = stack; }
                @Override public void onSelectHotbarSlot(int index) {
                    player.getInventory().setSelectedSlot(index);
                }
                @Override public ItemStack[] craftingGrid() { return player.getCraftingGrid(); }
                @Override public void onCraft(ItemStack stack) { onCrafted(stack); }
                @Override public void dropStack(ItemStack stack) {
                    dropItem(player.getPosition(), stack);
                }
            }, renderer.getTextureAtlas());
        screen.onClose(() -> {
            // Vanilla: the 2x2 crafting grid empties back into the inventory
            Inventory inv = player.getInventory();
            ItemStack[] grid = player.getCraftingGrid();
            for (int i = 0; i < grid.length; i++) {
                if (grid[i] == null || grid[i].isEmpty()) continue;
                ItemStack leftover = inv.addStack(grid[i]);
                grid[i] = leftover.isEmpty() ? new ItemStack(BlockType.AIR, 0) : leftover;
            }
            // Drop whatever is still on the cursor
            if (!mouseItem.isEmpty()) {
                dropItem(player.getPosition(), mouseItem);
                mouseItem = new ItemStack(BlockType.AIR, 0);
            }
            closeScreens();
        });
        screens.open(screen);
    }
    
    private void openCrafting() {
        CraftingScreen screen = new CraftingScreen(
            new CraftingScreen.Callbacks() {
                @Override public Inventory inventory() { return player.getInventory(); }
                @Override public ItemStack mouseItem() { return mouseItem; }
                @Override public void onMouseItemChanged(ItemStack stack) { mouseItem = stack; }
                @Override public void onClose() { closeScreens(); }
                @Override public void onCraft(ItemStack stack) { onCrafted(stack); }
                @Override public void dropStack(ItemStack stack) {
                    dropItem(player.getPosition(), stack);
                }
            }, renderer.getTextureAtlas());
        screen.onClose(() -> {
            if (!mouseItem.isEmpty()) {
                dropItem(player.getPosition(), mouseItem);
                mouseItem = new ItemStack(BlockType.AIR, 0);
            }
            closeScreens();
        });
        screens.open(screen);
    }

    /** [0.4] Achievement hooks for crafting: pickaxe / sword / furnace. */
    private void onCrafted(ItemStack stack) {
        if (stack == null || !stack.isItem() || stack.getItem() == null) return;
        Item it = stack.getItem();
        if (it.toolType == ToolType.PICKAXE) {
            AchievementRegistry.trigger("craft_pickaxe");
        } else if (it.toolType == ToolType.SWORD) {
            AchievementRegistry.trigger("craft_sword");
        }
        if (it == ItemRegistry.FURNACE_ITEM) {
            AchievementRegistry.trigger("craft_furnace");
        }
    }

    /** [0.4] Track furnace outputs so smelting an iron ingot unlocks its achievement. */
    private final java.util.Map<Long, Integer> smeltTrack = new java.util.HashMap<>();

    private void checkSmeltAchievement() {
        if (world == null) return;
        for (java.util.Map.Entry<Long, ContainerData> e :
                world.getContainerManager().getContainers().entrySet()) {
            ContainerData c = e.getValue();
            if (c.type != ContainerData.Type.FURNACE) continue;
            ItemStack out = c.getSlot(ContainerData.FURNACE_OUTPUT);
            int cur = out == null || out.isEmpty() ? 0 : out.getCount();
            Integer prev = smeltTrack.get(e.getKey());
            if (prev != null && cur > prev && out != null && out.isItem()
                    && out.getItem() == ItemRegistry.IRON_INGOT) {
                AchievementRegistry.trigger("smelt_iron");
            }
            smeltTrack.put(e.getKey(), cur);
        }
    }

    private void openFurnace(int x, int y, int z) {
        ContainerData container = world.getContainerManager().getOrCreate(
            x, y, z, ContainerData.Type.FURNACE);

        FurnaceScreen screen = new FurnaceScreen(
            new FurnaceScreen.Callbacks() {
                @Override public Inventory inventory() { return player.getInventory(); }
                @Override public ItemStack mouseItem() { return mouseItem; }
                @Override public void onMouseItemChanged(ItemStack stack) { mouseItem = stack; }
                @Override public ContainerData container() { return container; }
                @Override public void dropStack(ItemStack stack) {
                    dropItem(player.getPosition(), stack);
                }
            }, renderer.getTextureAtlas());
        screen.onClose(() -> {
            if (!mouseItem.isEmpty()) {
                dropItem(player.getPosition(), mouseItem);
                mouseItem = new ItemStack(BlockType.AIR, 0);
            }
            closeScreens();
        });
        screens.open(screen);
    }

    /** [ENCH] Open the enchanting table screen. */
    private void openEnchanting() {
        EnchantingTableScreen screen = new EnchantingTableScreen(
            new EnchantingTableScreen.Callbacks() {
                @Override public Inventory inventory() { return player.getInventory(); }
                @Override public ItemStack mouseItem() { return mouseItem; }
                @Override public void onMouseItemChanged(ItemStack stack) { mouseItem = stack; }
                @Override public int xpLevel() { return player.getXpLevel(); }
                @Override public int xpProgress() { return player.getXpProgress(); }
                @Override public boolean spendXp(int amount) { return player.spendXp(amount); }
                @Override public void dropStack(ItemStack stack) {
                    dropItem(player.getPosition(), stack);
                }
            }, renderer.getTextureAtlas());
        screen.onClose(() -> {
            if (!mouseItem.isEmpty()) {
                dropItem(player.getPosition(), mouseItem);
                mouseItem = new ItemStack(BlockType.AIR, 0);
            }
            closeScreens();
        });
        screens.open(screen);
    }

    private void openChest(int x, int y, int z) {
        // Get container data (handles double chests)
        ContainerData container = world.getContainerManager().getChest(x, y, z);

        ChestScreen screen = new ChestScreen(
            new ChestScreen.Callbacks() {
                @Override public Inventory inventory() { return player.getInventory(); }
                @Override public ItemStack mouseItem() { return mouseItem; }
                @Override public void onMouseItemChanged(ItemStack stack) { mouseItem = stack; }
                @Override public ContainerData container() { return container; }
                @Override public void dropStack(ItemStack stack) {
                    dropItem(player.getPosition(), stack);
                }
            }, renderer.getTextureAtlas());
        screen.onClose(() -> {
            if (!mouseItem.isEmpty()) {
                dropItem(player.getPosition(), mouseItem);
                mouseItem = new ItemStack(BlockType.AIR, 0);
            }
            closeScreens();
        });
        screens.open(screen);
    }

    private void openDeathScreen(Player.DeathCause cause, boolean hardcore) {
        screens.open(new DeathScreen(cause, hardcore, new DeathScreen.Callbacks() {
            @Override public void onRespawn() { respawnPlayer(); }
@Override public void onTitleScreen() {
                saveWorldSync();
                openMainMenu();
            }
        }));
        applyCursorMode();
    }
    
    /**
     * Put the player back at the world spawn with full health.
     */
    private void respawnPlayer() {
        // Drop inventory before respawning
        dropInventory();

        // [0.9] Respawn at the saved world spawn, not at the death site
        if (currentSave != null && currentSave.getMeta().hasSpawn) {
            WorldMeta meta = currentSave.getMeta();
            player.getPosition().set(meta.spawnX, meta.spawnY + 10, meta.spawnZ);
        }

        // Use the new respawn method which finds a safe position
        player.respawn();

        // Death wiped the inventory: hand out a fresh starter kit so
        // respawning in survival is playable, like the first spawn
        if (!player.isCreative() && player.getInventory().isCompletelyEmpty()) {
            player.getInventory().giveStarterKit();
        }
        
        // Update camera position
        camera.setPosition(new Vector3f(
            player.getPosition().x,
            player.getPosition().y + eyeHeight,
            player.getPosition().z
        ));
        
        closeScreens();
    }
    
    /** Drop every inventory slot as a loose item at the player's position. */
    private void dropInventory() {
        if (player == null || world == null) return;
        Vector3f pos = player.getPosition();
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            ItemStack stack = inv.getHotbarItem(i);
            if (!stack.isEmpty()) {
                dropItem(pos, stack);
                inv.setHotbarItem(i, new ItemStack(BlockType.AIR, 0));
            }
        }
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getInventoryItem(i);
            if (!stack.isEmpty()) {
                dropItem(pos, stack);
                inv.setInventoryItem(i, new ItemStack(BlockType.AIR, 0));
            }
        }
    }
    
    private void openSound(boolean overWorld) {
        screens.push(new SoundSettingsScreen(settings, new SoundSettingsScreen.Listener() {
            @Override public void onMusicVolume(float volume) {
                settings.musicVolume = volume;
            }
            @Override public void onSoundVolume(float volume) {
                settings.soundVolume = volume;
                AudioManager.setSoundVolume(volume);
            }
            @Override public void onClosed() {
                screens.pop();
                applyCursorMode();
            }
        }, overWorld));
        applyCursorMode();
    }
    
    private void openDifficulty(boolean overWorld) {
        boolean locked = player.isHardcore();
        screens.push(new DifficultyScreen(settings, new DifficultyScreen.Listener() {
            @Override public void onDifficultyChanged(int difficulty) {
                settings.difficulty = difficulty;
            }
            @Override public void onClosed() {
                screens.pop();
                applyCursorMode();
            }
        }, overWorld, locked));
        applyCursorMode();
    }
    
    /** F4 cycles SURVIVAL -> CREATIVE -> HARDCORE for testing. */
    private void cycleGameMode() {
        WorldMeta.GameMode next = switch (player.getGameMode()) {
            case SURVIVAL -> WorldMeta.GameMode.CREATIVE;
            case CREATIVE -> WorldMeta.GameMode.HARDCORE;
            case HARDCORE -> WorldMeta.GameMode.SURVIVAL;
        };
        player.setGameMode(next);
        if (next == WorldMeta.GameMode.CREATIVE) {
            player.toggleFlying();
        }
        System.out.println("Game mode: " + next);
    }
    
    private void handlePlayerDeath(Player.DeathCause cause) {
        if (player.isHardcore()) {
            // Mark the world dead РІР‚вЂќ only "Title Screen" will work
            if (currentSave != null) {
                currentSave.markDead();
            }
            openDeathScreen(cause, true);
        } else {
            openDeathScreen(cause, false);
        }
    }
    
    /** Graphics settings; every control applies live. */
    private void openVideoSettings(boolean overWorld) {
        screens.push(new VideoSettingsScreen(settings, new VideoSettingsScreen.Listener() {
            @Override public void onRenderDistance(int chunks) {
                settings.renderDistance = chunks;
                renderDistance = chunks;
                world.setRenderDistance(chunks);
            }
            @Override public void onGraphicsChanged(boolean fancy) {
                ChunkMeshBuilder.setFancyGraphics(fancy);
                ChunkMeshBuilder.setAmbientOcclusionEnabled(settings.ambientOcclusion);
        ChunkMeshBuilder.setFancyGraphics(settings.fancyGraphics);
                // Leaf geometry is baked, so every chunk has to be rebuilt
                for (var c : world.getChunks().values()) c.setDirty(true);
            }
            @Override public void onGuiScaleChanged(int scale) {
                settings.guiScale = scale;
                ui.setGuiScaleSetting(scale);
                screens.resize(ui.getWidth(), ui.getHeight());
            }
            @Override public void onGammaChanged(float gamma) {
                settings.gamma = gamma;
            }
            @Override public void onCloudsChanged(boolean on) {
                settings.clouds = on;
                cloudLayer.setEnabled(on);
            }
            @Override public void onParticlesChanged(int level) {
                settings.particleLevel = level;
            }
            @Override public void onViewBobbingChanged(boolean on) {
                settings.viewBobbing = on;
            }
            @Override public void onFovChanged(float fov) {
                settings.fov = fov;
                Game.this.fov = fov;
                camera.setFov(fov);
            }
            @Override public void onVsyncChanged(boolean on) {
                settings.vsync = on;
                glfwSwapInterval(on ? 1 : 0);
            }
            @Override public void onFullscreenChanged(boolean on) {
                settings.fullscreen = on;
                applyFullscreen(on);
            }
            @Override public void onShadowsChanged(boolean on) {
                settings.shadows = on;
                shadowsEnabled = on;
            }
            @Override public void onBloomChanged(boolean on) {
                settings.bloomEnabled = on;
            }
            @Override public void onFxaaChanged(boolean on) {
                settings.fxaaEnabled = on;
            }
            @Override public void onMenuThemeChanged(int theme) {
                settings.menuTheme = theme;
            }
            @Override public void onClosed() {
                screens.pop();
                applyCursorMode();
            }
        }, overWorld));
        applyCursorMode();
    }
    
    private void openControls(boolean overWorld) {
        screens.push(new ControlsScreen(settings, keyBindings,
            new ControlsScreen.Listener() {
                @Override public void onSensitivityChanged(float value) {
                    settings.mouseSensitivity = value;
                }
                @Override public void onInvertYChanged(boolean on) {
                    settings.invertY = on;
                }
                @Override public void onBindingsChanged() {
                    settings.save();
                }
                @Override public void onClosed() {
                    settings.save();
                    screens.pop();
                    applyCursorMode();
                }
            }, overWorld));
        applyCursorMode();
    }
    
    /** Toggle borderless fullscreen on the primary monitor. */
    private void applyFullscreen(boolean on) {
        var monitor = glfwGetPrimaryMonitor();
        var mode = glfwGetVideoMode(monitor);
        if (mode == null) return;
        
        if (on) {
            windowedWidth = width;
            windowedHeight = height;
            glfwSetWindowMonitor(window, monitor, 0, 0,
                mode.width(), mode.height(), mode.refreshRate());
        } else {
            glfwSetWindowMonitor(window, 0,
                (mode.width() - windowedWidth) / 2,
                (mode.height() - windowedHeight) / 2,
                windowedWidth, windowedHeight, 0);
        }
    }
    
    private int windowedWidth = 1280;
    private int windowedHeight = 720;
    
    /** Spawn a loose item at the given position (e.g. from breaking a block). */
    public void dropItem(Vector3f pos, ItemStack stack) {
        world.getItemEntities().add(new ItemEntity(world, pos, stack));
    }
    
    /** Move items toward the player, pick them up when close. */
    private void updateItemEntities(float dt) {
        var items = world.getItemEntities();
        var toRemove = new java.util.ArrayList<ItemEntity>();
        Vector3f playerPos = player.getPosition();
        
        for (ItemEntity item : items) {
            item.update(dt);
            
            // Magnet toward player when close
            if (item.canPickup()) {
                Vector3f itemPos = item.getPosition();
                float dx = playerPos.x - itemPos.x;
                float dz = playerPos.z - itemPos.z;
                float distSq = dx*dx + dz*dz;
                
                if (distSq < 1.5f * 1.5f) {
                    item.attractTo(playerPos, dt);
                }
                
                if (item.isCloseTo(playerPos, 1.0f)) {
                    ItemStack s = item.getStack();
                    boolean picked;
                    if (s.isBlock()) {
                        picked = player.getInventory().addItem(s.getBlockType(), s.getCount());
                    } else if (s.isItem() && s.getItem() != null) {
                        picked = player.getInventory().addItem(s.getItem(), s.getCount());
                    } else {
                        picked = false;
                    }
                    if (picked) {
                        item.markDead();
                        toRemove.add(item);
                    }
                }
            }
            
            if (item.isDead()) toRemove.add(item);
        }
        
        items.removeAll(toRemove);
    }

    /** [ENCH] Update XP orbs: gravity, magnet to player, pickup. */
    private void updateXpOrbs(float dt) {
        world.updateXpOrbs(dt);
        var orbs = world.getXpOrbs();
        Vector3f playerPos = player.getPosition();
        for (com.voxelgame.world.entity.XpOrb orb : orbs) {
            if (orb.canPickup() && orb.isCloseTo(playerPos, 3.0f)) {
                orb.attractTo(playerPos, dt);
            }
            if (orb.canPickup() && orb.isCloseTo(playerPos, 0.7f)) {
                player.addXp(orb.getValue());
                orb.markDead();
            }
        }
    }
    
    /** World list, reached from Singleplayer. */
    private void openWorldList() {
        screens.open(new SelectWorldScreen(new SelectWorldScreen.Callbacks() {
            @Override public void onPlay(WorldMeta meta) { loadWorld(meta); }
            @Override public void onCreate() { openCreateWorld(null); }
            @Override public void onRecreate(WorldMeta meta) { openCreateWorld(meta); }
            @Override public void onCancel() { openMainMenu(); }
        }));
        applyCursorMode();
    }
    
    private void openCreateWorld(WorldMeta template) {
        screens.open(new CreateWorldScreen(new CreateWorldScreen.Callbacks() {
            @Override public void onCreate(WorldMeta meta) { createWorld(meta); }
            @Override public void onCancel() { openWorldList(); }
        }, template));
        applyCursorMode();
    }
    
    /**
     * Build a brand new world and drop straight into it.
     */
    private void createWorld(WorldMeta meta) {
        System.out.println("Creating world '" + meta.displayName
            + "' seed=" + meta.seed + " mode=" + meta.gameMode);
        
        activateWorld(meta, true);
        configureDimension(meta);
    }
    
    /** Resume an existing save. */
    private void loadWorld(WorldMeta meta) {
        System.out.println("Loading world '" + meta.displayName + "'");
        activateWorld(meta, false);
        configureDimension(meta);
    }
    
    /**
     * Swap in a world and hand control to the player.
     *
     * The previous world is flushed first, so leaving one save and entering
     * another never loses edits.
     */
    private void activateWorld(WorldMeta meta, boolean fresh) {
        if (world != null) {
            world.cleanup();
        }
        
        currentSave = new WorldSave(meta);
        currentSave.saveMeta();

        // Load biome discovery for this world
        biomeDiscovery = new BiomeDiscovery(currentSave.getDirectory());
        biomeDiscovery.load();
        
        world = new World(meta.seed);
        world.setSave(currentSave);
        world.setRenderDistance(renderDistance);
        world.setStructuresEnabled(meta.generateStructures);
        
        float spawnX, spawnY, spawnZ;
if (fresh) {
            spawnX = 8.5f;
            spawnZ = 8.5f;
            world.preloadChunks(new Vector3f(spawnX, 70, spawnZ), 2);
            // [WG] Sea-heavy worlds may put the spawn underwater: surface it
            int g = world.getGroundHeight((int) spawnX, (int) spawnZ);
            if (g < Chunk.SEA_LEVEL) g = Chunk.SEA_LEVEL;
            spawnY = g + 2.0f;
        } else {
            spawnX = meta.playerX;
            spawnY = meta.playerY;
            spawnZ = meta.playerZ;
            world.preloadChunks(new Vector3f(spawnX, spawnY, spawnZ), 2);
        }

        // [0.9] Remember the world spawn (first play through, or legacy saves)
        if (!meta.hasSpawn) {
            meta.hasSpawn = true;
            meta.spawnX = spawnX;
            meta.spawnY = spawnY;
            meta.spawnZ = spawnZ;
            currentSave.saveMeta();
        }
        
        player = new Player(new Vector3f(spawnX, spawnY, spawnZ), world);
        camera.setPosition(new Vector3f(spawnX, spawnY + eyeHeight, spawnZ));
        camera.setOrientation(meta.playerYaw, meta.playerPitch);
        
        player.setGameMode(meta.gameMode);
        player.setDeathCallback(cause -> handlePlayerDeath(cause));
        // [GP-032] Explosions need the player to hurt
        world.setPlayer(player);
        
        // Hardcore locks difficulty to hard
        if (meta.gameMode == WorldMeta.GameMode.HARDCORE) {
            settings.difficulty = 2;
        } else {
            settings.difficulty = meta.difficulty;
        }
        
        // Creative starts with flight enabled
        if (meta.gameMode == WorldMeta.GameMode.CREATIVE) {
            player.toggleFlying();
        }
        
        player.getInventory().setSelectedSlot(meta.selectedSlot);
        dayNight.setTime(meta.dayTime);

        // Load inventory from save
        deserializeInventory(player.getInventory(), meta.inventoryData);

        // Survival always spawns with the starter kit: fresh worlds get it
        // from the constructor, and a save with a wiped inventory (or a
        // world converted from creative) is re-equipped here
        if (meta.gameMode != WorldMeta.GameMode.CREATIVE
                && player.getInventory().isCompletelyEmpty()) {
            player.getInventory().giveStarterKit();
        }

        panorama.setAnchor(spawnX, spawnY, spawnZ);

        lastAutosave = glfwGetTime();
        lastBiomeId = null; // Reset so first biome triggers toast
        closeScreens();

        // [UI-010] First launch: show the controls tutorial once
        if (!settings.tutorialSeen) {
            settings.tutorialSeen = true;
            settings.save();
            toastManager.enqueue(new com.voxelgame.ui.toast.TutorialToast());
        }
    }
    
/**
     * Persist the world and the player's position + inventory.
     */
    private void saveWorld() {
        if (currentSave == null || world == null) return;

        WorldMeta meta = currentSave.getMeta();
        Vector3f p = player.getPosition();
        meta.playerX = p.x;
        meta.playerY = p.y;
        meta.playerZ = p.z;
        meta.playerYaw = camera.getYaw();
        meta.playerPitch = camera.getPitch();
        meta.playerHealth = player.getHealth();
        meta.playerHunger = player.getHunger();
        meta.selectedSlot = player.getInventory().getSelectedSlot();
        meta.gameMode = player.getGameMode();
        meta.difficulty = settings.difficulty;
        meta.dayTime = dayNight.getTime();
        meta.inventoryData = serializeInventory(player.getInventory());

        // Meta and discovery are small; keep synchronous on the game thread
        currentSave.saveMeta();
        if (biomeDiscovery != null) biomeDiscovery.save();

        // Snapshot all dirty chunk data on the main thread (fast memcpy), then
        // write to disk on a daemon background thread so autosave never stalls
        // the render loop. On exit (cleanup), we use the synchronous path.
        java.util.List<com.voxelgame.world.save.WorldSave.ChunkSnapshot> snaps =
            world.snapshotDirtyChunks();
        final WorldSave saveRef = currentSave;
        final String displayName = meta.displayName;
        saveExecutor.submit(() -> {
            int chunks = saveRef.writeSnapshots(snaps);
            saveRef.createBackup();
            System.out.println("Saved '" + displayName + "' (" + chunks + " chunks)");
        });
    }

    /** Synchronous save used on exit — waits for the background queue to drain. */
    private void saveWorldSync() {
        if (currentSave == null || world == null) return;
        // Stop accepting new tasks and wait for pending autosave to finish
        saveExecutor.shutdown();
        try { saveExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        WorldMeta meta = currentSave.getMeta();
        Vector3f p = player.getPosition();
        meta.playerX = p.x; meta.playerY = p.y; meta.playerZ = p.z;
        meta.playerYaw = camera.getYaw(); meta.playerPitch = camera.getPitch();
        meta.playerHealth = player.getHealth(); meta.playerHunger = player.getHunger();
        meta.selectedSlot = player.getInventory().getSelectedSlot();
        meta.gameMode = player.getGameMode(); meta.difficulty = settings.difficulty;
        meta.dayTime = dayNight.getTime();
        meta.inventoryData = serializeInventory(player.getInventory());
        currentSave.saveMeta();
        if (biomeDiscovery != null) biomeDiscovery.save();
        int chunks = world.flushToDisk();
        currentSave.createBackup();
        System.out.println("Saved '" + meta.displayName + "' (" + chunks + " chunks)");
    }

    // ------------------------------------------------------------------
    // [SPACE] Dimension switching and rocket ride
    // ------------------------------------------------------------------

    /** Apply (or remove) the dark-sky space palette after a world swap. */
    private void configureDimension(WorldMeta meta) {
        spaceMode = "space".equals(meta.dimension);
        dayNight.setSpaceMode(spaceMode);
        dayNight.setPaused(spaceMode);
        if (spaceMode) {
            dayNight.setTime(0.35);
            currentWeather = "clear";
            overcast = 0;
        }
        if (player != null) {
            player.setGravityScale(spaceMode ? SPACE_GRAVITY_SCALE : 1.0f);
        }
    }

    /** True when the block type belongs to the rocket tower. */
    private static boolean isRocketPart(int id) {
        return id == BlockType.ROCKET_ENGINE.id
            || id == BlockType.ROCKET_FUEL.id
            || id == BlockType.ROCKET_BODY.id
            || id == BlockType.ROCKET_WINDOW.id;
    }

    /**
     * Scan upward from the launch pad and return {height, topBlockY} when the
     * stack is a valid rocket: engine at the base, body parts in the middle,
     * cone on top, nothing else above it.  Returns null on any mismatch.
     */
    private int[] detectRocket(int padX, int padY, int padZ) {
        int base = padY + 1;
        if (world.getBlock(padX, base, padZ) != BlockType.ROCKET_ENGINE.id) {
            return null; // must start with the engine
        }
        int top = base;
        boolean coneSeen = false;
        for (int y = base; y < 256; y++) {
            int b = world.getBlock(padX, y, padZ);
            if (b == BlockType.ROCKET_CONE.id) {
                top = y;
                coneSeen = true;
                break;
            }
            if (!isRocketPart(b)) {
                return null;
            }
            top = y;
        }
        if (!coneSeen) return null;
        // Nothing solid directly above the cone
        int above = world.getBlock(padX, top + 1, padZ);
        if (above != 0 && BlockType.fromId(above) != null && BlockType.fromId(above).solid) {
            return null;
        }
        int height = top - base + 1;
        return (height >= 3) ? new int[]{ height, top } : null;
    }

    /** Right-click handler for the launch pad: start the rocket or go home. */
    private void handleLaunchPad(int px, int py, int pz) {
        if (rocketRiding) return;
        if (spaceMode) {
            travelHome();
            return;
        }
        if (world.getBlock(px, py - 1, pz) == BlockType.AIR.id) {
            com.voxelgame.ui.toast.ToastManager tm = toastManager;
            tm.enqueue(new com.voxelgame.ui.toast.AchievementToast(
                "Запуск отменён", "Пад должен стоять на блоке", BlockType.ROCKET_LAUNCH_PAD.id, 0xFF808080));
            return;
        }
        int[] tower = detectRocket(px, py, pz);
        if (tower == null) {
            toastManager.enqueue(new com.voxelgame.ui.toast.AchievementToast(
                "Нет ракеты",
                "Двигатель / корпус / конус над падом",
                BlockType.ROCKET_LAUNCH_PAD.id, 0xFFFFAA00));
            return;
        }
        startRocketRide(px, py, pz, tower[0], tower[1]);
    }

    private void startRocketRide(int px, int py, int pz, int height, int topY) {
        rocketPadX = px; rocketPadY = py; rocketPadZ = pz;
        rocketReturnPos.set(px + 0.5f, py + 1.1f, pz + 0.5f);
        rocketRiding = true;
        rocketProgress = 0f;
        preRocketFov = camera.getFov();
        player.setFlying(true);
        player.getPosition().set(px + 0.5f, topY + 1.3f, pz + 0.5f);
        camera.setPosition(new Vector3f(
            player.getPosition().x, player.getPosition().y + eyeHeight, player.getPosition().z));
        player.setVerticalVelocity(0);
        player.setHorizontalVelocity(0, 0);
        AudioManager.play("sounds/fuse", 1.0f, 0.6f);
        toastManager.enqueue(new com.voxelgame.ui.toast.AchievementToast(
            "Ракета в космос!", "Набор высоты...", BlockType.ROCKET_ENGINE.id, 0xFFFFAA00));
    }

    private void rocketRide(double dt) {
        if (!rocketRiding || player == null) return;
        float fdt = (float) dt;
        rocketProgress += fdt;
        float speed = java.lang.Math.min(ROCKET_THRUST_MAX, ROCKET_THRUST_MIN + rocketProgress * 22f);
        player.setVerticalVelocity(speed);
        // Camera stays attached to the player (updateViewBob runs after)
        camera.setPosition(new Vector3f(
            player.getPosition().x,
            player.getPosition().y + eyeHeight,
            player.getPosition().z));
        // Thruster plume
        Vector3f p = player.getPosition();
        particles.emitRocketExhaust(p.x, p.y - 1.4f, p.z, speed / ROCKET_THRUST_MAX);
        // Gentle camera shake
        float t = java.lang.Math.min(1f, rocketProgress / 3f);
        camera.setShake(
            (float)(java.lang.Math.random() * 0.04) * t,
            (float)(java.lang.Math.random() * 0.04) * t,
            (float)(java.lang.Math.random() * 0.04) * t,
            (float)(java.lang.Math.random() * 0.02) * t);
        // Widen the fov for speed feel
        camera.setFov(preRocketFov + 18f * java.lang.Math.min(1f, rocketProgress / 4f));
        // Finish once high enough
        if (player.getPosition().y >= ROCKET_LAUNCH_Y || rocketProgress >= 14f) {
            rocketRiding = false;
            camera.setFov(preRocketFov);
            travelToSpace();
        }
    }

    private void travelToSpace() {
        if (currentSave == null || world == null) return;
        WorldMeta homeMeta = currentSave.getMeta();
        String homeFolder = homeMeta.folderName;

        // Persist the overworld with the player standing on the pad
        homeMeta.playerX = rocketReturnPos.x;
        homeMeta.playerY = rocketReturnPos.y;
        homeMeta.playerZ = rocketReturnPos.z;
        homeMeta.playerYaw = camera.getYaw();
        homeMeta.playerPitch = camera.getPitch();
        homeMeta.playerHealth = player.getHealth();
        homeMeta.playerHunger = player.getHunger();
        homeMeta.inventoryData = serializeInventory(player.getInventory());
        homeMeta.dayTime = dayNight.getTime();
        homeMeta.selectedSlot = player.getInventory().getSelectedSlot();
        currentSave.saveMeta();
        // Flush modified chunks (the rocket you just rode) on a background thread
        java.util.List<com.voxelgame.world.save.WorldSave.ChunkSnapshot> snaps =
            world.snapshotDirtyChunks();
        final com.voxelgame.world.save.WorldSave saveRef = currentSave;
        final String homeName = homeMeta.displayName;
        saveExecutor.submit(() -> {
            int c = saveRef.writeSnapshots(snaps);
            saveRef.createBackup();
            System.out.println("Saved '" + homeName + "' (" + c + " chunks) before space jump");
        });

        // Create or load the linked space world
        String spaceFolder = homeFolder + "_space";
        boolean fresh = !com.voxelgame.world.save.WorldSave.exists(spaceFolder);
        com.voxelgame.world.save.WorldSave spaceSave = null;
        com.voxelgame.world.save.WorldMeta spaceMeta;
        if (fresh) {
            spaceMeta = new com.voxelgame.world.save.WorldMeta(
                homeMeta.displayName + " Космос",
                homeMeta.seed ^ 0x5DEECE66DL,
                homeMeta.gameMode,
                homeMeta.generateStructures);
            spaceMeta.folderName = spaceFolder;
            spaceMeta.dimension = "space";
            spaceMeta.homeWorld = homeFolder;
            spaceMeta.inventoryData = homeMeta.inventoryData;
            spaceMeta.playerHealth = homeMeta.playerHealth;
            spaceMeta.playerHunger = homeMeta.playerHunger;
            spaceMeta.dayTime = 0.35;
        } else {
            spaceMeta = com.voxelgame.world.save.WorldSave.readWorld(spaceFolder);
            if (spaceMeta == null) {
                spaceMeta = new com.voxelgame.world.save.WorldMeta(
                    homeMeta.displayName + " Космос",
                    homeMeta.seed ^ 0x5DEECE66DL,
                    homeMeta.gameMode,
                    homeMeta.generateStructures);
                spaceMeta.folderName = spaceFolder;
                spaceMeta.dimension = "space";
                spaceMeta.homeWorld = homeFolder;
                spaceMeta.dayTime = 0.35;
                fresh = true; // couldn't load; treat as new
            } else {
                spaceMeta.inventoryData = homeMeta.inventoryData;
                spaceMeta.playerHealth = homeMeta.playerHealth;
                spaceMeta.playerHunger = homeMeta.playerHunger;
            }
        }
        activateWorld(spaceMeta, fresh);
        configureDimension(spaceMeta);
        if (fresh) {
            // Build a landing pad with platform at the space spawn so the player
            // can always fly home.
            int sx = (int) spaceMeta.spawnX;
            int sz = (int) spaceMeta.spawnZ;
            int g = java.lang.Math.max(world.getGroundHeight(sx, sz), 62);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlock(sx + dx, g, sz + dz, BlockType.SANDSTONE_BRICKS.id);
                }
            }
            world.setBlock(sx, g + 1, sz, BlockType.ROCKET_LAUNCH_PAD.id);
            float px = sx + 0.5f, py = g + 2.5f, pz = sz + 0.5f;
            player.getPosition().set(px, py, pz);
            camera.setPosition(new Vector3f(px, py + eyeHeight, pz));
            currentSave.saveMeta();
        }
        toastManager.enqueue(new com.voxelgame.ui.toast.AchievementToast(
            "Прибытие на планету",
            "Радиация за бортом — готовь ракету",
            BlockType.ROCKET_CONE.id, 0xFF40C0FF));
    }

    private void travelHome() {
        if (currentSave == null || world == null || !spaceMode) return;
        com.voxelgame.world.save.WorldSave spaceSave = currentSave;
        com.voxelgame.world.save.WorldMeta spaceMeta = spaceSave.getMeta();
        String homeFolder = spaceMeta.homeWorld;
        if (homeFolder == null || !com.voxelgame.world.save.WorldSave.exists(homeFolder)) {
            toastManager.enqueue(new com.voxelgame.ui.toast.AchievementToast(
                "Ошибка", "Родной мир не найден", -1, 0xFFFF4040));
            return;
        }
        // Persist the space world
        Vector3f sp = player.getPosition();
        spaceMeta.playerX = sp.x; spaceMeta.playerY = sp.y; spaceMeta.playerZ = sp.z;
        spaceMeta.playerYaw = camera.getYaw(); spaceMeta.playerPitch = camera.getPitch();
        spaceMeta.playerHealth = player.getHealth();
        spaceMeta.playerHunger = player.getHunger();
        spaceMeta.inventoryData = serializeInventory(player.getInventory());
        spaceMeta.dayTime = dayNight.getTime();
        spaceMeta.selectedSlot = player.getInventory().getSelectedSlot();
        spaceSave.saveMeta();
        java.util.List<com.voxelgame.world.save.WorldSave.ChunkSnapshot> snaps =
            world.snapshotDirtyChunks();
        saveExecutor.submit(() -> {
            int c = spaceSave.writeSnapshots(snaps);
            spaceSave.createBackup();
            System.out.println("Saved space world (" + c + " chunks)");
        });

        // Load the home world and place the player back on the pad
        com.voxelgame.world.save.WorldMeta homeMeta =
            com.voxelgame.world.save.WorldSave.readWorld(homeFolder);
        homeMeta.playerX = rocketReturnPos.x;
        homeMeta.playerY = rocketReturnPos.y;
        homeMeta.playerZ = rocketReturnPos.z;
        homeMeta.playerHealth = spaceMeta.playerHealth;
        homeMeta.playerHunger = spaceMeta.playerHunger;
        homeMeta.inventoryData = spaceMeta.inventoryData;
        homeMeta.selectedSlot = player.getInventory().getSelectedSlot();
        activateWorld(homeMeta, false);
        configureDimension(homeMeta);
        toastManager.enqueue(new com.voxelgame.ui.toast.AchievementToast(
            "Добро пожаловать домой!", homeMeta.displayName, BlockType.LANTERN.id, 0xFF80DDFF));
    }

    /** Serialize inventory to string: "slot:id:count:durability;..." */
    /**
     * Slots as "slot:b:blockId:count:durability" / "slot:i:itemId:count:durability".
     * The b/i letter disambiguates blocks with ids above 127 from items,
     * which the old purely-numeric encoding could not do.
     */
    private String serializeInventory(Inventory inv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            appendInventorySlot(sb, i, inv.getHotbarItem(i));
        }
        for (int i = 0; i < Inventory.MAIN_INVENTORY_SIZE; i++) {
            appendInventorySlot(sb, 9 + i, inv.getInventoryItem(i));
        }
        return sb.toString();
    }

    private void appendInventorySlot(StringBuilder sb, int slot, ItemStack item) {
        if (item.isEmpty()) return;
        char kind;
        int id;
        if (item.isBlock()) {
            kind = 'b';
            id = item.getBlockType().id;
        } else if (item.getItem() != null) {
            kind = 'i';
            id = item.getItem().id;
        } else {
            return;
        }
        if (sb.length() > 0) sb.append(';');
        sb.append(slot).append(':').append(kind).append(':').append(id)
          .append(':').append(item.getCount()).append(':').append(item.getDurability());
    }

    /**
     * Deserialize inventory from string. Accepts the new lettered format and
     * the legacy numeric one ("slot:id:count[:durability]", items stored as
     * 128 + itemId). The old reader wrapped ids in a byte, so every item id
     * above 127 turned negative and the item silently vanished on load.
     */
    private void deserializeInventory(Inventory inv, String data) {
        if (data == null || data.isEmpty()) return;
        for (String entry : data.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length < 3) continue;
            try {
                int slot = Integer.parseInt(parts[0]);
                boolean isItem;
                int id, count, durability;
                if (parts.length >= 5 && (parts[1].equals("b") || parts[1].equals("i"))) {
                    isItem = parts[1].equals("i");
                    id = Integer.parseInt(parts[2]);
                    count = Integer.parseInt(parts[3]);
                    durability = Integer.parseInt(parts[4]);
                } else {
                    id = Integer.parseInt(parts[1]);
                    count = Integer.parseInt(parts[2]);
                    durability = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                    isItem = id >= 128;
                    if (isItem) id -= 128;
                }

                if (slot < 0 || slot >= 9 + Inventory.MAIN_INVENTORY_SIZE) continue;

                if (isItem) {
                    Item it = ItemRegistry.getById(id);
                    if (it == null) continue;
                    if (slot < 9) inv.setHotbarItem(slot, it, count);
                    else inv.setInventoryItem(slot - 9, it, count);
                    ItemStack stored = slot < 9
                        ? inv.getHotbarItem(slot) : inv.getInventoryItem(slot - 9);
                    stored.setDurability(durability);
                } else {
                    BlockType block = BlockType.fromId(id);
                    if (block == null) continue;
                    if (slot < 9) inv.setHotbarItem(slot, block, count);
                    else inv.setInventoryItem(slot - 9, block, count);
                }
            } catch (NumberFormatException ignored) {}
        }
    }
    
    /**
     * Language picker. Choosing one reloads the dictionary and rebuilds the
     * whole stack, so labels change without a restart.
     */
    private void openLanguage(boolean overWorld) {
        screens.push(new LanguageScreen(new LanguageScreen.Callbacks() {
            @Override public void onLanguageChosen(String code) {
                settings.language = code;
                settings.save();
                Language.load(code);
                
                // Screens cache their labels at layout time
                screens.rebuildAll();
            }
            
            @Override public void onDone() {
                screens.pop();
                applyCursorMode();
            }
        }, overWorld));
        applyCursorMode();
    }
    
    /**
     * Options is pushed rather than opened, so closing it returns to whichever
     * menu launched it.
     */
    private void openOptions(boolean overWorld) {
        screens.push(new OptionsScreen(settings, new OptionsScreen.Listener() {
            @Override public void onRenderDistanceChanged(int chunks) {
                settings.renderDistance = chunks;
                renderDistance = chunks;
                // Load/unload radius has to follow, or the slider only
                // changes the fog and nothing actually streams in
                world.setRenderDistance(chunks);
            }
            @Override public void onFovChanged(float degrees) {
                settings.fov = degrees;
                fov = degrees;
                camera.setFov(degrees);
            }
            @Override public void onSensitivityChanged(float value) {
                settings.mouseSensitivity = value;
            }
            @Override public void onGuiScaleChanged(int scale) {
                settings.guiScale = scale;
                ui.setGuiScaleSetting(scale);
                screens.resize(ui.getWidth(), ui.getHeight());
            }
            @Override public void onVsyncChanged(boolean enabled) {
                settings.vsync = enabled;
                glfwSwapInterval(enabled ? 1 : 0);
            }
            @Override public void onCloudsChanged(boolean enabled) {
                settings.clouds = enabled;
                cloudLayer.setEnabled(enabled);
            }
            @Override public void onAoChanged(boolean enabled) {
                settings.ambientOcclusion = enabled;
                renderer.setAmbientOcclusionEnabled(enabled, world);
            }
            @Override public void onShadowsChanged(boolean enabled) {
                settings.shadows = enabled;
                shadowsEnabled = enabled;
            }
            @Override public void onDayLengthChanged(float minutes) {
                settings.dayLengthMinutes = minutes;
                dayNight.setDayLengthSeconds(minutes * 60.0);
            }
            @Override public void onAutoSaveChanged(double seconds) {
                settings.autoSaveSeconds = seconds;
            }
            @Override public void onLanguage() {
                openLanguage(overWorld);
            }
            @Override public void onVideoSettings() {
                openVideoSettings(overWorld);
            }
            @Override public void onControls() {
                openControls(overWorld);
            }
            @Override public void onSound() {
                openSound(overWorld);
            }
            @Override public void onDifficulty() {
                openDifficulty(overWorld);
            }
            @Override public void onClosed() {
                screens.pop();
                applyCursorMode();
            }
        }, overWorld));
        applyCursorMode();
    }
    
    /** Check if player entered a new biome and show toast. */
    // --- Weather particles ---
    private float weatherParticleTimer = 0;
    private static final float WEATHER_PARTICLE_INTERVAL = 0.05f; // spawn every 50ms

    private void updateWeatherParticles(float dt) {
        if (currentWeather == null || currentWeather.equals("clear")) return;
        if (player == null) return;

        weatherParticleTimer += dt;
        if (weatherParticleTimer < WEATHER_PARTICLE_INTERVAL) return;
        weatherParticleTimer = 0;

        Vector3f pos = player.getPosition();

        switch (currentWeather) {
            case "rain":
                particles.emitRain(pos, 3);
                break;
            case "thunder":
                // More intense rain + occasional lightning
                particles.emitRain(pos, 6);
                break;
            case "snow":
                particles.emitSnow(pos, 2);
                break;
        }
    }

    // ------------------------------------------------------------------
    // [WX] Weather: automatic changes, lightning strikes, sky dimming
    // ------------------------------------------------------------------

    /** True when the player stands in a cold biome where snow falls. */
    private boolean isColdBiome() {
        if (world == null || player == null) return false;
        com.voxelgame.world.biome.BiomeData biome = world.getDataBiomeAt(
            (int) player.getPosition().x, (int) player.getPosition().z);
        if (biome == null) return false;
        String id = biome.id.toLowerCase(java.util.Locale.ROOT);
        return id.contains("snow") || id.contains("taiga") || id.contains("ice")
            || id.contains("frozen") || id.contains("tundra");
    }

    /** Roll the next weather, honouring the player's biome. */
    public static String rollNextWeather(boolean coldBiome) {
        double r = java.lang.Math.random();
        if (coldBiome) {
            if (r < 0.55) return "clear";
            if (r < 0.75) return "snow";
            if (r < 0.88) return "rain";
            return "thunder";
        }
        if (r < 0.55) return "clear";
        if (r < 0.80) return "rain";
        if (r < 0.93) return "thunder";
        return "clear";
    }

    /** Drive automatic weather switches and lightning during thunder. */
    private void updateWeather(float dt) {
        // Lightning flash decays every frame, no matter the weather
        lightningFlash = java.lang.Math.max(0.0f, lightningFlash - dt * 2.5f);

        if (player == null) return;

        // [SPACE] Space planets always have a clear sky
        if (spaceMode) {
            currentWeather = "clear";
            overcast = 0;
            return;
        }

        float targetOvercast = switch (currentWeather == null ? "clear" : currentWeather) {
            case "thunder" -> 1.0f;
            case "rain" -> 0.70f;
            case "snow" -> 0.85f;
            default -> 0.0f;
        };
        overcast += (targetOvercast - overcast) * java.lang.Math.min(1.0f, dt * 0.4f);

        // Automatic weather changes (singleplayer survival only)
        if (player.getGameMode() != com.voxelgame.world.save.WorldMeta.GameMode.SURVIVAL) {
            return;
        }
        weatherChangeTimer -= dt;
        if (weatherChangeTimer <= 0) {
            weatherChangeTimer = 150.0f + (float) java.lang.Math.random() * 450.0f;
            String next = rollNextWeather(isColdBiome());
            if (next.equals(currentWeather)) return;
            currentWeather = next;
            // Chat notice in the player's language
            String key = switch (next) {
                case "rain" -> "weather.nowRain";
                case "thunder" -> "weather.nowThunder";
                case "snow" -> "weather.nowSnow";
                default -> "weather.nowClear";
            };
            chat.addMessage(Language.tr(key), 0xFFAAAAFF);
        }

        // Lightning: every few seconds a bolt strikes somewhere near the player
        if ("thunder".equals(currentWeather)) {
            lightningTimer -= dt;
            if (lightningTimer <= 0) {
                lightningTimer = 4.0f + (float) java.lang.Math.random() * 8.0f;
                spawnLightning();
            }
        }
    }

    /** A lightning bolt: sky flash, delayed thunder, a struck block ignites. */
    private void spawnLightning() {
        Vector3f pos = player.getPosition();
        float tx = pos.x + (float) (java.lang.Math.random() - 0.5) * 56.0f;
        float tz = pos.z + (float) (java.lang.Math.random() - 0.5) * 56.0f;
        int gy = world.getGroundHeight((int) tx, (int) tz);
        if (gy < 1) return;

        // Set the struck cell on fire
        world.igniteFire((int) tx, gy + 1, (int) tz);
        lightningFlash = 1.0f;

        // Thunder rolls in with a delay that grows with distance
        float dx = tx - pos.x, dz = tz - pos.z;
        float dist = (float) java.lang.Math.sqrt(dx * dx + dz * dz);
        float delay = java.lang.Math.min(4.0f, 0.1f + dist * 0.05f);
        float volume = java.lang.Math.max(0.25f, 1.0f - dist / 90.0f);
        thunderSounds.add(new ThunderStrike(
            delay, (System.nanoTime() / 1_000_000_000.0f) + delay, volume));

        // Standing next to the strike hurts
        if (dist < 3.0f) {
            player.setHealth(player.getHealth() - 5);
            chat.addMessage(Language.tr("weather.lightningHit"), 0xFFFF5555);
        }
    }

    /** Queued thunder rumbles (delay, fire time, volume). */
    private final java.util.List<ThunderStrike> thunderSounds = new java.util.ArrayList<>();
    private record ThunderStrike(float delay, double fireAt, float volume) {}

    /** Play thunder clips whose delay has elapsed. */
    private void updateThunderSounds(float now) {
        var it = thunderSounds.iterator();
        while (it.hasNext()) {
            ThunderStrike t = it.next();
            if (now >= t.fireAt()) {
                AudioManager.play("sounds/thunder", 1.0f, t.volume());
                it.remove();
            }
        }
    }

    /** [GP-073] Embers/smoke from every burning cell within a short radius. */
    private void updateFireParticles() {
        if (world == null || player == null) return;
        Vector3f pos = player.getPosition();

        // Cap iterations so a big blaze cannot tank the frame
        int emitted = 0;
        for (World.FireBlock fb : world.getFireBlocks()) {
            if (emitted >= 24) break;
            float dx = fb.x - pos.x;
            float dz = fb.z - pos.z;
            if (dx * dx + dz * dz > 49.0f) continue; // 7-block radius
            particles.emitFlame(fb.x + 0.5f, fb.y, fb.z + 0.5f);
            emitted++;
        }
    }

    private void checkBiomeDiscovery() {
        if (player == null || world == null) return;
        Vector3f pos = player.getPosition();
        BiomeData biome = world.getDataBiomeAt((int) pos.x, (int) pos.z);
        if (biome == null) return;

        String biomeId = biome.id;
        if (lastBiomeId != null && lastBiomeId.equals(biomeId)) return;
        lastBiomeId = biomeId;

        // First time in this biome?
        if (biomeDiscovery != null && !biomeDiscovery.has(biomeId)) {
            if (biomeDiscovery.discover(biomeId)) {
                biomeDiscovery.save();
                String name = Language.tr(biome.nameKey);
                toastManager.enqueue(new com.voxelgame.ui.toast.BiomeToast(
                    Language.tr("toast.newBiome"), name, biome.grassTint));

                // Trigger exploration achievements
                if (AchievementRegistry.getUnlockedCount() == 0) {
                    AchievementRegistry.trigger("first_biome");
                }
                if (biomeDiscovery.count() >= 5) {
                    AchievementRegistry.trigger("five_biomes");
                }
            }
        }
    }

    /** Return to gameplay with the mouse captured. */
    private void closeScreens() {
        boolean wasPanorama = isPanoramaActive();
        
        screens.closeAll();
        applyCursorMode();
        
        // Hand the camera back to the player, or it stays on the menu orbit
        if (wasPanorama) {
            Vector3f eye = new Vector3f(player.getPosition());
            eye.y += eyeHeight;
            camera.setPosition(eye);
            camera.setOrientation(-90.0f, 0.0f);
        }
    }
    
    /**
     * The cursor is captured only when no screen is open; re-centre tracking
     * on release so the camera does not jump on the next mouse move.
     */
    private void applyCursorMode() {
        // The chat box is not a screen, but typing still needs the cursor
        boolean wantCursor = screens.shouldShowCursor() || chat.isOpen();
        glfwSetInputMode(window, GLFW_CURSOR,
            wantCursor ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
        mouseCaptured = !wantCursor;
        if (mouseCaptured) firstMouse = true;
    }
    
    /** Earthquake screen shake: 0 = calm, 1 = violent impact. */
    private float shakeTrauma = 0;

    /** Drive the camera shake from the decaying trauma value. */
    private void applyCameraShake() {
        if (shakeTrauma <= 0) {
            camera.setShake(0, 0, 0, 0);
            return;
        }
        float s = shakeTrauma * shakeTrauma;
        float t = (float) glfwGetTime() * 21.0f;
        camera.setShake(
            (float) (java.lang.Math.sin(t * 1.9) * 0.55 + java.lang.Math.sin(t * 2.7) * 0.45) * 0.28f * s,
            (float) (java.lang.Math.cos(t * 2.3) * 0.5 + java.lang.Math.cos(t * 1.6) * 0.5) * 0.24f * s,
            (float) java.lang.Math.sin(t * 1.3) * 0.07f * s,
            (float) (java.lang.Math.sin(t * 2.1) * 0.6 + java.lang.Math.sin(t * 3.1) * 0.4) * 2.6f * s);
    }
    
    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            // Calculate delta time
            double currentFrame = glfwGetTime();
            deltaTime = currentFrame - lastFrame;
            lastFrame = currentFrame;
            frameCount++;
            FrameTimer.reset();
            
            // Update FPS counter
            fpsTimer += deltaTime;
            if (fpsTimer >= 1.0) {
                fps = frameCount / fpsTimer;
                frameCount = 0;
                fpsTimer = 0;
            }
            
            // A screen that pauses the game also blocks movement input
            paused = screens.shouldPauseGame();
            
            // Process co-op events (e.g. the join handshake) even while a
            // menu is open, so joining from the main menu works
            drainNetEvents();
            
            // The title screen drives the camera itself, orbiting the spawn
            if (isPanoramaActive()) {
                panorama.update(deltaTime);
                panorama.apply(camera);
                world.update(camera.getPosition());
                dayNight.update(deltaTime);
            }
            
            // Death takes over as soon as health runs out
            if (currentSave != null && !screens.isOpen() && player.getHealth() <= 0) {
                openDeathScreen(Player.DeathCause.GENERIC, player.isHardcore());
            }
            
            // Periodic autosave, so a crash costs at most half a minute
            if (!paused && currentSave != null
                && currentFrame - lastAutosave > settings.autoSaveSeconds) {
                lastAutosave = currentFrame;
                saveWorld();
            }
            
if (!paused) {
                processInput();
                // Clamp to prevent physics explosions after a lag spike
                deltaTime = java.lang.Math.min(deltaTime, 0.1);
                tickAccumulator += deltaTime;
                // Run world logic at a fixed 60 Hz regardless of actual framerate
                long worldStart = System.nanoTime();
                while (tickAccumulator >= TICK_RATE) {
                    tickAccumulator -= TICK_RATE;
                    double realDelta = deltaTime;
                    deltaTime = TICK_RATE;
                    updateWorld();
                    deltaTime = realDelta;
                }
                FrameTimer.add(FrameTimer.WORLD, System.nanoTime() - worldStart);
            } else {
                player.setHorizontalVelocity(0, 0);
                tickAccumulator = 0;
            }
            
            // The sky keeps moving while paused so the world stays alive,
            // but not while a menu is holding the game
            if (!paused) {
                dayNight.update(deltaTime);
                particles.update(deltaTime);
                
                Vector3f v = player.getVelocity();
                heldItem.update(deltaTime,
                    (float) java.lang.Math.sqrt(v.x * v.x + v.z * v.z),
                    player.isOnGround());
            }
            
            screens.update(deltaTime);

            // Cursor follow-up: the open/pop fade defers the stack swap, so
            // re-apply cursor mode whenever the visible top screen changes
            if (screens.revision() != lastScreensRevision) {
                lastScreensRevision = screens.revision();
                applyCursorMode();
            }
            
            // Earthquake shake: keep the world steady while a menu is open
            if (paused) {
                camera.setShake(0, 0, 0, 0);
            } else {
                shakeTrauma = java.lang.Math.max(0f, shakeTrauma - (float) deltaTime * 0.45f);
                applyCameraShake();
            }
            
            // Render
            render();
            
            // Debug console runs right after render so "screenshot"
            // captures the frame that just hit the framebuffer
            if (debugConsole != null) debugConsole.tick();
            
            // Swap buffers and poll events
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
    
    /** Is the key currently bound to this action held down? */
    private boolean isDown(KeyBindings.Action action) {
        int key = keyBindings.get(action);
        return key != GLFW_KEY_UNKNOWN && glfwGetKey(window, key) == GLFW_PRESS;
    }
    
    private void processInput() {
        // Typing in chat blocks movement; stop the player so they don't
        // coast while the world around them keeps running
        if (chat.isOpen()) {
            player.setHorizontalVelocity(0, 0);
            return;
        }
        // [SPACE] The rocket flies itself; lock player input during the ascent
        if (rocketRiding) {
            player.setHorizontalVelocity(0, 0);
            return;
        }
        float speed = player.isSprinting() ? 5.6f : 4.3f;
        
        // Reused scratch vectors: this runs every frame
        Vector3f movement = tmpMovement.set(0, 0, 0);
        
        // Camera-relative movement
        Vector3f front = tmpFront.set(camera.getFront());
        front.y = 0;
        if (front.lengthSquared() > 1e-6f) front.normalize();
        Vector3f right = tmpRight.set(camera.getRight());
        right.y = 0;
        if (right.lengthSquared() > 1e-6f) right.normalize();
        
        if (isDown(KeyBindings.Action.FORWARD)) movement.add(front);
        if (isDown(KeyBindings.Action.BACK)) movement.sub(front);
        if (isDown(KeyBindings.Action.LEFT)) movement.sub(right);
        if (isDown(KeyBindings.Action.RIGHT)) movement.add(right);
        
        // Normalize and apply speed (ice/soul sand alter it, GP-010)
        if (movement.lengthSquared() > 0) {
            movement.normalize().mul(speed * player.getGroundSpeedMultiplier());
        }
        
        player.setHorizontalVelocity(movement.x, movement.z);

        // Jump / Flight
        if (player.isFlying()) {
            // In flight mode: space held = ascend, sneak held = descend, both released = hover
            if (isDown(KeyBindings.Action.JUMP)) {
                player.flyAscend();
            } else if (isDown(KeyBindings.Action.SNEAK)) {
                player.descend();
            } else {
                player.flyHover();
            }
        } else if (isDown(KeyBindings.Action.JUMP)) {
            player.jump();
        }

        // Sprint
        player.setSprinting(isDown(KeyBindings.Action.SPRINT));
        
        // [GP-006] Sneak (Shift) - prevents edge falls and slows movement
        player.setSneaking(isDown(KeyBindings.Action.SNEAK));
    }
    
    private void updateWorld() {
        // Update world chunks based on camera position
        world.update(camera.getPosition());

        // Update furnaces (smelting logic)
        world.updateContainers();
        checkSmeltAchievement();

        // Update player physics
        player.update(deltaTime);

        // [BED] Sleeping: fade out, jump to sunrise, fade back in
        updateSleep();

        // [SPACE] Autonomous rocket ascent while flying to the space dimension
        rocketRide(deltaTime);

        // Update mobs
        world.updateMobs((float) deltaTime, player);

        // [WG] Mob spawner cores summon hostiles in dark rooms
        world.updateSpawners((float) deltaTime, player);

        // Update villagers (AI + physics), day-night time in 0-24000 ticks
        world.updateVillagers((float) deltaTime, player, (int) dayNight.getTime());

        // [GP-045] Farm animals wander, flee and grow
        world.updateAnimals((float) deltaTime, player);

        // [CR] Crops grow in loaded chunks around the player
        {
            var p = player.getPosition();
            int pcx = (int) java.lang.Math.floor(p.x / 16.0);
            int pcz = (int) java.lang.Math.floor(p.z / 16.0);
            world.updateCrops((float) deltaTime, pcx, pcz);
        }

        // Update primed TNT
        world.updateTnt((float) deltaTime);

        // [GP-022] Tumbling sand and gravel
        world.updateFallingBlocks((float) deltaTime);

        // [AST] Falling asteroids: impact craters, fire, blast damage
        world.updateAsteroids((float) deltaTime, player);
        updateAsteroidEvents();

        // Earthquake: asteroid impacts kick the camera, decaying over time
        shakeTrauma = java.lang.Math.min(1.0f, shakeTrauma + AsteroidEntity.consumeShakeImpulse());

        // [GP-073/GP-074] Fire burns, spreads and goes out
        world.updateFire((float) deltaTime, currentWeather);

        // Auto-spawn mobs around the player at night
        autoSpawnMobs();

        // [GP-045] Farm animals spawn around the player during the day
        autoSpawnAnimals();

        // Update chat message ages + command context
        chat.update((float) deltaTime);
        SpawnCommand.currentWorld = world;
        SpawnCommand.currentPlayerPos = player.getPosition();
        SpawnCommand.currentPlayerFront = camera.getFront();
        AsteroidEntity.particles = particles;
        AsteroidCommand.currentWorld = world;
        AsteroidCommand.currentPlayerPos = player.getPosition();
        particles.blockAtlas = renderer.getTextureAtlas();
        VillageCommand.currentWorld = world;
        TpCommand.currentPlayerPos = player.getPosition();
        TpCommand.teleporter = (x, y, z) -> {
            player.getPosition().set(x, y, z);
            camera.setPosition(new Vector3f(x, y + eyeHeight, z));
            player.setVerticalVelocity(0);
            player.setHorizontalVelocity(0, 0);
        };
        GamemodeCommand.changer = new GamemodeCommand.GameModeChanger() {
            @Override public WorldMeta.GameMode currentMode() { return player.getGameMode(); }
            @Override public void setMode(WorldMeta.GameMode mode) {
                player.setGameMode(mode);
                if (mode == WorldMeta.GameMode.CREATIVE && !player.isFlying()) {
                    player.toggleFlying();
                } else if (mode != WorldMeta.GameMode.CREATIVE && player.isFlying()) {
                    player.toggleFlying();
                }
            }
        };
        GiveCommand.currentInventory = player.getInventory();
        TimeCommand.accessor = new TimeCommand.TimeAccessor() {
            @Override public double getTime() { return dayNight.getTime(); }
            @Override public void setTime(double time) { dayNight.setTime(time); }
        };
        KillCommand.killer = () -> player.setHealth(0);
        WeatherCommand.changer = new WeatherCommand.WeatherChanger() {
            @Override public String currentWeather() { return currentWeather; }
            @Override public void setWeather(String weather) { currentWeather = weather; }
        };

        // Update loose items and handle pickup
        updateItemEntities((float) deltaTime);

        // [ENCH] Update experience orbs and pull them toward the player
        updateXpOrbs((float) deltaTime);

        // Update weather particles
        updateWeatherParticles((float) deltaTime);

        // [WX] Automatic weather, lightning and the thunder rumble queue
        updateWeather((float) deltaTime);
        updateThunderSounds((float) (System.nanoTime() / 1_000_000_000.0));

        // [GP-073] Embers and smoke rise from burning cells near the player
        updateFireParticles();

        // Check for biome discovery
        checkBiomeDiscovery();

        // Update toasts
        toastManager.update(deltaTime);

        // Update camera position (eye level)
        updateViewBob();
        
        if (viewMode == 0) {
            Vector3f eyePos = tmpEye.set(player.getPosition());
            eyePos.y += eyeHeight + bobOffsetY;
            eyePos.x += bobOffsetX * camera.getRight().x;
            eyePos.z += bobOffsetX * camera.getRight().z;
            camera.setPosition(eyePos);
        } else {
            updateThirdPersonCamera();
        }
        
        // Third-person limb swing: the phase advances with distance walked,
        // matching the cadence of the first-person camera bob
        Vector3f vel = player.getVelocity();
        float hSpeed = (float) java.lang.Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float targetSwing = (player.isOnGround() && hSpeed > 0.1f && !player.isFlying())
            ? java.lang.Math.min(hSpeed / 5.0f, 1.0f) : 0;
        limbSwingAmount += (targetSwing - limbSwingAmount)
            * java.lang.Math.min(1.0f, (float) deltaTime * 8);
if (targetSwing > 0) limbSwing += hSpeed * (float) deltaTime * 1.4f;

        // Keep the ears at the player's head, facing the same way
        Vector3f front = camera.getFront();
        Vector3f up = camera.getUp();
        tmpEye.set(player.getPosition());
        tmpEye.y += eyeHeight;
        AudioManager.setListener(tmpEye.x, tmpEye.y, tmpEye.z,
            front.x, front.y, front.z, up.x, up.y, up.z);

        updateFootsteps();

        updateSprintFov();
        
        // Update targeted block (for breaking/placing)
        updateTargetedBlock();
        
        // Handle block breaking progress
        updateBlockBreaking();

        // Network: send our state, drain incoming events, smooth remote players
        updateMultiplayer((float) deltaTime);
    }

    // ------------------------------------------------------------------
    // Local co-op
    // ------------------------------------------------------------------

    /** Connect to a co-op server. Called from the GL thread (chat command). */
    public void connectToServer(String host, int port) {
        disconnectFromServer();
        final Client.Listener listener = new Client.Listener() {
            @Override public void onJoined(int myId, long seed, int structures,
                                           float sx, float sy, float sz, double time) {
                netEvents.add(() -> applyJoin(seed, structures == 1, sx, sy, sz));
            }
            @Override public void onPlayerJoin(int id, String name) {
                netEvents.add(() -> addRemotePlayer(id, name));
            }
            @Override public void onPlayerLeave(int id) {
                netEvents.add(() -> removeRemotePlayer(id));
            }
            @Override public void onPlayerState(int id, float x, float y, float z,
                                                float yaw, float pitch, float health,
                                                int selected, boolean walking, boolean sneak) {
                netEvents.add(() -> {
                    com.voxelgame.net.RemotePlayer p = remoteById.get(id);
                    if (p != null) p.apply(x, y, z, yaw, pitch, health, selected, walking, sneak);
                });
            }
            @Override public void onBlockChange(int x, int y, int z, byte id) {
                netEvents.add(() -> {
                    if (world != null) world.setBlock(x, y, z, id);
                });
            }
            @Override public void onChat(int id, String name, String message) {
                netEvents.add(() -> chat.addMessage("<" + name + "> " + message, 0xFFFFFFFF));
            }
            @Override public void onServerMsg(String message) {
                netEvents.add(() -> chat.addMessage("* " + message + " *", 0xFFAAAAFF));
            }
            @Override public void onDisconnect(String reason) {
                netEvents.add(() -> {
                    chat.addMessage(reason, 0xFFFF5555);
                    if (multiplayerScreen != null && screens.current() instanceof com.voxelgame.ui.screen.MultiplayerScreen) {
                        multiplayerScreen.setStatus(reason);
                    }
                    disconnectFromServer();
                });
            }
        };
        try {
            netClient = new Client(host, port, playerName, listener);
            netClient.connect();
            chat.setMessageSink(text -> {
                if (netClient != null && netClient.isConnected()) netClient.sendChat(text);
            });
            chat.addMessage("Р СџР С•Р Т‘Р С”Р В»РЎР‹РЎвЂЎР ВµР Р…Р С‘Р Вµ Р С” " + host + ":" + port + "РІР‚В¦", 0xFFAAAAFF);
        } catch (java.io.IOException e) {
            chat.addMessage("Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С—Р С•Р Т‘Р С”Р В»РЎР‹РЎвЂЎР С‘РЎвЂљРЎРЉРЎРѓРЎРЏ: " + e.getMessage(), 0xFFFF5555);
            if (multiplayerScreen != null) {
                multiplayerScreen.setStatus("Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С—Р С•Р Т‘Р С”Р В»РЎР‹РЎвЂЎР С‘РЎвЂљРЎРЉРЎРѓРЎРЏ: " + e.getMessage());
            }
            netClient = null;
        }
    }

    public void disconnectFromServer() {
        if (netClient != null) {
            netClient.disconnect();
            netClient = null;
        }
        inMultiplayer = false;
        chat.setMessageSink(null);
        remoteById.clear();
        remotePlayers.clear();
    }

    private void addRemotePlayer(int id, String name) {
        com.voxelgame.net.RemotePlayer p = new com.voxelgame.net.RemotePlayer(id, name);
        p.colorIndex = id;
        remoteById.put(id, p);
        remotePlayers.add(p);
    }

    private void removeRemotePlayer(int id) {
        com.voxelgame.net.RemotePlayer p = remoteById.remove(id);
        if (p != null) remotePlayers.remove(p);
    }

    /** Apply WELCOME: (re)create the client world with the server's seed. */
    private void applyJoin(long seed, boolean structures, float sx, float sy, float sz) {
        inMultiplayer = true;
        if (world == null || world.getSeed() != seed) {
            WorldMeta meta = new WorldMeta("Co-op", seed, WorldMeta.GameMode.SURVIVAL, structures);
            meta.folderName = "coop_" + seed;
            currentSave = new WorldSave(meta);
            currentSave.saveMeta();
            world = new World(seed);
            world.setSave(currentSave);
            world.setRenderDistance(renderDistance);
            world.setStructuresEnabled(structures);
            world.preloadChunks(new Vector3f(sx, sy, sz), 2);
            if (player == null) {
                player = new Player(new Vector3f(sx, sy, sz), world);
                player.setGameMode(WorldMeta.GameMode.SURVIVAL);
                player.setDeathCallback(cause -> handlePlayerDeath(cause));
            }
            world.setPlayer(player);
            player.setWorld(world);
            if (biomeDiscovery == null) {
                biomeDiscovery = new BiomeDiscovery(currentSave.getDirectory());
                biomeDiscovery.load();
            }
            panorama.setAnchor(sx, sy, sz);
            lastAutosave = glfwGetTime();
            lastBiomeId = null;
        }
        player.getPosition().set(sx, sy, sz);
        player.setVerticalVelocity(0);
        player.setHorizontalVelocity(0, 0);
        camera.setPosition(new Vector3f(sx, sy + eyeHeight, sz));
        player.setHealth(20);
        chat.addMessage("Р СџР С•Р Т‘Р С”Р В»РЎР‹РЎвЂЎР ВµР Р…Р С•. Р СљР С‘РЎР‚ РЎРѓ РЎРѓР С‘Р Т‘Р С•Р С РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р В°, РЎвЂљРЎвЂ№ Р Р…Р В° РЎРѓР С—Р В°Р Р†Р Р…Р Вµ.", 0xFFAAFFAA);
        // Joined from the main menu: leave the menu and start playing
        if (screens.isOpen()) {
            closeScreens();
            applyCursorMode();
        }
    }

    /** Send our state + smooth remote players; drains net events on the GL thread. */
    private void updateMultiplayer(float dt) {
        drainNetEvents();
        if (netClient == null || !netClient.isConnected()) return;
        netStateTimer -= dt;
        if (netStateTimer <= 0f) {
            netStateTimer = NET_STATE_INTERVAL;
            Vector3f p = player.getPosition();
            Vector3f v = player.getVelocity();
            boolean walking = v.x * v.x + v.z * v.z > 0.05f;
            netClient.sendPlayerState(p.x, p.y, p.z,
                camera.getYaw(), camera.getPitch(),
                player.getHealth(), player.getInventory().getSelectedSlot(),
                walking, player.isSneaking());
        }
        for (com.voxelgame.net.RemotePlayer rp : remotePlayers) {
            float k = java.lang.Math.min(1f, dt * 14f);
            rp.renderPos.lerp(rp.position, k);
        }
    }

    /** Run any co-op events queued by the network reader on the GL thread. */
    private void drainNetEvents() {
        Runnable r;
        while ((r = netEvents.poll()) != null) {
            try { r.run(); } catch (Exception e) { e.printStackTrace(); }
        }
    }

/** Local block write that also pushes the change to the server (if connected). */
    private void localSetBlock(int x, int y, int z, int id) {
        if (world != null) world.setBlock(x, y, z, id);
        if (netClient != null && netClient.isConnected()) {
            netClient.sendBlockChange(x, y, z, (byte) id);
        }
    }

    // --- Auto-spawn mobs ---
    private float mobSpawnTimer = 0;
    private static final float MOB_SPAWN_INTERVAL = 3.0f; // seconds between spawns
    private static final int MAX_AUTO_MOBS = 70;

    /**
     * [AST] Random meteor events: every 60-120 seconds there is a chance
     * that 1-3 asteroids rain down within 200 blocks of the player.
     */
    private void updateAsteroidEvents() {
        if (world == null || player == null) return;

        asteroidEventTimer -= (float) deltaTime;
        if (asteroidEventTimer > 0) return;
        asteroidEventTimer = ASTEROID_EVENT_MIN_INTERVAL
            + (float) java.lang.Math.random()
            * (ASTEROID_EVENT_MAX_INTERVAL - ASTEROID_EVENT_MIN_INTERVAL);
        if (java.lang.Math.random() >= ASTEROID_EVENT_CHANCE) return;

        Vector3f p = player.getPosition();
        int count = 1 + (int) (java.lang.Math.random() * 3);
        for (int i = 0; i < count; i++) {
            float ax = p.x + (float) (java.lang.Math.random() - 0.5f) * 400.0f;
            float az = p.z + (float) (java.lang.Math.random() - 0.5f) * 400.0f;
            world.spawnAsteroidAt(ax, az, 1 + (int) (java.lang.Math.random() * 3));
        }
    }

    /**
     * [GP-034] Spawn hostile mobs in the dark around the player.
     *
     * Rules, close to vanilla: only at night, 24-48 blocks away (never in
     * view), on a loaded chunk, in darkness (block light < 7), with two free
     * blocks of head room. Peaceful difficulty never spawns anything.
     */
    private void autoSpawnMobs() {
        if (world == null || player == null || player.isCreative()) return;

        // Peaceful difficulty has no hostiles
        if (settings.difficulty == 0) return;

        // Night only: once the sun rises, the attempt counter resets
        if (dayNight.getDaylight() > 0.5f) {
            mobSpawnTimer = 0;
            return;
        }

        mobSpawnTimer += (float) deltaTime;
        if (mobSpawnTimer < MOB_SPAWN_INTERVAL) return;
        mobSpawnTimer = 0;

        if (world.getMobs().size() >= MAX_AUTO_MOBS) {
            System.out.println("[AutoSpawn] Max mobs reached (" + MAX_AUTO_MOBS
                + "), skipping. Total: " + world.getMobs().size());
            return;
        }

        Vector3f pos = player.getPosition();
        // 24-48 blocks away: spawns are never visible popping into the world
        float angle = (float) (java.lang.Math.random() * java.lang.Math.PI * 2);
        float radius = 24.0f + (float) java.lang.Math.random() * 24.0f;
        float sx = pos.x + (float) java.lang.Math.cos(angle) * radius;
        float sz = pos.z + (float) java.lang.Math.sin(angle) * radius;

        // The target column must be loaded
        if (world.getChunkAt((int) sx, (int) sz) == null) return;

        int groundY = world.getGroundHeight((int) sx, (int) sz);
        if (groundY <= 0 || groundY >= 250) return;

        // Darkness: mobs do not spawn where any light reaches
        if (world.getLight((int) sx, groundY + 1, (int) sz) >= 7) return;

        // Head room: two free cells above the floor
        if (world.isSolid((int) sx, groundY + 1, (int) sz)) return;
        if (world.isSolid((int) sx, groundY + 2, (int) sz)) return;

        com.voxelgame.world.entity.Zoloy mob = new com.voxelgame.world.entity.Zoloy(
            world, sx, groundY + 0.01f, sz);
        world.getMobs().add(mob);
        System.out.println("[AutoSpawn] Zoloy spawned at (" + String.format("%.1f", sx)
            + ", " + (groundY + 0.01f) + ", " + String.format("%.1f", sz)
            + ") | Total mobs: " + world.getMobs().size());
    }

// --- [GP-045] Auto-spawn farm animals ---
    private float animalSpawnTimer = 0;
    private static final float ANIMAL_SPAWN_INTERVAL = 4.0f;
    private static final int MAX_AUTO_ANIMALS = 40;

    /**
     * [GP-045] Spawn passive animals in lit open areas around the player:
     * the daylight mirror of mob spawning. 24-48 blocks away on a loaded
     * chunk, block light >= 7, two free cells of head room.
     */
    private void autoSpawnAnimals() {
        if (world == null || player == null) return;

        if (world.getAnimals().size() >= MAX_AUTO_ANIMALS) return;

        animalSpawnTimer += (float) deltaTime;
        if (animalSpawnTimer < ANIMAL_SPAWN_INTERVAL) return;
        animalSpawnTimer = 0;

        float angle = (float) (java.lang.Math.random() * java.lang.Math.PI * 2);
        float radius = 24.0f + (float) java.lang.Math.random() * 24.0f;
        float sx = player.getPosition().x + (float) java.lang.Math.cos(angle) * radius;
        float sz = player.getPosition().z + (float) java.lang.Math.sin(angle) * radius;

        if (world.getChunkAt((int) sx, (int) sz) == null) return;

        int groundY = world.getGroundHeight((int) sx, (int) sz);
        if (groundY <= 0 || groundY >= 250) return;

        // Daytime spawns: lit areas only
        if (world.getLight((int) sx, groundY + 1, (int) sz) < 7) return;

// Head room: two free cells above the floor
        if (world.isSolid((int) sx, groundY + 1, (int) sz)) return;
        if (world.isSolid((int) sx, groundY + 2, (int) sz)) return;

        Animal.AnimalType type = pickAnimalType((int) sx, groundY, (int) sz);
        world.spawnAnimal(type, sx, groundY + 0.01f, sz);
    }

    /**
     * [ANM] Weighted animal selection with biome flavour: parrots live in
     * jungles, bears and deer favour cold or forested land, farm animals
     * are everywhere.
     */
    private Animal.AnimalType pickAnimalType(int sx, int groundY, int sz) {
        boolean cold = isColdBiomeAt(sx, sz);
        boolean jungle = isJungleBiomeAt(sx, sz);
        float r = (float) java.lang.Math.random() * 100.0f;
        if (jungle) {
            // Parrots, chickens, pigs вЂ” jungle life
            if (r < 30) return Animal.AnimalType.PARROT;
            if (r < 55) return Animal.AnimalType.CHICKEN;
            if (r < 75) return Animal.AnimalType.PIG;
            if (r < 90) return Animal.AnimalType.FOX;
            return Animal.AnimalType.DEER;
        }
        if (cold) {
            // Bears and deer dominate the taiga
            if (r < 30) return Animal.AnimalType.BEAR;
            if (r < 55) return Animal.AnimalType.DEER;
            if (r < 70) return Animal.AnimalType.SHEEP;
            if (r < 85) return Animal.AnimalType.PIG;
            return Animal.AnimalType.COW;
        }
        // Farmland mix
        if (r < 22) return Animal.AnimalType.COW;
        if (r < 40) return Animal.AnimalType.PIG;
        if (r < 56) return Animal.AnimalType.CHICKEN;
        if (r < 70) return Animal.AnimalType.SHEEP;
        if (r < 80) return Animal.AnimalType.DEER;
        if (r < 90) return Animal.AnimalType.FOX;
        if (r < 96) return Animal.AnimalType.PARROT;
        return Animal.AnimalType.BEAR;
    }

    /** [ANM] Cold-biome test at an arbitrary point (not just the player). */
    private boolean isColdBiomeAt(int x, int z) {
        if (world == null) return false;
        com.voxelgame.world.biome.BiomeData biome = world.getDataBiomeAt(x, z);
        if (biome == null) return false;
        String id = biome.id.toLowerCase(java.util.Locale.ROOT);
        return id.contains("snow") || id.contains("taiga") || id.contains("ice")
            || id.contains("frozen") || id.contains("tundra");
    }

    /** [ANM] Jungle-biome test for parrot spawning. */
    private boolean isJungleBiomeAt(int x, int z) {
        if (world == null) return false;
        com.voxelgame.world.biome.BiomeData biome = world.getDataBiomeAt(x, z);
        if (biome == null) return false;
        return biome.id.toLowerCase(java.util.Locale.ROOT).contains("jungle");
    }

    // Reusable scratch, so the frame loop performs no allocation
    private final Vector3f tmpMovement = new Vector3f();
    private final Vector3f tmpFront = new Vector3f();
    private final Vector3f tmpRight = new Vector3f();
    private final Vector3f tmpEye = new Vector3f();
    private final Vector3f tmpTrample = new Vector3f();
    private final Vector3f tmpThirdDir = new Vector3f();
    private final Vector3f tmpThirdPos = new Vector3f();
    private final double[] cursorX = new double[1];
    private final double[] cursorY = new double[1];
    
    // View bobbing state
    private double bobPhase = 0;
    private float bobOffsetX = 0;
    private float bobOffsetY = 0;
    private float currentFovMultiplier = 1.0f;
    
    /**
     * Walking sway. The phase advances with distance travelled rather than
     * with time, so the head does not keep bobbing while standing still and
     * the rhythm matches the actual pace.
     */
    private void updateViewBob() {
        if (!settings.viewBobbing) {
            bobOffsetX = 0;
            bobOffsetY = 0;
            return;
        }
        
        Vector3f vel = player.getVelocity();
        float horizontalSpeed = (float) java.lang.Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        
        if (player.isOnGround() && horizontalSpeed > 0.1f) {
            bobPhase += horizontalSpeed * deltaTime * 1.9;
        } else {
            // Ease back to centre in the air or at rest
            bobPhase += deltaTime * 2.0;
            horizontalSpeed = 0;
        }
        
        float amount = java.lang.Math.min(horizontalSpeed / 5.0f, 1.0f) * 0.07f;
        
        // Vertical moves at twice the rate: one dip per footfall
        float targetY = (float) -java.lang.Math.abs(java.lang.Math.sin(bobPhase)) * amount;
        float targetX = (float) java.lang.Math.sin(bobPhase * 0.5) * amount * 0.6f;
        
        // Smooth so the camera never snaps when movement starts or stops
        bobOffsetY += (targetY - bobOffsetY) * java.lang.Math.min(1.0f, (float) deltaTime * 12.0f);
        bobOffsetX += (targetX - bobOffsetX) * java.lang.Math.min(1.0f, (float) deltaTime * 12.0f);
    }
    
    /** Widen the view slightly while sprinting, easing in and out. */
    private void updateSprintFov() {
        Vector3f vel = player.getVelocity();
        boolean movingFast = player.isSprinting()
            && (vel.x * vel.x + vel.z * vel.z) > 1.0f;
        
        float target = movingFast ? 1.12f : 1.0f;
        currentFovMultiplier += (target - currentFovMultiplier)
            * java.lang.Math.min(1.0f, (float) deltaTime * 6.0f);
        
        camera.setFov(fov * currentFovMultiplier);
    }

    /**
     * Places the camera behind (or in front of) the player for third-person
     * view. The camera travels along the horizontal look direction so
     * looking up or down does not drag it into the ground, and it stops
     * short of the first solid block like the vanilla perspective camera.
     */
    private void updateThirdPersonCamera() {
        Vector3f eye = tmpEye.set(player.getPosition());
        eye.y += eyeHeight;

        if (viewMode == 2) {
            // Front view: look back at the player, mirrored vertically
            camera.setOrientation(camera.getYaw() + 180, -camera.getPitch());
        }

        Vector3f front = camera.getFront();
        float len = (float) java.lang.Math.sqrt(front.x * front.x + front.z * front.z);
        float dx = front.x / len, dz = front.z / len;
        float sign = (viewMode == 2) ? -1.0f : 1.0f;
        tmpThirdDir.set(dx * sign, 0, dz * sign);

        Vector3f pos = tmpThirdPos.set(eye);
        final float maxDist = 4.2f;
        int by = (int) eye.y;
        for (float t = 0.25f; t <= maxDist; t += 0.25f) {
            float nx = eye.x - tmpThirdDir.x * t;
            float nz = eye.z - tmpThirdDir.z * t;
            if (isSolidForCamera((int) nx, by, (int) nz)) break;
            pos.x = nx;
            pos.z = nz;
        }
        camera.setPosition(pos);
    }

    /** Solid block the third-person camera must not fly through. */
    private boolean isSolidForCamera(int x, int y, int z) {
        if (world == null) return false;
        return BlockType.isSolidFast(world.getBlock(x, y, z));
    }


    // --- Footsteps ---

    /** Distance walked since the last step; a step fires every time it fills. */
    private double stepDistanceAccum = 0;

    private void updateFootsteps() {
        // No footsteps in the air, in the water or while flying
        if (!player.isOnGround() || player.isFlying() || player.isSwimming()) {
            stepDistanceAccum = 0;
            return;
        }

        Vector3f vel = player.getVelocity();
        float hSpeed = (float) java.lang.Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed <= 0.1f) {
            stepDistanceAccum = 0;
            return;
        }

        // Walking stride ~0.75 m, sprinting stretches it out
        stepDistanceAccum += hSpeed * deltaTime;
        float stride = player.isSprinting() ? 0.9f : 0.75f;
        while (stepDistanceAccum >= stride) {
            stepDistanceAccum -= stride;
            playFootstep();
        }
    }

    private void playFootstep() {
        String surface = surfaceUnderFeet();
        if (surface == null) return;

        // Random pitch keeps the cadence from sounding mechanical
        float pitch = 0.9f + (float) java.lang.Math.random() * 0.2f;
        float gain = player.isSprinting() ? 0.55f : 0.4f;
        AudioManager.play("sounds/steps/" + surface, pitch, gain);
    }

    /** Map the block under the player's feet to a footstep surface, or null. */
    private String surfaceUnderFeet() {
        Vector3f pos = player.getPosition();
        int x = (int) java.lang.Math.floor(pos.x);
        int y = (int) java.lang.Math.floor(pos.y) - 1;
        int z = (int) java.lang.Math.floor(pos.z);
        return switch (BlockType.fromId(world.getBlock(x, y, z))) {
            case GRASS_BLOCK, MYCELIUM -> "grass";
            case DIRT, COARSE_DIRT, PODZOL, CLAY -> "dirt";
            case SAND, RED_SAND, SOUL_SAND -> "sand";
            case OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS,
                 ACACIA_PLANKS, DARK_OAK_PLANKS, OAK_LOG, SPRUCE_LOG, BIRCH_LOG,
                 JUNGLE_LOG, CHARRED_LOG, CHARRED_LOG_TOP, PETRIFIED_LOG,
                 PETRIFIED_LOG_TOP, CRAFTING_TABLE, BOOKSHELF, CHEST -> "wood";
            case STONE, GRANITE, POLISHED_GRANITE, DIORITE, POLISHED_DIORITE,
                 ANDESITE, POLISHED_ANDESITE, COBBLESTONE, MOSSY_COBBLESTONE,
                 STONE_BRICKS, BRICK, OBSIDIAN, GLOWING_OBSIDIAN, BEDROCK,
                 COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, EMERALD_ORE,
                 REDSTONE_ORE, LAPIS_ORE, COPPER_ORE, COAL_BLOCK, IRON_BLOCK,
                 GOLD_BLOCK, DIAMOND_BLOCK, EMERALD_BLOCK, DEEPSLATE, BASALT,
                 NETHERRACK, NETHER_BRICKS, END_STONE, PURPUR_BLOCK, CRYSTAL,
                 SALT, ASH, FURNACE, GLOWSTONE, TERRACOTTA, WHITE_CONCRETE,
                 RED_CONCRETE, GREEN_CONCRETE, BLUE_CONCRETE, TNT, GLASS,
                 NETHER_REACTOR -> "stone";
            default -> null;
        };
    }


    
    private void updateTargetedBlock() {
        // [GP-020] Interactive raycast: also stops on non-solid blocks
        // (flowers, open doors), so they can be clicked and mined.
        targetedBlock = Raycast.castInteractive(
            camera.getPosition(), camera.getFront(), world, 6.0f);
        
        // [GP-019] Enforce 5-block interaction distance limit
        if (targetedBlock != null) {
            Vector3f blockCenter = new Vector3f(
                targetedBlock.x + 0.5f,
                targetedBlock.y + 0.5f,
                targetedBlock.z + 0.5f
            );
            float distance = camera.getPosition().distance(blockCenter);
            if (distance > 5.0f) {
                targetedBlock = null; // Too far to interact
            }
        }
    }
    
    /** Accumulator for periodic dig sounds while mining. */
    private double digSoundAccum = 0;

    private void updateBlockBreaking() {
        // [GP-026] Melee attack cooldown ticks down while playing
        if (attackCooldownTimer > 0) attackCooldownTimer -= (float) deltaTime;

        // Only mine while the left mouse button is actually held down
        boolean mining = mouseCaptured && !paused
                && glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        
        if (!mining || targetedBlock == null) {
            blockBreakProgress = 0;
            breakingBlock = null;
            digSoundAccum = 0;
            return;
        }
        
        // Creative breaks instantly РІР‚вЂќ no progress bar, no cracks
        if (player.isCreative()) {
            breakBlockInstant(targetedBlock);
            return;
        }
        
        // Restart progress if the player looked away at a different block
        if (breakingBlock == null
                || breakingBlock.x != targetedBlock.x
                || breakingBlock.y != targetedBlock.y
                || breakingBlock.z != targetedBlock.z) {
            breakingBlock = new Vector3i(targetedBlock.x, targetedBlock.y, targetedBlock.z);
            blockBreakProgress = 0;
            digSoundAccum = 0;
        }
        
        // [GP-054] Determine block type first so tool speed applies
        BlockType mined = BlockType.fromId(
            world.getBlock(breakingBlock.x, breakingBlock.y, breakingBlock.z));
        float hardness = getBlockHardness(breakingBlock.x, breakingBlock.y, breakingBlock.z);
        float toolSpeed = player.getBlockMiningMultiplier(mined);
        // [ENCH] Efficiency: each level speeds mining by 50%
        int efficiency = player.getHeldEfficiency();
        blockBreakProgress += deltaTime / hardness * toolSpeed * (1.0f + 0.5f * efficiency);
        
        // Keep swinging for as long as the block is being worked on
        if (!heldItem.isSwinging()) heldItem.startSwing();
        
        // Chips fly off while the block is being worked on
        if (mined != BlockType.AIR) {
            particles.emitDigging(breakingBlock.x, breakingBlock.y, breakingBlock.z,
                mined, renderer.getTextureAtlas());
        }

        // Dig sound every ~0.35s while actively mining
        digSoundAccum += deltaTime;
        if (digSoundAccum >= 0.35) {
            digSoundAccum = 0;
            playDigSound(mined);
        }
        
        if (blockBreakProgress >= 1.0f) {
            finishBlockBreak();
        }
    }

    /** Play a digging/breaking sound appropriate to the block type. */
    private void playDigSound(BlockType type) {
        String surface = blockToSurface(type);
        if (surface == null) return;
        float pitch = 0.8f + (float) java.lang.Math.random() * 0.3f;
        AudioManager.play("sounds/steps/" + surface, pitch, 0.35f);
    }

    /** [MC] Doors occupy two cells: breaking either half removes the other. */
    private void removeDoorOtherHalf(int x, int y, int z) {
        int below = world.getBlock(x, y - 1, z);
        int otherY = com.voxelgame.world.block.DoorBlock.isDoorId(below) ? y - 1 : y + 1;
        if (com.voxelgame.world.block.DoorBlock.isDoorId(world.getBlock(x, otherY, z))) {
            localSetBlock(x, otherY, z, (byte) 0);
        }
    }

    /** Play a louder break sound when a block is destroyed. */
    private void playBreakSound(BlockType type) {
        String surface = blockToSurface(type);
        if (surface == null) return;
        float pitch = 0.7f + (float) java.lang.Math.random() * 0.2f;
        AudioManager.play("sounds/steps/" + surface, pitch, 0.55f);
    }

    /** Play a placement sound when a block is placed. */
    private void playPlaceSound(BlockType type) {
        String surface = blockToSurface(type);
        if (surface == null) return;
        float pitch = 1.0f + (float) java.lang.Math.random() * 0.2f;
        AudioManager.play("sounds/steps/" + surface, pitch, 0.5f);
    }

    /** Map a block type to a footstep/dig surface category. */
    private String blockToSurface(BlockType type) {
        return switch (type) {
            case GRASS_BLOCK, MYCELIUM -> "grass";
            case DIRT, COARSE_DIRT, PODZOL, CLAY -> "dirt";
            case SAND, RED_SAND, SOUL_SAND -> "sand";
            case OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS,
                 ACACIA_PLANKS, DARK_OAK_PLANKS, OAK_LOG, SPRUCE_LOG, BIRCH_LOG,
                 JUNGLE_LOG, CHARRED_LOG, CHARRED_LOG_TOP, PETRIFIED_LOG,
                 PETRIFIED_LOG_TOP, CRAFTING_TABLE, BOOKSHELF, CHEST -> "wood";
            case STONE, GRANITE, POLISHED_GRANITE, DIORITE, POLISHED_DIORITE,
                 ANDESITE, POLISHED_ANDESITE, COBBLESTONE, MOSSY_COBBLESTONE,
                 STONE_BRICKS, BRICK, OBSIDIAN, GLOWING_OBSIDIAN, BEDROCK,
                 COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, EMERALD_ORE,
                 REDSTONE_ORE, LAPIS_ORE, COPPER_ORE, COAL_BLOCK, IRON_BLOCK,
                 GOLD_BLOCK, DIAMOND_BLOCK, EMERALD_BLOCK, DEEPSLATE, BASALT,
                 NETHERRACK, NETHER_BRICKS, END_STONE, PURPUR_BLOCK, CRYSTAL,
                 SALT, ASH, FURNACE, GLOWSTONE, TERRACOTTA, WHITE_CONCRETE,
                 RED_CONCRETE, GREEN_CONCRETE, BLUE_CONCRETE, TNT, GLASS,
                 NETHER_REACTOR -> "stone";
            default -> null;
        };
    }
    
    /** Creative: destroy the block in a single tick, no durability cost. */
    private void breakBlockInstant(Raycast.Hit target) {
        BlockType type = BlockType.fromId(world.getBlock(target.x, target.y, target.z));
        if (type == BlockType.AIR || type == BlockType.BEDROCK) return;
        
        particles.emitBlockBreak(target.x, target.y, target.z, type,
            renderer.getTextureAtlas());
        playBreakSound(type);
        // [CF] Chests and furnaces release their contents when broken
        if (type == BlockType.CHEST || type == BlockType.FURNACE) {
            dropContainerContents(target.x, target.y, target.z);
        }
        localSetBlock(target.x, target.y, target.z, (byte) 0);
        // [MC] The other half of a door breaks with it
        if (type == BlockType.OAK_DOOR || type == BlockType.OAK_DOOR_OPEN) {
            removeDoorOtherHalf(target.x, target.y, target.z);
        }
        // [BED] The other half of a bed breaks with it, dropping nothing
        if (type == BlockType.BED || type == BlockType.BED_HEAD) {
            breakBedPair(target.x, target.y, target.z);
        }
        // No inventory change РІР‚вЂќ creative has infinite blocks
    }
    
    /** Survival: the drop spawns as a loose item entity. */
    private void finishBlockBreak() {
        // Read the block type BEFORE destroying it, otherwise we always read AIR
        BlockType type = BlockType.fromId(
            world.getBlock(breakingBlock.x, breakingBlock.y, breakingBlock.z));
        
        particles.emitBlockBreak(breakingBlock.x, breakingBlock.y, breakingBlock.z,
            type, renderer.getTextureAtlas());
        playBreakSound(type);

        // [CF] Chests and furnaces release their contents when broken
        if (type == BlockType.CHEST || type == BlockType.FURNACE) {
            dropContainerContents(breakingBlock.x, breakingBlock.y, breakingBlock.z);
        }

        localSetBlock(breakingBlock.x, breakingBlock.y, breakingBlock.z, (byte) 0);

        // [MC] The other half of a door breaks with it
        if (type == BlockType.OAK_DOOR || type == BlockType.OAK_DOOR_OPEN) {
            removeDoorOtherHalf(breakingBlock.x, breakingBlock.y, breakingBlock.z);
        }

        // [BED] The other half of a bed breaks with it, dropping nothing
        if (type == BlockType.BED || type == BlockType.BED_HEAD) {
            breakBedPair(breakingBlock.x, breakingBlock.y, breakingBlock.z);
        }

        if (type != BlockType.AIR && type != BlockType.BEDROCK) {
            // [GP-054][GP-056] Wrong tool kind or too-low a tier: the block
            // breaks but drops nothing, exactly like vanilla
            BlockHarvest.Requirement req = BlockHarvest.get(type);
            BlockType dropType = null;
            int dropCount = 1;
            if (req == null) {
                dropType = type;
            } else {
                ToolTier tier = player.getHeldToolTier();
                ToolType tool = player.getHeldToolType();
                boolean okTool = req.tool == ToolType.NONE || tool == req.tool;
                boolean okTier = req.minTier == null
                    || (tier != null && tier.harvestLevel >= req.minTier.harvestLevel);
                if (okTool && okTier) {
                    // [ENCH] Silk Touch keeps the block itself, Fortune boosts drops
                    if (player.hasSilkTouch()) {
                        dropType = type;
                    } else {
                        dropType = req.drop != null ? req.drop : type;
                        dropCount = player.fortuneCount(dropType);
                    }
                }
            }

            if (dropType != null) {
                Vector3f dropPos = new Vector3f(
                    breakingBlock.x + 0.5f,
                    breakingBlock.y + 0.5f,
                    breakingBlock.z + 0.5f);
                dropItem(dropPos, new ItemStack(dropType, dropCount));
            }

            // [ENCH] Ores sparkle with experience
            spawnOreXp(type);

            // Trigger mining achievements
            triggerMiningAchievement(type);

            // Apples drop from oak leaves (~10% chance)
            if (type == BlockType.OAK_LEAVES && java.lang.Math.random() < 0.1) {
                Vector3f applePos = new Vector3f(
                    breakingBlock.x + 0.5f,
                    breakingBlock.y + 0.5f + 0.3f,
                    breakingBlock.z + 0.5f);
                dropItem(applePos, new ItemStack(BlockType.APPLE, 1));
            }
        }

        // Damage the held tool (pickaxe, axe, shovel take damage)
        if (!player.isCreative()) {
            player.damageHeldTool();
        }
        
        blockBreakProgress = 0;
        breakingBlock = null;
    }

    /** [CF] A broken chest/furnace drops its contents and forgets them. */
    private void dropContainerContents(int x, int y, int z) {
        ContainerData container = world.getContainerManager().get(x, y, z);
        if (container == null) return;
        Vector3f pos = new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f);
        for (int i = 0; i < container.size(); i++) {
            ItemStack stack = container.getSlot(i);
            if (stack != null && !stack.isEmpty()) {
                dropItem(pos, stack.copy());
            }
        }
        world.getContainerManager().remove(x, y, z);
    }
    
    /** Trigger mining achievements based on block type. */
    private void triggerMiningAchievement(BlockType type) {
        if (type == BlockType.STONE || type == BlockType.COBBLESTONE) {
            AchievementRegistry.trigger("mine_stone");
        } else if (type == BlockType.IRON_ORE) {
            AchievementRegistry.trigger("mine_iron");
        } else if (type == BlockType.DIAMOND_ORE) {
            AchievementRegistry.trigger("mine_diamond");
        }
    }

    /** [ENCH] Ores emit experience orbs when mined. */
    private void spawnOreXp(BlockType type) {
        int xp = switch (type) {
            case COAL_ORE -> 2;
            case IRON_ORE -> 3;
            case GOLD_ORE -> 4;
            case DIAMOND_ORE -> 7;
            case EMERALD_ORE -> 5;
            case REDSTONE_ORE -> 3;
            case LAPIS_ORE -> 4;
            case COPPER_ORE -> 2;
            default -> 0;
        };
        if (xp > 0) {
            world.spawnXpOrb(breakingBlock.x + 0.5f, breakingBlock.y + 0.5f,
                breakingBlock.z + 0.5f, xp);
        }
    }

    private float getBlockHardness(int x, int y, int z) {
        int blockId = world.getBlock(x, y, z);
        BlockType type = BlockType.fromId(blockId);
        
        if (type == BlockType.BEDROCK) return Float.MAX_VALUE; // Unbreakable
        if (type == BlockType.OBSIDIAN) return 50.0f;
        if (type == BlockType.STONE || type == BlockType.COBBLESTONE) return 1.5f;
        if (type == BlockType.DIRT || type == BlockType.GRASS_BLOCK || type == BlockType.SAND) return 0.5f;
        if (type == BlockType.OAK_LOG || type == BlockType.OAK_PLANKS) return 2.0f;
        if (type == BlockType.FIRE) return 0.05f; // Snuffed out instantly
        // Improved planks: hardness comes from the species x treatment data
        com.voxelgame.world.plank.PlankVariant plank =
            com.voxelgame.world.plank.PlankBlockRegistry.fromId(type.name);
        if (plank != null) return plank.getHardness();
        return 1.0f;
    }

    // ------------------------------------------------------------------
    // [BED] Sleep, bed placement and bed pair breaking
    // ------------------------------------------------------------------

    /**
     * Right-clicked a bed. Only night counts: sleeping skips straight to
     * sunrise, sets the respawn point and wakes the player on top of the
     * bed.
     */
    private void trySleep(int x, int y, int z) {
        if (sleeping || waking) return;
        if (!dayNight.isNight()) {
            chat.addMessage("Р СљР С•Р В¶Р Р…Р С• РЎРѓР С—Р В°РЎвЂљРЎРЉ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р Р…Р С•РЎвЂЎРЎРЉРЎР‹!", 0xFFFFAA00);
            return;
        }
        bedSpawn = new Vector3i(x, y, z);
        sleeping = true;
        sleepFade = 0;
        // Stand where the player is: the fade hides the transition
        player.setVerticalVelocity(0);
        player.setHorizontalVelocity(0, 0);
    }

    /** Advances the fade and performs the night skip at full black. */
    private void updateSleep() {
        if (sleeping) {
            sleepFade += (float) deltaTime / SLEEP_FADE_TIME;
            if (sleepFade >= 1.0f) {
                sleeping = false;
                waking = true;
                wakeUp();
            }
        } else if (waking) {
            sleepFade -= (float) deltaTime / SLEEP_FADE_TIME;
            if (sleepFade <= 0) {
                waking = false;
                sleepFade = 0;
            }
        }
    }

    /** Sunrise, teleport onto the bed, remember it as the respawn point. */
    private void wakeUp() {
        dayNight.setTime(0.25); // 06:00
        player.setBedSpawn(bedSpawn);
        if (bedSpawn != null) {
            player.getPosition().set(
                bedSpawn.x + 0.5f,
                bedSpawn.y + 0.7f,
                bedSpawn.z + 0.5f);
            camera.setPosition(new Vector3f(
                player.getPosition().x,
                player.getPosition().y + eyeHeight,
                player.getPosition().z));
            player.setVerticalVelocity(0);
            player.setHorizontalVelocity(0, 0);
        }
        chat.addMessage("Р вЂќР С•Р В±РЎР‚Р С•Р Вµ РЎС“РЎвЂљРЎР‚Р С•!", 0xFFAAFFAA);
    }

    /**
     * Place a two-block bed: the foot half in the placement cell, the head
     * half in the cell the player is facing. Both cells must be free.
     */
    private boolean placeBed(int x, int y, int z) {
        Vector3f front = camera.getFront();
        int fx = (int) java.lang.Math.round(front.x);
        int fz = (int) java.lang.Math.round(front.z);
        if (fx == 0 && fz == 0) fz = -1;
        int hx = x + fx, hz = z + fz;
        System.out.println("[Bed] place at (" + x + "," + y + "," + z
            + ") front=(" + fx + "," + fz + ") head=(" + hx + "," + hz + ")"
            + " foot=" + world.getBlock(x, y, z)
            + " headCell=" + world.getBlock(hx, y, hz));
        if (world.getBlock(x, y, z) != BlockType.AIR.id
                || world.getBlock(hx, y, hz) != BlockType.AIR.id) {
            return false;
        }
        localSetBlock(x, y, z, BlockType.BED.id);
        localSetBlock(hx, y, hz, BlockType.BED_HEAD.id);
        return true;
    }

    /** Destroy the matching half when one side of a bed breaks. */
    private void breakBedPair(int x, int y, int z) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + d[0], nz = z + d[1];
            int nb = world.getBlock(nx, y, nz);
            if (nb == BlockType.BED.id || nb == BlockType.BED_HEAD.id) {
                localSetBlock(nx, y, nz, (byte) 0);
                return;
            }
        }
    }

    private void render() {
        // The UI pass leaves depth testing off and blending on, so restore the
        // opaque-world GL state every frame before drawing chunks.
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);
        if (cullingEnabled) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        } else {
            glDisable(GL_CULL_FACE);
        }
        glFrontFace(GL_CCW);
        
        // Build/refresh chunk meshes before anything reads them
        long tRebuild = System.nanoTime();
        renderer.prepare(world);
        FrameTimer.add(FrameTimer.REBUILD, System.nanoTime() - tRebuild);
        
        // Pass 1: scene depth from the sun, into the shadow FBO.
        // Skipped at night - there is no direct light to occlude, so the
        // whole pass would be wasted work. The depth map only ever holds
        // chunk geometry, so it is cached and re-rendered only when chunk
        // geometry changed or the light space crossed a texel boundary.
        boolean castingShadows = shadowsEnabled && dayNight.getDaylight() > 0.05f;
        if (castingShadows) {
            boolean forceShadow = !wasCastingShadows
                || renderer.getRemeshGeneration() != lastShadowRemesh;
            long tShadow = System.nanoTime();
            boolean redrawShadow = shadowMap.updateIfNeeded(
                camera.getPosition(), dayNight.getSunDirection(), forceShadow);
            if (redrawShadow) {
                shadowMap.beginDepthPass();
                renderer.renderShadowPass(world, camera, shadowMap.getDepthShader(), shadowMap);
                shadowMap.endDepthPass(width, height);
                lastShadowRemesh = renderer.getRemeshGeneration();
                wasCastingShadows = true;

                // endDepthPass restores the viewport; re-assert world render state
                glFrontFace(GL_CCW);
                if (!cullingEnabled) glDisable(GL_CULL_FACE);
            }
            FrameTimer.add(FrameTimer.SHADOW, System.nanoTime() - tShadow);
        } else {
            wasCastingShadows = false;
        }
        
        // Clear to the current horizon colour: the skybox covers everything,
        // but this keeps any gap consistent with the time of day. The whole
        // 3D frame is rendered into the post-processing scene FBO.
        long tScene = System.nanoTime();
        Vector3f horizon = dayNight.getHorizonColor();
        postProcess.beginScene(width, height, horizon);
        
        // Sky first, while the depth buffer is still empty
        skybox.render(camera, dayNight, overcast, lightningFlash);
        
        // Fog hugs the render distance edge instead of fading nearby terrain
        float renderDistBlocks = renderDistance * 16.0f;
        
        // Render world
        shader.bind();
        shader.setUniformMat4("projection", camera.getProjectionMatrix());
        shader.setUniformMat4("view", camera.getViewMatrix());
        shader.setUniform3f("cameraPos", camera.getPosition());
        shader.setUniform1f("fogStart", renderDistBlocks * 0.75f);
        shader.setUniform1f("fogEnd", renderDistBlocks);
        // Fog tracks the sky's horizon band, so distant terrain dissolves
        // into whatever colour the sky currently is
        shader.setUniform3f("skyColor", dayNight.getHorizonColor());

        // [WG] Biome fog: mix the player's biome fog colour into the band
        Vector3f fogColor = dayNight.getHorizonColor();
        if (!spaceMode && world != null) {
            com.voxelgame.world.biome.BiomeData biome = world.getDataBiomeAt(
                (int) camera.getPosition().x, (int) camera.getPosition().z);
            if (biome != null && biome.fogColor != 0) {
                float mix = 0.30f * (0.4f + 0.6f * dayNight.getDaylight());
                fogColor = new Vector3f(
                    fogColor.x * (1 - mix) + (((biome.fogColor >> 16) & 0xFF) / 255.0f) * mix,
                    fogColor.y * (1 - mix) + (((biome.fogColor >> 8) & 0xFF) / 255.0f) * mix,
                    fogColor.z * (1 - mix) + ((biome.fogColor & 0xFF) / 255.0f) * mix);
            }
        }
        shader.setUniform3f("skyColor", fogColor);
        // [WX] Rain and thunder dim the fog to match the darkened sky
        if (overcast > 0.01f) {
            fogColor = new Vector3f(
                fogColor.x * (1 - overcast * 0.55f),
                fogColor.y * (1 - overcast * 0.55f),
                fogColor.z * (1 - overcast * 0.50f));
            shader.setUniform3f("skyColor", fogColor);
        }
        shader.setUniform3f("sunColor", dayNight.getSunColor());
        shader.setUniform1i("blockTextures", 0); // Texture unit 0
        shader.setUniform3f("sunDirection", dayNight.getSunDirection());
        
        // Shadow map on texture unit 1
        shadowMap.bindDepthTexture(1);
        shader.setUniform1i("shadowMap", 1);
        shader.setUniformMat4("lightSpace", shadowMap.getLightSpaceMatrix());
        shader.setUniform1f("shadowTexelSize", 1.0f / ShadowMap.SHADOW_RESOLUTION);
        // Must match the pass above, or the shader samples a stale depth map
        shader.setUniform1i("shadowsEnabled", castingShadows ? 1 : 0);
        shader.setUniform1f("daylight", dayNight.getDaylight());
        shader.setUniform1f("waterTime", (float) glfwGetTime());

        // [GR-015] Lava flows slower than water but flickers and brightens
        shader.setUniform1f("lavaTime", (float) glfwGetTime());
        
        // Wind for grass and leaves
        shader.setUniform1f("windTime", (float) glfwGetTime());
        shader.setUniform1f("windStrength", 1.0f);
        
        // Plants bend away from the player's feet, not the camera, or they
        // would flatten in front of you while you look down
        tmpTrample.set(player.getPosition());
        shader.setUniform3f("trampleOrigin", tmpTrample);
        shader.setUniform1f("trampleRadius", 1.5f);
        shader.setUniform1i("waterLayer", renderer.getTextureAtlas().getWaterLayer());

        // [GLASS] Window panes: the glass layer skips the alpha test and gets
        // a fresnel sky reflection; ice and glass share the same highlight
        shader.setUniform1i("glassLayer", renderer.getTextureAtlas().getGlassLayer());
        shader.setUniform1i("iceLayer", renderer.getTextureAtlas().getIceLayer());
        // [SHINE] Subtle specular sheen on every surface (0 = off, ~0.3 = subtle)
        shader.setUniform1f("specStrength", 0.30f);

        // [GR-015] Lava layer for shader-side animation
        shader.setUniform1i("lavaLayer", renderer.getTextureAtlas().getLavaLayer());
        // [GR-002] Chunk fade-in: chunks override this per draw; everything
        // else (TNT, item entities) stays fully opaque
        shader.setUniform1f("fadeAlpha", 1.0f);
        
        renderer.render(world, shader, camera);
        shader.unbind();

        // Render loose item entities as spinning mini-blocks
        renderer.renderItemEntities(world, shader, camera, renderer.getTextureAtlas(),
            entityLightLevel());

        // [ENCH] Experience orbs glow at full brightness
        renderer.renderXpOrbs(world, shader, camera, renderer.getTextureAtlas(), 1.0f);

        // Render primed TNT entities
        renderer.renderTntEntities(world, shader, camera, renderer.getTextureAtlas(),
            entityLightLevel());

        // [GP-022] Render tumbling sand/gravel
        renderer.renderFallingBlocks(world, shader, camera, renderer.getTextureAtlas(),
            entityLightLevel());

        // [AST] Render falling asteroids
        renderer.renderAsteroids(world, shader, camera, renderer.getTextureAtlas());

        // Render mobs (Zoloy)
        if (mobRenderer != null) {
            mobRenderer.render(world.getMobs(), camera, dayNight.getDaylight());
        }

        // Render villagers (peaceful humanoids living in villages)
        if (villagerRenderer != null) {
            villagerRenderer.render(world.getVillagers(), camera, dayNight.getDaylight());
        }

        // [GP-045] Render farm animals
        if (animalRenderer != null) {
            animalRenderer.render(world.getAnimals(), camera, dayNight.getDaylight());
        }

        // Render other players in the local co-op session
        if (remotePlayerRenderer != null && !remotePlayers.isEmpty()) {
            remotePlayerRenderer.render(remotePlayers, camera, dayNight.getDaylight());
        }

        // Third-person view: render the local player's own body (F5)
        if (playerBodyRenderer != null && viewMode != 0 && !sleeping && !waking) {
            float bodyYaw = camera.getYaw() - (viewMode == 2 ? 180 : 0);
            float headPitch = (float) java.lang.Math.toRadians(
                java.lang.Math.max(-60, java.lang.Math.min(60, camera.getPitch())));
            playerBodyRenderer.render(camera, player, bodyYaw,
                dayNight.getDaylight(), dayNight.getSunColor(),
                renderer.getTextureAtlas(),
                player.getInventory().getSelectedItem().getBlockType(),
                limbSwing, limbSwingAmount, heldItem.getAttackPhase(),
                headPitch, player.isInvincible() ? 0.55f : 0.0f);
        }

        // Clouds pass after all opaque geometry: depth is tested against the
        // terrain but never written, so the deck blends at 80% opacity
        // [SPACE] Space planets have no atmosphere — skip the cloud layer
        if (!spaceMode) {
            cloudLayer.update(deltaTime, camera.getPosition());
            cloudLayer.render(camera, dayNight.getDaylight());
        }

        // Particles sit between the world and the UI: depth-tested against
        // terrain, but not writing depth so they blend with each other
        particles.render(camera, renderer.getTextureAtlas(), dayNight.getDaylight());
        
        // View model last, so it can clear depth without erasing the world
        if (viewMode == 0 && !screens.isOpen() && !isPanoramaActive()) {
            // Ambient floor of 0.12 so the held item dims with the world
            // instead of staying readable at full brightness all night
            float light = 0.12f + 0.88f * dayNight.getDaylight();
            heldItem.render(camera, renderer.getTextureAtlas(),
                player.getInventory().getSelectedItem().getBlockType(),
                light, dayNight.getSunColor());
        }
        
        // Render block highlight
        if (targetedBlock != null) {
            renderBlockHighlight(targetedBlock);
        }

        // [GP-013] Cracks grow on the block while it is being mined
        if (breakingBlock != null && blockBreakProgress > 0 && blockBreakProgress < 1) {
            int stage = java.lang.Math.min(4, (int) (blockBreakProgress * 5));
            crackOverlay.render(breakingBlock.x, breakingBlock.y, breakingBlock.z,
                stage, camera, renderer.getTextureAtlas());
        }
        FrameTimer.add(FrameTimer.SCENE, System.nanoTime() - tScene);
        
        // [PP] Resolve the 3D frame and post-process it (bloom + FXAA),
        // restoring the default framebuffer before the 2D interface draws
        long tPost = System.nanoTime();
        postProcess.renderScene(width, height, settings.bloomEnabled, settings.fxaaEnabled);

        // [Glass] When a menu sits on top of the live world, frost the scene
        // into a blurred backdrop so UI panels can read as frosted glass
        Screen top = screens.current();
        if (screens.isOpen() && top != null && top.usesBlurredBackdrop()) {
            postProcess.blurSceneForBackdrop();
            MenuTheme.blurredBackdrop = postProcess.getBackdropTexture();
        } else {
            MenuTheme.blurredBackdrop = 0;
        }
        FrameTimer.add(FrameTimer.POST, System.nanoTime() - tPost);

        long tUi = System.nanoTime();
        renderInterface();
        FrameTimer.add(FrameTimer.UI, System.nanoTime() - tUi);
    }
    
    /**
     * Final pass: depth test off, alpha blending on, everything batched
     * through UIRenderer so no stray GL state reaches the next frame.
     */
    private void renderInterface() {
        ui.begin();
        
        // [UI-002] Damage vignette: red edges when player takes damage
        renderDamageVignette();
        
        // [BED] Sleep blackout: fullscreen fade that hides the night skip
        if (sleeping || waking) {
            int alpha = (int) (sleepFade * 255);
            if (alpha > 0) {
                ui.useSolidColor();
                ui.fillRect(0, 0, ui.getWidth(), ui.getHeight(),
                    (alpha << 24) | 0x000000);
            }
        }
        
        // HUD only while actually playing
        if (!screens.isOpen()) {
            hud.update(deltaTime, player.getInventory().getSelectedSlot(), player.getHealth());
            hud.render(ui, font, uiTextures, player.getInventory(), collectDebugInfo(), world);
        }
        
        screens.render(ui, font, uiTextures, guiMouseX(), guiMouseY());

        // Biome discovery toasts (above screens)
        toastManager.render(ui, font, uiTextures, width);

        // Chat overlay (input line + message tape)
        chat.render(ui, font, ui.getWidth(), ui.getHeight(), (float) glfwGetTime());

        ui.end();
    }
    
    // [UI-002] Damage vignette state
    private float damageVignetteIntensity = 0;
    private static final float VIGNETTE_FADE_SPEED = 2.0f; // Fade out over 500ms
    
    /**
     * [UI-002] Render a red radial vignette when the player takes damage.
     * Intensity fades out over 500ms.
     */
    private void renderDamageVignette() {
        if (player == null) return;
        
        // Detect health decrease to trigger vignette
        if (lastRenderedHealth > player.getHealth() && player.getHealth() > 0) {
            int damageTaken = lastRenderedHealth - player.getHealth();
            damageVignetteIntensity = java.lang.Math.min(1.0f, damageTaken * 0.15f); // Scale with damage
        }
        lastRenderedHealth = player.getHealth();
        
        // Fade out
        damageVignetteIntensity = java.lang.Math.max(0, damageVignetteIntensity - (float) deltaTime * VIGNETTE_FADE_SPEED);
        
        if (damageVignetteIntensity <= 0.01f) return;
        
        // Render red vignette overlay
        int w = ui.getWidth();
        int h = ui.getHeight();
        ui.useSolidColor();
        
        // Draw radial gradient approximation: concentric rectangles fading from edges to center
        int layers = 8;
        for (int i = 0; i < layers; i++) {
            float t = (float) i / layers;
            int alpha = (int) (damageVignetteIntensity * (1.0f - t) * 120);
            if (alpha <= 0) continue;
            int color = (alpha << 24) | 0x880000; // Dark red with alpha
            int inset = (int) (t * java.lang.Math.min(w, h) * 0.3f);
            ui.drawRectOutline(inset, inset, w - inset * 2, h - inset * 2, color);
        }
    }
    
    private int lastRenderedHealth = 20;
    
    private final GameHud.DebugInfo debugInfo = new GameHud.DebugInfo();
    
    private GameHud.DebugInfo collectDebugInfo() {
        Vector3f pos = player.getPosition();
        Vector3f dir = camera.getFront();
        
        debugInfo.fps = fps;
        debugInfo.frameMs = deltaTime * 1000.0;
        debugInfo.x = pos.x;
        debugInfo.y = pos.y;
        debugInfo.z = pos.z;
        debugInfo.yaw = camera.getYaw();
        debugInfo.pitch = camera.getPitch();
        debugInfo.dirX = dir.x;
        debugInfo.dirY = dir.y;
        debugInfo.dirZ = dir.z;
        debugInfo.loadedChunks = world.getChunks().size();
        debugInfo.renderedChunks = renderer.getDrawnChunks();
        debugInfo.triangles = renderer.getDrawnTriangles();
        debugInfo.renderDistance = renderDistance;
        debugInfo.health = player.getHealth();
        debugInfo.maxHealth = player.getMaxHealth();
        // [ENCH] Experience for the HUD bar
        debugInfo.xpLevel = player.getXpLevel();
        debugInfo.xpProgress = player.getXpProgress();
        debugInfo.xpToNext = player.getXpToNext();
        debugInfo.hunger = player.getHunger();
        debugInfo.maxHunger = player.getMaxHunger();
        debugInfo.air = player.getAir();
        debugInfo.maxAir = player.getMaxAir();
        debugInfo.eatProgress = player.getEatProgress();
        // [POT] Copy active effects for the HUD icons
        debugInfo.activeEffects = player.getActiveEffects();
        debugInfo.gameMode = player.getGameMode();
        debugInfo.biomeName = world.getBiomeDisplayName((int) pos.x, (int) pos.z);
        debugInfo.guiScale = ui.getScale();
        debugInfo.clock = dayNight.getClock();
        debugInfo.daylight = dayNight.getDaylight();
        debugInfo.particles = particles.getAliveCount();
        debugInfo.biomeName = world.getBiomeDisplayName((int) pos.x, (int) pos.z);
        debugInfo.culled = renderer.getCulledByFrustum();
        debugInfo.pendingChunks = world.getLoader().getPendingCount();
        debugInfo.lightBacklog = renderer.getLightBacklog();
        debugInfo.meshBacklog = renderer.getMeshBacklog();
        debugInfo.workers = world.getLoader().getWorkerCount();
        debugInfo.leafTriangles = renderer.getLeafTriangles();
        debugInfo.leafChunks = renderer.getLeafChunks();
        // [OPT] Per-pass frame timings from this frame
        debugInfo.rebuildUs = FrameTimer.us(FrameTimer.REBUILD);
        debugInfo.shadowUs = FrameTimer.us(FrameTimer.SHADOW);
        debugInfo.worldUs = FrameTimer.us(FrameTimer.WORLD);
        debugInfo.sceneUs = FrameTimer.us(FrameTimer.SCENE);
        debugInfo.postUs = FrameTimer.us(FrameTimer.POST);
        debugInfo.uiUs = FrameTimer.us(FrameTimer.UI);
        // [UI-009] Chunks still streaming in (or waiting for their mesh)
        debugInfo.loadingChunks = world.isLoadingChunks()
            || renderer.getMeshBacklog() > 0;
        debugInfo.tuningLines = handTuningMode ? heldItem.getTuning().describe() : null;
        return debugInfo;
    }
    
    private float guiMouseX() {
        glfwGetCursorPos(window, cursorX, cursorY);
        return ui.toGuiX(cursorX[0]);
    }
    
    private float guiMouseY() {
        glfwGetCursorPos(window, cursorX, cursorY);
        return ui.toGuiY(cursorY[0]);
    }
    
    private void renderBlockHighlight(Raycast.Hit hit) {
        if (hit == null || blockOutline == null) return;
        // Black outline with slight transparency, like Minecraft
        blockOutline.render(hit.x, hit.y, hit.z, camera, 0x000000AA);
    }
    
    private void mouseCallback(long win, double xpos, double ypos) {
        if (screens.isOpen()) {
            // Feed drags to sliders; hover is refreshed during render
            if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
                screens.mouseDragged(ui.toGuiX(xpos), ui.toGuiY(ypos), GLFW_MOUSE_BUTTON_LEFT);
            }
            return;
        }
        
        if (!mouseCaptured) return;
        
        if (firstMouse) {
            lastMouseX = xpos;
            lastMouseY = ypos;
            firstMouse = false;
        }
        
        float xoffset = (float) (xpos - lastMouseX);
        float yoffset = (float) (lastMouseY - ypos);
        lastMouseX = xpos;
        lastMouseY = ypos;
        
        float sensitivity = settings.mouseSensitivity;
        float invert = settings.invertY ? -1.0f : 1.0f;
        camera.rotate(xoffset * sensitivity, invert * yoffset * sensitivity);
    }
    
    // [GP-026] Melee attack state
    private float attackCooldownTimer = 0;
    private static final float ATTACK_COOLDOWN = 0.5f;
    private static final float ATTACK_REACH = 3.5f;

    /**
     * [GP-026] Swing at the closest mob on the camera ray.
     *
     * Damage comes from the held weapon (fist = 1). A critical hit lands
     * while the player is falling: 1.5x damage plus a burst of particles.
     * Swords lose durability, everything else does not.
     */
    private void attackMobs() {
        if (attackCooldownTimer > 0) return;
        if (player == null || world == null) return;

        Vector3f eye = camera.getPosition();
        Vector3f dir = camera.getFront();

        Zoloy best = null;
        float bestT = Float.MAX_VALUE;
        for (Zoloy mob : world.getMobs()) {
            Vector3f p = mob.getPosition();
            // Aim at the torso so crouching feels fair
            float mx = p.x, my = p.y + 0.9f, mz = p.z;
            float dx = mx - eye.x, dy = my - eye.y, dz = mz - eye.z;
            float t = dx * dir.x + dy * dir.y + dz * dir.z;
            if (t < 0 || t > ATTACK_REACH) continue;
            float cx = eye.x + dir.x * t - mx;
            float cy = eye.y + dir.y * t - my;
            float cz = eye.z + dir.z * t - mz;
            float perpSq = cx * cx + cy * cy + cz * cz;
            if (perpSq < 0.75f * 0.75f && t < bestT) {
                bestT = t;
                best = mob;
            }
        }

        // [0.7] Villagers are attackable too - pick whichever target is closer
        Villager bestV = null;
        float bestVT = Float.MAX_VALUE;
        for (Villager v : world.getVillagers()) {
            Vector3f p = v.getPosition();
            float mx = p.x, my = p.y + 0.9f, mz = p.z;
            float dx = mx - eye.x, dy = my - eye.y, dz = mz - eye.z;
            float t = dx * dir.x + dy * dir.y + dz * dir.z;
            if (t < 0 || t > ATTACK_REACH) continue;
            float cx = eye.x + dir.x * t - mx;
            float cy = eye.y + dir.y * t - my;
            float cz = eye.z + dir.z * t - mz;
            float perpSq = cx * cx + cy * cy + cz * cz;
            if (perpSq < 0.75f * 0.75f && t < bestVT) {
                bestVT = t;
                bestV = v;
            }
        }

        // [GP-045] Farm animals are hit too, aiming at the body
        Animal bestA = null;
        float bestAT = Float.MAX_VALUE;
        for (Animal a : world.getAnimals()) {
            Vector3f p = a.getPosition();
            float mx = p.x, my = p.y + 0.7f, mz = p.z;
            float dx = mx - eye.x, dy = my - eye.y, dz = mz - eye.z;
            float t = dx * dir.x + dy * dir.y + dz * dir.z;
            if (t < 0 || t > ATTACK_REACH) continue;
            float cx = eye.x + dir.x * t - mx;
            float cy = eye.y + dir.y * t - my;
            float cz = eye.z + dir.z * t - mz;
            float perpSq = cx * cx + cy * cy + cz * cz;
            if (perpSq < 0.75f * 0.75f && t < bestAT) {
                bestAT = t;
                bestA = a;
            }
        }

        // Whoever is closest wins the swing
        Animal hitAnimal = null;
        Villager hitVillager = null;
        Zoloy hitMob = null;
        if (bestA != null && (bestV == null || bestAT < bestVT)
                && (best == null || bestAT < bestT)) {
            hitAnimal = bestA;
        } else if (bestV != null && (best == null || bestVT < bestT)) {
            hitVillager = bestV;
        } else {
            hitMob = best;
        }
        if (hitMob == null && hitVillager == null && hitAnimal == null) return;

        attackCooldownTimer = ATTACK_COOLDOWN;

        ItemStack held = player.getInventory().getSelectedItem();
        float dmg = 1.0f;
        boolean sword = false;
        if (held.isItem() && held.getItem() != null) {
            dmg = held.getItem().getAttackDamage();
            sword = held.getItem().toolType == ToolType.SWORD;
            // [ENCH] Sharpness adds +0.5 damage per level on swords
            dmg += 0.5f * held.getEnchantLevel(com.voxelgame.item.Enchantment.SHARPNESS);
        }

        // Critical hit: strike while falling
        Vector3f vel = player.getVelocity();
        boolean crit = !player.isOnGround() && vel.y < -0.5f;
        if (crit) dmg *= 1.5f;

        // [POT] Strength effect boosts melee damage (+50% per level)
        dmg *= player.getStrengthMultiplier();

        if (hitVillager != null) {
            hitVillager.takeDamage(dmg);
            if (hitVillager.isDead()) {
                AchievementRegistry.trigger("kill_mob");
                // [ENCH] Experience for the kill
                Vector3f vp = hitVillager.getPosition();
                world.spawnXpOrb(vp.x, vp.y + 0.6f, vp.z, 3);
            }
            if (crit) {
                Vector3f bp = hitVillager.getPosition();
                particles.emitBlockBreak((int) bp.x, (int) bp.y, (int) bp.z,
                    BlockType.COBBLESTONE, renderer.getTextureAtlas());
            }
        } else if (hitAnimal != null) {
            Vector3f from = new Vector3f(hitAnimal.getPosition()).sub(player.getPosition());
            hitAnimal.takeDamage(dmg, from);
            if (hitAnimal.isDead()) {
                AchievementRegistry.trigger("kill_mob");
                // [ENCH] Small experience reward for the kill
                Vector3f ap = hitAnimal.getPosition();
                world.spawnXpOrb(ap.x, ap.y + 0.5f, ap.z, 2);
            }
            if (crit) {
                Vector3f bp = hitAnimal.getPosition();
                particles.emitBlockBreak((int) bp.x, (int) bp.y, (int) bp.z,
                    BlockType.COBBLESTONE, renderer.getTextureAtlas());
            }
        } else {
            Vector3f from = new Vector3f(hitMob.getPosition()).sub(player.getPosition());
            hitMob.takeDamage(dmg, from);

            // [GP-026] First kill unlocks the achievement
            if (hitMob.isDead()) {
                AchievementRegistry.trigger("kill_mob");
                // [ENCH] Experience for the kill
                Vector3f mp = hitMob.getPosition();
                world.spawnXpOrb(mp.x, mp.y + 0.6f, mp.z, 5);
            }

            // [GP-026] Particles burst on a critical hit
            if (crit) {
                Vector3f bp = hitMob.getPosition();
                particles.emitBlockBreak((int) bp.x, (int) bp.y, (int) bp.z,
                    BlockType.COBBLESTONE, renderer.getTextureAtlas());
            }
        }

        if (sword) {
            player.damageHeldTool(1);
        }
    }

    /** [GP-045] Nearest farm animal under the cursor, within reach. */
    private Animal pickAnimal(Vector3f eye, Vector3f dir, float reach) {
        Animal best = null;
        float bestT = Float.MAX_VALUE;
        for (Animal a : world.getAnimals()) {
            Vector3f p = a.getPosition();
            float mx = p.x, my = p.y + 0.7f, mz = p.z;
            float dx = mx - eye.x, dy = my - eye.y, dz = mz - eye.z;
            float t = dx * dir.x + dy * dir.y + dz * dir.z;
            if (t < 0 || t > reach) continue;
            float cx = eye.x + dir.x * t - mx;
            float cy = eye.y + dir.y * t - my;
            float cz = eye.z + dir.z * t - mz;
            float perpSq = cx * cx + cy * cy + cz * cz;
            if (perpSq < 0.6f * 0.6f && t < bestT) {
                bestT = t;
                best = a;
            }
        }
        return best;
    }
    
    private void mouseButtonCallback(long win, int button, int action, int mods) {
        // Screens get first refusal on the mouse
        if (screens.isOpen()) {
            float mx = guiMouseX();
            float my = guiMouseY();
            if (action == GLFW_PRESS) {
                screens.mouseClicked(mx, my, button);
            } else if (action == GLFW_RELEASE) {
                screens.mouseReleased(mx, my, button);
            }
            return;
        }
        
        if (action == GLFW_PRESS && mouseCaptured) {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                blockBreakProgress = 0;
                breakingBlock = null;
                heldItem.startSwing();
                // [GP-026] Left click is an attack first, mining second
                attackMobs();
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                // Check if targeting a container block or TNT
                if (targetedBlock != null) {
                    BlockType targeted = BlockType.fromId(
                        world.getBlock(targetedBlock.x, targetedBlock.y, targetedBlock.z));
                    // [BED] Right-click a bed to sleep through the night
                    if (targeted == BlockType.BED || targeted == BlockType.BED_HEAD) {
                        trySleep(targetedBlock.x, targetedBlock.y, targetedBlock.z);
                        return;
                    }
                    // [GP-020] Interactive blocks: door toggles open/closed
                    if (DoorBlock.isDoor(targeted)) {
                        DoorBlock.INSTANCE.onInteract(world,
                            targetedBlock.x, targetedBlock.y, targetedBlock.z);
                        AudioManager.play("sounds/steps/wood", 1.0f, 0.5f);
                        return;
                    }
                    // [SD] Sliding door: click either half toggles it
                    if (targeted == BlockType.SLIDING_DOOR) {
                        world.toggleSlidingDoor(
                            targetedBlock.x, targetedBlock.y, targetedBlock.z);
                        return;
                    }
if (targeted == BlockType.CHEST) {
                        openChest(targetedBlock.placeX, targetedBlock.placeY, targetedBlock.placeZ);
                        return;
                    } else if (targeted == BlockType.CRATE) {
                        // [BASE] A crate is a chest in disguise: right-click opens it
                        openChest(targetedBlock.x, targetedBlock.y, targetedBlock.z);
                        return;
                    } else if (targeted == BlockType.FURNACE) {
                        openFurnace(targetedBlock.placeX, targetedBlock.placeY, targetedBlock.placeZ);
                        return;
                    } else if (targeted == BlockType.CRAFTING_TABLE) {
                        openCrafting();
                        return;
                    } else if (targeted == BlockType.ENCHANTING_TABLE) {
                        // [ENCH] The runes hum as the table opens
                        AudioManager.play("sounds/steps/stone", 1.0f, 0.4f);
                        openEnchanting();
                        return;
                    } else if (targeted == BlockType.TNT) {
                        // Prime the TNT: remove the block and spawn a ticking entity
                        world.getTntEntities().add(new TntEntity(world,
                            targetedBlock.x + 0.5f,
                            targetedBlock.y,
                            targetedBlock.z + 0.5f));
                        localSetBlock(targetedBlock.x, targetedBlock.y, targetedBlock.z, (byte) 0);
                        AudioManager.play("sounds/fuse", 1.0f, 0.5f);
                        return;
                    } else if (targeted == BlockType.ROCKET_LAUNCH_PAD) {
                        // [SPACE] Launch the rocket built above, or return home
                        handleLaunchPad(targetedBlock.x, targetedBlock.y, targetedBlock.z);
                        return;
                    }
                }

                // [0.6] Flint and steel sets fire to the cell in front of the hit face
                ItemStack heldFs = player.getInventory().getSelectedItem();
                if (!player.isCreative() && heldFs.isItem()
                        && heldFs.getItem() == ItemRegistry.FLINT_AND_STEEL
                        && targetedBlock != null) {
                    int ix = targetedBlock.placeX;
                    int iy = targetedBlock.placeY;
                    int iz = targetedBlock.placeZ;
                    world.igniteFire(ix, iy, iz);
                    boolean ignited = world.getBlock(ix, iy, iz) == BlockType.FIRE.id;
                    if (!ignited && world.getBlock(ix, iy, iz) == BlockType.TNT.id) {
                        world.getTntEntities().add(new TntEntity(world,
                            ix + 0.5f, iy, iz + 0.5f));
                        localSetBlock(ix, iy, iz, (byte) 0);
                        ignited = true;
                    }
                    if (ignited) {
                        localSetBlock(ix, iy, iz, (byte) BlockType.FIRE.id);
                        heldFs.damage(1);
                        AudioManager.play("sounds/fuse", 1.0f, 0.6f);
                        return;
                    }
                }

// [ANM] Animal care and breeding
                ItemStack heldCare = player.getInventory().getSelectedItem();
                Animal careTarget = pickAnimal(camera.getPosition(), camera.getFront(), 3.5f);
                if (careTarget != null) {
                    // Sheep shearing: wool now, more later
                    if (heldCare.isItem() && heldCare.getItem() == ItemRegistry.SHEARS
                            && careTarget.getType() == Animal.AnimalType.SHEEP
                            && !careTarget.isSheared()) {
                        careTarget.shear();
                        Vector3f sap = careTarget.getPosition();
                        world.spawnDrop(sap, BlockType.WHITE_WOOL,
                            1 + (int) (java.lang.Math.random() * 3));
                        AudioManager.play("sounds/animal/sheep", 1.0f, 0.4f);
                        return;
                    }
                    // Cow milking: bucket turns into milk
                    if (heldCare.isItem() && heldCare.getItem() == ItemRegistry.BUCKET
                            && careTarget.getType() == Animal.AnimalType.COW) {
                        if (!player.isCreative()) {
                            heldCare.decrement(1);
                        }
                        if (!player.getInventory().addItem(ItemRegistry.MILK, 1)) {
                            world.spawnDrop(careTarget.getPosition(), BlockType.ITEM_MILK, 1);
                        }
                        AudioManager.play("sounds/animal/cow", 1.0f, 0.5f);
                        return;
                    }
                    // Parrot taming: wheat wins a friend
                    if (careTarget.getType() == Animal.AnimalType.PARROT
                            && !careTarget.isTamed()
                            && careTarget.tryTame(heldCare)) {
                        if (!player.isCreative()) {
                            player.getInventory().removeSelectedItem();
                        }
                        return;
                    }
                    // Feeding: the right food grows a baby or breeds a pair
                    if (Animal.isFoodFor(careTarget.getType(), heldCare)) {
                        boolean used = false;
                        if (careTarget.isBaby()) {
                            used = careTarget.feedBaby();
                        } else if (careTarget.tryBreed(world, player)) {
                            used = true;
                            Vector3f bp = careTarget.getPosition();
                            particles.emitHearts(bp.x, bp.y + 0.8f, bp.z);
                        }
                        if (used) {
                            if (!player.isCreative()) {
                                player.getInventory().removeSelectedItem();
                            }
                            return;
                        }
                    }
                }

                // [POT] Right-click a potion: drink it instantly and apply its effect
                ItemStack heldStack = player.getInventory().getSelectedItem();
                if (heldStack.isItem() && heldStack.getItem() != null
                        && heldStack.getItem().isPotion()) {
                    com.voxelgame.item.Item potion = heldStack.getItem();
                    player.applyPotion(potion.potionEffect, 1, potion.potionEffect.baseDurationTicks);
                    if (!player.isCreative()) {
                        player.getInventory().removeSelectedItem();
                    }
                    return;
                }

                // Survival + holding food = start eating (any food item or the apple block)
                boolean isFood = heldStack.isItem() && heldStack.getItem() != null
                    && heldStack.getItem().isFood();
                if (heldStack.isBlock() && heldStack.getBlockType() == BlockType.APPLE) {
                    isFood = true;
                }
                if (!player.isCreative() && isFood) {
                    player.startEating();
                } else {
                    heldItem.startPlace();
                    // Place block with collision check [GP-012]
                    if (targetedBlock != null) {
                        int blockId = player.getInventory().getSelectedBlockId();
                        if (blockId != 0) {
                            // [BED] Beds place as a two-block pair: the foot
                            // lands in the placement cell, the head in the
                            // cell the player is facing
                            if (blockId == BlockType.BED.id) {
                                System.out.println("[Bed] picked BED, targeted="
                                    + targetedBlock.x + "," + targetedBlock.y + ","
                                    + targetedBlock.z + " place=" + targetedBlock.placeX
                                    + "," + targetedBlock.placeY + "," + targetedBlock.placeZ);
                                if (placeBed(targetedBlock.placeX, targetedBlock.placeY,
                                        targetedBlock.placeZ)) {
                                    playPlaceSound(BlockType.BED);
                                    AchievementRegistry.trigger("place_block");
                                    if (!player.isCreative()) {
                                        player.getInventory().removeSelectedItem();
                                    }
                                }
                                return;
                            }
                            // [MC] Doors occupy two cells: the placement cell
                            // takes the lower half, the cell above the upper
                            if (blockId == BlockType.OAK_DOOR.id) {
                                int lx = targetedBlock.placeX;
                                int ly = targetedBlock.placeY;
                                int lz = targetedBlock.placeZ;
                                int above = world.getBlock(lx, ly + 1, lz);
                                if (above != 0) return; // needs a free upper cell
                                if (world.placeBlock(camera.getPosition(), camera.getFront(),
                                        blockId, player.getPosition(), 0.6f, 1.8f)) {
                                    world.setBlock(lx, ly + 1, lz, BlockType.OAK_DOOR.id);
                                    playPlaceSound(BlockType.OAK_DOOR);
                                    AchievementRegistry.trigger("place_block");
                                    if (!player.isCreative()) {
                                        player.getInventory().removeSelectedItem();
                                    }
                                }
                                return;
                            }
                            boolean placed = world.placeBlock(
                                camera.getPosition(), camera.getFront(), blockId,
                                player.getPosition(), 0.6f, 1.8f
                            );
                            if (placed) {
                                playPlaceSound(BlockType.fromId(blockId));
                                // Trigger building achievement
                                AchievementRegistry.trigger("place_block");
                                // Only survival spends the item
                                if (!player.isCreative()) {
                                    player.getInventory().removeSelectedItem();
                                }
                                // Broadcast the placed block so other players see it
                                if (netClient != null && netClient.isConnected()
                                        && world.getLastPlacedX() != Integer.MIN_VALUE) {
netClient.sendBlockChange(world.getLastPlacedX(),
                                        world.getLastPlacedY(), world.getLastPlacedZ(), (byte) blockId);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (action == GLFW_RELEASE && button == GLFW_MOUSE_BUTTON_RIGHT) {
            player.stopEating();
        }
    }
    
    private void scrollCallback(long win, double xoffset, double yoffset) {
        if (screens.isOpen()) {
            screens.mouseScrolled(guiMouseX(), guiMouseY(), yoffset);
            return;
        }
        // Wheel over the corner map zooms it instead of scrolling the hotbar
        if (hud.isMinimapVisible() && hud.minimapHover(guiMouseX(), guiMouseY())) {
            hud.minimapZoom(yoffset > 0 ? -1 : 1);
            return;
        }
        player.getInventory().scrollSlot(yoffset > 0 ? -1 : 1);
        heldItem.startEquip();
    }
    
    private void keyCallback(long win, int key, int scancode, int action, int mods) {
        // Track modifiers for the inventory screens before anything else
        if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) {
            shiftKeyDown = action != GLFW_RELEASE;
        }
        if (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL) {
            ctrlKeyDown = action != GLFW_RELEASE;
        }

        // --- Chat input takes priority when open ---
        if (chat.isOpen()) {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                switch (key) {
                    case GLFW_KEY_ENTER:
                        boolean stayOpen = chat.send();
                        if (!stayOpen) {
                            chat.close();
                            applyCursorMode();
                        }
                        return;
                    case GLFW_KEY_ESCAPE:
                        chat.close();
                        applyCursorMode();
                        return;
                    case GLFW_KEY_BACKSPACE:
                        chat.backspace();
                        return;
                    case GLFW_KEY_DELETE:
                        chat.delete();
                        return;
                    case GLFW_KEY_LEFT:
                        chat.moveCursorLeft();
                        return;
                    case GLFW_KEY_RIGHT:
                        chat.moveCursorRight();
                        return;
                    case GLFW_KEY_HOME:
                        chat.moveCursorHome();
                        return;
                    case GLFW_KEY_END:
                        chat.moveCursorEnd();
                        return;
                    case GLFW_KEY_UP:
                        chat.historyNavigate(-1);
                        return;
                    case GLFW_KEY_DOWN:
                        chat.historyNavigate(1);
                        return;
                    case GLFW_KEY_V:
                        if ((mods & GLFW_MOD_CONTROL) != 0) {
                            chat.paste(glfwGetClipboardString(window));
                        }
                        return;
                }
            }
            return; // Don't process gameplay keys while chat is open
        }

        if (action != GLFW_PRESS) return;

        // Open chat with T (empty) or / (with / prefix)
        if (key == GLFW_KEY_T) {
            chat.open(null);
            applyCursorMode();
            return;
        }
        if (key == GLFW_KEY_SLASH) {
            chat.open("/");
            applyCursorMode();
            return;
        }

        // Let the open screen consume the key first
        if (screens.isOpen()) {
            if (screens.keyPressed(key, mods)) return;

            Screen top = screens.current();

            // E closes any container/inventory screen, mirroring the key
            // that opened it
            if (key == keyBindings.get(KeyBindings.Action.INVENTORY)
                && (top instanceof CreativeInventoryScreen
                    || top instanceof SurvivalInventoryScreen
                    || top instanceof CraftingScreen
                    || top instanceof ChestScreen
                    || top instanceof FurnaceScreen)) {
                closeScreens();
                return;
            }
            
            // F3 still toggles the debug panel from anywhere
            if (key == GLFW_KEY_F3) {
                hud.toggleDebug();
                return;
            }

            // F9 toggles ambient occlusion (debug)
            if (key == GLFW_KEY_F9) {
                boolean ao = !ChunkMeshBuilder.isAmbientOcclusionEnabled();
                ChunkMeshBuilder.setAmbientOcclusionEnabled(ao);
                // Mark all chunks dirty so they rebuild with the new AO state
                if (world != null) {
                    for (var c : world.getChunks().values()) c.setDirty(true);
                }
                System.out.println("AO: " + (ao ? "on" : "off"));
                return;
            }
            
            if (key == GLFW_KEY_ESCAPE && top != null && top.closableWithEscape()) {
                // Closing the last screen returns to gameplay
                screens.pop();
                if (!screens.isOpen() && top instanceof OptionsScreen) {
                    // Options opened straight from the title: fall back to it
                    openMainMenu();
                } else {
                    applyCursorMode();
                }
            }
            return;
        }
        
        // While tuning, the arrow cluster drives the view model instead of
        // falling through to the normal bindings
        if (handTuningMode && handleHandTuningKey(key, mods)) return;
        
        // Rebindable actions are checked before the fixed function keys
        if (key == keyBindings.get(KeyBindings.Action.INVENTORY)) {
            openCreativeInventory();
            return;
        }
        
        {
            switch (key) {
                case GLFW_KEY_ESCAPE:
                    openPauseMenu();
                    break;

                case GLFW_KEY_F2:
                    takeScreenshot();
                    break;
                case GLFW_KEY_F3:
                    hud.toggleDebug();
                    break;

                case GLFW_KEY_M:
                    hud.toggleMinimap();
                    break;
                
                case GLFW_KEY_SPACE:
                    // Double-space toggles creative flight
                    if (player.isCreative()) {
                        double now = glfwGetTime();
                        if (now - lastSpacePress < 0.3) {
                            player.toggleFlying();
                        }
                        lastSpacePress = now;
                    }
                    break;
                case GLFW_KEY_F4:
                    // Cycle game mode for testing (no menu, no HUD label)
                    cycleGameMode();
                    break;
                case GLFW_KEY_F5:
                    // Cycle first person в†’ behind в†’ front, like vanilla
                    viewMode = (viewMode + 1) % 3;
                    System.out.println("View mode: " + (viewMode == 0 ? "first person"
                        : viewMode == 1 ? "third person (behind)" : "third person (front)"));
                    break;
                case GLFW_KEY_F7:
                    cullingEnabled = !cullingEnabled;
                    renderer.setCullingEnabled(cullingEnabled);
                    System.out.println("Back-face culling: " + (cullingEnabled ? "on" : "off"));
                    break;
                case GLFW_KEY_F6:
                    handTuningMode = !handTuningMode;
                    if (!handTuningMode) {
                        heldItem.getTuning().save(settings);
                        System.out.println("Hand tuning saved: "
                            + heldItem.getTuning().toCode());
                    } else {
                        System.out.println("Hand tuning: on");
                    }
                    break;
                case GLFW_KEY_1:
                case GLFW_KEY_2:
                case GLFW_KEY_3:
                case GLFW_KEY_4:
                case GLFW_KEY_5:
                case GLFW_KEY_6:
                case GLFW_KEY_7:
                case GLFW_KEY_8:
                case GLFW_KEY_9:
                    player.getInventory().setSelectedSlot(key - GLFW_KEY_1);
                    heldItem.startEquip();
                    break;
            }
        }
    }

    // --- Screenshot (F2) ---
    private int screenshotCounter = 0;

    private void takeScreenshot() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String filename = String.format("screenshot_%04d-%02d-%02d_%02d-%02d-%02d.png",
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
            now.getHour(), now.getMinute(), now.getSecond());
        takeScreenshot(filename);
    }

    private void takeScreenshot(String filename) {
        try {
            // Read pixels from the framebuffer
            int w = width;
            int h = height;
            int channels = 3; // RGB
            int stride = w * channels;

            // Allocate buffer for pixel data
            java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(w * h * channels);
            glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, pixels);

            // Flip vertically (OpenGL has origin at bottom-left, PNG at top-left)
            java.nio.ByteBuffer flipped = org.lwjgl.system.MemoryUtil.memAlloc(w * h * channels);
            for (int y = 0; y < h; y++) {
                int srcOffset = y * stride;
                int dstOffset = (h - 1 - y) * stride;
                for (int i = 0; i < stride; i++) {
                    flipped.put(dstOffset + i, pixels.get(srcOffset + i));
                }
            }

            // Write PNG using STB
            boolean success = org.lwjgl.stb.STBImageWrite.stbi_write_png(
                filename, w, h, channels, flipped, stride);

            org.lwjgl.system.MemoryUtil.memFree(pixels);
            org.lwjgl.system.MemoryUtil.memFree(flipped);

            if (success) {
                System.out.println("Screenshot saved: " + filename);
                chat.addMessage("Р РЋР С”РЎР‚Р С‘Р Р…РЎв‚¬Р С•РЎвЂљ РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎвЂР Р…: " + filename, 0xFF55FF55);
            } else {
                System.err.println("Failed to save screenshot");
                chat.addMessage("Р С›РЎв‚¬Р С‘Р В±Р С”Р В° РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…Р ВµР Р…Р С‘РЎРЏ РЎРѓР С”РЎР‚Р С‘Р Р…РЎв‚¬Р С•РЎвЂљР В°", 0xFFFF5555);
            }
} catch (Exception e) {
            System.err.println("Screenshot error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ------------------------------------------------------------------
    // Debug command console (debug_commands.txt)
    // ------------------------------------------------------------------

    /** Executes one debug command line; only called from the main loop thread. */
    private void executeDebugCommand(String[] t, java.util.function.Consumer<String> out) {
        String cmd = t[0].toLowerCase(java.util.Locale.ROOT);
        switch (cmd) {
            case "help" -> out.accept(
                "commands: help | status | screenshot [file] | tp x y z | look yaw pitch"
                + " | time <0-1|day|noon|night> | gamemode <survival|creative|hardcore> | fly | nofly"
                + " | set_block x y z <name|id> | get_block x y z | give <block|item> [count]"
                + " | create_world <name> <seed> [creative|survival] | load_world <folder>"
                + " | save | weather <clear|rain|thunder|snow> | spawn_mob x y z | kill_mobs"
                + " | village | quit");

            case "status" -> {
                requirePlayer(out);
                Vector3f p = player.getPosition();
                out.accept(String.format("pos: %.2f %.2f %.2f", p.x, p.y, p.z));
                out.accept(String.format("view: yaw %.1f pitch %.1f",
                    camera.getYaw(), camera.getPitch()));
                out.accept(String.format("fps: %.0f | chunks: %d | health: %d | mode: %s",
                    fps, world.getChunks().size(), player.getHealth(), player.getGameMode()));
                out.accept("screen: " + (screens.isOpen()
                    ? screens.current().getClass().getSimpleName() : "playing"));
                out.accept("biome: " + world.getBiomeDisplayName((int) p.x, (int) p.z));
                out.accept("world: " + (currentSave != null ? currentSave.getMeta().displayName : "(none)")
                    + " | seed: " + world.getSeed());
            }

            case "screenshot" -> takeScreenshot(t.length > 1 ? t[1] : "debug_shot.png");

            case "tp" -> {
                requirePlayer(out);
                float x = num(t, 1), y = num(t, 2), z = num(t, 3);
                player.getPosition().set(x, y, z);
                camera.setPosition(new Vector3f(x, y + eyeHeight, z));
                player.setVerticalVelocity(0);
                player.setHorizontalVelocity(0, 0);
            }

            case "village" -> {
                requirePlayer(out);
                requireWorld(out);
                float[] v = world.findNearestVillage(player.getPosition().x, player.getPosition().z);
                if (v == null) {
                    out.accept("no village nearby");
                    break;
                }
                player.getPosition().set(v[0], v[1], v[2]);
                camera.setPosition(new Vector3f(v[0], v[1] + eyeHeight, v[2]));
                player.setVerticalVelocity(0);
                player.setHorizontalVelocity(0, 0);
                out.accept(String.format("teleported to village: %.1f %.1f %.1f", v[0], v[1], v[2]));
            }

            case "look" -> camera.setOrientation(num(t, 1), num(t, 2));

            case "time" -> {
                double tt;
                switch (arg(t, 1).toLowerCase(java.util.Locale.ROOT)) {
                    case "day" -> tt = 0.25;
                    case "noon" -> tt = 0.5;
                    case "night" -> tt = 0.75;
                    default -> tt = Double.parseDouble(arg(t, 1));
                }
                dayNight.setTime(tt);
                out.accept("time set to " + tt);
            }

            case "gamemode" -> {
                requirePlayer(out);
                WorldMeta.GameMode mode = switch (arg(t, 1).toLowerCase(java.util.Locale.ROOT)) {
                    case "survival" -> WorldMeta.GameMode.SURVIVAL;
                    case "creative" -> WorldMeta.GameMode.CREATIVE;
                    case "hardcore" -> WorldMeta.GameMode.HARDCORE;
                    default -> throw new IllegalArgumentException("unknown mode: " + arg(t, 1));
                };
                player.setGameMode(mode);
                if (mode == WorldMeta.GameMode.CREATIVE && !player.isFlying()) player.toggleFlying();
                if (mode != WorldMeta.GameMode.CREATIVE && player.isFlying()) player.toggleFlying();
                out.accept("gamemode: " + mode);
            }

            case "fly" -> { requirePlayer(out); if (!player.isFlying()) player.toggleFlying(); }
            case "nofly" -> { requirePlayer(out); if (player.isFlying()) player.toggleFlying(); }

            case "set_block" -> {
                requireWorld(out);
                BlockType bt = resolveBlock(arg(t, 4));
                int x = numI(t, 1), y = numI(t, 2), z = numI(t, 3);
                world.setBlock(x, y, z, bt.id);
                out.accept("set " + bt.name + " at " + x + " " + y + " " + z);
            }

            case "get_block" -> {
                requireWorld(out);
                int x = numI(t, 1), y = numI(t, 2), z = numI(t, 3);
                out.accept(x + " " + y + " " + z + " = "
                    + BlockType.fromId(world.getBlock(x, y, z)).name);
            }

            case "door_toggle" -> {
                requireWorld(out);
                int x = numI(t, 1), y = numI(t, 2), z = numI(t, 3);
                out.accept(world.toggleSlidingDoor(x, y, z)
                    ? "door toggled at " + x + " " + y + " " + z
                    : "no sliding door at " + x + " " + y + " " + z);
            }

            case "door_aabb" -> {
                requireWorld(out);
                int x = numI(t, 1), y = numI(t, 2), z = numI(t, 3);
                float[] aabb = new float[6];
                float[] r = world.getBlockAabb(x, y, z, aabb);
                if (r == null) {
                    out.accept("no collision at " + x + " " + y + " " + z);
                } else {
                    out.accept("aabb minY=" + r[1] + " maxY=" + r[4]
                        + " height=" + (r[4] - r[1])
                        + " (" + r[0] + "," + r[1] + "," + r[2]
                        + ")..(" + r[3] + "," + r[4] + "," + r[5] + ")");
                }
            }

            case "door_list" -> {
                requireWorld(out);
                java.util.Collection<SlidingDoor> dl = world.getSlidingDoors();
                if (dl.isEmpty()) {
                    out.accept("no sliding doors loaded");
                    return;
                }
                float[] aabb = new float[6];
                for (SlidingDoor d : dl) {
                    float[] r = world.getBlockAabb(d.x, d.y, d.z, aabb);
                    out.accept("door " + d.x + "," + d.y + "," + d.z
                        + " axis=" + d.axis + " sign=" + d.slideSign
                        + " state=" + d.getState() + " anim=" + d.slideAmount()
                        + " bottom=" + BlockType.fromId(world.getBlock(d.x, d.y, d.z)).name
                        + " top=" + BlockType.fromId(world.getBlock(d.x, d.y + 1, d.z)).name
                        + " aabb=" + (r == null ? "none(passable)"
                            : ("minY=" + r[1] + " maxY=" + r[4] + " h=" + (r[4] - r[1]))));
                }
            }

            case "door_scan" -> {
                requireWorld(out);
                float px = player.getPosition().x;
                float py = player.getPosition().y;
                float pz = player.getPosition().z;
                int found = 0;
                for (int dx = -24; dx <= 24; dx++) {
                    for (int dz = -24; dz <= 24; dz++) {
                        int wx = (int) java.lang.Math.floor(px) + dx;
                        int wz = (int) java.lang.Math.floor(pz) + dz;
                        for (int wy = (int) java.lang.Math.max(0, py - 16); wy <= py + 20; wy++) {
                            if (world.getBlock(wx, wy, wz) == BlockType.SLIDING_DOOR.id) {
                                found++;
                                if (found <= 12) {
                                    out.accept("sd block at " + wx + "," + wy + "," + wz
                                        + " top=" + BlockType.fromId(world.getBlock(wx, wy + 1, wz)).name);
                                }
                            }
                        }
                    }
                }
                out.accept("sliding_door blocks found: " + found
                    + ", registry: " + world.getSlidingDoors().size());
            }

            case "give" -> {
                requirePlayer(out);
                int count = t.length > 2 ? Integer.parseInt(t[2]) : 64;
                BlockType bt = BlockType.fromName(arg(t, 1));
                if (bt != null) {
                    player.getInventory().setHotbarItem(0, bt, count);
                    out.accept("gave " + count + "x " + bt.name);
                    return;
                }
                int id;
                try { id = Integer.parseInt(arg(t, 1)); }
                catch (NumberFormatException e) {
                    throw new IllegalArgumentException("unknown block/item: " + arg(t, 1));
                }
                Item it = ItemRegistry.getById(id);
                if (it == null) throw new IllegalArgumentException("unknown item id: " + id);
                player.getInventory().setHotbarItem(0, it, count);
                out.accept("gave " + count + "x " + arg(t, 1));
            }

            case "create_world" -> {
                String name = arg(t, 1);
                long seed = Long.parseLong(arg(t, 2));
                WorldMeta.GameMode mode = t.length > 3 && t[3].equalsIgnoreCase("survival")
                    ? WorldMeta.GameMode.SURVIVAL
                    : WorldMeta.GameMode.CREATIVE;
                WorldMeta meta = new WorldMeta(name, seed, mode, true);
                activateWorld(meta, true);
                configureDimension(meta);
                out.accept("created world '" + name + "' seed=" + seed + " mode=" + mode
                    + " spawn=" + player.getPosition());
            }

            case "space_world" -> {
                // [SPACE] Debug: jump straight to a space dimension planet
                requireWorld(out);
                WorldMeta cur = currentSave.getMeta();
                String spaceFolder = cur.folderName + "_space";
                boolean fresh = !WorldSave.exists(spaceFolder);
                WorldMeta spaceMeta = fresh
                    ? new WorldMeta(cur.displayName + " Космос",
                        cur.seed ^ 0x5DEECE66DL, cur.gameMode, cur.generateStructures)
                    : WorldSave.readWorld(spaceFolder);
                if (fresh) {
                    spaceMeta.folderName = spaceFolder;
                    spaceMeta.dimension = "space";
                    spaceMeta.homeWorld = cur.folderName;
                    spaceMeta.dayTime = 0.35;
                }
                activateWorld(spaceMeta, fresh);
                configureDimension(spaceMeta);
                if (fresh) {
                    int sx = (int) spaceMeta.spawnX;
                    int sz = (int) spaceMeta.spawnZ;
                    int g = java.lang.Math.max(world.getGroundHeight(sx, sz), 62);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            world.setBlock(sx + dx, g, sz + dz, BlockType.SANDSTONE_BRICKS.id);
                        }
                    }
                    world.setBlock(sx, g + 1, sz, BlockType.ROCKET_LAUNCH_PAD.id);
                    float px = sx + 0.5f, py = g + 2.5f, pz = sz + 0.5f;
                    player.getPosition().set(px, py, pz);
                    camera.setPosition(new Vector3f(px, py + eyeHeight, pz));
                    currentSave.saveMeta();
                }
                out.accept("teleported to space planet '" + spaceFolder + "'");
            }

            case "load_world" -> {
                WorldMeta meta = null;
                for (WorldMeta m : WorldSave.listWorlds()) {
                    if (m.folderName.equals(arg(t, 1)) || m.displayName.equals(arg(t, 1))) {
                        meta = m;
                        break;
                    }
                }
                if (meta == null) throw new IllegalArgumentException("world not found: " + arg(t, 1));
                activateWorld(meta, false);
                configureDimension(meta);
                out.accept("loaded world '" + meta.displayName + "'");
            }

            case "save" -> { requireWorld(out); saveWorldSync(); out.accept("world saved"); }

            case "weather" -> {
                currentWeather = arg(t, 1).toLowerCase(java.util.Locale.ROOT);
                out.accept("weather: " + currentWeather);
            }

            case "spawn_mob" -> {
                requireWorld(out);
                float mx = num(t, 1), my = num(t, 2), mz = num(t, 3);
                world.getMobs().add(new Zoloy(world, mx, my, mz));
                out.accept("spawned zoloy at " + mx + " " + my + " " + mz);
            }

            case "kill_mobs" -> {
                requireWorld(out);
                int n = world.getMobs().size();
                world.getMobs().clear();
                out.accept("removed " + n + " mobs");
            }

            case "ui" -> {
                // Debug helper: open a container screen for visual checks
                String which = t.length > 1 ? t[1].toLowerCase(java.util.Locale.ROOT) : "inventory";
                switch (which) {
                    case "inventory", "survival" -> openSurvivalInventory();
                    case "creative" -> openCreativeInventory();
                    case "crafting" -> openCrafting();
                    case "furnace" -> openFurnace(
                        (int) java.lang.Math.floor(player.getPosition().x),
                        (int) java.lang.Math.floor(player.getPosition().y),
                        (int) java.lang.Math.floor(player.getPosition().z));
                    case "chest" -> openChest(
                        (int) java.lang.Math.floor(player.getPosition().x),
                        (int) java.lang.Math.floor(player.getPosition().y),
                        (int) java.lang.Math.floor(player.getPosition().z));
                    case "enchanting" -> openEnchanting();
                    default -> { out.accept("unknown screen: " + which); return; }
                }
                applyCursorMode();
                out.accept("opened ui: " + which);
            }

            case "quit" -> glfwSetWindowShouldClose(window, true);

            default -> throw new IllegalArgumentException("unknown command: " + t[0]);
        }
    }

    private void requireWorld(java.util.function.Consumer<String> out) {
        if (world == null) throw new IllegalStateException("no world available");
    }

    private void requirePlayer(java.util.function.Consumer<String> out) {
        if (player == null || world == null) {
            throw new IllegalStateException("no world spawned yet - use create_world first");
        }
    }

    private static String arg(String[] t, int i) {
        if (t.length <= i) throw new IllegalArgumentException("missing argument #" + (i + 1));
        return t[i];
    }

    private static float num(String[] t, int i) {
        return Float.parseFloat(arg(t, i));
    }

    private static int numI(String[] t, int i) {
        return Integer.parseInt(arg(t, i));
    }

    /** Resolves a block by registry name ("oak_log") or numeric id. */
    private static BlockType resolveBlock(String s) {
        BlockType bt = BlockType.fromName(s);
        if (bt != null) return bt;
        try {
            bt = BlockType.fromId(Byte.parseByte(s));
            if (bt != null) return bt;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("unknown block: " + s);
    }

private void cleanup() {
        // Persist before tearing anything down (synchronous so we don't exit mid-write)
        saveWorldSync();
        world.cleanup();
        shader.cleanup();
        renderer.cleanup();
        mobRenderer.cleanup();
        villagerRenderer.cleanup();
        animalRenderer.cleanup();
        if (remotePlayerRenderer != null) remotePlayerRenderer.cleanup();
        skybox.cleanup();
        cloudLayer.cleanup();
        shadowMap.cleanup();
        if (postProcess != null) postProcess.cleanup();
        particles.cleanup();
        heldItem.cleanup();
        if (playerBodyRenderer != null) playerBodyRenderer.cleanup();
        if (blockOutline != null) blockOutline.cleanup();
        if (crackOverlay != null) crackOverlay.cleanup();
        skin.cleanup();
        hud.cleanup();
        ui.cleanup();
        font.cleanup();
        uiTextures.cleanup();
        AudioManager.destroy();
        glfwDestroyWindow(window);
        glfwTerminate();
        
        System.out.println("Game closed.");
    }
    
    // Getters
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public double getDeltaTime() { return deltaTime; }
    public float getFPS() { return fps; }
    public Camera getCamera() { return camera; }
    public World getWorld() { return world; }
    public Player getPlayer() { return player; }
    public int getRenderDistance() { return renderDistance; }
}

