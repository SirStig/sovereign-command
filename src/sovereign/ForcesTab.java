package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;

import java.util.ArrayList;
import java.util.List;

final class ForcesTab extends TabView {

    private int index;
    private final List<SpecialForcesIntel> forces = new ArrayList<SpecialForcesIntel>();

    ForcesTab(SovereignWindow window) {
        super(window);
    }

    private SpecialForcesIntel selected() {
        if (forces.isEmpty()) {
            return null;
        }
        index = Math.max(0, Math.min(forces.size() - 1, index));
        return forces.get(index);
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        forces.clear();
        forces.addAll(SovereignActions.playerTaskForces());
        float leftW = 250f;
        SovereignWindow.Card list = w.card(body, 0f, 0f, leftW, bh, "Task forces");
        if (forces.isEmpty()) {
            w.empty(list.tm, "No task forces", "Raise one at a military market");
        }
        for (int i = 0; i < forces.size(); i++) {
            SpecialForcesIntel f = forces.get(i);
            w.listRow(list, "tf:" + i, SovereignActions.taskForceName(f), null, null, null, i == index,
                    Theme.base(), Theme.dark());
        }
        w.done(list);

        SovereignWindow.Card detail = w.card(body, leftW + 8f, 0f, bw - leftW - 8f, bh, "Orders");
        SpecialForcesIntel f = selected();
        if (f == null) {
            w.empty(detail.tm, "Nothing to command", null);
            w.done(detail);
            return;
        }
        TooltipMakerAPI tm = detail.tm;
        float rw = detail.rowWidth();
        tm.addPara(SovereignActions.taskForceName(f), Theme.bright(), 6f);

        String commander = "Unknown";
        String fleetPoints = "Not deployed";
        String task = "Idle";
        try {
            if (f.getCommander() != null) {
                commander = f.getCommander().getNameString();
            }
            CampaignFleetAPI fleet = f.getRoute().getActiveFleet();
            if (fleet != null && fleet.isAlive()) {
                fleetPoints = String.valueOf(fleet.getFleetPoints());
            }
            if (f.getRouteAI().getCurrentTask() != null) {
                task = f.getRouteAI().getCurrentTask().getText();
            }
        } catch (Exception ex) {
            Global.getLogger(ForcesTab.class).warn("Task force details incomplete", ex);
        }
        w.facts(tm, rw, new String[]{"Commander", "Fleet points", "Doing"},
                new String[]{commander, fleetPoints, task}, null, 8f);

        MarketAPI target = w.target();
        tm.addPara("Target", Theme.dim(), 12f);
        tm.addPara(target == null ? "None" : SovereignKnowledge.nameOf(target) + ", " + SovereignKnowledge.systemName(target),
                target == null ? Theme.dim() : Theme.text(), 2f);
        w.button(tm, target == null ? "Choose in Factions" : "Change target", "pick", rw, 22f, 4f, true);

        float half = (rw - 6f) / 2f;
        boolean has = target != null;
        w.button(tm, "Patrol target", "patrol", half, 24f, 12f, has);
        w.button(tm, "Hold at target", "orbit", half, 24f, 6f, has);
        w.button(tm, "Follow me", "follow", half, 24f, 6f, true);
        w.button(tm, "Hold here", "here", half, 24f, 6f, true);
        w.button(tm, "Refit at target", "rebuild", half, 24f, 6f, has);
        w.tip(tm, "Rebuild losses at the target");
        w.button(tm, "Let it decide", "auto", half, 24f, 6f, true);
        w.button(tm, "Open in intel", "intel", rw, 24f, 12f, true);
        w.done(detail);
    }

    @Override
    boolean handle(String id) {
        if (id.startsWith("tf:")) {
            index = Integer.parseInt(id.substring(3));
            return true;
        }
        if ("pick".equals(id)) {
            w.show(SovereignWindow.TabId.FACTIONS);
            return true;
        }
        final SpecialForcesIntel f = selected();
        MarketAPI target = w.target();
        SovereignActions.Outcome out = null;
        if ("patrol".equals(id)) {
            out = SovereignActions.patrol(f, target);
        } else if ("orbit".equals(id)) {
            out = SovereignActions.orbit(f, target);
        } else if ("follow".equals(id)) {
            out = SovereignActions.follow(f);
        } else if ("here".equals(id)) {
            out = SovereignActions.holdHere(f);
        } else if ("rebuild".equals(id)) {
            out = SovereignActions.rebuild(f, target);
        } else if ("auto".equals(id)) {
            out = SovereignActions.auto(f);
        } else if ("intel".equals(id) && f != null) {
            w.closeThen(new Runnable() {
                @Override
                public void run() {
                    Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, f);
                }
            });
            return true;
        }
        if (out == null) {
            return false;
        }
        w.say(out.text, out.ok);
        return true;
    }
}
