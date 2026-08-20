package com.voxelgame.rendering.texture;

import java.awt.image.BufferedImage;

/** A 16x16 procedural texture generator. */
@FunctionalInterface
public interface TextureGenerator {
    BufferedImage generate();
}
