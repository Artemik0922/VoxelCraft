package com.voxelgame.world.particle;

/**
 * Ambient particle types that spawn based on biome.
 */
public enum BiomeParticle {
    NONE(null, 0),
    FALLING_LEAVES("leaves", 0.02f),
    EMBERS("ember", 0.03f),
    WILL_O_WISP("wisp", 0.01f),
    SMOKE("smoke", 0.02f);

    public final String name;
    public final float density;

    BiomeParticle(String name, float density) {
        this.name = name;
        this.density = density;
    }

    public static BiomeParticle fromName(String name) {
        for (BiomeParticle p : values()) {
            if (p.name != null && p.name.equals(name)) return p;
        }
        return NONE;
    }
}
