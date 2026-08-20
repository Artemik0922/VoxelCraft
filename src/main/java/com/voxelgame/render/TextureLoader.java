package com.voxelgame.render;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;

/**
 * Loads a PNG texture with pixel-art settings: GL_NEAREST filtering,
 * no mipmaps, CLAMP_TO_EDGE wrapping.
 *
 * Tries classpath first (works when running from build/), then filesystem.
 */
public class TextureLoader {

    public static int loadTexture(String path) {
        byte[] imageData = null;

        // Try classpath first
        try (InputStream is = TextureLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                imageData = is.readAllBytes();
            }
        } catch (IOException ignored) {}

        // Fall back to filesystem
        if (imageData == null) {
            try {
                imageData = Files.readAllBytes(Paths.get(path));
            } catch (IOException e) {
                System.err.println("TextureLoader: cannot read " + path + " - " + e.getMessage());
                return 0;
            }
        }

        ByteBuffer buf = MemoryUtil.memAlloc(imageData.length).put(imageData).flip();
        int[] w = new int[1], h = new int[1], channels = new int[1];
        ByteBuffer image = stbi_load_from_memory(buf, w, h, channels, 4);
        MemoryUtil.memFree(buf);

        if (image == null) {
            System.err.println("TextureLoader: STB failed to decode " + path);
            return 0;
        }

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w[0], h[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        org.lwjgl.stb.STBImage.stbi_image_free(image);
        return id;
    }
}
