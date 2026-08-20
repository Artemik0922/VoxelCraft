package com.voxelgame.rendering;

import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL Mesh with VAO, position, texCoord, normal, and color buffers
 */
public class Mesh {
    private int vaoId;
    private int posVbo;
    private int texCoordVbo;
    private int normalVbo;
    private int colorVbo;
private int layerVbo = 0;
    private int aoVbo = 0;
    private int waveVbo = 0;
    private int emissiveVbo = 0;
    private int blockLightVbo = 0;
    private int eboId;
    private int indexCount;
    
    // Vertex attribute locations
    private static final int POS_LOC = 0;
    private static final int TEXCOORD_LOC = 1;
    private static final int NORMAL_LOC = 2;
    private static final int COLOR_LOC = 3;
    private static final int LAYER_LOC = 4;
    private static final int AO_LOC = 5;
    /** How freely a vertex may sway: 0 = pinned, 1 = full movement. */
    private static final int WAVE_LOC = 6;
    /** Emissive: 0 = normal, 1 = glowing. */
    private static final int EMISSIVE_LOC = 7;
    /** Block light (torch contribution): 0..1. */
    private static final int BLOCKLIGHT_LOC = 8;
    
    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals, List<Float> colors, List<Integer> indices) {
        this(positions, texCoords, normals, colors, null, null, null, indices);
    }
    
    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Integer> indices) {
        this(positions, texCoords, normals, colors, layers, null, null, indices);
    }
    
    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Float> ao,
                List<Float> wave, List<Integer> indices) {
        this(positions, texCoords, normals, colors, layers, ao, wave, null, indices);
    }

    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Float> ao,
                List<Float> wave, List<Float> emissive, List<Integer> indices) {
        this(positions, texCoords, normals, colors, layers, ao, wave, emissive, null, indices);
    }

    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Float> ao,
                List<Float> wave, List<Float> emissive, List<Float> blockLight,
                List<Integer> indices) {
        indexCount = indices.size();

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // Position buffer
        posVbo = createVBO(positions, POS_LOC, 3);

        // Texture coordinate buffer
        texCoordVbo = createVBO(texCoords, TEXCOORD_LOC, 2);

        // Normal buffer
        normalVbo = createVBO(normals, NORMAL_LOC, 3);

        // Color/AO buffer
        colorVbo = createVBO(colors, COLOR_LOC, 3);

        // Texture array layer index (one float per vertex)
        if (layers != null && !layers.isEmpty()) {
            layerVbo = createVBO(layers, LAYER_LOC, 1);
        }

        // Per-vertex ambient occlusion, kept separate from the light term so
        // the shader can weight them independently
        if (ao != null && !ao.isEmpty()) {
            aoVbo = createVBO(ao, AO_LOC, 1);
        }

        // Sway weight: zero for solid blocks, tapering to 1 at plant tips
        if (wave != null && !wave.isEmpty()) {
            waveVbo = createVBO(wave, WAVE_LOC, 1);
        }

        // Emissive: 1.0 for glowing blocks, 0.0 for normal
        if (emissive != null && !emissive.isEmpty()) {
            emissiveVbo = createVBO(emissive, EMISSIVE_LOC, 1);
        }

        // Block light: torch contribution to this vertex's light
        if (blockLight != null && !blockLight.isEmpty()) {
            blockLightVbo = createVBO(blockLight, BLOCKLIGHT_LOC, 1);
        }

        // Index buffer
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) indexArray[i] = indices.get(i);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexArray, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }
    
    private int createVBO(List<Float> data, int attribLoc, int components) {
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        float[] arr = new float[data.size()];
        for (int i = 0; i < data.size(); i++) arr[i] = data.get(i);
        glBufferData(GL_ARRAY_BUFFER, arr, GL_STATIC_DRAW);
        glVertexAttribPointer(attribLoc, components, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(attribLoc);
        return vbo;
    }
    
    public void render() {
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }
    
    public void cleanup() {
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glDeleteBuffers(posVbo);
        glDeleteBuffers(texCoordVbo);
        glDeleteBuffers(normalVbo);
        glDeleteBuffers(colorVbo);
        if (layerVbo != 0) glDeleteBuffers(layerVbo);
        if (aoVbo != 0) glDeleteBuffers(aoVbo);
        if (waveVbo != 0) glDeleteBuffers(waveVbo);
        if (emissiveVbo != 0) glDeleteBuffers(emissiveVbo);
        if (blockLightVbo != 0) glDeleteBuffers(blockLightVbo);
        glDeleteBuffers(eboId);

        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }
    
    public int getIndexCount() { return indexCount; }
}
