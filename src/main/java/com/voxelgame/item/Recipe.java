package com.voxelgame.item;

import com.voxelgame.world.BlockType;

/**
 * A crafting recipe. Can be shaped (3x3) or shapeless.
 */
public class Recipe {
    private final ItemStack result;
    private final ItemStack[] inputs; // up to 9 slots (3x3)
    private final int gridWidth; // 1, 2, or 3
    private final int gridHeight;
    private final boolean shapeless;

    public Recipe(ItemStack result, int width, int height, boolean shapeless, ItemStack... inputs) {
        this.result = result;
        this.gridWidth = width;
        this.gridHeight = height;
        this.shapeless = shapeless;
        this.inputs = new ItemStack[width * height];
        for (int i = 0; i < inputs.length && i < this.inputs.length; i++) {
            this.inputs[i] = inputs[i];
        }
    }

    public static Recipe shaped(ItemStack result, ItemStack... inputs) {
        if (inputs.length == 9) return new Recipe(result, 3, 3, false, inputs);
        if (inputs.length == 4) return new Recipe(result, 2, 2, false, inputs);
        return new Recipe(result, 1, 1, false, inputs);
    }

    public static Recipe shapeless(ItemStack result, ItemStack... inputs) {
        return new Recipe(result, 3, 3, true, inputs);
    }

    public static Recipe shaped2x2(ItemStack result, ItemStack... inputs) {
        return new Recipe(result, 2, 2, false, inputs);
    }

    public ItemStack getResult() { return result; }
    public ItemStack[] getInputs() { return inputs; }
    public int getWidth() { return gridWidth; }
    public int getHeight() { return gridHeight; }
    public boolean isShapeless() { return shapeless; }

    /**
     * Check if this recipe matches the given grid.
     */
    public boolean matches(ItemStack[] grid, int gridSize) {
        if (shapeless) {
            return matchesShapeless(grid, gridSize);
        }
        return matchesShaped(grid, gridSize);
    }

    private boolean matchesShaped(ItemStack[] grid, int gridSize) {
        // Try all positions in the grid
        for (int offsetY = 0; offsetY <= gridSize - gridHeight; offsetY++) {
            for (int offsetX = 0; offsetX <= gridSize - gridWidth; offsetX++) {
                if (matchesAt(grid, gridSize, offsetX, offsetY)) return true;
            }
        }
        return false;
    }

    private boolean matchesAt(ItemStack[] grid, int gridSize, int ox, int oy) {
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                ItemStack required = inputs[y * gridWidth + x];
                ItemStack actual = grid[(y + oy) * gridSize + (x + ox)];
                if (required == null || required.isEmpty()) {
                    if (!actual.isEmpty()) return false;
                } else {
                    if (!itemMatches(required, actual)) return false;
                }
            }
        }
        return true;
    }

    private boolean matchesShapeless(ItemStack[] grid, int gridSize) {
        boolean[] used = new boolean[grid.length];
        for (ItemStack required : inputs) {
            if (required == null || required.isEmpty()) continue;
            boolean found = false;
            for (int i = 0; i < grid.length; i++) {
                if (!used[i] && itemMatches(required, grid[i])) {
                    used[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        // Check no extra items
        for (int i = 0; i < grid.length; i++) {
            if (!used[i] && !grid[i].isEmpty()) return false;
        }
        return true;
    }

    private boolean itemMatches(ItemStack required, ItemStack actual) {
        if (required.isEmpty() && actual.isEmpty()) return true;
        if (required.isEmpty() || actual.isEmpty()) return false;
        if (required.isBlock() && actual.isBlock()) return required.getBlockType() == actual.getBlockType();
        if (required.isItem() && actual.isItem()) return required.getItem() == actual.getItem();
        return false;
    }
}
