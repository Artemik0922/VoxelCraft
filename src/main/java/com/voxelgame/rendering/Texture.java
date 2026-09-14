package com.voxelgame.rendering;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class Texture {
    private int id;
    private int width;
    private int height;
    
    public Texture(String path) {
        // Try loading from classpath first
        byte[] imageData;
        try {
            var is = getClass().getClassLoader().getResourceAsStream(path);
            if (is != null) {
                imageData = is.readAllBytes();
            } else {
                imageData = Files.readAllBytes(Paths.get(path));
            }
        } catch (IOException e) {
            // Generate procedural texture if file not found
            System.out.println("Texture not found: " + path + ", generating procedural texture");
            generateProceduralTexture(path);
            return;
        }
        
        loadFromBuffer(imageData);
    }
    
    public Texture(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        
        ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
            buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
            buffer.put((byte) (pixel & 0xFF));          // B
            buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
        }
        buffer.flip();
        
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        
        glGenerateMipmap(GL_TEXTURE_2D);
        org.lwjgl.system.MemoryUtil.memFree(buffer);
    }
    
    private void loadFromBuffer(byte[] imageData) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            
            ByteBuffer image = STBImage.stbi_load_from_memory(
                org.lwjgl.system.MemoryUtil.memAlloc(imageData.length).put(imageData).flip(),
                w, h, channels, 4);
            
            if (image == null) {
                throw new RuntimeException("Failed to load texture: " + STBImage.stbi_failure_reason());
            }
            
            width = w.get();
            height = h.get();
            
            id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            
            glGenerateMipmap(GL_TEXTURE_2D);
            STBImage.stbi_image_free(image);
        }
    }
    
    private void generateProceduralTexture(String path) {
        // Generate simple colored textures based on block name
        width = 16;
        height = 16;
        
        int baseColor = getBlockColor(path);
        int[] pixels = new int[256];
        
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // Add some noise/variation
                int noise = (int)(Math.random() * 30) - 15;
                int r = Math.min(255, Math.max(0, ((baseColor >> 16) & 0xFF) + noise));
                int g = Math.min(255, Math.max(0, ((baseColor >> 8) & 0xFF) + noise));
                int b = Math.min(255, Math.max(0, (baseColor & 0xFF) + noise));
                
                // Add border shading
                if (x == 0 || x == 15 || y == 0 || y == 15) {
                    r = Math.max(0, r - 20);
                    g = Math.max(0, g - 20);
                    b = Math.max(0, b - 20);
                }
                
                pixels[y * 16 + x] = (255 << 24) | (r << 16) | (g << 8) | b;
            }
        }
        
        ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(256 * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();
        
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        
        org.lwjgl.system.MemoryUtil.memFree(buffer);
    }
    
    private int getBlockColor(String path) {
        if (path.contains("grass")) return 0x5D9B3A;
        if (path.contains("dirt")) return 0x8B6914;
        if (path.contains("stone")) return 0x808080;
        if (path.contains("sand")) return 0xE8D8A0;
        if (path.contains("water")) return 0x3070D0;
        if (path.contains("wood") || path.contains("log")) return 0x6B4423;
        if (path.contains("leaves")) return 0x2D6B1E;
        if (path.contains("bedrock")) return 0x2A2A2A;
        if (path.contains("cobblestone")) return 0x6B6B6B;
        if (path.contains("planks")) return 0xB8905A;
        if (path.contains("glass")) return 0xC8E8FF;
        if (path.contains("coal_ore")) return 0x3A3A3A;
        if (path.contains("iron_ore")) return 0xC8A882;
        if (path.contains("gold_ore")) return 0xFCEE4B;
        if (path.contains("diamond_ore")) return 0x5DECF5;
        if (path.contains("crafting_table")) return 0x8B5A2B;
        if (path.contains("furnace")) return 0x696969;
        if (path.contains("snow")) return 0xF8F8F8;
        if (path.contains("ice")) return 0xA5D8F0;
        if (path.contains("lava")) return 0xE25822;
        if (path.contains("gravel")) return 0x7B7B7B;
        return 0xFF00FF; // Magenta for missing textures
    }
    
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, id);
    }

    /**
     * Replace the pixel contents in place, keeping size and GL state.
     * Pixels are ARGB ints, row-major from the top-left corner. Used by
     * dynamically repainted textures such as the minimap raster.
     */
    public void update(int[] pixels) {
        if (pixels.length != width * height) return;
        ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(pixels.length * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
            buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
            buffer.put((byte) (pixel & 0xFF));         // B
            buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
        }
        buffer.flip();
        bind();
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        org.lwjgl.system.MemoryUtil.memFree(buffer);
    }
    
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    public void cleanup() {
        glDeleteTextures(id);
    }
    
    public int getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
