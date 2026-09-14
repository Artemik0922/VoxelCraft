package com.voxelgame.rendering;

import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL Mesh with a VAO and one buffer per vertex attribute.
 *
 * The chunk path uploads straight from {@link MeshGeom} (primitive arrays, no
 * boxing) and re-uploads into the same buffers on rebuild instead of
 * deleting and re-creating GL objects every frame.
 */
public class Mesh {
    private int vaoId;
    private int[] vboIds = new int[ATTRIBUTE_COUNT];
    private int eboId;
    private int indexCount;

    // Vertex attribute locations
    private static final int ATTRIBUTE_COUNT = 9;
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

    /** Components per attribute location, in location order. */
    private static final int[] COMPONENTS = {3, 2, 3, 3, 1, 1, 1, 1, 1};

    private static final FloatList EMPTY_F = new FloatList(0);

    public Mesh(MeshGeom geom) {
        vaoId = glGenVertexArrays();
        upload(geom);
    }

    /**
     * Re-upload new vertex data into the existing buffers. Uses the same
     * attribute layout as the chunk mesher (all nine attributes present), so
     * pointer setup from the initial creation stays valid.
     */
    public void upload(MeshGeom geom) {
        indexCount = geom.indices.size();
        glBindVertexArray(vaoId);
        fillVBO(POS_LOC, geom.positions);
        fillVBO(TEXCOORD_LOC, geom.texCoords);
        fillVBO(NORMAL_LOC, geom.normals);
        fillVBO(COLOR_LOC, geom.colors);
        fillVBO(LAYER_LOC, geom.layers);
        fillVBO(AO_LOC, geom.ao);
        fillVBO(WAVE_LOC, geom.wave);
        fillVBO(EMISSIVE_LOC, geom.emissive);
        fillVBO(BLOCKLIGHT_LOC, geom.blockLight);

        if (eboId == 0) {
            eboId = glGenBuffers();
        }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, geom.indices.toArray(), GL_STATIC_DRAW);
        glBindVertexArray(0);
    }

    /**
     * Upload CPU geometry into {@code existing} if present and non-empty,
     * otherwise clean up unused geometry and return a fresh (or null) mesh.
     * Lets a RenderChunk keep its GPU objects across rebuilds.
     */
    public static Mesh rebind(Mesh existing, MeshGeom geom) {
        if (geom == null || geom.isEmpty()) {
            if (existing != null) existing.cleanup();
            return null;
        }
        if (existing != null) {
            existing.upload(geom);
            return existing;
        }
        return new Mesh(geom);
    }

    private void fillVBO(int loc, FloatList data) {
        if (data == null || data.isEmpty()) return;
        int vbo = vboIds[loc];
        if (vbo == 0) {
            vbo = glGenBuffers();
            vboIds[loc] = vbo;
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glVertexAttribPointer(loc, COMPONENTS[loc], GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(loc);
        } else {
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
        }
        glBufferData(GL_ARRAY_BUFFER, data.toArray(), GL_STATIC_DRAW);
    }

    private static FloatList toFloatList(List<Float> src) {
        if (src == null || src.isEmpty()) return EMPTY_F;
        FloatList out = new FloatList(src.size());
        for (float f : src) out.add(f);
        return out;
    }

    // ---- Legacy List-based constructors (item previews, doors) -----------

    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals, List<Float> colors, List<Integer> indices) {
        this(buildGeom(positions, texCoords, normals, colors, null, null, null, null, null, indices));
    }

    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Integer> indices) {
        this(buildGeom(positions, texCoords, normals, colors, layers, null, null, null, null, indices));
    }

    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Float> ao,
                List<Float> wave, List<Integer> indices) {
        this(buildGeom(positions, texCoords, normals, colors, layers, ao, wave, null, null, indices));
    }

    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Float> ao,
                List<Float> wave, List<Float> emissive, List<Integer> indices) {
        this(buildGeom(positions, texCoords, normals, colors, layers, ao, wave, emissive, null, indices));
    }

    public Mesh(List<Float> positions, List<Float> texCoords, List<Float> normals,
                List<Float> colors, List<Float> layers, List<Float> ao,
                List<Float> wave, List<Float> emissive, List<Float> blockLight,
                List<Integer> indices) {
        this(buildGeom(positions, texCoords, normals, colors, layers, ao, wave, emissive, blockLight, indices));
    }

    private static MeshGeom buildGeom(List<Float> positions, List<Float> texCoords,
                                      List<Float> normals, List<Float> colors, List<Float> layers,
                                      List<Float> ao, List<Float> wave, List<Float> emissive,
                                      List<Float> blockLight, List<Integer> indices) {
        MeshGeom g = new MeshGeom();
        g.positions.addAll(toFloatList(positions));
        g.texCoords.addAll(toFloatList(texCoords));
        g.normals.addAll(toFloatList(normals));
        g.colors.addAll(toFloatList(colors));
        if (layers != null && !layers.isEmpty()) g.layers.addAll(toFloatList(layers));
        if (ao != null && !ao.isEmpty()) g.ao.addAll(toFloatList(ao));
        if (wave != null && !wave.isEmpty()) g.wave.addAll(toFloatList(wave));
        if (emissive != null && !emissive.isEmpty()) g.emissive.addAll(toFloatList(emissive));
        if (blockLight != null && !blockLight.isEmpty()) g.blockLight.addAll(toFloatList(blockLight));
        if (indices != null && !indices.isEmpty()) {
            for (int i : indices) g.indices.add(i);
        }
        return g;
    }

    public void render() {
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        for (int i = 0; i < ATTRIBUTE_COUNT; i++) {
            if (vboIds[i] != 0) {
                glDeleteBuffers(vboIds[i]);
                vboIds[i] = 0;
            }
        }
        if (eboId != 0) {
            glDeleteBuffers(eboId);
            eboId = 0;
        }
        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
        vaoId = 0;
    }

    public int getIndexCount() { return indexCount; }
}