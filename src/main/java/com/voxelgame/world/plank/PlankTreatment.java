package com.voxelgame.world.plank;

/**
 * A treatment applied to a wood species: drying, sealing, charring, ...
 *
 * The multipliers feed the Part-6 property formulas:
 *   hardness      = species.hardness      * hardnessMultiplier
 *   resistance    = species.resistance    * resistanceMultiplier
 *   flammability  = species.flammability  * flammabilityMultiplier
 *   waterResist   = species.waterResistance + waterResistanceBonus
 *
 * {@code fireProof} treatments never catch fire at all.
 */
public enum PlankTreatment {

    RAW("raw", 0.9f, 0.8f, 1.6f, -2, false),
    DRIED("dried", 1.0f, 1.0f, 1.0f, 0, false),
    SEALED("sealed", 1.05f, 1.1f, 0.9f, 4, false),
    CHARRED("charred", 0.85f, 0.9f, 0.0f, 3, true),
    REINFORCED("reinforced", 1.4f, 1.8f, 0.3f, 2, false),
    COMPOSITE("composite", 1.2f, 1.3f, 0.5f, 3, false),
    WAXED("waxed", 1.0f, 1.0f, 1.0f, 5, false),
    LACQUERED("lacquered", 1.0f, 1.1f, 1.3f, 4, false);

    public final String id;
    public final String langKey;
    public final float hardnessMultiplier;
    public final float resistanceMultiplier;
    public final float flammabilityMultiplier;
    public final int waterResistanceBonus;
    public final boolean fireProof;

    PlankTreatment(String id, float hardnessMultiplier, float resistanceMultiplier,
                   float flammabilityMultiplier, int waterResistanceBonus, boolean fireProof) {
        this.id = id;
        this.langKey = "plankTreatment." + id;
        this.hardnessMultiplier = hardnessMultiplier;
        this.resistanceMultiplier = resistanceMultiplier;
        this.flammabilityMultiplier = flammabilityMultiplier;
        this.waterResistanceBonus = waterResistanceBonus;
        this.fireProof = fireProof;
    }

    /** Look up by registry id ("dried"), or null. */
    public static PlankTreatment fromId(String id) {
        for (PlankTreatment t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}