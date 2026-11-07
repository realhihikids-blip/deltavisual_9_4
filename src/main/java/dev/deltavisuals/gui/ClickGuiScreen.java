package dev.deltavisuals.gui;

import dev.deltavisuals.config.ConfigManager;
import dev.deltavisuals.core.Bind;
import dev.deltavisuals.core.BindManager;
import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.I18n;
import dev.deltavisuals.util.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main menu: category sidebar, searchable module list, per-module settings panel (sliders, switches, mode
 * selectors, colour picker with HEX, keybind capture). All hit-testing uses regions recorded while drawing,
 * so what you see is exactly what you can click.
 */
public final class ClickGuiScreen extends Screen {
    private static Category cat = Category.HUD;
    private static Module selected;

    private static final int RH = 36, GAP = 4;
    private static final int K_CAT = 1, K_TOGGLE = 2, K_ROW = 3, K_BIND = 4, K_RESET = 5, K_BOOL = 6, K_NUM = 7,
        K_MODE = 8, K_COLOR = 9, K_ACTION = 10, K_SEARCH = 11, K_SV = 12, K_HUE = 13, K_ALPHA = 14, K_HEX = 15, K_ENABLED = 16, K_CHIP = 17;
    private static final int WARN = 0xFFFBBF24;

    /** A clickable region. (x,y,w,h) is clipped to the scroll viewport; (rx,ry,rw,rh) is the full original rect. */
    private static final class Hit {
        final int x, y, w, h, rx, ry, rw, rh, kind; final Object o;
        Hit(int x, int y, int w, int h, int rx, int ry, int rw, int rh, int kind, Object o) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.rx = rx; this.ry = ry; this.rw = rw; this.rh = rh; this.kind = kind; this.o = o;
        }
        boolean in(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final float[] catAnim = new float[Category.values().length];
    private List<Module> shown;
    private String search = "";
    private boolean searchFocus;

    private float open, page = 1f;
    private boolean closing;
    private final long openedAt = System.currentTimeMillis();
    private float listScroll, listScrollT, setScroll, setScrollT, listMax, setMax;

    private float curScale = 1f;
    private int vw, vh, oy, mx, my;
    private int wx, wy, ww, wh, side;
    private int clipTop = Integer.MIN_VALUE, clipBottom = Integer.MAX_VALUE;
    private int[] setPanel = new int[4];
    private int[] listPanel = new int[4];

    private Setting.Num dragNum; private Hit dragNumHit;
    private Setting.Color pickerFor; private float hue, sat, val;
    private Hit pickHit; private boolean hexFocus; private String hexBuf = "";

    private Bind cachedBind; private boolean cachedVanilla;

    public ClickGuiScreen() {
        super(Text.literal("Delta Visuals"));
        open = Theme.animations() ? 0f : 1f;
        refreshList();
    }

    // ---------------------------------------------------------------- state
    private void refreshList() {
        if (search.isEmpty()) { shown = Modules.byCategory(cat); return; }
        String q = search.toLowerCase(Locale.ROOT);
        List<Module> l = new ArrayList<>();
        for (Module m : Modules.all())
            if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)
                || I18n.t(m.name).toLowerCase(Locale.ROOT).contains(q) || I18n.t(m.description).toLowerCase(Locale.ROOT).contains(q)) l.add(m);
        shown = l;
        listScroll = listScrollT = 0;
    }

    private void select(Module m) {
        selected = m;
        setScroll = setScrollT = 0;
        pickerFor = null; hexFocus = false;
    }

    private void startClose() {
        if (!Theme.animations()) finish(); else closing = true;
    }

    private void finish() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        BindManager.cancel();
        ConfigManager.save();
    }

    private void addHit(int x, int y, int w, int h, int kind, Object o) {
        int y1 = Math.max(y, clipTop), y2 = Math.min(y + h, clipBottom);
        if (y2 <= y1) return;
        hits.add(new Hit(x, y1, w, y2 - y1, x, y, w, h, kind, o));
    }

    private boolean hover(int x, int y, int w, int h) {
        return my >= clipTop && my < clipBottom && Ui.in(mx, my, x, y, w, h);
    }

    // --------------------------------------------------------------- render
    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        Ui.frame();
        float target = closing ? 0f : 1f;
        open = Theme.animations() ? open + (target - open) * Math.min(1f, Ui.dt * 16f) : target;
        if (closing && open < 0.04f) { finish(); return; }

        curScale = Math.min(Theme.scale(), Math.min(width / 640f, height / 380f));
        vw = Math.round(width / curScale);
        vh = Math.round(height / curScale);
        oy = Math.round((1f - open) * 12f);
        mx = Math.round(mouseX / curScale);
        my = Math.round(mouseY / curScale) - oy;
        page = Theme.animations() ? page + (1f - page) * Math.min(1f, Ui.dt * 12f) : 1f;

        g.fill(0, 0, width, height, ColorUtil.argb(Math.round(110 * open), 0, 0, 0));
        Ui.alpha = open;
        var m = g.getMatrices();
        m.pushMatrix();
        m.scale(curScale, curScale);
        m.translate(0, oy);
        hits.clear();
        drawWindow(g);
        m.popMatrix();
        Ui.alpha = 1f;
    }

    private void drawWindow(DrawContext g) {
        ww = Math.min(780, vw - 20);
        wh = Math.min(470, vh - 20);
        wx = (vw - ww) / 2;
        wy = (vh - wh) / 2;
        side = 142;

        // soft shadow, translucent panel, thin light border and an accent line on top
        for (int i = 6; i >= 1; i--) Ui.round(g, wx - i * 2, wy - i * 2 + 4, ww + i * 4, wh + i * 4, 12 + i * 2, ColorUtil.argb(9, 0, 0, 0));
        Ui.round(g, wx, wy, ww, wh, 12, Theme.panel());
        int edge = ColorUtil.withAlpha(0xFFFFFFFF, 22);
        Ui.rect(g, wx + 12, wy, ww - 24, 1, edge);
        Ui.rect(g, wx + 12, wy + wh - 1, ww - 24, 1, edge);
        Ui.rect(g, wx, wy + 12, 1, wh - 24, edge);
        Ui.rect(g, wx + ww - 1, wy + 12, 1, wh - 24, edge);
        Ui.rect(g, wx + 24, wy, ww - 48, 1, ColorUtil.withAlpha(Theme.accent(), 170));

        drawSidebar(g);

        int mx0 = wx + side + 4;
        int mainW = wx + ww - 6 - mx0;
        drawHeader(g, mx0, mainW);

        int spw = selected != null ? Math.max(190, Math.min(280, mainW * 45 / 100)) : 0;
        int lx = mx0 + 8;
        int ly = wy + 44;
        int right = mx0 + mainW - 8 - (selected != null ? spw + 6 : 0);
        int lw = right - lx;
        int lh = wy + wh - 10 - ly;
        drawList(g, lx, ly, lw, lh);
        if (selected != null) drawSettings(g, mx0 + mainW - 8 - spw, wy + 40, spw, wy + wh - 8 - (wy + 40));
    }

    private void drawSidebar(DrawContext g) {
        int sx = wx + 6, sy = wy + 6, sw = side - 8, sh = wh - 12;
        Ui.round(g, sx, sy, sw, sh, 8, Theme.sidebar());
        int logoH = 26;
        Logo.draw(g, sx + 12, sy + 8, logoH);
        int tx = sx + 12 + Logo.width(logoH) + 8;
        Ui.text(g, "DELTA", tx, sy + 9, Theme.accent());
        Ui.text(g, "VISUALS", tx, sy + 10 + Ui.th(), Theme.text());
        Ui.rect(g, sx + 12, sy + 8 + logoH + 6, sw - 24, 1, ColorUtil.withAlpha(Theme.accent(), 90));

        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            int bx = sx + 6, by = sy + 46 + i * 30, bw = sw - 12, bh = 26;
            boolean sel = search.isEmpty() && cats[i] == cat;
            boolean hov = Ui.in(mx, my, bx, by, bw, bh);
            catAnim[i] = Ui.step(catAnim[i], sel ? 1f : hov ? 0.5f : 0f, 14f);
            if (catAnim[i] > 0.02f) {
                int top = ColorUtil.mulAlpha(Theme.selection(), 0.55f * catAnim[i]);
                int bot = ColorUtil.mulAlpha(Theme.accent2(), 0.55f * catAnim[i]);
                if (Theme.gradient()) {
                    Ui.round(g, bx, by, bw, bh, 6, top);
                    Ui.vGradient(g, bx + 2, by + bh / 2, bw - 4, bh / 2 - 2, ColorUtil.withAlpha(top, 0), bot);
                } else Ui.round(g, bx, by, bw, bh, 6, top);
            }
            if (sel) Ui.rect(g, bx, by + 6, 2, bh - 12, Theme.accent());
            int col = ColorUtil.lerp(Theme.dim(), Theme.text(), Math.min(1f, catAnim[i] * 1.2f));
            Ui.text(g, cats[i].label, bx + 10, by + (bh - Ui.th()) / 2, col);
            Ui.textRight(g, String.valueOf(Modules.byCategory(cats[i]).size()), bx + bw - 8, by + (bh - Ui.th()) / 2, Theme.dim());
            hits.add(new Hit(bx, by, bw, bh, bx, by, bw, bh, K_CAT, cats[i]));
        }
        Ui.text(g, "v" + dev.deltavisuals.DeltaVisuals.VERSION + "  |  " + ConfigManager.profile(), sx + 12, sy + sh - Ui.th() - 8, Theme.dim());
    }

    private void drawHeader(DrawContext g, int mx0, int mainW) {
        String title = search.isEmpty() ? cat.label : "Search";
        Ui.text(g, title, mx0 + 10, wy + 16, Theme.text());
        int bw = Math.min(180, mainW / 2), bh = 20, bx = mx0 + mainW - bw - 8, by = wy + 11;
        Ui.round(g, bx, by, bw, bh, 6, searchFocus ? ColorUtil.lerp(Theme.field(), Theme.accent() | 0xFF000000, 0.25f) : Theme.field());
        String t = search.isEmpty() && !searchFocus ? "Search modules..." : search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
        Ui.text(g, Ui.fit(t, bw - 14), bx + 7, by + (bh - Ui.th()) / 2, search.isEmpty() && !searchFocus ? Theme.dim() : Theme.text());
        hits.add(new Hit(bx, by, bw, bh, bx, by, bw, bh, K_SEARCH, null));
    }

    private void drawList(DrawContext g, int lx, int ly, int lw, int lh) {
        listPanel = new int[]{lx, ly, lw, lh};
        int total = shown.size() * (RH + GAP);
        listMax = Math.max(0, total - lh);
        listScrollT = Math.max(0, Math.min(listMax, listScrollT));
        listScroll = Theme.animations() ? listScroll + (listScrollT - listScroll) * Math.min(1f, Ui.dt * 18f) : listScrollT;

        if (shown.isEmpty()) {
            Ui.text(g, "No modules found", lx + 6, ly + 6, Theme.dim());
            return;
        }
        float prevAlpha = Ui.alpha;
        Ui.alpha = prevAlpha * page;
        int slide = Math.round((1f - page) * 10f);
        g.enableScissor(lx - 2, ly, lx + lw + 2, ly + lh);
        clipTop = ly; clipBottom = ly + lh;
        for (int i = 0; i < shown.size(); i++) {
            Module m = shown.get(i);
            int ry = ly + i * (RH + GAP) - Math.round(listScroll) + slide;
            if (ry + RH < ly || ry > ly + lh) continue;
            boolean hov = hover(lx, ry, lw, RH);
            m.anim[0] = Ui.step(m.anim[0], hov ? 1f : 0f, 14f);
            m.anim[1] = Ui.step(m.anim[1], m.isEnabled() ? 1f : 0f, 12f);
            int body = ColorUtil.lerp(Theme.card(), Theme.cardHover(), m.anim[0]);
            if (m == selected) Ui.roundOutline(g, lx, ry, lw, RH, 7, ColorUtil.withAlpha(Theme.selection(), 200), body);
            else Ui.round(g, lx, ry, lw, RH, 7, body);
            if (!m.alwaysOn && m.anim[1] > 0.02f)
                Ui.rect(g, lx, ry + 9, 3, RH - 18, ColorUtil.mulAlpha(Theme.accent(), m.anim[1]));
            int textW = lw - 24 - (m.alwaysOn ? 0 : 42);
            Ui.text(g, Ui.fit(m.name, textW), lx + 12, ry + 6, ColorUtil.lerp(Theme.dim(), Theme.text(), 0.6f + 0.4f * m.anim[1]));
            Ui.text(g, Ui.fit(m.description, textW), lx + 12, ry + 8 + Ui.th(), Theme.dim());
            if (!m.alwaysOn) {
                Ui.toggle(g, lx + lw - 36, ry + (RH - 12) / 2, m.anim[1]);
                if (m.bind.isSet()) {
                    String b = "[" + m.bind.label() + "]";
                    Ui.textRight(g, Ui.fit(b, 90), lx + lw - 44, ry + (RH - Ui.th()) / 2, Theme.dim());
                }
            } else if (m.bind.isSet()) {
                Ui.textRight(g, "[" + m.bind.label() + "]", lx + lw - 10, ry + (RH - Ui.th()) / 2, Theme.dim());
            }
            addHit(lx, ry, lw, RH, K_ROW, m);
            if (!m.alwaysOn) addHit(lx + lw - 50, ry, 50, RH, K_TOGGLE, m);
        }
        g.disableScissor();
        clipTop = Integer.MIN_VALUE; clipBottom = Integer.MAX_VALUE;
        Ui.alpha = prevAlpha;
        scrollbar(g, lx + lw + 1, ly, lh, listScroll, listMax);
    }

    private void scrollbar(DrawContext g, int x, int y, int h, float scroll, float max) {
        if (max <= 0) return;
        float vis = h / (h + max);
        int bh = Math.max(14, Math.round(h * vis));
        int by = y + Math.round((h - bh) * (scroll / max));
        Ui.round(g, x, by, 2, bh, 1, ColorUtil.withAlpha(Theme.accent(), 150));
    }

    // ------------------------------------------------------------- settings
    private void drawSettings(DrawContext g, int px, int py, int pw, int ph) {
        Module m = selected;
        setPanel = new int[]{px, py, pw, ph};
        Ui.round(g, px, py, pw, ph, 8, Theme.sidebar());
        Ui.text(g, Ui.fit(m.name, pw - 24), px + 12, py + 9, Theme.accent());
        Ui.text(g, Ui.fit(m.description, pw - 24), px + 12, py + 11 + Ui.th(), Theme.dim());

        int vx = px + 8, vy = py + 16 + Ui.th() * 2, vwid = pw - 16, vhei = py + ph - 8 - vy;
        setScrollT = Math.max(0, Math.min(setMax, setScrollT));
        setScroll = Theme.animations() ? setScroll + (setScrollT - setScroll) * Math.min(1f, Ui.dt * 18f) : setScrollT;

        g.enableScissor(vx - 2, vy, vx + vwid + 2, vy + vhei);
        clipTop = vy; clipBottom = vy + vhei;
        int top = vy - Math.round(setScroll);
        int y = top;

        if (!m.alwaysOn) y += rowBool(g, "Enabled", m.isEnabled(), m.anim, 1, vx, y, vwid, K_ENABLED, m);
        y += rowBind(g, m, vx, y, vwid);
        for (Setting s : m.settings) {
            if (!s.isVisible()) continue;
            y += rowSetting(g, s, vx, y, vwid);
        }
        y += 4;
        y += rowButton(g, "Reset module", m.anim, 4, vx, y, vwid, K_RESET, m);

        setMax = Math.max(0, (y - top) - vhei);
        g.disableScissor();
        clipTop = Integer.MIN_VALUE; clipBottom = Integer.MAX_VALUE;
        scrollbar(g, px + pw - 4, vy, vhei, setScroll, setMax);
    }

    private int rowBool(DrawContext g, String label, boolean v, float[] a, int slot, int x, int y, int w, int kind, Object o) {
        boolean hov = hover(x, y, w, 24);
        a[slot] = Ui.step(a[slot], v ? 1f : 0f, 14f);
        float hv = Ui.step(a[(slot + 1) & 3], hov ? 1f : 0f, 14f);
        a[(slot + 1) & 3] = hv;
        if (hv > 0.02f) Ui.round(g, x, y, w, 24, 5, ColorUtil.mulAlpha(Theme.cardHover(), hv));
        Ui.text(g, Ui.fit(label, w - 50), x + 8, y + (24 - Ui.th()) / 2, Theme.text());
        Ui.toggle(g, x + w - 34, y + 6, a[slot]);
        addHit(x, y, w, 24, kind, o);
        return 26;
    }

    private int rowButton(DrawContext g, String label, float[] a, int slot, int x, int y, int w, int kind, Object o) {
        boolean hov = hover(x, y, w, 22);
        a[slot] = Ui.step(a[slot], hov ? 1f : 0f, 14f);
        int body = ColorUtil.lerp(Theme.card(), ColorUtil.withAlpha(Theme.accent(), 190), a[slot] * 0.7f);
        Ui.round(g, x, y, w, 22, 6, body);
        Ui.text(g, Ui.fit(label, w - 10), x + (w - Ui.tw(Ui.fit(label, w - 10))) / 2, y + (22 - Ui.th()) / 2, Theme.text());
        addHit(x, y, w, 22, kind, o);
        return 26;
    }

    private int rowBind(DrawContext g, Module m, int x, int y, int w) {
        boolean lis = BindManager.listening() == m;
        int bw = Math.min(118, w / 2 + 14), bx = x + w - bw, bh = 18;
        boolean hov = hover(bx, y + 3, bw, bh);
        m.anim[3] = Ui.step(m.anim[3], hov || lis ? 1f : 0f, 14f);
        Ui.text(g, "Keybind", x + 8, y + 3 + (bh - Ui.th()) / 2, Theme.text());
        int body = lis
            ? ColorUtil.lerp(Theme.card(), Theme.accent() | 0xFF000000, 0.35f + 0.15f * (float) Math.sin(System.currentTimeMillis() / 180.0))
            : ColorUtil.lerp(Theme.card(), Theme.cardHover(), m.anim[3]);
        Ui.round(g, bx, y + 3, bw, bh, 5, body);
        String t = Ui.fit(lis ? "Press a key..." : m.bind.label(), bw - 8);
        Ui.text(g, t, bx + (bw - Ui.tw(t)) / 2, y + 3 + (bh - Ui.th()) / 2, Theme.text());
        addHit(bx, y + 3, bw, bh, K_BIND, m);
        int adv = 26;
        if (lis) {
            Ui.text(g, Ui.fit("Esc cancel   Del clear   any key / mouse button binds", w - 8), x + 8, y + adv, Theme.dim());
            adv += Ui.th() + 4;
        } else if (m.bind.isSet()) {
            List<Module> c = BindManager.conflicts(m, m.bind);
            if (!c.isEmpty()) {
                StringBuilder sb = new StringBuilder("Also used by: ");
                for (int i = 0; i < c.size(); i++) sb.append(i > 0 ? ", " : "").append(c.get(i).name);
                Ui.text(g, Ui.fit(sb.toString(), w - 8), x + 8, y + adv, WARN);
                adv += Ui.th() + 4;
            }
            if (!m.bind.equals(cachedBind)) {
                cachedBind = m.bind;
                cachedVanilla = Compat.conflictsWithVanilla(MinecraftClient.getInstance(), m.bind.type == Bind.Type.MOUSE, m.bind.code);
            }
            if (cachedVanilla) {
                Ui.text(g, Ui.fit("Warning: Minecraft also uses this key", w - 8), x + 8, y + adv, WARN);
                adv += Ui.th() + 4;
            }
        }
        return adv + 2;
    }

    private int rowSetting(DrawContext g, Setting s, int x, int y, int w) {
        if (s instanceof Setting.Bool b) return rowBool(g, s.name, b.get(), s.anim, 0, x, y, w, K_BOOL, s);
        if (s instanceof Setting.Num n) return rowNum(g, n, x, y, w);
        if (s instanceof Setting.Header hd) return rowHeader(g, hd.name, x, y, w);
        if (s instanceof Setting.Mode md) return md.modes.length > 5 ? rowChips(g, md, x, y, w) : rowMode(g, md, x, y, w);
        if (s instanceof Setting.Color c) return rowColor(g, c, x, y, w);
        if (s instanceof Setting.Action) return rowButton(g, s.name, s.anim, 0, x, y, w, K_ACTION, s);
        if (s instanceof Setting.Str st) {
            String v = st.get();
            Ui.text(g, Ui.fit(I18n.t(s.name) + ": " + (v.isEmpty() ? I18n.t("(empty)") : v), w - 8), x + 8, y + 2, v.isEmpty() ? Theme.dim() : Theme.text());
            return Ui.th() + 8;
        }
        if (s instanceof Setting.Info in) {
            Ui.text(g, Ui.fit(I18n.t(s.name) + ": " + I18n.t(in.text.get()), w - 8), x + 8, y + 2, Theme.dim());
            return Ui.th() + 8;
        }
        return 0;
    }

    private String fmt(Setting.Num n) {
        double v = n.get();
        String s = (n.step >= 1 || v == Math.rint(v)) ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.2f", v);
        return s + n.suffix;
    }

    private int rowNum(DrawContext g, Setting.Num n, int x, int y, int w) {
        boolean hov = hover(x, y, w, 34) || dragNum == n;
        n.anim[0] = Ui.step(n.anim[0], hov ? 1f : 0f, 14f);
        n.anim[1] = Ui.step(n.anim[1], n.fraction(), 22f);
        if (n.anim[0] > 0.02f) Ui.round(g, x, y, w, 34, 5, ColorUtil.mulAlpha(Theme.cardHover(), n.anim[0]));
        Ui.text(g, Ui.fit(n.name, w - 70), x + 8, y + 4, Theme.text());
        Ui.textRight(g, fmt(n), x + w - 8, y + 4, Theme.accent());
        int tx = x + 8, tw = w - 16, ty = y + 22;
        Ui.round(g, tx, ty, tw, 4, 2, Theme.field());
        int fw = Math.max(0, Math.round(tw * n.anim[1]));
        if (fw > 0) Ui.round(g, tx, ty, Math.max(4, fw), 4, 2, Theme.accent());
        Ui.round(g, tx + Math.max(0, Math.min(tw - 8, fw - 4)), ty - 2, 8, 8, 4, 0xFFFFFFFF);
        addHit(tx, y, tw, 34, K_NUM, n);
        return 36;
    }

    private record ChipRef(Setting.Mode mode, int index) { }

    /** Section title: accent text with a thin line, used to group settings (Sky Presets, Sky Colors, Clouds...). */
    private int rowHeader(DrawContext g, String title, int x, int y, int w) {
        Ui.text(g, Ui.fit(title.toUpperCase(Locale.ROOT), w - 16), x + 6, y + 8, Theme.accent());
        Ui.rect(g, x + 6, y + 8 + Ui.th() + 3, w - 12, 1, ColorUtil.withAlpha(Theme.accent(), 80));
        return Ui.th() + 18;
    }

    /** Mode setting with many options: wrapped, clickable chips instead of the < value > cycler. */
    private int rowChips(DrawContext g, Setting.Mode m, int x, int y, int w) {
        Ui.text(g, Ui.fit(m.name, w - 16), x + 8, y + 2, Theme.text());
        final int chipH = 20;
        int left = x + 4, maxX = x + w - 4;
        int cx = left, cy = y + Ui.th() + 8;
        for (int i = 0; i < m.modes.length; i++) {
            String label = Ui.fit(m.modes[i], w - 24);
            int cw = Ui.tw(label) + 14;
            if (cx + cw > maxX && cx > left) { cx = left; cy += chipH + 3; }
            boolean sel = i == m.index();
            boolean hov = hover(cx, cy, cw, chipH);
            int body = sel ? ColorUtil.lerp(Theme.field(), Theme.accent() | 0xFF000000, 0.55f)
                : hov ? Theme.cardHover() : Theme.field();
            Ui.round(g, cx, cy, cw, chipH, 6, body);
            if (sel) Ui.roundOutline(g, cx, cy, cw, chipH, 6, Theme.accent(), body);
            Ui.text(g, label, cx + 7, cy + (chipH - Ui.th()) / 2, sel ? 0xFFFFFFFF : hov ? Theme.text() : Theme.dim());
            addHit(cx, cy, cw, chipH, K_CHIP, new ChipRef(m, i));
            cx += cw + 4;
        }
        return cy + chipH + 6 - y;
    }

    private int rowMode(DrawContext g, Setting.Mode m, int x, int y, int w) {
        boolean hov = hover(x, y, w, 24);
        m.anim[0] = Ui.step(m.anim[0], hov ? 1f : 0f, 14f);
        if (m.anim[0] > 0.02f) Ui.round(g, x, y, w, 24, 5, ColorUtil.mulAlpha(Theme.cardHover(), m.anim[0]));
        Ui.text(g, Ui.fit(m.name, w / 2 - 8), x + 8, y + (24 - Ui.th()) / 2, Theme.text());
        String v = Ui.fit("< " + I18n.t(m.get()) + " >", w / 2 - 4);
        Ui.textRight(g, v, x + w - 8, y + (24 - Ui.th()) / 2, Theme.accent());
        addHit(x, y, w, 24, K_MODE, m);
        return 26;
    }

    private int rowColor(DrawContext g, Setting.Color c, int x, int y, int w) {
        boolean hov = hover(x, y, w, 24);
        c.anim[0] = Ui.step(c.anim[0], hov ? 1f : 0f, 14f);
        if (c.anim[0] > 0.02f) Ui.round(g, x, y, w, 24, 5, ColorUtil.mulAlpha(Theme.cardHover(), c.anim[0]));
        Ui.text(g, Ui.fit(c.name, w - 60), x + 8, y + (24 - Ui.th()) / 2, Theme.text());
        Ui.round(g, x + w - 38, y + 5, 30, 14, 4, 0xFF15121F);
        Ui.round(g, x + w - 37, y + 6, 28, 12, 3, c.get());
        addHit(x, y, w, 24, K_COLOR, c);
        int used = 26;
        if (pickerFor == c) used += picker(g, c, x + 4, y + 26, w - 8);
        return used;
    }

    private int picker(DrawContext g, Setting.Color c, int x, int y, int w) {
        int svh = 62;
        // saturation / value square: one vertical gradient per saturation column
        for (int cx = 0; cx < w; cx += 3) {
            float sa = cx / (float) w;
            Ui.vGradient(g, x + cx, y, Math.min(3, w - cx), svh, ColorUtil.hsv(hue, sa, 1f, 255), ColorUtil.hsv(hue, sa, 0f, 255));
        }
        int mxp = x + Math.round(sat * (w - 1)), myp = y + Math.round((1f - val) * (svh - 1));
        Ui.round(g, mxp - 3, myp - 3, 7, 7, 3, 0xFFFFFFFF);
        Ui.round(g, mxp - 2, myp - 2, 5, 5, 2, ColorUtil.hsv(hue, sat, val, 255));
        addHit(x, y, w, svh, K_SV, c);
        int yy = y + svh + 6;
        for (int cx = 0; cx < w; cx += 3)
            Ui.rect(g, x + cx, yy, Math.min(3, w - cx), 10, ColorUtil.hsv(cx / (float) w, 1f, 1f, 255));
        int hx = x + Math.round(hue * (w - 1));
        Ui.rect(g, hx - 1, yy - 2, 3, 14, 0xFFFFFFFF);
        addHit(x, yy, w, 10, K_HUE, c);
        yy += 16;
        int total = svh + 6 + 16;
        if (c.hasAlpha) {
            Ui.rect(g, x, yy, w, 10, 0xFF15121F);
            for (int cx = 0; cx < w; cx += 3)
                Ui.rect(g, x + cx, yy, Math.min(3, w - cx), 10, ColorUtil.withAlpha(c.get(), Math.round(255f * cx / w)));
            int ax = x + Math.round(ColorUtil.alpha(c.get()) / 255f * (w - 1));
            Ui.rect(g, ax - 1, yy - 2, 3, 14, 0xFFFFFFFF);
            addHit(x, yy, w, 10, K_ALPHA, c);
            yy += 16; total += 16;
        }
        int fw = Math.min(w, 120);
        Ui.round(g, x, yy, fw, 18, 5, hexFocus ? ColorUtil.lerp(Theme.field(), Theme.accent() | 0xFF000000, 0.3f) : Theme.field());
        String hexText = hexFocus ? hexBuf : ColorUtil.hex(c.get(), c.hasAlpha);
        Ui.text(g, "#" + hexText + (hexFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""), x + 6, yy + (18 - Ui.th()) / 2, Theme.text());
        addHit(x, yy, fw, 18, K_HEX, c);
        return total + 24;
    }

    // ---------------------------------------------------------------- input
    private int toX(double v) { return Math.round((float) v / curScale); }
    private int toY(double v) { return Math.round((float) v / curScale) - oy; }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int button = click.button();
        if (BindManager.listening() != null) {          // waiting for a bind: any button is the bind
            BindManager.captureMouse(button);
            return true;
        }
        int cx = toX(click.x()), cy = toY(click.y());
        searchFocus = false;
        boolean keepHex = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.in(cx, cy)) continue;
            if (h.kind == K_HEX) keepHex = true;
            handle(h, button, cx, cy);
            break;
        }
        if (!keepHex) hexFocus = false;
        return true;
    }

    private void handle(Hit h, int button, int cx, int cy) {
        switch (h.kind) {
            case K_CAT -> {
                cat = (Category) h.o;
                search = "";
                listScroll = listScrollT = 0;
                page = 0f;
                refreshList();
            }
            case K_SEARCH -> searchFocus = true;
            case K_TOGGLE -> ((Module) h.o).toggle();
            case K_ROW -> { if (button == 0 || button == 1) select((Module) h.o); }
            case K_ENABLED -> ((Module) h.o).toggle();
            case K_BIND -> {
                Module m = (Module) h.o;
                if (button == 0) BindManager.startListening(m);
                else if (button == 1) m.setBind(m.defaultBind);
            }
            case K_RESET -> { Module m = (Module) h.o; m.resetToDefaults(); ConfigManager.markDirty(); pickerFor = null; }
            case K_BOOL -> ((Setting.Bool) h.o).toggle();
            case K_NUM -> { dragNum = (Setting.Num) h.o; dragNumHit = h; setNum(cx); }
            case K_MODE -> { Setting.Mode m = (Setting.Mode) h.o; if (button == 1) m.prev(); else m.next(); }
            case K_ACTION -> ((Setting.Action) h.o).run.run();
            case K_CHIP -> { ChipRef r = (ChipRef) h.o; r.mode().set(r.index()); }
            case K_COLOR -> {
                Setting.Color c = (Setting.Color) h.o;
                if (pickerFor == c) pickerFor = null;
                else { pickerFor = c; float[] hsv = ColorUtil.toHsv(c.get()); hue = hsv[0]; sat = hsv[1]; val = hsv[2]; }
                hexFocus = false;
            }
            case K_SV, K_HUE, K_ALPHA -> { pickHit = h; pick(cx, cy); }
            case K_HEX -> { hexFocus = true; hexBuf = ColorUtil.hex(((Setting.Color) h.o).get(), ((Setting.Color) h.o).hasAlpha); }
            default -> { }
        }
    }

    private void setNum(int cx) {
        if (dragNum == null || dragNumHit == null) return;
        float f = Math.max(0f, Math.min(1f, (cx - dragNumHit.rx) / (float) dragNumHit.rw));
        dragNum.set(dragNum.min + f * (dragNum.max - dragNum.min));
    }

    private void pick(int cx, int cy) {
        if (pickHit == null || pickerFor == null) return;
        Hit h = pickHit;
        float fx = Math.max(0f, Math.min(1f, (cx - h.rx) / (float) (h.rw - 1)));
        float fy = Math.max(0f, Math.min(1f, (cy - h.ry) / (float) (h.rh - 1)));
        int a = ColorUtil.alpha(pickerFor.get());
        if (h.kind == K_SV) { sat = fx; val = 1f - fy; pickerFor.set(ColorUtil.hsv(hue, sat, val, a)); }
        else if (h.kind == K_HUE) { hue = fx * 0.9999f; pickerFor.set(ColorUtil.hsv(hue, sat, val, a)); }
        else if (h.kind == K_ALPHA) pickerFor.set(ColorUtil.withAlpha(pickerFor.get(), Math.round(fx * 255f)));
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        int cx = toX(click.x()), cy = toY(click.y());
        if (dragNum != null) setNum(cx);
        if (pickHit != null) pick(cx, cy);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragNum = null; dragNumHit = null; pickHit = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int cx = toX(mouseX), cy = toY(mouseY);
        if (selected != null && Ui.in(cx, cy, setPanel[0], setPanel[1], setPanel[2], setPanel[3]))
            setScrollT = Math.max(0, Math.min(setMax, setScrollT - (float) verticalAmount * 28f));
        else
            listScrollT = Math.max(0, Math.min(listMax, listScrollT - (float) verticalAmount * 28f));
        return true;
    }

    private static char typed(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return (char) ('a' + key - GLFW.GLFW_KEY_A);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return (char) ('0' + key - GLFW.GLFW_KEY_0);
        if (key == GLFW.GLFW_KEY_SPACE) return ' ';
        return 0;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (BindManager.listening() != null) {          // Esc cancels, Del clears, anything else binds
            BindManager.captureKey(key);
            return true;
        }
        if (hexFocus && pickerFor != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { hexFocus = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !hexBuf.isEmpty()) hexBuf = hexBuf.substring(0, hexBuf.length() - 1);
            else if (hexBuf.length() < (pickerFor.hasAlpha ? 8 : 6)) {
                char ch = typed(key);
                if (Character.digit(ch, 16) >= 0 && ch != ' ') hexBuf += Character.toUpperCase(ch);
            }
            Integer parsed = ColorUtil.parseHex(hexBuf, ColorUtil.alpha(pickerFor.get()));
            if (parsed != null && (hexBuf.length() == 6 || hexBuf.length() == 8)) {
                pickerFor.set(parsed);
                float[] hsv = ColorUtil.toHsv(pickerFor.get()); hue = hsv[0]; sat = hsv[1]; val = hsv[2];
            }
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { searchFocus = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!search.isEmpty()) { search = search.substring(0, search.length() - 1); refreshList(); } return true; }
            char ch = typed(key);
            if (ch != 0 && search.length() < 24) { search += ch; refreshList(); }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { startClose(); return true; }
        Bind gb = Modules.GUI.bind;
        if (gb.type == Bind.Type.KEY && gb.code == key && System.currentTimeMillis() - openedAt > 350) { startClose(); return true; }
        return true;
    }
}
