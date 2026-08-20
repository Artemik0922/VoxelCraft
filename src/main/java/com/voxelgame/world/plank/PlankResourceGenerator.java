package com.voxelgame.world.plank;

import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.Recipe;
import com.voxelgame.world.BlockType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data generation for the improved plank system: crafting recipes, language
 * entries and the skip report for unsupported forms.
 *
 * Runs as a datagen tool ({@link #main}) to print the lang keys and the
 * report; {@link #buildRecipes()} feeds {@code RecipeRegistry.RECIPES}.
 */
public final class PlankResourceGenerator {

    /** Localized names, ru by default; {@code en} returns English names. */
    public static Map<String, String> langEntries(boolean en) {
        Map<String, String> out = new LinkedHashMap<>();
        for (PlankVariant v : PlankBlockRegistry.REGISTERED) {
            out.put(v.getLangKey(), en ? enName(v) : ruName(v));
        }
        out.put("item.resin", en ? "Resin" : "Смола");
        out.put("item.wax", en ? "Wax" : "Воск");
        return out;
    }

    private static String ruName(PlankVariant v) {
        if (v.form == PlankForm.SLAB) {
            String adj = switch (v.treatment) {
                case RAW -> "сырая";
                case DRIED -> "сушёная";
                case SEALED -> "герметичная";
                case CHARRED -> "обожжённая";
                case REINFORCED -> "армированная";
                case COMPOSITE -> "композитная";
                case WAXED -> "вощёная";
                case LACQUERED -> "лакированная";
            };
            String wood = switch (v.species) {
                case OAK -> "дубовая";
                case SPRUCE -> "еловая";
                case BIRCH -> "берёзовая";
                case JUNGLE -> "тропическая";
            };
            return adj + " " + wood + " плита";
        }
        String adj = switch (v.treatment) {
            case RAW -> "сырые";
            case DRIED -> "сушёные";
            case SEALED -> "герметичные";
            case CHARRED -> "обожжённые";
            case REINFORCED -> "армированные";
            case COMPOSITE -> "композитные";
            case WAXED -> "вощёные";
            case LACQUERED -> "лакированные";
        };
        String wood = switch (v.species) {
            case OAK -> "дубовые";
            case SPRUCE -> "еловые";
            case BIRCH -> "берёзовые";
            case JUNGLE -> "тропические";
        };
        return adj + " " + wood + " доски";
    }

    private static String enName(PlankVariant v) {
        String wood = switch (v.species) {
            case OAK -> "Oak";
            case SPRUCE -> "Spruce";
            case BIRCH -> "Birch";
            case JUNGLE -> "Jungle";
        };
        String treat = switch (v.treatment) {
            case RAW -> "Raw";
            case DRIED -> "Dried";
            case SEALED -> "Sealed";
            case CHARRED -> "Charred";
            case REINFORCED -> "Reinforced";
            case COMPOSITE -> "Composite";
            case WAXED -> "Waxed";
            case LACQUERED -> "Lacquered";
        };
        return treat + " " + wood + (v.form == PlankForm.PLANKS ? " Planks" : " Slab");
    }

    /**
     * All improved-plank crafting recipes. Layouts are distinct so no two
     * recipes can match the same grid:
     *   log -> 4 raw planks (single log, 2x2)
     *   2 logs -> 2 resin
     *   dried + coal -> charred (top row)
     *   dried + iron_ingot -> reinforced (left column)
     *   dried + resin -> sealed (main diagonal)
     *   dried + wax -> waxed (anti-diagonal)
     *   dried + resin + dried -> lacquered (3x3 top row)
     *   raw ring + resin center -> composite (3x3)
     *   3 raw planks -> 6 slabs (3x3 top row)
     *   resin + egg (shapeless) -> wax
     * Raw planks are dried in the furnace (see RecipeRegistry.SMELTING_RECIPES).
     */
    public static Recipe[] buildRecipes() {
        List<Recipe> out = new ArrayList<>();
        for (WoodSpecies s : WoodSpecies.values()) {
            BlockType raw = BlockType.fromName(s.id + "_raw_planks");
            BlockType dried = BlockType.fromName(s.id + "_dried_planks");
            BlockType charred = BlockType.fromName(s.id + "_charred_planks");
            BlockType reinforced = BlockType.fromName(s.id + "_reinforced_planks");
            BlockType sealed = BlockType.fromName(s.id + "_sealed_planks");
            BlockType waxed = BlockType.fromName(s.id + "_waxed_planks");
            BlockType lacquered = BlockType.fromName(s.id + "_lacquered_planks");
            BlockType composite = BlockType.fromName(s.id + "_composite_planks");
            BlockType slab = BlockType.fromName(s.id + "_raw_slab");
            if (raw == null || dried == null) continue;

            out.add(Recipe.shaped2x2(new ItemStack(raw, 4),
                block(s.logBlock), null, null, null));

            if (charred != null) {
                out.add(Recipe.shaped2x2(block(charred),
                    block(dried), item(ItemRegistry.COAL), null, null));
            }
            if (reinforced != null) {
                out.add(Recipe.shaped2x2(block(reinforced),
                    block(dried), null, item(ItemRegistry.IRON_INGOT), null));
            }
            if (sealed != null) {
                out.add(Recipe.shaped2x2(block(sealed),
                    block(dried), null, null, item(ItemRegistry.RESIN)));
            }
            if (waxed != null) {
                out.add(Recipe.shaped2x2(block(waxed),
                    null, block(dried), null, item(ItemRegistry.WAX)));
            }
            if (lacquered != null) {
                out.add(Recipe.shaped(block(lacquered),
                    block(dried), item(ItemRegistry.RESIN), block(dried),
                    null, null, null, null, null, null));
            }
            if (composite != null) {
                out.add(Recipe.shaped(block(composite),
                    block(raw), block(raw), block(raw),
                    block(raw), item(ItemRegistry.RESIN), block(raw),
                    block(raw), block(raw), block(raw)));
            }
            if (slab != null) {
                out.add(Recipe.shaped(new ItemStack(slab, 6),
                    block(raw), block(raw), block(raw),
                    null, null, null, null, null, null));
            }
        }

        // Resin comes from a 2x2 block of logs (no overlap with the
        // sliding single-log recipe); wax needs a binder
        out.add(Recipe.shaped2x2(new ItemStack(ItemRegistry.RESIN, 4),
            block(BlockType.OAK_LOG), block(BlockType.OAK_LOG),
            block(BlockType.OAK_LOG), block(BlockType.OAK_LOG)));
        out.add(Recipe.shapeless(new ItemStack(ItemRegistry.WAX),
            item(ItemRegistry.RESIN), item(ItemRegistry.EGG)));
        return out.toArray(new Recipe[0]);
    }

    /**
     * Report of what was registered and what was skipped because the engine
     * does not support the shape (stairs, fences, doors, ...).
     */
    public static String report() {
        int blocks = PlankBlockRegistry.REGISTERED.size();
        StringBuilder sb = new StringBuilder();
        sb.append("Improved planks: ").append(blocks).append(" blocks registered\n");
        sb.append("Textures: 32 base plank tiles (tools/PlankTextureGenerator)\n");
        sb.append("Substitute materials: resin (").append(ItemRegistry.RESIN.id)
            .append("), wax (").append(ItemRegistry.WAX.id).append(")\n");
        sb.append("Skipped (engine has no such shape): ");
        List<String> skipped = new ArrayList<>();
        for (PlankForm f : PlankForm.values()) {
            if (f.isComplex) skipped.add(f.id);
        }
        sb.append(String.join(", ", skipped)).append("\n");
        sb.append("Note: byte ids 191-254 are now fully used; no room left for more blocks.\n");
        return sb.toString();
    }

    /** Datagen tool: prints lang keys and the report. */
    public static void main(String[] args) {
        System.out.println("=== ru ===");
        for (Map.Entry<String, String> e : langEntries(false).entrySet()) {
            System.out.println("    \"" + e.getKey() + "\": \"" + e.getValue() + "\",");
        }
        System.out.println("=== en ===");
        for (Map.Entry<String, String> e : langEntries(true).entrySet()) {
            System.out.println("    \"" + e.getKey() + "\": \"" + e.getValue() + "\",");
        }
        System.out.println("=== report ===");
        System.out.println(report());
    }

    private static ItemStack block(BlockType t) { return new ItemStack(t, 1); }
    private static ItemStack item(com.voxelgame.item.Item i) { return new ItemStack(i, 1); }

    private PlankResourceGenerator() {}
}