package com.voxelgame.world.entity;

import com.voxelgame.rendering.Shader;
import com.voxelgame.rendering.model.ModelBox;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Part-based models for the farm and wild animals. Model space is Y down,
 * origin at the body centre; the base matrix (translate, rotateY, Y-flip,
 * scale) puts the feet (y = 12 px) on the ground. Legs swing with the
 * vanilla quadruped formula, chickens and parrots bob their heads, foxes
 * sit with a folded hind pair and a big swaying tail.
 *
 * Sheet layout per type lives in the matching 64x64 texture (cow.png,
 * pig.png, sheep.png, deer.png, bear.png share the quadruped layout,
 * chicken.png and parrot.png have their own, fox.png is the sitting pose).
 */
public class AnimalModel {

    private enum Pose { QUADRUPED, SITTING, CHICKEN, PARROT }

    /** A box plus the pivot it rotates about, in skin-pixel space. */
    private static class Part {
        final ModelBox box;
        final float[] pivot;
        Part(ModelBox box, float[] pivot) {
            this.box = box;
            this.pivot = pivot;
        }
    }

    private final Pose pose;
    private final List<Part> parts = new ArrayList<>();
    private Part body;
    private final List<Part> legGroupF = new ArrayList<>();
    private final List<Part> legGroupB = new ArrayList<>();
    private final List<Part> headParts = new ArrayList<>();
    private Part tail;

    private final Matrix4f scratch = new Matrix4f();

    public AnimalModel(Animal.AnimalType type) {
        switch (type) {
            case CHICKEN: pose = Pose.CHICKEN; buildChicken(); break;
            case PARROT:  pose = Pose.PARROT;  buildParrot();  break;
            case FOX:     pose = Pose.SITTING; buildFox();     break;
            case DEER:    pose = Pose.QUADRUPED; buildDeer();  break;
            case BEAR:    pose = Pose.QUADRUPED; buildBear();  break;
            default:      pose = Pose.QUADRUPED; buildQuadruped(); break;
        }
    }

    private void addPart(Part p) {
        parts.add(p);
    }

    private Part part(int u, int v, int w, int h, int d,
                      float ox, float oy, float oz) {
        return new Part(new ModelBox(u, v, w, h, d, ox, oy, oz, 0, true),
            new float[]{ox + w / 2f, oy, oz + d / 2f});
    }

    /** Standard quadruped: cow / pig / sheep (16 px long body). */
    private void buildQuadruped() {
        body = part(0, 0, 8, 8, 16, -4, -4, -8);
        addPart(body);
        Part head = part(0, 24, 8, 8, 8, -4, -8, -9);
        addPart(head);
        headParts.add(head);
        float[] xs = {-5, 1, -5, 1};
        float[] zs = {-6, -6, 2, 2};
        for (int i = 0; i < 4; i++) {
            Part leg = part(22, 40, 4, 12, 4, xs[i], 0, zs[i]);
            addPart(leg);
            (i < 2 ? legGroupF : legGroupB).add(leg);
        }
    }

    /** Deer: slim legs, a long neck, antlers that follow the head. */
    private void buildDeer() {
        body = part(0, 0, 8, 8, 16, -4, -4, -8);
        addPart(body);
        Part head = part(0, 24, 8, 8, 8, -4, -8, -9);
        addPart(head);
        headParts.add(head);
        // Antlers: two prongs on top of the head, pivoting with it
        Part antlerL = part(24, 0, 2, 6, 2, -5, -14, -7);
        Part antlerR = part(24, 0, 2, 6, 2, 3, -14, -7);
        addPart(antlerL);
        addPart(antlerR);
        headParts.add(antlerL);
        headParts.add(antlerR);
        float[] xs = {-4.5f, 1.5f, -4.5f, 1.5f};
        float[] zs = {-6, -6, 2, 2};
        for (int i = 0; i < 4; i++) {
            Part leg = part(24, 40, 3, 12, 3, xs[i], 0, zs[i]);
            addPart(leg);
            (i < 2 ? legGroupF : legGroupB).add(leg);
        }
    }

