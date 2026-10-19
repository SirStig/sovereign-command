package sovereign;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.input.Keyboard;

final class SettingsTab extends TabView {

    SettingsTab(SovereignWindow window) {
        super(window);
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        float half = Math.min(bw, 560f);
        SovereignWindow.Card other = w.card(body, 0f, 0f, half, bh, "Intel and controls");
        w.toggleRow(other, "Fog of war", "set:fog", SovereignSettings.fog(),
                "Hides colonies you have not seen");
        w.stepper(other, "Survey cost", SovereignKnowledge.credits(SovereignSettings.investigateCost()),
                "set:cost-", "set:cost+", true);
        TooltipMakerAPI tm = other.tm;
        tm.addPara("Open key", Theme.dim(), 14f);
        String key = w.isRebinding() ? "Press a key" : Keyboard.getKeyName(SovereignSettings.hotkey());
        w.button(tm, key, "set:key", other.rowWidth(), 24f, 4f, !w.isRebinding());
        w.button(tm, "Reset all", "set:reset", other.rowWidth(), 24f, 20f, true);
        w.done(other);
    }

    @Override
    boolean handle(String id) {
        if (!id.startsWith("set:")) {
            return false;
        }
        String k = id.substring(4);
        if ("fog".equals(k)) {
            SovereignSettings.setFog(!SovereignSettings.fog());
        } else if ("cost-".equals(k)) {
            SovereignSettings.setInvestigateCost(SovereignSettings.investigateCost() - 5000);
        } else if ("cost+".equals(k)) {
            SovereignSettings.setInvestigateCost(SovereignSettings.investigateCost() + 5000);
        } else if ("key".equals(k)) {
            w.startRebind();
        } else if ("reset".equals(k)) {
            SovereignSettings.reset();
        }
        return true;
    }
}
