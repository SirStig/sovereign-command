package sovereign;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.List;

final class LogTab extends TabView {

    LogTab(SovereignWindow window) {
        super(window);
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        SovereignWindow.Card c = w.card(body, 0f, 0f, bw, bh, "Log");
        TooltipMakerAPI tm = c.tm;
        List<String[]> entries = SovereignLog.entries();
        if (entries.isEmpty()) {
            w.empty(tm, "Nothing logged yet", null);
        } else {
            w.button(tm, "Clear", "clear", 90f, 22f, 6f, true);
            for (int i = entries.size() - 1; i >= 0; i--) {
                String[] e = entries.get(i);
                tm.addPara(e[0] + "   " + e[2], color(e[1]), 5f);
            }
        }
        w.done(c);
    }

    private Color color(String kind) {
        if (SovereignLog.BORDER.equals(kind)) {
            return Theme.hl();
        }
        if (SovereignLog.DIPLOMACY.equals(kind)) {
            return Theme.bright();
        }
        return Theme.text();
    }

    @Override
    boolean handle(String id) {
        if ("clear".equals(id)) {
            SovereignLog.clear();
            return true;
        }
        return false;
    }
}
