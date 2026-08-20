package com.voxelgame.world.particle;

import com.voxelgame.world.biome.BiomeData;

import java.util.Random;

/**
 * Spawns ambient particles for a biome at a given chunk position.
 */
public final class BiomeParticleSpawner {

    private static final Random RAND = new Random();

    private BiomeParticleSpawner() {}

    /** Attempt to spawn ambient particles for this biome. */
    public static void spawn(BiomeData biome, int worldX, int worldY, int worldZ,
                              java.util.List<Particle> out) {
        if (biome == null || biome.particleType == null || biome.particleType.equals("none")) return;

        BiomeParticle type = BiomeParticle.fromName(biome.particleType);
        if (type == BiomeParticle.NONE) return;

        if (RAND.nextFloat() > type.density * 10) return;

        float px = worldX + RAND.nextFloat() * 16;
        float py = worldY + RAND.nextFloat() * 10;
        float pz = worldZ + RAND.nextFloat() * 16;

        int color = switch (type) {
            case FALLING_LEAVES -> 0xD06020;
            case EMBERS -> 0xFF4400;
            case WILL_O_WISP -> 0x88FFAA;
            case SMOKE -> 0x666666;
            default -> 0xFFFFFF;
        };

        out.add(new Particle(px, py, pz, color, type.name));
    }

    /** Simple particle data. */
    public record Particle(float x, float y, float z, int color, String type) {}
}
