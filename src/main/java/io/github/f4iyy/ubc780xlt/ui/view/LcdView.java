package io.github.f4iyy.ubc780xlt.ui.view;

import io.github.f4iyy.ubc780xlt.protocol.lcd.Icon;
import io.github.f4iyy.ubc780xlt.protocol.lcd.Indicator;
import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdState;
import io.github.f4iyy.ubc780xlt.ui.lcd.SevenSegment;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * Replica of the BC780XLT display, drawn in vectors on a canvas: seven-segment channel, frequency and
 * CTCSS/DCS, every icon (lit, off or flashing), trunk bar, signal meter, bank numbers and the two
 * 16-character text lines. Scales with the available space.
 */
public final class LcdView extends Region {

    private static final double W = 780;
    private static final double H = 372;
    private static final double PREFERRED_WIDTH = 540;

    private static final Color GLASS = Color.web("#04141c");
    private static final Color LIT = Color.web("#58dcff");
    private static final Color DIM = Color.web("#0d3140");
    private static final Color FRAME = Color.web("#1b2630");

    private final Canvas canvas = new Canvas();
    private final ObjectProperty<LcdState> state = new SimpleObjectProperty<>(LcdState.blank());
    private boolean blinkPhase = true;
    private final Timeline blink = new Timeline(new KeyFrame(Duration.millis(500), e -> {
        blinkPhase = !blinkPhase;
        draw();
    }));

    public LcdView() {
        getChildren().add(canvas);
        state.addListener((obs, old, now) -> draw());
        blink.setCycleCount(Timeline.INDEFINITE);
        blink.play();
        setMinSize(W / 2, H / 2);
    }

    public ObjectProperty<LcdState> stateProperty() {
        return state;
    }

