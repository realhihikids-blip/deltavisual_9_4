package dev.deltavisuals.gui;

import net.minecraft.client.gui.DrawContext;

/** The Delta Visuals crystal, drawn from filled triangles (no texture). Height in pixels, width is 0.72 of it. */
public final class Logo {
    private Logo() {}

    // each facet: x1,y1, x2,y2, x3,y3 (0..1 inside the logo box), colour at the top and at the bottom of the facet
    private static final double A = 0.499, L = 0.0, R = 1.0, LL = 0.152, RR = 0.850;
    private static final double Y_SIDE = 0.342, Y_C1 = 0.376, Y_MID = 0.517, Y_C2 = 0.667, Y_BOT = 0.683;
    private static final double[][] TRI = {
        {A, 0, L, Y_SIDE, A, Y_C1},          // top left, light
        {A, 0, R, Y_SIDE, A, Y_C1},          // top right
        {L, Y_SIDE, A, Y_C1, A, Y_C2},       // upper left band
        {L, Y_SIDE, LL, Y_BOT, A, Y_C2},     // lower left band
        {R, Y_SIDE, A, Y_C1, A, Y_C2},       // upper right band
        {R, Y_SIDE, RR, Y_BOT, A, Y_C2},     // lower right band
        {LL, Y_BOT, A, 1, A, Y_C2},          // bottom left
        {RR, Y_BOT, A, 1, A, Y_C2},          // bottom right
        {A, Y_C1, 0.326, Y_MID, A, Y_C2},    // inner diamond, left half
        {A, Y_C1, 0.672, Y_MID, A, Y_C2},    // inner diamond, right half
    };
    private static final int[][] COL = {
        {0xFFE1D8FF, 0xFFB79DF6}, {0xFF7547DF, 0xFF6A3CD3}, {0xFF9563EC, 0xFF8556E4}, {0xFF6A32D8, 0xFF5424B8},
        {0xFF4A2496, 0xFF3C1D80}, {0xFF2F1668, 0xFF24105A}, {0xFF5329AE, 0xFF4A24A0}, {0xFF1F1050, 0xFF160B3A},
        {0xFFA172F2, 0xFF9266EA}, {0xFF43208A, 0xFF371A74},
    };

    public static int width(int h) { return Math.round(h * 0.72f); }

    public static void draw(DrawContext g, int x, int y, int h) {
        int w = width(h);
        Ui.round(g, x - 3, y - 2, w + 6, h + 4, 6, 0x22000000 | (Theme.accent() & 0xFFFFFF));
        for (int i = 0; i < TRI.length; i++) fill(g, x, y, w, h, TRI[i], COL[i][0], COL[i][1]);
    }

    private static void fill(DrawContext g, int x, int y, int w, int h, double[] t, int top, int bottom) {
        double[] px = {x + t[0] * w, x + t[2] * w, x + t[4] * w}, py = {y + t[1] * h, y + t[3] * h, y + t[5] * h};
        double minY = Math.min(py[0], Math.min(py[1], py[2])), maxY = Math.max(py[0], Math.max(py[1], py[2]));
        for (int row = (int) Math.floor(minY); row < (int) Math.ceil(maxY); row++) {
            double yc = row + 0.5, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int e = 0; e < 3; e++) {
                int f = (e + 1) % 3;
                double y1 = py[e], y2 = py[f];
                if ((yc < Math.min(y1, y2)) || (yc >= Math.max(y1, y2)) || y1 == y2) continue;
                double xx = px[e] + (yc - y1) / (y2 - y1) * (px[f] - px[e]);
                lo = Math.min(lo, xx); hi = Math.max(hi, xx);
            }
            if (hi <= lo) continue;
            float k = (float) Math.max(0, Math.min(1, (yc - minY) / Math.max(1e-6, maxY - minY)));
            int c = dev.deltavisuals.util.ColorUtil.lerp(top, bottom, k);
            Ui.rect(g, (int) Math.round(lo), row, Math.max(1, (int) Math.round(hi) - (int) Math.round(lo)), 1, c);
        }
    }
}
