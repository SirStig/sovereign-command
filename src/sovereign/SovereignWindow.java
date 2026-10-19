package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SovereignWindow implements CustomVisualDialogDelegate {

    enum TabId {
        FACTIONS("Factions"), OPERATIONS("Operations"), FORCES("Task forces"), COLONIES("Colonies"), LAWS("Laws"), LOG("Log"), SETTINGS("Settings");

        final String title;

        TabId(String title) {
            this.title = title;
        }
    }

    private static final class Bound {
        final ButtonAPI button;
        final String id;
        final boolean expected;

        Bound(ButtonAPI button, String id, boolean expected) {
            this.button = button;
            this.id = id;
            this.expected = expected;
        }
    }

    static final class Card {
        final CustomPanelAPI panel;
        final TooltipMakerAPI tm;
        final float w;
        final float h;

        Card(CustomPanelAPI panel, TooltipMakerAPI tm, float w, float h) {
            this.panel = panel;
            this.tm = tm;
            this.w = w;
            this.h = h;
        }

        float rowWidth() {
            return w - 12f - 18f;
        }
    }

    static final float PAD = 10f;
    static final float HEADER = 44f;
    static final float TABS = 28f;
    static final float FOOT = 26f;

    private final InteractionDialogAPI dialog;
    private final Map<TabId, TabView> tabs = new LinkedHashMap<TabId, TabView>();
    private final List<Bound> bound = new ArrayList<Bound>();
    private final Set<String> queue = new LinkedHashSet<String>();
    private final RootPlugin rootPlugin = new RootPlugin();

    final float width;
    final float height;

    private DialogCallbacks callbacks;
    private CustomPanelAPI root;
    private CustomPanelAPI content;
    private TabId current = TabId.FACTIONS;
    private boolean dirty;
    private boolean rebinding;
    private String toast = "";
    private boolean toastGood = true;

    String targetId;

    public static float windowWidth() {
        return Math.min(1180f, Math.max(860f, Global.getSettings().getScreenWidth() - 80f));
    }

    public static float windowHeight() {
        return Math.min(740f, Math.max(540f, Global.getSettings().getScreenHeight() - 80f));
    }

    SovereignWindow(InteractionDialogAPI dialog, StarSystemAPI focus) {
        this.dialog = dialog;
        this.width = windowWidth();
        this.height = windowHeight();
        FactionsTab factions = new FactionsTab(this, focus);
        tabs.put(TabId.FACTIONS, factions);
        tabs.put(TabId.OPERATIONS, new OperationsTab(this));
        tabs.put(TabId.FORCES, new ForcesTab(this));
        tabs.put(TabId.COLONIES, new ColoniesTab(this));
        tabs.put(TabId.LAWS, new LawsTab(this));
        tabs.put(TabId.LOG, new LogTab(this));
        tabs.put(TabId.SETTINGS, new SettingsTab(this));
    }

    // ---- delegate -------------------------------------------------------------------------------------------

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.root = panel;
        this.callbacks = callbacks;
        build();
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return rootPlugin;
    }

    @Override
    public float getNoiseAlpha() {
        return 0f;
    }

    @Override
    public void advance(float amount) {
        try {
            for (Bound b : bound) {
                if (b.button.isEnabled() && b.button.isChecked() != b.expected) {
                    b.button.setChecked(b.expected);
                    queue.add(b.id);
                }
            }
            if (!queue.isEmpty()) {
                List<String> ids = new ArrayList<String>(queue);
                queue.clear();
                for (String id : ids) {
                    handle(id);
                }
            }
            if (dirty) {
                dirty = false;
                build();
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignWindow.class).error("Command window update failed", ex);
        }
    }

    @Override
    public void reportDismissed(int option) {
        dialog.dismiss();
    }

    // ---- events ---------------------------------------------------------------------------------------------

    void press(Object id) {
        if (!(id instanceof String)) {
            return;
        }
        String s = (String) id;
        for (Bound b : bound) {
            if (b.id.equals(s)) {
                b.button.setChecked(b.expected);
            }
        }
        queue.add(s);
    }

    private void handle(String id) {
        try {
            if ("close".equals(id)) {
                callbacks.dismissDialog();
                return;
            }
            if (id.startsWith("tab:")) {
                current = TabId.valueOf(id.substring(4));
                dirty = true;
                return;
            }
            if (tab().handle(id)) {
                dirty = true;
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignWindow.class).error("Command failed: " + id, ex);
            say("That failed. Check starsector.log.", false);
            dirty = true;
        }
    }

    TabView tab() {
        return tabs.get(current);
    }

    void show(TabId id) {
        current = id;
        dirty = true;
    }

    void redraw() {
        dirty = true;
    }

    void say(String text, boolean good) {
        toast = text == null ? "" : text;
        toastGood = good;
    }

    void startRebind() {
        rebinding = true;
    }

    boolean isRebinding() {
        return rebinding;
    }

    void closeThen(Runnable action) {
        SovereignDialog.later(action);
        callbacks.dismissDialog();
    }

    MarketAPI target() {
        if (targetId == null || Global.getSector().getEconomy() == null) {
            return null;
        }
        return Global.getSector().getEconomy().getMarket(targetId);
    }

    // ---- layout ---------------------------------------------------------------------------------------------

    private void build() {
        if (root == null) {
            return;
        }
        try {
            if (content != null) {
                root.removeComponent(content);
            }
            bound.clear();
            content = root.createCustomPanel(width, height, new Box(this));
            buildHeader();
            buildTabStrip();
            float bodyY = PAD + HEADER + 8f + TABS + 8f;
            float bodyW = width - 2f * PAD;
            float bodyH = height - bodyY - FOOT - PAD;
            CustomPanelAPI body = content.createCustomPanel(bodyW, bodyH, new Box(this));
            content.addComponent(body).inTL(PAD, bodyY);
            try {
                tab().build(body, bodyW, bodyH);
            } catch (Exception ex) {
                Global.getLogger(SovereignWindow.class).error("Could not draw " + current, ex);
                Card c = card(body, 0, 0, bodyW, 90f, null);
                empty(c.tm, "This page failed to load", "Details are in starsector.log");
                done(c);
            }
            buildFooter();
            root.addComponent(content).inTL(0f, 0f);
        } catch (Exception ex) {
            Global.getLogger(SovereignWindow.class).error("Could not draw the command window", ex);
        }
    }

    private void buildHeader() {
        TooltipMakerAPI title = content.createUIElement(520f, HEADER, false);
        title.setTitleOrbitronLarge();
        title.addTitle("Sovereign Command", Theme.bright());
        content.addUIElement(title).inTL(PAD + 2f, PAD);

        TooltipMakerAPI close = content.createUIElement(90f, 26f, false);
        button(close, "Close", "close", 90f, 24f, 0f, true);
        content.addUIElement(close).inTR(PAD, PAD + 4f);

        float credits = 0f;
        if (Global.getSector().getPlayerFleet() != null) {
            credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        }
        TooltipMakerAPI money = content.createUIElement(240f, 24f, false);
        LabelAPI label = money.addPara(Misc.getDGSCredits(credits), Theme.hl(), 6f);
        label.setAlignment(Alignment.RMID);
        content.addUIElement(money).inTR(PAD + 100f, PAD + 6f);
    }

    private void buildTabStrip() {
        TabId[] ids = TabId.values();
        float tabW = Math.min(150f, (width - 2f * PAD) / ids.length - 4f);
        for (int i = 0; i < ids.length; i++) {
            TooltipMakerAPI tm = content.createUIElement(tabW, TABS, false);
            boolean active = ids[i] == current;
            ButtonAPI b = tm.addAreaCheckbox(ids[i].title, "tab:" + ids[i].name(),
                    Theme.base(), Theme.dark(), Theme.bright(), tabW, TABS - 2f, 0f);
            b.setChecked(active);
            bound.add(new Bound(b, "tab:" + ids[i].name(), active));
            content.addUIElement(tm).inTL(PAD + i * (tabW + 4f), PAD + HEADER + 8f);
        }
    }

    private void buildFooter() {
        TooltipMakerAPI tm = content.createUIElement(width - 2f * PAD, FOOT, false);
        Color c = toast.isEmpty() ? Theme.dim() : (toastGood ? Theme.text() : Theme.bad());
        tm.addPara(toast, c, 0f);
        content.addUIElement(tm).inBL(PAD + 2f, 8f);
    }

    // ---- widgets --------------------------------------------------------------------------------------------

    Card card(CustomPanelAPI parent, float x, float y, float cw, float ch, String title) {
        CustomPanelAPI panel = parent.createCustomPanel(cw, ch, new Box(this, Theme.CARD, 0.92f, Theme.base(), 0.35f));
        TooltipMakerAPI tm = panel.createUIElement(cw - 12f, ch - 12f, true);
        if (title != null) {
            tm.addSectionHeading(title, Alignment.LMID, 0f);
        }
        parent.addComponent(panel).inTL(x, y);
        return new Card(panel, tm, cw, ch);
    }

    CustomPanelAPI frame(CustomPanelAPI parent, float x, float y, float fw, float fh) {
        CustomPanelAPI panel = parent.createCustomPanel(fw, fh, new Box(this, Theme.CARD, 0.92f, Theme.base(), 0.35f));
        parent.addComponent(panel).inTL(x, y);
        return panel;
    }

    void done(Card c) {
        c.panel.addUIElement(c.tm).inTL(6f, 6f);
    }

    ButtonAPI button(TooltipMakerAPI tm, String text, String id, float bw, float bh, float pad, boolean enabled) {
        return styled(tm, text, id, bw, bh, pad, enabled, Theme.base(), Theme.dark());
    }

    ButtonAPI danger(TooltipMakerAPI tm, String text, String id, float bw, float bh, float pad, boolean enabled) {
        return styled(tm, text, id, bw, bh, pad, enabled, Theme.bad(), new Color(70, 14, 14));
    }

    ButtonAPI confirm(TooltipMakerAPI tm, String text, String id, float bw, float bh, float pad, boolean enabled) {
        return styled(tm, text, id, bw, bh, pad, enabled, Theme.good(), new Color(14, 60, 28));
    }

    private ButtonAPI styled(TooltipMakerAPI tm, String text, String id, float bw, float bh, float pad,
                             boolean enabled, Color base, Color bg) {
        ButtonAPI b = tm.addButton(text, id, base, bg, Alignment.MID, CutStyle.ALL, bw, bh, pad);
        b.setEnabled(enabled);
        bound.add(new Bound(b, id, false));
        return b;
    }

    ButtonAPI chip(TooltipMakerAPI tm, String text, String id, boolean on, float bw, float bh, float pad, boolean enabled) {
        ButtonAPI b = tm.addAreaCheckbox(text, id, Theme.base(), Theme.dark(), Theme.bright(), bw, bh, pad);
        b.setChecked(on);
        b.setEnabled(enabled);
        bound.add(new Bound(b, id, on));
        return b;
    }

    void listRow(Card c, String id, String main, String mid, String right, Color rightColor,
                 boolean selected, Color base, Color dark) {
        float rw = c.rowWidth();
        float rightW = right == null ? 0f : 100f;
        float midW = mid == null ? 0f : Math.max(60f, rw * 0.30f);
        float mainW = rw - rightW - midW - (mid == null ? 0f : 6f);
        CustomPanelAPI row = c.panel.createCustomPanel(rw, 24f, new Box(this));

        TooltipMakerAPI a = row.createUIElement(mainW, 24f, false);
        ButtonAPI b = a.addAreaCheckbox(a.shortenString(main, mainW - 16f), id, base, dark, Theme.bright(),
                mainW, 22f, 0f, true);
        b.setChecked(selected);
        bound.add(new Bound(b, id, selected));
        row.addUIElement(a).inTL(0f, 0f);

        if (mid != null) {
            TooltipMakerAPI m = row.createUIElement(midW, 24f, false);
            m.addPara(m.shortenString(mid, midW - 4f), Theme.dim(), 5f);
            row.addUIElement(m).inTL(mainW + 6f, 0f);
        }
        if (right != null) {
            TooltipMakerAPI r = row.createUIElement(rightW, 24f, false);
            LabelAPI l = r.addPara(right, rightColor == null ? Theme.text() : rightColor, 5f);
            l.setAlignment(Alignment.RMID);
            row.addUIElement(r).inTL(rw - rightW, 0f);
        }
        c.tm.addCustom(row, 2f);
    }

    void stepper(Card c, String label, String value, String downId, String upId, boolean enabled) {
        float rw = c.rowWidth();
        CustomPanelAPI row = c.panel.createCustomPanel(rw, 28f, new Box(this));
        TooltipMakerAPI l = row.createUIElement(rw - 150f, 26f, false);
        l.addPara(label, Theme.text(), 6f);
        row.addUIElement(l).inTL(0f, 0f);
        TooltipMakerAPI down = row.createUIElement(28f, 26f, false);
        button(down, "-", downId, 28f, 24f, 0f, enabled);
        row.addUIElement(down).inTL(rw - 150f, 0f);
        TooltipMakerAPI v = row.createUIElement(84f, 26f, false);
        LabelAPI vl = v.addPara(value, Theme.hl(), 6f);
        vl.setAlignment(Alignment.MID);
        row.addUIElement(v).inTL(rw - 120f, 0f);
        TooltipMakerAPI up = row.createUIElement(28f, 26f, false);
        button(up, "+", upId, 28f, 24f, 0f, enabled);
        row.addUIElement(up).inTL(rw - 28f, 0f);
        c.tm.addCustom(row, 4f);
    }

    void toggleRow(Card c, String label, String id, boolean on, String hint) {
        toggleRow(c, label, id, on, hint, "On", "Off");
    }

    void toggleRow(Card c, String label, String id, boolean on, String hint, String onText, String offText) {
        float rw = c.rowWidth();
        CustomPanelAPI row = c.panel.createCustomPanel(rw, 28f, new Box(this));
        TooltipMakerAPI l = row.createUIElement(rw - 100f, 26f, false);
        l.addPara(label, Theme.text(), 6f);
        if (hint != null) {
            tip(l, hint);
        }
        row.addUIElement(l).inTL(0f, 0f);
        TooltipMakerAPI b = row.createUIElement(90f, 26f, false);
        chip(b, on ? onText : offText, id, on, 90f, 24f, 0f, true);
        row.addUIElement(b).inTL(rw - 90f, 0f);
        c.tm.addCustom(row, 4f);
    }

    void choiceRow(Card c, String label, String id, String value, String hint) {
        float rw = c.rowWidth();
        CustomPanelAPI row = c.panel.createCustomPanel(rw, 28f, new Box(this));
        TooltipMakerAPI l = row.createUIElement(rw - 170f, 26f, false);
        l.addPara(label, Theme.text(), 6f);
        if (hint != null) {
            tip(l, hint);
        }
        row.addUIElement(l).inTL(0f, 0f);
        TooltipMakerAPI b = row.createUIElement(160f, 26f, false);
        button(b, value, id, 160f, 24f, 0f, true);
        row.addUIElement(b).inTL(rw - 160f, 0f);
        c.tm.addCustom(row, 4f);
    }

    void empty(TooltipMakerAPI tm, String title, String hint) {
        tm.addSpacer(26f);
        tm.setParaFontOrbitron();
        LabelAPI t = tm.addPara(title, Theme.dim(), 0f);
        t.setAlignment(Alignment.MID);
        tm.setParaFontDefault();
        if (hint != null) {
            LabelAPI h = tm.addPara(hint, Theme.dim(), 6f);
            h.setAlignment(Alignment.MID);
        }
    }

    void tip(TooltipMakerAPI tm, final String text) {
        tm.addTooltipToPrevious(new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 300f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara(text, 0f);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    void facts(TooltipMakerAPI tm, float fw, String[] keys, String[] values, Color[] colors, float pad) {
        tm.beginGrid(fw, 1, Theme.dim());
        for (int i = 0; i < keys.length; i++) {
            Color c = colors != null && colors[i] != null ? colors[i] : Theme.text();
            tm.addToGrid(0, i, keys[i], values[i], c);
        }
        tm.addGrid(pad);
    }

    // ---- root plugin ----------------------------------------------------------------------------------------

    private final class RootPlugin extends BaseCustomUIPanelPlugin {
        private PositionAPI pos;

        @Override
        public void positionChanged(PositionAPI position) {
            this.pos = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (pos == null) {
                return;
            }
            float x = pos.getX();
            float y = pos.getY();
            float w = pos.getWidth();
            float h = pos.getHeight();
            Theme.fill(x - 3f, y - 3f, w + 6f, h + 6f, Color.BLACK, 0.55f * alphaMult);
            Theme.fill(x, y, w, h, Theme.WINDOW, 0.97f * alphaMult);
            Theme.fill(x, y + h - HEADER - PAD, w, HEADER + PAD, Theme.dark(), 0.28f * alphaMult);
            Theme.fill(x, y + h - HEADER - PAD - 1f, w, 1f, Theme.base(), 0.7f * alphaMult);
            Theme.outline(x, y, w, h, Theme.base(), 0.75f * alphaMult);
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            for (InputEventAPI event : events) {
                if (event.isConsumed() || !event.isKeyDownEvent()) {
                    continue;
                }
                int key = event.getEventValue();
                if (rebinding) {
                    event.consume();
                    rebinding = false;
                    if (key != Keyboard.KEY_ESCAPE) {
                        SovereignSettings.setHotkey(key);
                        say("Open key set to " + Keyboard.getKeyName(key), true);
                    }
                    dirty = true;
                    continue;
                }
                if (key == Keyboard.KEY_ESCAPE || key == SovereignSettings.hotkey()) {
                    event.consume();
                    callbacks.dismissDialog();
                    return;
                }
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
            press(buttonId);
        }
    }
}