    /** Stops the flashing animation (call when the window closes). */
    public void dispose() {
        blink.stop();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    @Override
    public javafx.geometry.Orientation getContentBias() {
        return javafx.geometry.Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefWidth(double height) {
        return height > 0 ? height * W / H : PREFERRED_WIDTH;
    }

    @Override
    protected double computePrefHeight(double width) {
        return (width > 0 ? width : PREFERRED_WIDTH) * H / W;
    }

    /** Draws the display into any graphics context, at the logical size {@code 780 x 372}. */
    public static void render(GraphicsContext gc, LcdState s, boolean blinkPhase) {
        gc.setFill(FRAME);
        gc.fillRoundRect(0, 0, W, H, 18, 18);
        gc.setFill(GLASS);
        gc.fillRoundRect(8, 8, W - 16, H - 16, 12, 12);

        // Row 1: priority, channel, status icons, bank numbers
        letter(gc, "P", 22, 62, 46, s.icon(Icon.P), blinkPhase);
        segments(gc, pad(s.channel(), 3), 66, 22, 34, 58, 5, false);

        double x = 250;
        for (Icon icon : new Icon[] {Icon.BANK, Icon.LIST, Icon.SCAN, Icon.SRCH, Icon.HOLD, Icon.PRI, Icon.DLY,
                Icon.LOUT, Icon.LINE, Icon.DATA}) {
            x += icon(gc, label(icon), x, 38, s.icon(icon), blinkPhase) + 14;
        }
        for (int i = 0; i < LcdState.BANKS; i++) {
            char c = i < s.banks().length() ? s.banks().charAt(i) : '-';
            Indicator ind = Indicator.fromSymbol(c);
            icon(gc, Integer.toString(i + 1 == 10 ? 10 : i + 1), 250 + i * 25.0, 58, ind, blinkPhase);
        }
        x = 400;
        for (Icon icon : new Icon[] {Icon.FREQ, Icon.CHAN, Icon.ALPHA, Icon.RMT, Icon.AUTO, Icon.ATT, Icon.ALT}) {
            x += icon(gc, label(icon), x, 80, s.icon(icon), blinkPhase) + 14;
        }

        // Row 2: trunk type icons and the activity bar
        double tx = 22;
        for (Icon icon : new Icon[] {Icon.L, Icon.M, Icon.E, Icon.TRUNK}) {
            tx += icon(gc, label(icon), tx, 106, s.icon(icon), blinkPhase) + 8;
        }
        String bar = s.bar();
        for (int i = 0; i < LcdState.BAR_SEGMENTS; i++) {
            char c = i < bar.length() ? bar.charAt(i) : '-';
            gc.setFill(Indicator.fromSymbol(c).isLit(blinkPhase) ? LIT : DIM);
            gc.fillRect(190 + i * 19.0, 92, 15, 14);
        }

        // Row 3: frequency, modulation, CTCSS/DCS, key lock, signal meter
        segments(gc, s.frequency(), 22, 122, 40, 70, 6, true);
        double mx = 478;
        icon(gc, "AM", mx, 138, s.icon(Icon.AM), blinkPhase);
        icon(gc, "NFM", mx, 160, s.icon(Icon.N), blinkPhase);
        icon(gc, "FM", mx, 182, nonNfm(s), blinkPhase);
        icon(gc, "WFM", mx + 44, 182, s.icon(Icon.WFM), blinkPhase);
        icon(gc, "CTCSS", 560, 138, s.icon(Icon.CTCSS), blinkPhase);
        icon(gc, "DCS", 622, 138, s.icon(Icon.DCS), blinkPhase);
        segments(gc, s.ctc(), 560, 150, 18, 32, 3, true);
        icon(gc, "HZ", 640, 204, s.icon(Icon.HZ), blinkPhase);
        icon(gc, "⚿", 712, 138, s.icon(Icon.KLOC), blinkPhase);
        icon(gc, "S", 706, 188, s.icon(Icon.S), blinkPhase);
        int bars = s.signalBars();
        for (int i = 0; i < LcdState.SIGNAL_BARS; i++) {
            double h = 8 + i * 7;
            gc.setFill(i < bars ? LIT : DIM);
            gc.fillRect(722 + i * 8.0, 196 - h + 8, 6, h);
        }

        // Two lines of 16 dot-matrix characters
        text(gc, s.line1(), s.line1Cursor(), 22, 262, blinkPhase);
        text(gc, s.line2(), s.line2Cursor(), 22, 330, blinkPhase);
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.clearRect(0, 0, w, h);
        double scale = Math.min(w / W, h / H);
        gc.translate((w - W * scale) / 2, (h - H * scale) / 2);
        gc.scale(scale, scale);
        render(gc, state.get(), blinkPhase);
    }

    // ---- drawing helpers -------------------------------------------------------------------

    private static String label(Icon icon) {
        return switch (icon) {
            case LOUT -> "L/O";
            case SRCH -> "SEARCH";
            case TRUNK -> "TRUNK";
            default -> icon.name();
        };
    }

    /** FM lit, but not narrow FM (NFM shows both "N" and "FM"). */
    private static Indicator nonNfm(LcdState s) {
        return s.icon(Icon.N) == Indicator.OFF ? s.icon(Icon.FM) : Indicator.OFF;
    }

    private static String pad(String text, int width) {
        String t = text == null ? "" : text.strip();
        return t.length() >= width ? t : " ".repeat(width - t.length()) + t;
    }

    /** Draws a small label; returns its width. */
    private static double icon(GraphicsContext gc, String text, double x, double baseline, Indicator ind, boolean phase) {
        Font font = Font.font("System", FontWeight.BOLD, 13);
        gc.setFont(font);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(ind.isLit(phase) ? LIT : DIM);
        gc.fillText(text, x, baseline);
        Text measure = new Text(text);
        measure.setFont(font);
        return measure.getLayoutBounds().getWidth();
    }

    private static void letter(GraphicsContext gc, String text, double x, double baseline, double size, Indicator ind,
            boolean phase) {
        gc.setFont(Font.font("System", FontWeight.BOLD, size));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(ind.isLit(phase) ? LIT : DIM);
        gc.fillText(text, x, baseline);
    }

    /**
     * Draws seven-segment characters. A '.' lights the decimal point of the previous digit. Unlit segments are
     * drawn dim, like on the real glass.
     *
     * @param ghost draw the dim "8" background (the unlit segments)
     */
    private static void segments(GraphicsContext gc, String text, double x, double y, double w, double h,
            double thickness, boolean ghost) {
        String t = text == null ? "" : text;
        double cursor = x;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '.') {
                gc.setFill(LIT);
                gc.fillRect(cursor - thickness - 1, y + h - thickness, thickness, thickness);
                continue;
            }
            digit(gc, SevenSegment.segmentsFor(c), cursor, y, w, h, thickness, ghost);
            cursor += w + thickness * 2;
        }
    }

