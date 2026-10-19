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
        float half = (bw - 8f) / 2f;
        SovereignWindow.Card extras = w.card(body, 0f, 0f, half, bh, "Extras");
        w.toggleRow(extras, "Diplomacy", "set:diplomacy", SovereignSettings.diplomacy(),
                "Gifts, war declarations and peace talks");
        w.toggleRow(extras, "Border law", "set:laws", SovereignSettings.laws(),
                "Closed borders, customs and banned goods. Adds the Laws tab");
        w.toggleRow(extras, "Fog of war", "set:fog", SovereignSettings.fog(),
                "Only colonies you have visited or surveyed are shown");
        if (SovereignSettings.fog()) {
            w.stepper(extras, "Survey cost", SovereignKnowledge.credits(SovereignSettings.investigateCost()),
                    "set:cost-", "set:cost+", true);
        }
        w.button(extras.tm, "Run setup again", "set:setup", extras.rowWidth(), 24f, 20f, true);
        w.done(extras);

        SovereignWindow.Card controls = w.card(body, half + 8f, 0f, half, bh, "Controls");
        TooltipMakerAPI tm = controls.tm;
        tm.addPara("Open key", Theme.dim(), 10f);
        String key = w.isRebinding() ? "Press a key" : Keyboard.getKeyName(SovereignSettings.hotkey());
        w.button(tm, key, "set:key", controls.rowWidth(), 24f, 4f, !w.isRebinding());
        w.button(tm, "Reset everything", "set:reset", controls.rowWidth(), 24f, 20f, true);
        w.done(controls);
    }

    @Override
    boolean handle(String id) {
        if (!id.startsWith("set:")) {
            return false;
        }
        String k = id.substring(4);
        if ("diplomacy".equals(k)) {
            SovereignSettings.setDiplomacy(!SovereignSettings.diplomacy());
        } else if ("laws".equals(k)) {
            SovereignSettings.setLaws(!SovereignSettings.laws());
        } else if ("setup".equals(k)) {
            w.restartSetup();
        } else if ("fog".equals(k)) {
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
