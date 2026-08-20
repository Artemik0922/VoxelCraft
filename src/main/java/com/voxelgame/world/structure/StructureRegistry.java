package com.voxelgame.world.structure;

import com.voxelgame.world.BlockType;

/**
 * Registry of all structure templates.
 * Provides factory methods for common structures.
 */
public final class StructureRegistry {

    private StructureRegistry() {}

    /**
     * Create a small village house (5x4x5).
     */
    public static StructureTemplate createVillageHouse() {
        StructureTemplate t = new StructureTemplate("village_house", 5, 4, 5);

        // Floor
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                t.setBlock(x, 0, z, BlockType.OAK_PLANKS);
            }
        }

        // Walls (hollow box)
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                t.setBlock(x, y, 0, BlockType.OAK_LOG);
                t.setBlock(x, y, 4, BlockType.OAK_LOG);
            }
            for (int z = 1; z < 4; z++) {
                t.setBlock(0, y, z, BlockType.OAK_LOG);
                t.setBlock(4, y, z, BlockType.OAK_LOG);
            }
        }

        // Door (front center)
        t.setBlock(2, 1, 0, BlockType.AIR);
        t.setBlock(2, 2, 0, BlockType.AIR);

        // Windows
        t.setBlock(0, 2, 2, BlockType.GLASS);
        t.setBlock(4, 2, 2, BlockType.GLASS);

        // Roof (flat)
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                t.setBlock(x, 3, z, BlockType.OAK_PLANKS);
            }
        }

        return t;
    }

    /**
     * Create a large village house (7x5x7).
     */
    public static StructureTemplate createLargeHouse() {
        StructureTemplate t = new StructureTemplate("large_house", 7, 5, 7);

        // Stone foundation
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                t.setBlock(x, 0, z, BlockType.COBBLESTONE);
            }
        }

        // Wooden walls
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 7; x++) {
                t.setBlock(x, y, 0, BlockType.OAK_LOG);
                t.setBlock(x, y, 6, BlockType.OAK_LOG);
            }
            for (int z = 1; z < 6; z++) {
                t.setBlock(0, y, z, BlockType.OAK_LOG);
                t.setBlock(6, y, z, BlockType.OAK_LOG);
            }
        }

        // Door
        t.setBlock(3, 1, 0, BlockType.AIR);
        t.setBlock(3, 2, 0, BlockType.AIR);

        // Windows
        t.setBlock(1, 2, 0, BlockType.GLASS);
        t.setBlock(5, 2, 0, BlockType.GLASS);
        t.setBlock(0, 2, 3, BlockType.GLASS);
        t.setBlock(6, 2, 3, BlockType.GLASS);

        // Floor at y=1
        for (int x = 1; x < 6; x++) {
            for (int z = 1; z < 6; z++) {
                t.setBlock(x, 1, z, BlockType.OAK_PLANKS);
            }
        }

        // Roof
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                t.setBlock(x, 4, z, BlockType.OAK_PLANKS);
            }
        }

        return t;
    }

    /**
     * Create a watchtower (3x8x3).
     */
    public static StructureTemplate createWatchtower() {
        StructureTemplate t = new StructureTemplate("watchtower", 3, 8, 3);

        // Stone base
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    t.setBlock(x, y, z, BlockType.COBBLESTONE);
                }
            }
        }

        // Wooden upper part (hollow)
        for (int y = 2; y < 7; y++) {
            for (int x = 0; x < 3; x++) {
                t.setBlock(x, y, 0, BlockType.OAK_LOG);
                t.setBlock(x, y, 2, BlockType.OAK_LOG);
            }
            t.setBlock(0, y, 1, BlockType.OAK_LOG);
            t.setBlock(2, y, 1, BlockType.OAK_LOG);
        }

        // Platform at top
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                t.setBlock(x, 4, z, BlockType.OAK_PLANKS);
            }
        }

        // Railing at top
        for (int x = 0; x < 3; x++) {
            t.setBlock(x, 7, 0, BlockType.OAK_PLANKS);
            t.setBlock(x, 7, 2, BlockType.OAK_PLANKS);
        }
        t.setBlock(0, 7, 1, BlockType.OAK_PLANKS);
        t.setBlock(2, 7, 1, BlockType.OAK_PLANKS);

        return t;
    }

    /**
     * Create a well (3x3x3).
     */
    public static StructureTemplate createWell() {
        StructureTemplate t = new StructureTemplate("well", 3, 3, 3);

        // Cobblestone ring
        t.setBlock(0, 0, 0, BlockType.COBBLESTONE);
        t.setBlock(1, 0, 0, BlockType.COBBLESTONE);
        t.setBlock(2, 0, 0, BlockType.COBBLESTONE);
        t.setBlock(0, 0, 1, BlockType.COBBLESTONE);
        t.setBlock(2, 0, 1, BlockType.COBBLESTONE);
        t.setBlock(0, 0, 2, BlockType.COBBLESTONE);
        t.setBlock(1, 0, 2, BlockType.COBBLESTONE);
        t.setBlock(2, 0, 2, BlockType.COBBLESTONE);

        // Water in center
        t.setBlock(1, 0, 1, BlockType.WATER);

        // Walls up
        for (int y = 1; y <= 2; y++) {
            t.setBlock(0, y, 0, BlockType.COBBLESTONE);
            t.setBlock(2, y, 0, BlockType.COBBLESTONE);
            t.setBlock(0, y, 2, BlockType.COBBLESTONE);
            t.setBlock(2, y, 2, BlockType.COBBLESTONE);
        }

        return t;
    }

    /**
     * Create a ruined wall segment (5x3x1).
     */
    public static StructureTemplate createRuinedWall() {
        StructureTemplate t = new StructureTemplate("ruined_wall", 5, 3, 1);

        // Broken wall
        t.setBlock(0, 0, 0, BlockType.COBBLESTONE);
        t.setBlock(0, 1, 0, BlockType.COBBLESTONE);
        t.setBlock(1, 0, 0, BlockType.COBBLESTONE);
        t.setBlock(1, 1, 0, BlockType.COBBLESTONE);
        t.setBlock(1, 2, 0, BlockType.COBBLESTONE);
        t.setBlock(2, 0, 0, BlockType.COBBLESTONE);
        t.setBlock(3, 0, 0, BlockType.COBBLESTONE);
        t.setBlock(3, 1, 0, BlockType.COBBLESTONE);
        t.setBlock(4, 0, 0, BlockType.COBBLESTONE);

        return t;
    }

    /**
     * Create a small farm plot (5x1x5).
     */
    public static StructureTemplate createFarmPlot() {
        StructureTemplate t = new StructureTemplate("farm_plot", 5, 1, 5);

        // Dirt base with wheat
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                t.setBlock(x, 0, z, BlockType.WHEAT);
            }
        }

        // Water channel in center
        t.setBlock(2, 0, 0, BlockType.WATER);
        t.setBlock(2, 0, 1, BlockType.WATER);
        t.setBlock(2, 0, 2, BlockType.WATER);
        t.setBlock(2, 0, 3, BlockType.WATER);
        t.setBlock(2, 0, 4, BlockType.WATER);

        return t;
    }

    // ------------------------------------------------------------------
    // [WG] World-gen expansion structures
    // ------------------------------------------------------------------

    /** Dungeon: mossy cobblestone room with a mob spawner and a loot chest. */
    public static StructureTemplate createDungeon() {
        StructureTemplate t = new StructureTemplate("dungeon", 7, 5, 7);
        t.setBlock(3, 1, 3, BlockType.MOB_SPAWNER);
        t.setBlock(1, 1, 1, BlockType.CHEST);
        t.setBlock(5, 1, 5, BlockType.CHEST);

        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 0 || y == 4;
                    if (!wall) continue;
                    if (y == 1 && (x == 3 || (x == 1 && z == 3))) continue; // entrance
                    if (y == 2 && x == 3 && z == 0) continue;
                    t.setBlock(x, y, z, y == 0 ? BlockType.COBBLESTONE : BlockType.MOSSY_COBBLESTONE);
                }
            }
        }

        t.addLoot(1, 1, 1, 130, 3);   // iron_ingot
        t.addLoot(1, 1, 1, 132, 1);   // diamond
        t.addLoot(1, 1, 1, 177, 2);   // bread
        t.addLoot(5, 1, 5, 129, 8);   // coal
        t.addLoot(5, 1, 5, 134, 2);   // string
        t.addLoot(5, 1, 5, 194, 2);   // bone
        return t;
    }

    /** Mineshaft: wooden supports with a rail corridor and loot cart. */
    public static StructureTemplate createMineshaft() {
        StructureTemplate t = new StructureTemplate("mineshaft", 9, 5, 9);
        // Floor and corridor
        for (int x = 0; x < 9; x++) {
            for (int z = 2; z < 7; z++) {
                t.setBlock(x, 0, z, BlockType.OAK_PLANKS);
            }
            t.setBlock(x, 0, 4, BlockType.RAILS);
        }
        // Supports
        for (int x = 1; x < 9; x += 3) {
            for (int y = 1; y <= 4; y++) {
                t.setBlock(x, y, 1, BlockType.OAK_LOG);
                t.setBlock(x, y, 7, BlockType.OAK_LOG);
            }
            t.setBlock(x, 4, 1, BlockType.OAK_PLANKS);
            t.setBlock(x, 4, 7, BlockType.OAK_PLANKS);
        }
        // Roof
        for (int x = 0; x < 9; x++) {
            for (int z = 2; z < 7; z++) {
                t.setBlock(x, 4, z, BlockType.OAK_PLANKS);
            }
        }
        t.setBlock(0, 1, 4, BlockType.CHEST);
        t.addLoot(0, 1, 4, 129, 6);   // coal
        t.addLoot(0, 1, 4, 130, 2);   // iron_ingot
        t.addLoot(0, 1, 4, 136, 3);   // flint
        t.addLoot(0, 1, 4, 177, 1);   // bread
        return t;
    }

    /** Desert temple: chiseled sandstone pyramid with a hidden treasure room. */
    public static StructureTemplate createDesertTemple() {
        StructureTemplate t = new StructureTemplate("desert_temple", 9, 6, 9);
        for (int y = 0; y < 5; y++) {
            int inset = Math.min(3, y);
            for (int x = inset; x < 9 - inset; x++) {
                for (int z = inset; z < 9 - inset; z++) {
                    if (x == inset || x == 8 - inset || z == inset || z == 8 - inset) {
                        t.setBlock(x, y, z, BlockType.SANDSTONE);
                    }
                }
            }
        }
        // Capstone
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                t.setBlock(x, 4, z, BlockType.CHISELED_SANDSTONE);
            }
        }
        // Hidden chamber floor
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                t.setBlock(x, 0, z, BlockType.SANDSTONE);
            }
        }
        t.setBlock(4, 0, 4, BlockType.CHEST);
        t.addLoot(4, 0, 4, 130, 3);   // iron_ingot
        t.addLoot(4, 0, 4, 131, 2);   // gold_ingot
        t.addLoot(4, 0, 4, 132, 1);   // diamond
        t.addLoot(4, 0, 4, 177, 2);   // bread
        t.addLoot(4, 0, 4, 187, 1);   // bow
        t.addLoot(4, 0, 4, 188, 8);   // arrows
        return t;
    }

    /** Jungle temple: stone brick structure with a loot chest. */
    public static StructureTemplate createJungleTemple() {
        StructureTemplate t = new StructureTemplate("jungle_temple", 7, 5, 7);
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 4;
                    if (!wall) continue;
                    if (y == 1 && x == 3 && z == 0) continue;
                    if (y == 2 && x == 3 && z == 0) continue;
                    t.setBlock(x, y, z, BlockType.STONE_BRICKS);
                }
            }
        }
        // Overgrown floor
        for (int x = 1; x < 6; x++) {
            for (int z = 1; z < 6; z++) {
                t.setBlock(x, 0, z, BlockType.MOSSY_COBBLESTONE);
            }
        }
        t.setBlock(3, 1, 3, BlockType.CHEST);
        t.addLoot(3, 1, 3, 132, 1);   // diamond
        t.addLoot(3, 1, 3, 131, 3);   // gold_ingot
        t.addLoot(3, 1, 3, 193, 2);   // rotten flesh
        t.addLoot(3, 1, 3, 177, 2);   // bread
        return t;
    }

    /** Ocean monument: stone bricks and water, sits on the ocean floor. */
    public static StructureTemplate createOceanMonument() {
        StructureTemplate t = new StructureTemplate("ocean_monument", 9, 6, 9).allowUnderwater();
        for (int y = 0; y <= 5; y++) {
            for (int x = 0; x < 9; x++) {
                for (int z = 0; z < 9; z++) {
                    boolean wall = x == 0 || x == 8 || z == 0 || z == 8
                        || (y == 5 && x >= 3 && x <= 5 && z >= 3 && z <= 5);
                    if (!wall) continue;
                    if (y == 1 && x == 4 && z == 0) continue; // entrance
                    if (y == 2 && x == 4 && z == 0) continue;
                    t.setBlock(x, y, z, BlockType.STONE_BRICKS);
                }
            }
        }
        t.setBlock(4, 1, 4, BlockType.CHEST);
        t.addLoot(4, 1, 4, 130, 4);   // iron_ingot
        t.addLoot(4, 1, 4, 131, 2);   // gold_ingot
        t.addLoot(4, 1, 4, 132, 2);   // diamond
        t.addLoot(4, 1, 4, 177, 3);   // bread
        return t;
    }

    /** Shipwreck: a tilted wooden hull with a treasure chest. */
    public static StructureTemplate createShipwreck() {
        StructureTemplate t = new StructureTemplate("shipwreck", 9, 4, 5).allowUnderwater();
        // Hull
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 5; z++) {
                boolean hull = z == 0 || z == 4 || x == 0 || x == 8;
                t.setBlock(x, 0, z, BlockType.OAK_LOG);
                t.setBlock(x, 1, z, hull ? BlockType.OAK_PLANKS : BlockType.AIR);
                t.setBlock(x, 2, z, hull ? BlockType.OAK_PLANKS : BlockType.AIR);
                t.setBlock(x, 3, z, z == 2 ? BlockType.OAK_LOG : BlockType.AIR);
            }
        }
        t.setBlock(4, 1, 2, BlockType.CHEST);
        t.addLoot(4, 1, 2, 131, 3);   // gold_ingot
        t.addLoot(4, 1, 2, 130, 2);   // iron_ingot
        t.addLoot(4, 1, 2, 177, 2);   // bread
        t.addLoot(4, 1, 2, 134, 2);   // string
        return t;
    }

    /** Igloo: compact snow dome with a small supply stash. */
    public static StructureTemplate createIgloo() {
        StructureTemplate t = new StructureTemplate("igloo", 5, 4, 5);
        for (int y = 0; y <= 3; y++) {
            int inset = (y == 3) ? 2 : 0;
            for (int x = inset; x < 5 - inset; x++) {
                for (int z = inset; z < 5 - inset; z++) {
                    boolean wall = x == inset || x == 4 - inset || z == inset || z == 4 - inset || y == 0;
                    if (!wall) continue;
                    if (y == 1 && x == 2 && z == 0) continue; // door
                    t.setBlock(x, y, z, y == 0 ? BlockType.PACKED_ICE : BlockType.SNOW);
                }
            }
        }
        t.setBlock(1, 1, 1, BlockType.CHEST);
        t.addLoot(1, 1, 1, 177, 1);   // bread
        t.addLoot(1, 1, 1, 129, 4);   // coal
        t.addLoot(1, 1, 1, 138, 2);   // wheat
        return t;
    }

    /** Ruins: scattered stone brick remnants with a small loot cache. */
    public static StructureTemplate createRuins() {
        StructureTemplate t = new StructureTemplate("ruins", 7, 3, 7);
        t.setBlock(0, 0, 0, BlockType.STONE_BRICKS);
        t.setBlock(0, 1, 0, BlockType.STONE_BRICKS);
        t.setBlock(1, 0, 0, BlockType.STONE_BRICKS);
        t.setBlock(1, 0, 1, BlockType.MOSSY_COBBLESTONE);
        t.setBlock(2, 0, 1, BlockType.STONE_BRICKS);
        t.setBlock(3, 0, 2, BlockType.STONE_BRICKS);
        t.setBlock(3, 1, 2, BlockType.STONE_BRICKS);
        t.setBlock(4, 0, 3, BlockType.MOSSY_COBBLESTONE);
        t.setBlock(5, 0, 3, BlockType.STONE_BRICKS);
        t.setBlock(5, 0, 4, BlockType.STONE_BRICKS);
        t.setBlock(6, 0, 5, BlockType.STONE_BRICKS);
        t.setBlock(6, 1, 5, BlockType.STONE_BRICKS);
        t.setBlock(5, 2, 5, BlockType.STONE_BRICKS);
        t.setBlock(2, 0, 5, BlockType.CHEST);
        t.addLoot(2, 0, 5, 129, 3);   // coal
        t.addLoot(2, 0, 5, 194, 2);   // bone
        t.addLoot(2, 0, 5, 177, 1);   // bread
        return t;
    }

    // ------------------------------------------------------------------
    // [BASE] Survivor bases - one per cold biome, each with a bed, a
    // workbench, a furnace, lighting, a crate and a campfire.
    // ------------------------------------------------------------------

    private static void baseInterior(StructureTemplate t, BlockType wall, BlockType light,
                                     int w, int h, int d) {
        int cx = w / 2;
        // Bed (foot + head)
        t.setBlock(cx - 1, 1, 1, BlockType.BED);
        t.setBlock(cx, 1, 1, BlockType.BED_HEAD);
        // Workbench + furnace
        t.setBlock(1, 1, 1, BlockType.CRAFTING_TABLE);
        t.setBlock(1, 1, d - 2, BlockType.FURNACE);
        // Lighting in two opposite corners
        t.setBlock(1, h - 2, 1, light);
        t.setBlock(w - 2, h - 2, d - 2, light);
        // Loot crate
        t.setBlock(w - 2, 1, d - 2, BlockType.CRATE);
        // Campfire on the floor (heals nearby survivors)
        t.setBlock(cx, 1, d - 2, BlockType.CAMPFIRE);
    }

    /** Ice outpost: snow-brick blockhouse with icy lanterns. */
    public static StructureTemplate createIceOutpost() {
        StructureTemplate t = new StructureTemplate("ice_outpost", 7, 5, 7);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 0 || y == 4;
                    if (!wall) continue;
                    if (y >= 1 && y <= 3 && x == 3 && z == 0) continue; // door
                    if (y == 1 && (x == 1 || x == 5) && z == 0) continue; // windows
                    t.setBlock(x, y, z, y == 4 ? BlockType.PACKED_ICE : BlockType.SNOW_BRICKS);
                }
            }
        }
        baseInterior(t, BlockType.SNOW_BRICKS, BlockType.ICE_LANTERN, 7, 5, 7);
        t.addLoot(5, 1, 5, 129, 6);   // coal
        t.addLoot(5, 1, 5, 130, 2);   // iron_ingot
        t.addLoot(5, 1, 5, 177, 2);   // bread
        return t;
    }

    /** Ice mine: a short snow-brick drift with a mining stash. */
    public static StructureTemplate createIceMine() {
        StructureTemplate t = new StructureTemplate("ice_mine", 5, 6, 7);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 6 || y == 0;
                    if (!wall) continue;
                    if (y >= 1 && y <= 4 && z == 0 && x == 2) continue; // entrance
                    t.setBlock(x, y, z, y == 0 ? BlockType.SNOW : BlockType.SNOW_BRICKS);
                }
            }
        }
        // Timber props down the drift
        for (int z = 2; z <= 4; z++) {
            t.setBlock(2, 1, z, BlockType.OAK_LOG);
            t.setBlock(2, 2, z, BlockType.OAK_LOG);
        }
        t.setBlock(2, 3, 3, BlockType.ICE_LANTERN);
        t.setBlock(1, 1, 5, BlockType.CRATE);
        t.setBlock(3, 1, 5, BlockType.FURNACE);
        t.addLoot(1, 1, 5, 130, 3);   // iron_ingot
        t.addLoot(1, 1, 5, 131, 1);   // gold_ingot
        t.addLoot(1, 1, 5, 177, 1);   // bread
        return t;
    }

    /** Oasis camp: sandstone-brick shelter with warm lanterns. */
    public static StructureTemplate createOasisCamp() {
        StructureTemplate t = new StructureTemplate("oasis_camp", 7, 5, 7);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 0 || y == 4;
                    if (!wall) continue;
                    if (y >= 1 && y <= 3 && x == 3 && z == 0) continue; // door
                    if (y == 2 && (x == 1 || x == 5) && z == 0) continue; // windows
                    t.setBlock(x, y, z, y == 0 ? BlockType.SAND : BlockType.SANDSTONE_BRICKS);
                }
            }
        }
        t.setBlock(3, 1, 1, BlockType.LANTERN);
        baseInterior(t, BlockType.SANDSTONE_BRICKS, BlockType.LANTERN, 7, 5, 7);
        t.addLoot(5, 1, 5, 130, 2);   // iron_ingot
        t.addLoot(5, 1, 5, 131, 2);   // gold_ingot
        t.addLoot(5, 1, 5, 138, 3);   // wheat
        t.addLoot(5, 1, 5, 177, 2);   // bread
        return t;
    }

    /** Jungle camp: a log platform in the canopy with a lean-to roof. */
    public static StructureTemplate createJungleCamp() {
        StructureTemplate t = new StructureTemplate("jungle_camp", 7, 4, 7);
        // Corner posts down to the ground (template baseY sits on the ground)
        for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            for (int y = 0; y < 3; y++) t.setBlock(c[0], y, c[1], BlockType.OAK_LOG);
        }
        // Platform
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) t.setBlock(x, 1, z, BlockType.OAK_PLANKS);
        }
        // Rail walls and leaf roof
        for (int x = 0; x < 7; x++) {
            t.setBlock(x, 2, 0, BlockType.OAK_LOG);
            t.setBlock(x, 2, 6, BlockType.OAK_LOG);
            t.setBlock(x, 3, 0, BlockType.JUNGLE_LEAVES);
            t.setBlock(x, 3, 6, BlockType.JUNGLE_LEAVES);
        }
        for (int z = 1; z < 6; z++) {
            t.setBlock(0, 2, z, BlockType.OAK_LOG);
            t.setBlock(6, 2, z, BlockType.OAK_LOG);
        }
        for (int z = 0; z < 7; z++) t.setBlock(3, 3, z, BlockType.JUNGLE_LEAVES);
        // Bed, workbench, crate and campfire on the platform
        t.setBlock(1, 2, 1, BlockType.BED);
        t.setBlock(2, 2, 1, BlockType.BED_HEAD);
        t.setBlock(1, 2, 5, BlockType.CRAFTING_TABLE);
        t.setBlock(3, 2, 5, BlockType.CRATE);
        t.setBlock(5, 2, 1, BlockType.FURNACE);
        t.setBlock(3, 2, 3, BlockType.CAMPFIRE);
        t.setBlock(5, 3, 5, BlockType.LANTERN);
        t.addLoot(3, 2, 5, 132, 1);   // diamond
        t.addLoot(3, 2, 5, 130, 2);   // iron_ingot
        t.addLoot(3, 2, 5, 177, 2);   // bread
        return t;
    }

    /** Swamp hut: a plank cabin on stilts over the mire. */
    public static StructureTemplate createSwampHut() {
        StructureTemplate t = new StructureTemplate("swamp_hut", 7, 4, 7);
        for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            for (int y = 0; y < 4; y++) t.setBlock(c[0], y, c[1], BlockType.OAK_LOG);
        }
        // Floor lifted one block above the water line
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) t.setBlock(x, 1, z, BlockType.OAK_PLANKS);
        }
        // Walls and flat roof
        for (int y = 2; y <= 3; y++) {
            for (int x = 0; x < 7; x++) {
                t.setBlock(x, y, 0, BlockType.OAK_LOG);
                t.setBlock(x, y, 6, BlockType.OAK_LOG);
            }
            for (int z = 1; z < 6; z++) {
                t.setBlock(0, y, z, BlockType.OAK_LOG);
                t.setBlock(6, y, z, BlockType.OAK_LOG);
            }
        }
        t.setBlock(3, 2, 0, BlockType.AIR); // door
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) t.setBlock(x, 3, z, BlockType.OAK_LOG);
        }
        // Interior
        t.setBlock(1, 2, 1, BlockType.BED);
        t.setBlock(2, 2, 1, BlockType.BED_HEAD);
        t.setBlock(1, 2, 5, BlockType.CRAFTING_TABLE);
        t.setBlock(5, 2, 5, BlockType.CRATE);
        t.setBlock(5, 2, 1, BlockType.FURNACE);
        t.setBlock(3, 2, 3, BlockType.CAMPFIRE);
        t.setBlock(5, 2, 3, BlockType.LANTERN);
        t.addLoot(5, 2, 5, 177, 2);   // bread
        t.addLoot(5, 2, 5, 129, 4);   // coal
        t.addLoot(5, 2, 5, 134, 2);   // string
        return t;
    }

    /** Mountain shelter: a stone bunker tucked into the peaks. */
    public static StructureTemplate createMountainShelter() {
        StructureTemplate t = new StructureTemplate("mountain_shelter", 7, 5, 7);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 0 || y == 4;
                    if (!wall) continue;
                    if (y >= 1 && y <= 3 && x == 3 && z == 0) continue; // door
                    t.setBlock(x, y, z, y == 4 ? BlockType.OBSIDIAN : BlockType.STONE_BRICKS);
                }
            }
        }
        t.setBlock(3, 1, 1, BlockType.LANTERN);
        baseInterior(t, BlockType.STONE_BRICKS, BlockType.LANTERN, 7, 5, 7);
        t.addLoot(5, 1, 5, 130, 3);   // iron_ingot
        t.addLoot(5, 1, 5, 129, 6);   // coal
        t.addLoot(5, 1, 5, 177, 2);   // bread
        return t;
    }

    /** Underwater base: a glass dome on the sea floor with a stash. */
    public static StructureTemplate createUnderwaterBase() {
        StructureTemplate t = new StructureTemplate("underwater_base", 9, 5, 9).allowUnderwater();
        // Stone brick ring + glass dome
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                t.setBlock(x, 0, z, BlockType.STONE_BRICKS);
                t.setBlock(x, 1, z, edge ? BlockType.STONE_BRICKS : BlockType.GLASS);
                t.setBlock(x, 2, z, edge ? BlockType.STONE_BRICKS : BlockType.GLASS);
                t.setBlock(x, 3, z, edge ? BlockType.STONE_BRICKS : BlockType.GLASS);
                if (x >= 2 && x <= 6 && z >= 2 && z <= 6) {
                    t.setBlock(x, 4, z, BlockType.GLASS); // dome cap
                }
            }
        }
        t.setBlock(4, 1, 4, BlockType.GLOWSTONE);
        t.setBlock(2, 1, 2, BlockType.BED);
        t.setBlock(3, 1, 2, BlockType.BED_HEAD);
        t.setBlock(2, 1, 6, BlockType.CRAFTING_TABLE);
        t.setBlock(6, 1, 6, BlockType.CRATE);
        t.setBlock(6, 1, 2, BlockType.FURNACE);
        t.setBlock(4, 1, 6, BlockType.LANTERN);
        t.addLoot(6, 1, 6, 131, 3);   // gold_ingot
        t.addLoot(6, 1, 6, 132, 1);   // diamond
        t.addLoot(6, 1, 6, 177, 2);   // bread
        t.addLoot(6, 1, 6, 188, 8);   // arrows
        return t;
    }
}