    /** Bear: a massive 12x10x14 torso, a big head with ears, thick legs. */
    private void buildBear() {
        body = part(0, 0, 12, 10, 14, -6, -4, -7);
        addPart(body);
        Part head = part(0, 24, 8, 8, 8, -4, -10, -9);
        addPart(head);
        headParts.add(head);
        Part earL = part(24, 0, 2, 2, 2, -4, -12, -7);
        Part earR = part(24, 0, 2, 2, 2, 2, -12, -7);
        addPart(earL);
        addPart(earR);
        headParts.add(earL);
        headParts.add(earR);
        float[] xs = {-5.5f, 0.5f, -5.5f, 0.5f};
        float[] zs = {-6, -6, 2, 2};
        for (int i = 0; i < 4; i++) {
            Part leg = part(24, 40, 5, 6, 5, xs[i], 6, zs[i]);
            addPart(leg);
            (i < 2 ? legGroupF : legGroupB).add(leg);
        }
    }

    /** Fox: sits upright, hind legs folded, chest forward, a big tail. */
    private void buildFox() {
        body = part(0, 0, 8, 8, 10, -4, -2, -4);
        addPart(body);
        Part head = part(0, 16, 8, 8, 6, -4, -8, -7);
        addPart(head);
        headParts.add(head);
        Part snout = part(16, 16, 4, 4, 3, -2, -6, -10);
        addPart(snout);
        headParts.add(snout);
        Part earL = part(0, 24, 2, 3, 2, -4, -12, -6);
        Part earR = part(0, 24, 2, 3, 2, 2, -12, -6);
        addPart(earL);
        addPart(earR);
        headParts.add(earL);
        headParts.add(earR);
        // Front legs stand; hind pair is folded under the sitting body
        float[] fxs = {-3, 1};
        for (float fx : fxs) {
            Part leg = part(20, 24, 2, 6, 2, fx, 6, -2);
            addPart(leg);
            legGroupF.add(leg);
        }
        for (float fx : fxs) {
            Part leg = part(28, 24, 2, 4, 2, fx, 6, 4);
            addPart(leg);
            legGroupB.add(leg);
        }
        tail = part(32, 0, 6, 6, 6, -3, -4, 6);
        addPart(tail);
    }

    /** Chicken: the classic two-legged bird with a comb. */
    private void buildChicken() {
        body = part(0, 16, 6, 7, 8, -3, -3, -4);
        addPart(body);
        Part head = part(0, 0, 4, 4, 4, -2, -6, -6);
        addPart(head);
        headParts.add(head);
        Part beak = part(0, 8, 2, 2, 2, -1, -5, -8);
        addPart(beak);
        headParts.add(beak);
        for (int i = 0; i < 2; i++) {
            Part wing = part(16, 8, 2, 4, 6, i == 0 ? -5 : 3, -2, -3);
            addPart(wing);
            legGroupB.add(wing); // wings flap with the back-leg group
        }
        for (int i = 0; i < 2; i++) {
            Part leg = part(32, 0, 2, 8, 2, i == 0 ? -1 : 1, 4, -1);
            addPart(leg);
            legGroupF.add(leg);
        }
    }

    /** Parrot: chicken-like, plus a long tail that sways while walking. */
    private void buildParrot() {
        body = part(0, 16, 6, 7, 8, -3, -3, -4);
        addPart(body);
        Part head = part(0, 0, 4, 4, 4, -2, -6, -6);
        addPart(head);
        headParts.add(head);
        Part beak = part(0, 8, 2, 2, 2, -1, -5, -8);
        addPart(beak);
        headParts.add(beak);
        for (int i = 0; i < 2; i++) {
            Part wing = part(16, 8, 2, 4, 6, i == 0 ? -5 : 3, -2, -3);
            addPart(wing);
            legGroupB.add(wing);
        }
        for (int i = 0; i < 2; i++) {
            Part leg = part(32, 0, 2, 8, 2, i == 0 ? -1 : 1, 4, -1);
            addPart(leg);
            legGroupF.add(leg);
        }
        tail = part(24, 0, 4, 6, 4, -2, -2, 4);
        addPart(tail);
    }

