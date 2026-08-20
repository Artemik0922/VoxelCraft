package com.voxelgame.rendering.texture;

import com.voxelgame.rendering.TextureAtlas;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Builds the block atlas from registered texture generators.
 *
 * Flow: render every tile -> pack into a grid with bleed padding ->
 * upload as a texture array. UV coords are derived from grid position.
 */
public final class AtlasBuilder {

    public static final int TILE_SIZE = TextureAtlas.TEXTURE_SIZE; // 16
    public static final int BLEED = 1; // pixels to duplicate at each edge

    private AtlasBuilder() {}

    /**
     * Build the atlas from registered tiles.
     * @return UV lookup: blockId -> face -> UV rect (normalized 0..1)
     */
    public static Map<String, Map<String, float[]>> build() {
        Map<String, TextureGenerator> tiles = TextureRegistry.tiles();
        int count = tiles.size();

        // Grid size: smallest square >= count
        int gridW = (int) Math.ceil(Math.sqrt(count));
        int gridH = (int) Math.ceil((double) count / gridW);

        // Render each tile to a BufferedImage
        Map<String, BufferedImage> rendered = new LinkedHashMap<>();
        int i = 0;
        for (var entry : tiles.entrySet()) {
            BufferedImage img = entry.getValue().generate();
            rendered.put(entry.getKey(), img);
            i++;
        }

        // Calculate atlas dimensions with bleed
        int atlasW = gridW * (TILE_SIZE + BLEED * 2);
        int atlasH = gridH * (TILE_SIZE + BLEED * 2);

        // Pack tiles into atlas image with bleed
        BufferedImage atlasImage = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlasImage.createGraphics();

        Map<String, int[]> tilePositions = new LinkedHashMap<>(); // tileName -> [gridX, gridY]
        i = 0;
        for (var entry : rendered.entrySet()) {
            int gx = i % gridW;
            int gy = i / gridW;
            tilePositions.put(entry.getKey(), new int[]{gx, gy});

            int px = gx * (TILE_SIZE + BLEED * 2) + BLEED;
            int py = gy * (TILE_SIZE + BLEED * 2) + BLEED;

            // Draw the tile
            g.drawImage(entry.getValue(), px, py, null);

            // Bleed: duplicate edge pixels to prevent UV bleeding between tiles
            BufferedImage img = entry.getValue();
            // Top edge
            g.drawImage(img.getSubimage(0, 0, TILE_SIZE, 1), px, py - 1, null);
            // Bottom edge
            g.drawImage(img.getSubimage(0, TILE_SIZE - 1, TILE_SIZE, 1), px, py + TILE_SIZE, null);
            // Left edge
            g.drawImage(img.getSubimage(0, 0, 1, TILE_SIZE), px - 1, py, null);
            // Right edge
            g.drawImage(img.getSubimage(TILE_SIZE - 1, 0, 1, TILE_SIZE), px + TILE_SIZE, py, null);

            i++;
        }
        g.dispose();

        // Build UV map
        Map<String, Map<String, float[]>> uvMap = new HashMap<>();

        for (var blockEntry : TextureRegistry.blocks()) {
            String blockId = blockEntry.blockId();
            Map<String, float[]> faceUVs = new HashMap<>();

            String[] faces = {"top", "side", "bottom"};
            String[] tileNames = {blockEntry.top(), blockEntry.side(), blockEntry.bottom()};

            for (int f = 0; f < 3; f++) {
                String tileName = tileNames[f];
                int[] pos = tilePositions.get(tileName);
                if (pos == null) continue;

                int gx = pos[0];
                int gy = pos[1];

                // UV with bleed compensation
                float u1 = (gx * (TILE_SIZE + BLEED * 2) + BLEED) / (float) atlasW;
                float u2 = (gx * (TILE_SIZE + BLEED * 2) + BLEED + TILE_SIZE) / (float) atlasW;
                float v1 = (gy * (TILE_SIZE + BLEED * 2) + BLEED) / (float) atlasH;
                float v2 = (gy * (TILE_SIZE + BLEED * 2) + BLEED + TILE_SIZE) / (float) atlasH;

                faceUVs.put(faces[f], new float[]{u1, v1, u2, v2});
            }

            uvMap.put(blockId, faceUVs);
        }

        // Dump debug image
        try {
            javax.imageio.ImageIO.write(atlasImage, "PNG",
                new java.io.File("atlas_debug.png"));
        } catch (Exception e) {
            System.err.println("Could not dump atlas debug image: " + e.getMessage());
        }

        System.out.println("Atlas built: " + count + " tiles in " + gridW + "x" + gridH
            + " grid (" + atlasW + "x" + atlasH + " px)");

        return uvMap;
    }
}
