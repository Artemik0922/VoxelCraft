package com.voxelgame.world.plank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of every improved-plank block: 4 species x 8 treatments x the
 * engine-supported forms. Complex forms (stairs, fences, doors, ...) stay
 * in the list for reporting but are never turned into blocks.
 */
public final class PlankBlockRegistry {

    /** Every species x treatment x form combination, in registration order. */
    public static final List<PlankVariant> ALL = new ArrayList<>();

    /** Only the engine-supported, registrable variants (planks + slabs). */
    public static final List<PlankVariant> REGISTERED = new ArrayList<>();

    static {
        for (WoodSpecies species : WoodSpecies.values()) {
            for (PlankTreatment treatment : PlankTreatment.values()) {
                for (PlankForm form : PlankForm.values()) {
                    PlankVariant v = new PlankVariant(species, treatment, form);
                    ALL.add(v);
                    if (!form.isComplex) {
                        REGISTERED.add(v);
                    }
                }
            }
        }
    }

    private static final Map<String, PlankVariant> BY_ID = new LinkedHashMap<>();
    static {
        for (PlankVariant v : ALL) {
            BY_ID.put(v.getId(), v);
        }
    }

    /** Look up a variant by registry id ("oak_dried_planks"), or null. */
    public static PlankVariant fromId(String id) {
        return BY_ID.get(id);
    }

    private PlankBlockRegistry() {}
}