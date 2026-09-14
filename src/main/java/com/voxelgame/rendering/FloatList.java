package com.voxelgame.rendering;

/**
 * Minimal growable float array: no boxing, no Iterator, no synchronization.
 * Replaces {@code List<Float>} in the chunk mesher so per-vertex data never
 * allocates boxed objects.
 */
public final class FloatList {
    private float[] data;
    private int size;

    public FloatList() { this(64); }

    public FloatList(int capacity) {
        data = new float[java.lang.Math.max(4, capacity)];
    }

    public void add(float v) {
        if (size == data.length) grow();
        data[size++] = v;
    }

    public void addAll(FloatList other) {
        for (int i = 0; i < other.size; i++) add(other.data[i]);
    }

    public float get(int i) {
        return data[i];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Exact-size copy, safe to hand to glBufferData. */
    public float[] toArray() {
        float[] out = new float[size];
        System.arraycopy(data, 0, out, 0, size);
        return out;
    }

    public void clear() {
        size = 0;
    }

    private void grow() {
        float[] bigger = new float[data.length * 2];
        System.arraycopy(data, 0, bigger, 0, size);
        data = bigger;
    }
}