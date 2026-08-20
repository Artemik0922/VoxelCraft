package com.voxelgame.world;

/**
 * [SD] A sliding door: a 2-cell panel (bottom cell x,y,z + top cell y+1)
 * that slides sideways along the wall instead of swinging on hinges.
 *
 * State and slide direction live on the BOTTOM half; the top half links to
 * it through the chunk's door meta map. Both halves are positioned from the
 * same (x, y, z) and move together.
 *
 * axis 0: panel normal is Z (slides along X, walls at x-1 / x+1).
 * axis 1: panel normal is X (slides along Z, walls at z-1 / z+1).
 * slideSign 0: slides toward +X/+Z; 1: toward -X/-Z (toward a solid wall).
 */
public class SlidingDoor {
    public static final byte STATE_CLOSED  = 0;
    public static final byte STATE_OPENING = 1;
    public static final byte STATE_OPEN    = 2;
    public static final byte STATE_CLOSING = 3;

    /** Full slide takes this long, ease-out cubic. */
    public static final float SLIDE_DURATION = 0.4f;
    /** Auto-close delay after the last trigger. */
    public static final float CLOSE_DELAY = 2.5f;
    /** Retry interval while entities are still in the doorway. */
    public static final float RETRY_DELAY = 0.5f;
    /** Panel sits this far in front of the wall face, avoiding z-fighting. */
    public static final float WALL_OFFSET = 0.015f;
    /** Panel thickness used for the closed AABB. */
    public static final float PANEL_THICKNESS = 0.125f;

    /** World coords of the BOTTOM half. */
    public final int x;
    public final int y;
    public final int z;

    public final byte axis;      // 0 = normal Z, 1 = normal X
    public final byte slideSign; // 0 = +dir, 1 = -dir

    private byte state = STATE_CLOSED;
    /** 0..1 progress: OPENING rises 0->1, CLOSING falls 1->0. */
    private float anim = 0f;
    private float closeTimer = 0f;
    private boolean soundPlayed = false;

    public SlidingDoor(int x, int y, int z, int axis, int slideSign) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.axis = (byte) axis;
        this.slideSign = (byte) slideSign;
    }

    public static SlidingDoor fromMeta(int x, int y, int z, byte meta) {
        SlidingDoor d = new SlidingDoor(x, y, z, (meta >> 4) & 1, (meta >> 5) & 1);
        d.setState((byte) (meta & 3));
        d.anim = d.state == STATE_OPEN || d.state == STATE_CLOSING ? 1f : 0f;
        return d;
    }

    /** Packed meta: bits 0-1 state, bit 4 axis, bit 5 slideSign. */
    public byte toMeta() {
        return (byte) ((state & 3) | ((axis & 1) << 4) | ((slideSign & 1) << 5));
    }

    public byte getState() { return state; }
    public void setState(byte s) { state = s; }

    /** True while the doorway is passable (collision off). */
    public boolean isPassable() { return state != STATE_CLOSED; }

    /** 0..1 eased slide amount; 0 = flush panel, 1 = fully slid into the wall. */
    public float slideAmount() {
        float t = anim;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float e = 1f - (1f - t) * (1f - t) * (1f - t); // ease-out cubic
        return e;
    }

    /** Signed world-space offset of the panel along the wall (-1..1). */
    public float slideOffset() {
        float dir = slideSign == 0 ? 1f : -1f;
        return dir * slideAmount();
    }

    public void startOpen() {
        if (state == STATE_OPEN) return;
        state = STATE_OPENING; // CLOSING mid-flight reverses smoothly
        closeTimer = 0f;
    }

    public void startClose() {
        if (state == STATE_CLOSED) return;
        state = STATE_CLOSING;
        closeTimer = 0f;
    }

    /** Step animation; returns the new state when it transitions. */
    public byte update(float dt) {
        byte before = state;
        switch (state) {
            case STATE_OPENING:
                anim = Math.min(1f, anim + dt / SLIDE_DURATION);
                if (anim >= 1f) {
                    state = STATE_OPEN;
                    closeTimer = CLOSE_DELAY;
                }
                break;
            case STATE_OPEN:
                closeTimer -= dt;
                break;
            case STATE_CLOSING:
                anim = Math.max(0f, anim - dt / SLIDE_DURATION);
                if (anim <= 0f) state = STATE_CLOSED;
                break;
            default:
                break;
        }
        return state == before ? -1 : state;
    }

    public float getCloseTimer() { return closeTimer; }
    public void setCloseTimer(float t) { closeTimer = t; }
    public boolean isSoundPending() { return !soundPlayed; }
    public void markSoundPlayed() { soundPlayed = true; }
    public void resetSound() { soundPlayed = false; }

    @Override
    public String toString() {
        return "SlidingDoor(" + x + "," + y + "," + z + " axis=" + axis
            + " sign=" + slideSign + " state=" + state + " anim=" + anim + ")";
    }
}