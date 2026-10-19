package sovereign;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lwjgl.input.Keyboard;

/** First-run walkthrough: what the window is, and which optional extras to switch on. */
final class SetupTab extends TabView {

    private static final int LAST = 3;

    private int page;

    SetupTab(SovereignWindow window) {
        super(window);
    }

    void restart() {
        page = 0;
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        float cw = Math.min(680f, bw - 40f);
        float ch = Math.min(bh - 10f, page == 2 ? 500f : 400f);
        SovereignWindow.Card c = w.card(body, (bw - cw) / 2f, 8f, cw, ch, null);
        TooltipMakerAPI tm = c.tm;
        float rw = c.rowWidth();
        tm.setTitleOrbitronLarge();
        if (page == 0) {
            tm.addTitle("Sovereign Command", Theme.bright());
            tm.addPara("A control panel for Nexerelin. Factions, fleets, task forces and colonies in one window, "
                    + "instead of digging through the Special functions menus.", Theme.text(), 16f);
            tm.addPara("Out of the box it only shows and does what Nexerelin already does. "
                    + "It doesn't change how the game plays.", Theme.text(), 12f);
            tm.addPara("There are a few optional extras. The next pages explain them so you can pick.", Theme.text(), 12f);
        } else if (page == 1) {
            tm.addTitle("Always here", Theme.bright());
            item(tm, "Factions", "Relations, wars, alliances and every colony a faction holds. Your own colonies are "
                    + "here too: reinforce their defenses or open a free port.");
            item(tm, "Fleets", "Orders for your task forces, new fleet requests with Nexerelin's costs and fleet pool, "
                    + "and every operation under way.");
            item(tm, "Log and Settings", "The two buttons at the top right, next to Close.");
        } else if (page == 2) {
            tm.addTitle("Extras", Theme.bright());
            tm.addPara("Each one changes how the game plays. All of them are off until you switch them on.",
                    Theme.dim(), 10f);
            w.buttonPair(c, "Interface only", "setup:none", "Everything", "setup:all", true);
            w.toggleRow(c, "Diplomacy", "setup:diplomacy", SovereignSettings.diplomacy(), null);
            tm.addPara("Send gifts, declare war and negotiate peace. What a faction asks for depends on who is "
                    + "winning, and a beaten faction will take almost anything.", Theme.dim(), 0f);
            w.toggleRow(c, "Border law", "setup:laws", SovereignSettings.laws(), null);
            tm.addPara("Order warships out of your systems, ban goods and fine or seize cargo from traders. "
                    + "Fleets can ignore you, and pirates will try to slip in.", Theme.dim(), 0f);
            w.toggleRow(c, "Fog of war", "setup:fog", SovereignSettings.fog(), null);
            tm.addPara("You only see colonies you have visited. Pay for a survey to see inside the rest.",
                    Theme.dim(), 0f);
        } else {
            tm.addTitle("Ready", Theme.bright());
            tm.addPara("Open this window with " + Keyboard.getKeyName(SovereignSettings.hotkey())
                    + ", the Sovereign Command ability, or its entry in the intel screen.", Theme.text(), 16f);
            tm.addPara("The extras you picked are under Settings. You can change them there, or run this setup "
                    + "again, whenever you like.", Theme.text(), 12f);
        }
        if (page == 0) {
            w.buttonPair(c, "Skip", "setup:skip", "Next", "setup:next", true);
        } else if (page == LAST) {
            w.buttonPair(c, "Back", "setup:back", "Start", "setup:done", true);
        } else {
            w.buttonPair(c, "Back", "setup:back", "Next", "setup:next", true);
        }
        w.done(c);
    }

    private void item(TooltipMakerAPI tm, String name, String text) {
        tm.addPara(name, Theme.hl(), 14f);
        tm.addPara(text, Theme.text(), 2f);
    }

    @Override
    boolean handle(String id) {
        if (!id.startsWith("setup:")) {
            return false;
        }
        String k = id.substring(6);
        if ("next".equals(k)) {
            page = Math.min(LAST, page + 1);
        } else if ("back".equals(k)) {
            page = Math.max(0, page - 1);
        } else if ("skip".equals(k)) {
            choose(false);
            SovereignSettings.setSetupDone(true);
        } else if ("done".equals(k)) {
            SovereignSettings.setSetupDone(true);
        } else if ("none".equals(k)) {
            choose(false);
        } else if ("all".equals(k)) {
            choose(true);
        } else if ("diplomacy".equals(k)) {
            SovereignSettings.setDiplomacy(!SovereignSettings.diplomacy());
        } else if ("laws".equals(k)) {
            SovereignSettings.setLaws(!SovereignSettings.laws());
        } else if ("fog".equals(k)) {
            SovereignSettings.setFog(!SovereignSettings.fog());
        }
        return true;
    }

    private void choose(boolean on) {
        SovereignSettings.setDiplomacy(on);
        SovereignSettings.setLaws(on);
        SovereignSettings.setFog(on);
    }
}