    private static void digit(GraphicsContext gc, int mask, double x, double y, double w, double h, double t,
            boolean ghost) {
        double mid = y + h / 2;
        // horizontal: a, g, d ; vertical: f, b, e, c
        seg(gc, mask, SevenSegment.A, x + t, y, w - t, t, ghost);
        seg(gc, mask, SevenSegment.G, x + t, mid - t / 2, w - t, t, ghost);
        seg(gc, mask, SevenSegment.D, x + t, y + h - t, w - t, t, ghost);
        seg(gc, mask, SevenSegment.F, x, y + t, t, h / 2 - t * 1.5, ghost);
        seg(gc, mask, SevenSegment.B, x + w, y + t, t, h / 2 - t * 1.5, ghost);
        seg(gc, mask, SevenSegment.E, x, mid + t / 2, t, h / 2 - t * 1.5, ghost);
        seg(gc, mask, SevenSegment.C, x + w, mid + t / 2, t, h / 2 - t * 1.5, ghost);
    }

    private static void seg(GraphicsContext gc, int mask, int bit, double x, double y, double w, double h,
            boolean ghost) {
        boolean lit = (mask & bit) != 0;
        if (!lit && !ghost) {
            return;
        }
        gc.setFill(lit ? LIT : DIM);
        gc.fillRoundRect(x, y, w, h, 3, 3);
    }

    /** Width of the 16 character cells and the room left to the frequency above them. */
    private static final double CELL = 46;

    private static final double TEXT_SIZE = 52;

    private static double glyphAdvance = -1;

    /** Advance of the widest capital of the text font, to stretch each character over its cell. */
    private static double glyphAdvance(Font font) {
        if (glyphAdvance < 0) {
            Text m = new Text("M");
            m.setFont(font);
            glyphAdvance = m.getLayoutBounds().getWidth();
        }
        return glyphAdvance;
    }

    /**
     * A line of 16 characters, one character per cell, left-aligned with the digits above. Each character is
     * stretched to fill about 80 % of its cell, so letters look like the tall dot-matrix characters of the scanner
     * whatever monospaced font the system has; the cursor is an underline.
     */
    private static void text(GraphicsContext gc, String line, String cursor, double x, double baseline,
            boolean phase) {
        String family = Font.getFamilies().contains("Consolas") ? "Consolas" : "Monospaced";
        Font font = Font.font(family, FontWeight.BOLD, TEXT_SIZE);
        double stretch = CELL * 0.8 / glyphAdvance(font);
        gc.setFont(font);
        gc.setTextAlign(TextAlignment.CENTER);
        String t = line == null ? "" : line;
        String c = cursor == null ? "" : cursor;
        for (int i = 0; i < LcdState.LINE_WIDTH; i++) {
            double cx = x + i * CELL + CELL / 2;
            if (i < t.length() && t.charAt(i) != ' ') {
                gc.save();
                gc.translate(cx, baseline);
                gc.scale(stretch, 1);
                gc.setFill(LIT);
                gc.fillText(String.valueOf(t.charAt(i)), 0, 0);
                gc.restore();
            }
            if (i < c.length() && c.charAt(i) != ' ' && phase) {
                gc.setFill(LIT);
                gc.fillRect(x + i * CELL + CELL * 0.1, baseline + 8, CELL * 0.8, 4);
            }
        }
    }
}
