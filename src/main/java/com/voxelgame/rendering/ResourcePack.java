package com.voxelgame.rendering;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads 16x16 block textures from a resource pack.
 *
 * Lookup order for a tile named "grass_top":
 *   1. resourcepacks/&lt;selected&gt;/assets/minecraft/textures/block/grass_top.png
 *   2. resourcepacks/&lt;selected&gt;/textures/blocks/grass_top.png   (flat layout)
 *   3. classpath textures/blocks/grass_top.png                    (bundled CC0)
 *   4. procedural generation                                      (caller's job)
 *
 * Both the modern and the legacy folder layouts are accepted so packs from
 * either era drop in without repackaging. Only loose folders are supported,
 * not zips, which keeps this dependency-free.
 */
public class ResourcePack {

    private static final Path PACK_ROOT = Paths.get("resourcepacks");

    /** Sub-paths tried inside a pack folder, in order. */
    private static final String[] LAYOUTS = {
        "assets/minecraft/textures/block/",
        "assets/minecraft/textures/blocks/",
        "textures/block/",
        "textures/blocks/",
        ""
    };

    private final Path packDir;
    private final String packName;

    private int loadedCount = 0;

    public ResourcePack(String packName) {
        this.packName = (packName == null || packName.isBlank()) ? "" : packName.trim();
        this.packDir = this.packName.isEmpty() ? null : PACK_ROOT.resolve(this.packName);

        if (packDir != null && !Files.isDirectory(packDir)) {
            System.out.println("Resource pack '" + this.packName + "' not found in "
                + PACK_ROOT.toAbsolutePath() + ", using built-in textures");
        }
    }

    public boolean isActive() {
        return packDir != null && Files.isDirectory(packDir);
    }

    public String getName() { return packName; }
    public int getLoadedCount() { return loadedCount; }

    /**
     * List the pack folders a user could choose from.
     */
    public static java.util.List<String> available() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!Files.isDirectory(PACK_ROOT)) return out;
        try (var stream = Files.list(PACK_ROOT)) {
            stream.filter(Files::isDirectory)
                  .forEach(p -> out.add(p.getFileName().toString()));
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * Read one tile, resampled to 16x16 ARGB. Returns null when the tile is
     * not present anywhere, letting the atlas fall back to generation.
     */
    public int[] loadTile(String name, int size) {
        byte[] bytes = readBytes(name);
        if (bytes == null) return null;

        int[] pixels = decode(bytes, size);
        if (pixels != null) loadedCount++;
        return pixels;
    }

    private byte[] readBytes(String name) {
        String file = name + ".png";

        // 1-2. the selected pack, whichever layout it uses
        if (isActive()) {
            for (String layout : LAYOUTS) {
                Path p = packDir.resolve(layout + file);
                if (Files.isRegularFile(p)) {
                    try {
                        return Files.readAllBytes(p);
                    } catch (Exception e) {
                        System.err.println("Could not read " + p + ": " + e.getMessage());
                    }
                }
            }
        }

        // 3. bundled textures on the classpath
        try (InputStream in = ResourcePack.class.getClassLoader()
                .getResourceAsStream("textures/blocks/" + file)) {
            if (in != null) return in.readAllBytes();
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Decode a PNG and nearest-neighbour resample it to the atlas tile size,
     * so packs authored at 32x or 64x still load (just downsampled).
     */
    private int[] decode(byte[] bytes, int size) {
        ByteBuffer raw = MemoryUtil.memAlloc(bytes.length);
        raw.put(bytes).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer img = STBImage.stbi_load_from_memory(raw, w, h, comp, 4);
            if (img == null) return null;

            int iw = w.get(0);
            int ih = h.get(0);

            // Animated textures are stored as a vertical strip; take frame 0
            int frameH = (ih > iw && ih % iw == 0) ? iw : ih;

            int[] out = new int[size * size];
            for (int y = 0; y < size; y++) {
                int sy = y * frameH / size;
                for (int x = 0; x < size; x++) {
                    int sx = x * iw / size;
                    int i = (sy * iw + sx) * 4;
                    int r = img.get(i) & 0xFF;
                    int g = img.get(i + 1) & 0xFF;
                    int b = img.get(i + 2) & 0xFF;
                    int a = img.get(i + 3) & 0xFF;
                    out[y * size + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            STBImage.stbi_image_free(img);
            return out;
        } finally {
            MemoryUtil.memFree(raw);
        }
    }
}
