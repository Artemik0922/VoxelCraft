package com.voxelgame.rendering;

/**
 * Minimal growable int array: no boxing, no Iterator, no synchronization.
 * Replaces {@code List<Integer>} for mesh element indices.
 */
public final class IntList {
    private int[] data;
    private int size;

    public IntList() { this(64); }

    public IntList(int capacity) {
        data = new int[java.lang.Math.max(4, capacity)];
    }

    public void add(int v) {
        if (size == data.length) grow();
        data[size++] = v;
    }

    public int get(int i) {
        return data[i];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Exact-size copy, safe to hand to glBufferData. */
    public int[] toArray() {
        int[] out = new int[size];
        System.arraycopy(data, 0, out, 0, size);
        return out;
    }

    public void clear() {
        size = 0;
    }

    private void grow() {
        int[] bigger = new int[data.length * 2];
        System.arraycopy(data, 0, bigger, 0, size);
        data = bigger;
    }
}