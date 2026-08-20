package com.voxelgame.world.plank;

/**
 * The shape a plank material takes.
 *
 * PLANKS and SLAB are fully implemented by the engine (full cubes and
 * half-height slabs). The rest are recognised forms the engine does not
 * mesh/collide yet: they stay registered as data with {@code isComplex}
 * true and are skipped by the block registry (see the report in
 * {@link PlankResourceGenerator}).
 */
public enum PlankForm {

    PLANKS("planks", "full", false),
    SLAB("slab", "slab", false),

    // --- Engine does not support these shapes yet: data only ---
    STAIRS("stairs", "stairs", true),
    FENCE("fence", "fence", true),
    FENCE_GATE("fence_gate", "fence_gate", true),
    DOOR("door", "door", true),
    TRAPDOOR("trapdoor", "trapdoor", true),
    BUTTON("button", "button", true),
    PRESSURE_PLATE("pressure_plate", "pressure_plate", true);

    public final String id;
    public final String modelType;
    public final boolean isComplex;

    PlankForm(String id, String modelType, boolean isComplex) {
        this.id = id;
        this.modelType = modelType;
        this.isComplex = isComplex;
    }

    /** Look up by registry id ("slab"), or null. */
    public static PlankForm fromId(String id) {
        for (PlankForm f : values()) {
            if (f.id.equals(id)) return f;
        }
        return null;
    }
}