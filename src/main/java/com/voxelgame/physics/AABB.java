package com.voxelgame.physics;

import org.joml.*;

public class AABB {
    public Vector3f min;
    public Vector3f max;
    
    public AABB(Vector3f min, Vector3f max) {
        this.min = new Vector3f(min);
        this.max = new Vector3f(max);
    }
    
    public AABB(float x, float y, float z, float w, float h, float d) {
        this.min = new Vector3f(x, y, z);
        this.max = new Vector3f(x + w, y + h, z + d);
    }
    
    public boolean intersects(AABB other) {
        return min.x < other.max.x && max.x > other.min.x &&
               min.y < other.max.y && max.y > other.min.y &&
               min.z < other.max.z && max.z > other.min.z;
    }
    
    public AABB expand(float x, float y, float z) {
        Vector3f newMin = new Vector3f(min);
        Vector3f newMax = new Vector3f(max);
        
        if (x < 0) newMin.x += x;
        if (x > 0) newMax.x += x;
        if (y < 0) newMin.y += y;
        if (y > 0) newMax.y += y;
        if (z < 0) newMin.z += z;
        if (z > 0) newMax.z += z;
        
        return new AABB(newMin, newMax);
    }
    
    public boolean contains(Vector3f point) {
        return point.x >= min.x && point.x <= max.x &&
               point.y >= min.y && point.y <= max.y &&
               point.z >= min.z && point.z <= max.z;
    }
}
