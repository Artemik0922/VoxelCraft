package com.voxelgame.ui2;

/**
 * Layout containers. Each positions its visible children inside its own
 * bounds when {@link Element#arrangeChildren()} runs; sizes given to
 * children at build time are respected, zero sizes shrink-wrap.
 */
public final class Layouts {

    private Layouts() {
    }

    /** Vertical stack with a fixed gap and horizontal alignment. */
    public static class Column extends Element {
        @Override
        protected void draw(UiDraw d) {
        }

        public enum Align { LEFT, CENTER, RIGHT, FILL }

        public int gap;
        public Align align = Align.CENTER;
        public int padTop, padBottom, padLeft, padRight;

        public Column gap(int g) { this.gap = g; return this; }

        public Column align(Align a) { this.align = a; return this; }

        public Column pad(int all) {
            padTop = padBottom = padLeft = padRight = all;
            return this;
        }

        @Override
        public void arrangeChildren() {
            int innerX = x + padLeft;
            int innerW = width - padLeft - padRight;
            int cy = y + padTop;
            for (Element c : children) {
                if (!c.visible) continue;
                switch (align) {
                    case LEFT -> c.x = innerX;
                    case CENTER -> c.x = x + (width - c.width) / 2;
                    case RIGHT -> c.x = x + width - padRight - c.width;
                    case FILL -> {
                        c.x = innerX;
                        c.width = innerW;
                    }
                }
                c.y = cy;
                cy += c.height + gap;
            }
            if (height == 0) {
                height = cy - gap + padBottom - y;
            }
        }
    }

    /** Horizontal row with a fixed gap and vertical alignment. */
    public static class Row extends Element {
        @Override
        protected void draw(UiDraw d) {
        }

        public enum Align { TOP, CENTER, BOTTOM, FILL }

        public int gap;
        public Align align = Align.CENTER;

        public Row gap(int g) { this.gap = g; return this; }

        public Row align(Align a) { this.align = a; return this; }

        @Override
        public void arrangeChildren() {
            int cx = x;
            for (Element c : children) {
                if (!c.visible) continue;
                switch (align) {
                    case TOP -> c.y = y;
                    case CENTER -> c.y = y + (height - c.height) / 2;
                    case BOTTOM -> c.y = y + height - c.height;
                    case FILL -> {
                        c.y = y;
                        c.height = height;
                    }
                }
                c.x = cx;
                cx += c.width + gap;
            }
            if (width == 0) {
                int last = children.size() - 1;
                while (last >= 0 && !children.get(last).visible) last--;
                width = last >= 0 ? children.get(last).x + children.get(last).width - x : 0;
            }
        }
    }

    /** All children share the element's bounds, centred if larger is zero. */
    public static class Stack extends Element {
        @Override
        protected void draw(UiDraw d) {
        }

        @Override
        public void arrangeChildren() {
            for (Element c : children) {
                if (!c.visible) continue;
                if (c.width == 0 || c.height == 0) continue;
                if (c.x == 0 && c.y == 0) {
                    c.x = x + (width - c.width) / 2;
                    c.y = y + (height - c.height) / 2;
                }
            }
        }
    }

    /** Invisible spacer; height participates in Column/Row flows. */
    public static class Spacer extends Element {
        public Spacer(int h) {
            height = h;
        }

        @Override
        protected void draw(UiDraw d) {
        }
    }
}
