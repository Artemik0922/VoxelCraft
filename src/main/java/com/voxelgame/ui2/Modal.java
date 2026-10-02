package com.voxelgame.ui2;

/**
 * Centre-screen confirmation card on a dark veil, kraft paper with a brass
 * frame. Built with a question line, an optional warning line and two
 * buttons. Hidden by default.
 *
 * The element spans the whole canvas; the card is centred inside it.
 */
public class Modal extends Element {

    private final String question;
    private String warning;
    private final Runnable onConfirm;
    private final boolean danger;

    private Modal(String question, String warning, Runnable onConfirm, boolean danger) {
        this.question = question;
        this.warning = warning;
        this.onConfirm = onConfirm;
        this.danger = danger;
        this.visible = false;
        this.appearSlide = 0f;
    }

    /** Adds the modal with its two buttons (confirm/cancel) and returns it. */
    public static Modal confirm(String question, String warning,
                                String confirmLabel, String cancelLabel,
                                Runnable onConfirm) {
        Modal m = new Modal(question, warning, onConfirm, true);
        Button2 yes = new Button2(confirmLabel, 100, 22, () -> {
            m.close();
            onConfirm.run();
        });
        yes.danger = true;
        m.add(yes);
        m.add(new Button2(cancelLabel, 100, 22, m::close));
        return m;
    }

    public void open() {
        visible = true;
    }

    public Modal warning(String w) {
        this.warning = w;
        return this;
    }

    public boolean isOpen() {
        return visible;
    }

    public void close() {
        visible = false;
    }

    private int cardW() {
        return Math.min(320, width - 20);
    }

    private int cardX() {
        return (width - cardW()) / 2;
    }

    private int cardY() {
        return (height - 110) / 2;
    }

    @Override
    protected boolean interactive() {
        return visible;
    }

    @Override
    public boolean mouseClicked(float mx, float my, int button) {
        if (!visible) return false;
        // The veil swallows every click; the buttons get their shot first
        boolean consumed = super.mouseClicked(mx, my, button);
        if (!consumed) close();
        return true;
    }

    @Override
    public void arrangeChildren() {
        // The modal spans the whole canvas (the root it is added to)
        if (parent != null) {
            x = 0;
            y = 0;
            width = parent.width;
            height = parent.height;
        }

        int cbw = (cardW() - 24) / 2;
        if (children.size() >= 2) {
            Element yes = children.get(0);
            Element no = children.get(1);
            yes.x = cardX() + 8;
            yes.y = cardY() + 60;
            yes.width = cbw;
            yes.height = 22;
            no.x = cardX() + cardW() - 8 - cbw;
            no.y = cardY() + 60;
            no.width = cbw;
            no.height = 22;
        }
    }

    @Override
    protected void draw(UiDraw d) {
        if (!visible) return;
        d.fill(0, 0, width, height, 0xC0261A10);

        int w = cardW(), h = 110, x = cardX(), y = cardY();
        d.paperCard(x, y, w, h);
        d.cornerRivets(x, y, w, h);

        d.textCentered(question, x + w / 2.0f, y + 12, UiTheme.INK);
        if (warning != null && !warning.isEmpty()) {
            d.textCentered(d.font.trimToWidth(warning, w - 16),
                x + w / 2.0f, y + 30, UiTheme.EMBER);
        }
    }
}
