package com.voxelgame.world.plank;

/**
 * One concrete block: a wood species + treatment + form combination.
 *
 * Registry ids follow the {wood}_{treatment}_{form} convention:
 *   oak_dried_planks, spruce_charred_slab, ...
 * Slabs and planks share the {wood}_{treatment}_planks texture.
 */
public final class PlankVariant {

    public final WoodSpecies species;
    public final PlankTreatment treatment;
    public final PlankForm form;

    public PlankVariant(WoodSpecies species, PlankTreatment treatment, PlankForm form) {
        this.species = species;
        this.treatment = treatment;
        this.form = form;
    }

    public String getId() {
        return species.id + "_" + treatment.id + "_" + form.id;
    }

    public String getLangKey() {
        return "block." + getId();
    }

    /** The block's registry name, e.g. "oak_dried_planks". */
    public String getBlockRegistryName() {
        return getId();
    }

    /** Items for blocks are implicit in this engine (ItemStack(BlockType)). */
    public String getItemRegistryName() {
        return getId();
    }

    /** Base texture name shared by the form's block, e.g. "oak_dried_planks". */
    public String getTextureBaseName() {
        return species.id + "_" + treatment.id + "_planks";
    }

    // --- Part-6 property formulas ---

    public float getHardness() {
        return species.hardness * treatment.hardnessMultiplier;
    }

    public float getResistance() {
        return species.resistance * treatment.resistanceMultiplier;
    }

    public float getFlammability() {
        return species.flammability * treatment.flammabilityMultiplier;
    }

    public int getWaterResistance() {
        return species.waterResistance + treatment.waterResistanceBonus;
    }

    public boolean isFireProof() {
        return treatment.fireProof;
    }
}