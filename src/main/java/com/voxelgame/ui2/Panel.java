package com.voxelgame.ui2;

/**
 * Workshop card: a deep wood board with a brass hairline just inside the
 * edge and rivets in the corners. Children are positioned by hand in the
 * scene's build() (their coordinates are relative to the screen).
 */
public class Panel extends Element {

    public boolean rivets = true;
    public boolean brassFrame = true;

    public Panel(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    @Override
    protected void draw(UiDraw d) {
        d.card(x, y, width, height);
        if (brassFrame) {
            int brass = UiTheme.fade(UiTheme.BRASS_DIM, 0.8f);
            d.ui.drawRectOutline(x + d.offX + 2, y + d.offY + 2,
                width - 4, height - 4, d.aAlpha(brass));
        }
        if (rivets) {
            d.cornerRivets(x, y, width, height);
        }
    }
}
