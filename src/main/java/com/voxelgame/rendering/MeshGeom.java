package com.voxelgame.rendering;

/**
 * CPU-side vertex data for one mesh, built by the greedy mesher before any
 * GL upload. All arrays are primitive grows so a chunk rebuild allocates no
 * boxed objects.
 */
public final class MeshGeom {
    public final FloatList positions;
    public final FloatList texCoords;
    public final FloatList normals;
    public final FloatList colors;
    public final FloatList ao;
    /** Sway weight per vertex; 0 keeps the vertex pinned. */
    public final FloatList wave;
    public final FloatList layers;
    /** Emissive per vertex; 0 = normal, 1 = glowing. */
    public final FloatList emissive;
    /** Block light per vertex (torch contribution), 0..1. */
    public final FloatList blockLight;
    public final IntList indices;

    public MeshGeom() {
        positions = new FloatList();
        texCoords = new FloatList();
        normals = new FloatList();
        colors = new FloatList();
        ao = new FloatList();
        wave = new FloatList();
        layers = new FloatList();
        emissive = new FloatList();
        blockLight = new FloatList();
        indices = new IntList();
    }

    public boolean isEmpty() {
        return positions.size() == 0;
    }

    /** Reuses the same growable arrays for a fresh bake, avoiding churn. */
    public void reset() {
        positions.clear();
        texCoords.clear();
        normals.clear();
        colors.clear();
        ao.clear();
        wave.clear();
        layers.clear();
        emissive.clear();
        blockLight.clear();
        indices.clear();
    }
}