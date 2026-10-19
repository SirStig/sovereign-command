package sovereign;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.input.Keyboard;

/** Which extras are on, the open key, and the prices behind a collapsed Advanced section. */
final class SettingsTab extends TabView {

    private boolean advanced;

    SettingsTab(SovereignWindow window) {
        super(window);
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        float half = (bw - 8f) / 2f;
        SovereignWindow.Card extras = w.card(body, 0f, 0f, half, bh, "Extras");
        w.toggleRow(extras, "Diplomacy", "set:diplomacy", SovereignSettings.diplomacy(),
                "Gifts, declaring war and peace talks.");
        w.toggleRow(extras, "Border law", "set:laws", SovereignSettings.laws(),
                "Border patrols, customs and banned goods. Adds the Laws tab.");
        w.toggleRow(extras, "Fog of war", "set:fog", SovereignSettings.fog(),
                "Only colonies you have visited or surveyed are shown.");
        w.button(extras.tm, "Run setup again", "set:setup", extras.rowWidth(), 24f, 20f, true);
        w.done(extras);

        SovereignWindow.Card controls = w.card(body, half + 8f, 0f, half, bh, "Controls");
        TooltipMakerAPI tm = controls.tm;
        tm.addPara("Open key", Theme.dim(), 10f);
        String key = w.isRebinding() ? "Press a key" : Keyboard.getKeyName(SovereignSettings.hotkey());
        w.button(tm, key, "set:key", controls.rowWidth(), 24f, 4f, !w.isRebinding());
        w.button(tm, advanced ? "Hide advanced" : "Advanced", "set:advanced", controls.rowWidth(), 24f, 16f, true);
        if (advanced) {
            tm.addPara("Prices", Theme.dim(), 12f);
            w.stepper(controls, "Survey party", SovereignKnowledge.credits(SovereignSettings.investigateCost()),
                    "set:cost-", "set:cost+", true);
            w.stepper(controls, "Gift price", SovereignSettings.giftPercent() + "%", "set:gift-", "set:gift+", true);
            w.tip(tm, "Share of the receiving faction's monthly income.");
            w.stepper(controls, "Envoy fee", SovereignSettings.envoyPercent() + "%", "set:envoy-", "set:envoy+", true);
            w.tip(tm, "Share of your own monthly income, paid for every round of peace talks.");
            w.button(tm, "Reset everything", "set:reset", controls.rowWidth(), 24f, 16f, true);
        }
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
        } else if ("advanced".equals(k)) {
            advanced = !advanced;
        } else if ("cost-".equals(k)) {
            SovereignSettings.setInvestigateCost(SovereignSettings.investigateCost() - 5000);
        } else if ("cost+".equals(k)) {
            SovereignSettings.setInvestigateCost(SovereignSettings.investigateCost() + 5000);
        } else if ("gift-".equals(k)) {
            SovereignSettings.setGiftPercent(SovereignSettings.giftPercent() - 2);
        } else if ("gift+".equals(k)) {
            SovereignSettings.setGiftPercent(SovereignSettings.giftPercent() + 2);
        } else if ("envoy-".equals(k)) {
            SovereignSettings.setEnvoyPercent(SovereignSettings.envoyPercent() - 1);
        } else if ("envoy+".equals(k)) {
            SovereignSettings.setEnvoyPercent(SovereignSettings.envoyPercent() + 1);
        } else if ("key".equals(k)) {
            w.startRebind();
        } else if ("reset".equals(k)) {
            SovereignSettings.reset();
        }
        return true;
    }
}
