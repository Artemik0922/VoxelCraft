import com.voxelgame.item.ItemRegistry;
import com.voxelgame.item.ItemStack;
import com.voxelgame.item.Recipe;
import com.voxelgame.item.RecipeRegistry;
import com.voxelgame.world.BlockType;

/** Textual verification of the plank recipes (no GUI needed). */
public class RecipeCheck {

    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        ItemStack[] g = new ItemStack[9];

        clear(g);
        g[0] = block(BlockType.OAK_LOG);
        check("log -> raw planks x4", g, result(BlockType.OAK_RAW_PLANKS, 4));

        clear(g);
        g[0] = block(BlockType.OAK_DRIED_PLANKS);
        g[1] = item(ItemRegistry.COAL);
        check("dried+coal -> charred", g, result(BlockType.OAK_CHARRED_PLANKS, 1));

        clear(g);
        g[0] = block(BlockType.OAK_DRIED_PLANKS);
        g[3] = item(ItemRegistry.IRON_INGOT);
        check("dried+iron -> reinforced", g, result(BlockType.OAK_REINFORCED_PLANKS, 1));

        clear(g);
        g[0] = block(BlockType.OAK_DRIED_PLANKS);
        g[4] = item(ItemRegistry.RESIN);
        check("dried+resin diag -> sealed", g, result(BlockType.OAK_SEALED_PLANKS, 1));

        clear(g);
        g[1] = block(BlockType.SPRUCE_DRIED_PLANKS);
        g[4] = item(ItemRegistry.WAX);
        check("dried+wax anti-diag -> waxed", g, result(BlockType.SPRUCE_WAXED_PLANKS, 1));

        clear(g);
        g[0] = block(BlockType.BIRCH_DRIED_PLANKS);
        g[1] = item(ItemRegistry.RESIN);
        g[2] = block(BlockType.BIRCH_DRIED_PLANKS);
        check("dried+resin+dried row -> lacquered", g, result(BlockType.BIRCH_LACQUERED_PLANKS, 1));

        clear(g);
        g[0] = block(BlockType.JUNGLE_RAW_PLANKS); g[1] = block(BlockType.JUNGLE_RAW_PLANKS); g[2] = block(BlockType.JUNGLE_RAW_PLANKS);
        g[3] = block(BlockType.JUNGLE_RAW_PLANKS); g[4] = item(ItemRegistry.RESIN); g[5] = block(BlockType.JUNGLE_RAW_PLANKS);
        g[6] = block(BlockType.JUNGLE_RAW_PLANKS); g[7] = block(BlockType.JUNGLE_RAW_PLANKS); g[8] = block(BlockType.JUNGLE_RAW_PLANKS);
        check("raw ring + resin -> composite", g, result(BlockType.JUNGLE_COMPOSITE_PLANKS, 1));

        clear(g);
        g[0] = block(BlockType.OAK_RAW_PLANKS); g[1] = block(BlockType.OAK_RAW_PLANKS); g[2] = block(BlockType.OAK_RAW_PLANKS);
        check("3 raw planks -> 6 slabs", g, result(BlockType.OAK_RAW_SLAB, 6));

        clear(g);
        g[0] = block(BlockType.SPRUCE_LOG); g[1] = block(BlockType.SPRUCE_LOG);
        check("2 logs -> 2 resin", g, resultItem(ItemRegistry.RESIN, 2));

        clear(g);
        g[0] = item(ItemRegistry.RESIN);
        g[1] = item(ItemRegistry.EGG);
        check("resin+egg shapeless -> wax", g, resultItem(ItemRegistry.WAX, 1));

        clear(g);
        g[0] = item(ItemRegistry.COAL);
        check("coal alone matches nothing", g, null);

        clear(g);
        g[0] = block(BlockType.OAK_DRIED_PLANKS);
        g[1] = block(BlockType.OAK_DRIED_PLANKS);
        check("2 dried planks matches nothing", g, null);

        clear(g);
        g[0] = block(BlockType.OAK_DRIED_PLANKS);
        g[1] = item(ItemRegistry.RESIN);
        g[2] = block(BlockType.OAK_DRIED_PLANKS);
        g[3] = block(BlockType.OAK_DRIED_PLANKS);
        g[4] = item(ItemRegistry.RESIN);
        g[5] = block(BlockType.OAK_DRIED_PLANKS);
        g[6] = block(BlockType.OAK_DRIED_PLANKS);
        g[7] = item(ItemRegistry.RESIN);
        g[8] = block(BlockType.OAK_DRIED_PLANKS);
        check("3x3 dried/resin checker matches nothing", g, null);

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }

    static ItemStack[] gridCopy(ItemStack[] g) { return g.clone(); }

    static ItemStack block(BlockType t) { return new ItemStack(t, 1); }
    static ItemStack item(com.voxelgame.item.Item i) { return new ItemStack(i, 1); }

    static ItemStack result(BlockType t, int count) {
        ItemStack s = new ItemStack(t, count);
        return s;
    }
    static ItemStack resultItem(com.voxelgame.item.Item i, int count) {
        return new ItemStack(i, count);
    }

    static void check(String name, ItemStack[] grid, ItemStack expected) {
        ItemStack match = null;
        for (Recipe r : RecipeRegistry.RECIPES) {
            if (r.matches(grid, 3)) { match = r.getResult(); break; }
        }
        boolean ok;
        if (expected == null) {
            ok = (match == null);
        } else {
            ok = match != null
                && match.isBlock() == expected.isBlock()
                && match.isItem() == expected.isItem()
                && (expected.isBlock()
                    ? match.getBlockType() == expected.getBlockType()
                    : match.getItem() == expected.getItem())
                && match.getCount() == expected.getCount();
        }
        if (ok) { pass++; System.out.println("PASS: " + name); }
        else {
            fail++;
            System.out.println("FAIL: " + name + " (got "
                + (match == null ? "no match" : match.toString()) + ")");
        }
    }

    static void clear(ItemStack[] g) {
        for (int i = 0; i < g.length; i++) g[i] = new ItemStack(BlockType.AIR, 0);
    }
}