    private void renderPart(Part part, float rotX,
                            Matrix4f base, Matrix4f viewProj, Shader shader) {
        final float P = 1.0f / 16.0f;
        scratch.identity();
        scratch.translate(part.pivot[0] * P, part.pivot[1] * P, part.pivot[2] * P);
        scratch.rotateX(-rotX); // negate due to Y-flip
        scratch.translate(-part.pivot[0] * P, -part.pivot[1] * P, -part.pivot[2] * P);

        Matrix4f mvp = viewProj.mul(base, new Matrix4f()).mul(scratch);
        shader.setUniformMat4("mvp", mvp);
        part.box.render();
    }

    public void render(Matrix4f base, Matrix4f viewProj, Shader shader,
                       float limbSwing, float limbSwingAmount) {
        switch (pose) {
            case QUADRUPED:
                renderQuadruped(base, viewProj, shader, limbSwing, limbSwingAmount);
                break;
            case SITTING:
                renderSitting(base, viewProj, shader, limbSwing, limbSwingAmount);
                break;
            case CHICKEN:
                renderChickenLike(base, viewProj, shader, limbSwing, limbSwingAmount, 0);
                break;
            case PARROT:
                renderChickenLike(base, viewProj, shader, limbSwing, limbSwingAmount, 1);
                break;
        }
    }

    private void renderQuadruped(Matrix4f base, Matrix4f viewProj, Shader shader,
                                 float limbSwing, float limbSwingAmount) {
        float a = limbSwingAmount;
        // Front pair swings opposite the back pair
        float frontRot = (float) Math.cos(limbSwing * 0.6662f) * 1.2f * a;
        float backRot = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 1.2f * a;

        for (Part leg : legGroupF) renderPart(leg, frontRot, base, viewProj, shader);
        for (Part leg : legGroupB) renderPart(leg, backRot, base, viewProj, shader);
        renderPart(body, 0.06f, base, viewProj, shader);
        // Head bob with the walk: gentle nod around the neck joint
        float headBob = (float) Math.sin(limbSwing * 0.6662f) * 0.08f * a;
        for (Part hp : headParts) renderPart(hp, headBob, base, viewProj, shader);
    }

    private void renderSitting(Matrix4f base, Matrix4f viewProj, Shader shader,
                               float limbSwing, float limbSwingAmount) {
        float a = limbSwingAmount;
        float frontRot = (float) Math.cos(limbSwing * 0.6662f) * 1.2f * a;
        for (Part leg : legGroupF) renderPart(leg, frontRot, base, viewProj, shader);
        for (Part leg : legGroupB) renderPart(leg, -0.4f * a, base, viewProj, shader);
        renderPart(body, 0.02f, base, viewProj, shader);
        float headBob = (float) Math.sin(limbSwing * 0.6662f) * 0.1f * a;
        for (Part hp : headParts) renderPart(hp, headBob, base, viewProj, shader);
        // The tail sways lazily with the walk
        float tailSway = (float) Math.sin(limbSwing * 0.6662f) * 0.25f * a;
        renderPart(tail, tailSway, base, viewProj, shader);
    }

    private void renderChickenLike(Matrix4f base, Matrix4f viewProj, Shader shader,
                                   float limbSwing, float limbSwingAmount, int withTail) {
        float a = limbSwingAmount;
        float legRot = (float) Math.cos(limbSwing * 0.6662f) * 1.2f * a;

        for (Part leg : legGroupF) renderPart(leg, legRot, base, viewProj, shader);
        renderPart(body, 0, base, viewProj, shader);
        for (Part wing : legGroupB) renderPart(wing, 0.08f * a, base, viewProj, shader);
        // Head bobs around the neck joint while walking
        float headBob = (float) Math.sin(limbSwing * 0.6662f) * 0.25f * a;
        for (Part hp : headParts) renderPart(hp, headBob, base, viewProj, shader);
        if (withTail != 0) {
            float tailSway = (float) Math.sin(limbSwing * 0.6662f) * 0.15f * a;
            renderPart(tail, tailSway, base, viewProj, shader);
        }
    }

    public void cleanup() {
        for (Part p : parts) p.box.cleanup();
    }
}