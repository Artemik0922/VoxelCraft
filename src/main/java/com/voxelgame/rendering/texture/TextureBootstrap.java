package com.voxelgame.rendering.texture;

import com.voxelgame.world.BlockType;

/**
 * Registers all texture generators into the registry at startup.
 */
public final class TextureBootstrap {

    private TextureBootstrap() {}

    public static void init() {
        // Register procedural tile generators
        TextureRegistry.registerTile("autumn_leaves", BiomeTextures::autumn_leaves);
        TextureRegistry.registerTile("cherry_leaves", BiomeTextures::cherry_leaves);
        TextureRegistry.registerTile("lavender", BiomeTextures::lavender);
        TextureRegistry.registerTile("crystal", BiomeTextures::crystal);
        TextureRegistry.registerTile("basalt", BiomeTextures::basalt);
        TextureRegistry.registerTile("salt", BiomeTextures::salt);
        TextureRegistry.registerTile("ash", BiomeTextures::ash);
        TextureRegistry.registerTile("charred_log", BiomeTextures::charred_log);
        TextureRegistry.registerTile("charred_log_top", BiomeTextures::charred_log_top);
        TextureRegistry.registerTile("petrified_log", BiomeTextures::petrified_log);
        TextureRegistry.registerTile("petrified_log_top", BiomeTextures::petrified_log_top);
        TextureRegistry.registerTile("blue_ice", BiomeTextures::blue_ice);
        TextureRegistry.registerTile("mycelium_top", BiomeTextures::mycelium_top);
        TextureRegistry.registerTile("terracotta", BiomeTextures::terracotta);
        TextureRegistry.registerTile("red_sand", BiomeTextures::red_sand);
        TextureRegistry.registerTile("cactus_side", BiomeTextures::cactus_side);
        TextureRegistry.registerTile("lily_pad", BiomeTextures::lily_pad);
        TextureRegistry.registerTile("sunflower", BiomeTextures::sunflower);
        TextureRegistry.registerTile("vine", BiomeTextures::vine);

        // Register block face mappings
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("autumn_leaves", "autumn_leaves"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("cherry_leaves", "cherry_leaves"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("lavender", "lavender"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("crystal", "crystal"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("basalt", "basalt"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("salt", "salt"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("ash", "ash"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.topSide("charred_log", "charred_log_top", "charred_log"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.topSide("petrified_log", "petrified_log_top", "petrified_log"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("blue_ice", "blue_ice"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("mycelium", "mycelium_top"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("terracotta", "terracotta"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("red_sand", "red_sand"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("cactus_side", "cactus_side"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("lily_pad", "lily_pad"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("sunflower", "sunflower"));
        TextureRegistry.registerBlock(TextureRegistry.BlockFaces.single("vine", "vine"));
    }
}
