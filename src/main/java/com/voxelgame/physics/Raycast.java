package com.voxelgame.physics;

import com.voxelgame.world.World;
import org.joml.*;

public class Raycast {
    
    public static class Hit {
        public int x, y, z;
        public int placeX, placeY, placeZ;
        public int face; // 0=+X, 1=-X, 2=+Y, 3=-Y, 4=+Z, 5=-Z
        
        public Hit(int x, int y, int z, int face) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
            
            // Calculate placement position (adjacent block)
            switch (face) {
                case 0: placeX = x + 1; placeY = y; placeZ = z; break;
                case 1: placeX = x - 1; placeY = y; placeZ = z; break;
                case 2: placeX = x; placeY = y + 1; placeZ = z; break;
                case 3: placeX = x; placeY = y - 1; placeZ = z; break;
                case 4: placeX = x; placeY = y; placeZ = z + 1; break;
                case 5: placeX = x; placeY = y; placeZ = z - 1; break;
                default: placeX = x; placeY = y; placeZ = z;
            }
        }
    }
    
    /**
     * DDA raycasting algorithm for voxel worlds
     */
    public static Hit cast(Vector3f origin, Vector3f direction, World world, float maxDistance) {
        return cast(origin, direction, world, maxDistance, false);
    }

    /**
     * [GP-020] Raycast that stops at ANY block, including non-solid ones
     * (flowers, grass, open doors). Used for interaction so clickable
     * blocks can never be missed because the ray walked straight through
     * their hitbox.
     */
    public static Hit castInteractive(Vector3f origin, Vector3f direction, World world, float maxDistance) {
        return cast(origin, direction, world, maxDistance, true);
    }

    private static Hit cast(Vector3f origin, Vector3f direction, World world,
                            float maxDistance, boolean stopAtNonSolid) {
        direction.normalize();
        
        // Current block position
        int x = (int) java.lang.Math.floor(origin.x);
        int y = (int) java.lang.Math.floor(origin.y);
        int z = (int) java.lang.Math.floor(origin.z);
        
        // Step direction
        int stepX = direction.x > 0 ? 1 : (direction.x < 0 ? -1 : 0);
        int stepY = direction.y > 0 ? 1 : (direction.y < 0 ? -1 : 0);
        int stepZ = direction.z > 0 ? 1 : (direction.z < 0 ? -1 : 0);
        
        // Distance to next block boundary
        double tMaxX = stepX > 0 ? (x + 1 - origin.x) / direction.x : 
                      stepX < 0 ? (x - origin.x) / direction.x : Double.MAX_VALUE;
        double tMaxY = stepY > 0 ? (y + 1 - origin.y) / direction.y : 
                      stepY < 0 ? (y - origin.y) / direction.y : Double.MAX_VALUE;
        double tMaxZ = stepZ > 0 ? (z + 1 - origin.z) / direction.z : 
                      stepZ < 0 ? (z - origin.z) / direction.z : Double.MAX_VALUE;
        
        // Distance to cross one block
        double tDeltaX = java.lang.Math.abs(1.0 / direction.x);
        double tDeltaY = java.lang.Math.abs(1.0 / direction.y);
        double tDeltaZ = java.lang.Math.abs(1.0 / direction.z);
        
        double distance = 0;
        int lastFace = -1;
        
        while (distance < maxDistance) {
            // Check if current block is solid
            if (stopAtNonSolid) {
                if (world.getBlock(x, y, z) != 0) {
                    return new Hit(x, y, z, lastFace);
                }
            } else if (world.isSolid(x, y, z)) {
                return new Hit(x, y, z, lastFace);
            }
            
            // Move to next block
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    distance = tMaxX;
                    x += stepX;
                    tMaxX += tDeltaX;
                    lastFace = stepX > 0 ? 1 : 0; // -X or +X
                } else {
                    distance = tMaxZ;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                    lastFace = stepZ > 0 ? 5 : 4; // -Z or +Z
                }
            } else {
                if (tMaxY < tMaxZ) {
                    distance = tMaxY;
                    y += stepY;
                    tMaxY += tDeltaY;
                    lastFace = stepY > 0 ? 3 : 2; // -Y or +Y
                } else {
                    distance = tMaxZ;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                    lastFace = stepZ > 0 ? 5 : 4;
                }
            }
        }
        
        return null;
    }
}
