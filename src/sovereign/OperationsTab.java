package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest.FleetType;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

final class OperationsTab extends TabView {

    private static final String[] STAGES = {"Organizing", "Assembling", "En route", "Attacking", "Returning"};

    private FleetType type;
    private String sourceId;
    private int fp = 200;
    private int marines = 200;
    private boolean armed;
    private final List<OffensiveFleetIntel> shown = new ArrayList<OffensiveFleetIntel>();

    OperationsTab(SovereignWindow window) {
        super(window);
    }

    private FleetType type() {
        List<FleetType> kinds = FleetRequest.kinds();
        if (type == null || !kinds.contains(type)) {
            type = kinds.get(0);
        }
        return type;
    }

    private MarketAPI source(List<MarketAPI> sources) {
        for (MarketAPI m : sources) {
            if (m.getId().equals(sourceId)) {
                return m;
            }
        }
        if (sources.isEmpty()) {
            return null;
        }
        sourceId = sources.get(0).getId();
        return sources.get(0);
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        float leftW = Math.min(470f, bw * 0.5f);
        buildRequest(body, 0f, 0f, leftW, bh);
        buildActive(body, leftW + 8f, 0f, bw - leftW - 8f, bh);
    }

    private void buildRequest(CustomPanelAPI body, float x, float y, float cw, float ch) {
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Request a fleet");
        TooltipMakerAPI tm = c.tm;
        FleetType t = type();
        List<MarketAPI> sources = FleetRequest.sources();
        MarketAPI source = source(sources);
        MarketAPI target = w.target();

        List<FleetType> kinds = FleetRequest.kinds();
        for (FleetType k : kinds) {
            w.chip(tm, FleetRequest.label(k), "type:" + k.name(), k == t, c.rowWidth(), 22f, 3f, true);
        }
        tm.addPara(FleetRequest.blurb(t), Theme.dim(), 6f);

        tm.addPara("From", Theme.dim(), 10f);
        if (sources.isEmpty()) {
            tm.addPara("No colony with a spaceport", Theme.bad(), 2f);
        } else {
            w.button(tm, source.getName(), "src:next", c.rowWidth(), 24f, 2f, sources.size() > 1);
            w.tip(tm, "Click for the next colony");
        }

        tm.addPara("Target", Theme.dim(), 10f);
        String problem = FleetRequest.targetProblem(t, target);
        if (target == null) {
            w.button(tm, "Choose in Factions", "pick", c.rowWidth(), 24f, 2f, true);
        } else {
            FactionAPI tf = target.getFaction();
            tm.addPara(SovereignKnowledge.nameOf(target) + ", " + SovereignKnowledge.systemName(target),
                    tf == null ? Theme.text() : tf.getBaseUIColor(), 2f);
            w.button(tm, "Change target", "pick", c.rowWidth(), 22f, 4f, true);
            if (problem != null) {
                tm.addPara(problem, Theme.bad(), 4f);
            }
        }

        int max = FleetRequest.maxFp();
        fp = Math.max(FleetRequest.MIN_FP, Math.min(max, fp));
        w.stepper(c, "Fleet points", String.valueOf(fp), "fp:-", "fp:+", true);
        if (t == FleetType.INVASION) {
            marines = Math.max(FleetRequest.MIN_MARINES, marines);
            w.stepper(c, "Marines", String.valueOf(marines), "mar:-", "mar:+", true);
        }

        int cost = FleetRequest.cost(t, fp, marines);
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        boolean afford = credits >= cost || Global.getSettings().isDevMode();
        int pool = FleetRequest.pool();
        w.facts(tm, c.rowWidth(),
                new String[]{"Cost", "Fleet pool", "Ready in"},
                new String[]{SovereignKnowledge.credits(cost), pool + " FP",
                        Math.round(FleetRequest.days(t, fp)) + " days"},
                new Color[]{afford ? Theme.text() : Theme.bad(), pool >= FleetRequest.MIN_FP ? Theme.text() : Theme.bad(), null},
                10f);

        boolean ready = source != null && problem == null && afford && pool >= FleetRequest.MIN_FP;
        boolean provokes = ready && t.isAggressive && !target.getFaction().isHostileTo(Factions.PLAYER);
        if (armed && provokes) {
            w.danger(tm, "Confirm: " + target.getFaction().getDisplayName() + " turns hostile", "launch",
                    c.rowWidth(), 28f, 12f, true);
        } else {
            w.confirm(tm, "Launch", "launch", c.rowWidth(), 28f, 12f, ready);
        }
        if (!afford) {
            tm.addPara("Not enough credits", Theme.bad(), 4f);
        } else if (pool < FleetRequest.MIN_FP) {
            tm.addPara("The fleet pool is empty", Theme.bad(), 4f);
        }
        w.done(c);
    }

    private void buildActive(CustomPanelAPI body, float x, float y, float cw, float ch) {
        shown.clear();
        List<OffensiveFleetIntel> mine = new ArrayList<OffensiveFleetIntel>();
        List<OffensiveFleetIntel> incoming = new ArrayList<OffensiveFleetIntel>();
        for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel(OffensiveFleetIntel.class)) {
            OffensiveFleetIntel intel = (OffensiveFleetIntel) plugin;
            if (intel.isEnding() || intel.isEnded()) {
                continue;
            }
            if (intel.isPlayerSpawned()) {
                mine.add(intel);
            } else if (intel.getTarget() != null && SovereignState.isPlayerMarket(intel.getTarget())) {
                incoming.add(intel);
            }
        }
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Fleets in motion");
        TooltipMakerAPI tm = c.tm;
        tm.addPara("Yours", Theme.dim(), 8f);
        if (mine.isEmpty()) {
            tm.addPara("None", Theme.dim(), 4f);
        }
        for (OffensiveFleetIntel intel : mine) {
            row(c, intel);
        }
        tm.addPara("Coming for you", Theme.dim(), 12f);
        if (incoming.isEmpty()) {
            tm.addPara("Quiet", Theme.dim(), 4f);
        }
        for (OffensiveFleetIntel intel : incoming) {
            row(c, intel);
        }
        w.done(c);
    }

    private void row(SovereignWindow.Card c, OffensiveFleetIntel intel) {
        int index = shown.size();
        shown.add(intel);
        int stage = Math.max(0, Math.min(STAGES.length - 1, intel.getCurrentStage()));
        String eta = "";
        try {
            float days = intel.getETA();
            if (days > 0f && stage < 3) {
                eta = Math.round(days) + " d";
            }
        } catch (Exception ex) {
            eta = "";
        }
        FactionAPI f = intel.getFaction();
        w.listRow(c, "op:" + index, intel.getName(), STAGES[stage], eta, Theme.text(), false,
                f == null ? Theme.base() : f.getBaseUIColor(), f == null ? Theme.dark() : f.getDarkUIColor());
    }

    @Override
    boolean handle(String id) {
        boolean wasArmed = armed;
        armed = false;
        if (id.startsWith("type:")) {
            type = FleetType.valueOf(id.substring(5));
            return true;
        }
        if ("src:next".equals(id)) {
            List<MarketAPI> sources = FleetRequest.sources();
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).getId().equals(sourceId)) {
                    sourceId = sources.get((i + 1) % sources.size()).getId();
                    return true;
                }
            }
            if (!sources.isEmpty()) {
                sourceId = sources.get(0).getId();
            }
            return true;
        }
        if ("pick".equals(id)) {
            w.show(SovereignWindow.TabId.FACTIONS);
            return true;
        }
        if ("fp:-".equals(id)) {
            fp = Math.max(FleetRequest.MIN_FP, fp - FleetRequest.FP_STEP);
            return true;
        }
        if ("fp:+".equals(id)) {
            fp = Math.min(FleetRequest.maxFp(), fp + FleetRequest.FP_STEP);
            return true;
        }
        if ("mar:-".equals(id)) {
            marines = Math.max(FleetRequest.MIN_MARINES, marines - FleetRequest.MARINE_STEP);
            return true;
        }
        if ("mar:+".equals(id)) {
            marines = Math.min(InvasionIntel.MAX_MARINES_TOTAL,
                    marines + FleetRequest.MARINE_STEP);
            return true;
        }
        if ("launch".equals(id)) {
            FleetType t = type();
            MarketAPI target = w.target();
            MarketAPI source = source(FleetRequest.sources());
            boolean provokes = target != null && t.isAggressive
                    && !target.getFaction().isHostileTo(Factions.PLAYER);
            if (provokes && !wasArmed) {
                armed = true;
                return true;
            }
            SovereignActions.Outcome out = FleetRequest.launch(t, source, target, fp, marines);
            w.say(out.text, out.ok);
            return true;
        }
        if (id.startsWith("op:")) {
            int index = Integer.parseInt(id.substring(3));
            if (index >= 0 && index < shown.size()) {
                final OffensiveFleetIntel intel = shown.get(index);
                w.closeThen(new Runnable() {
                    @Override
                    public void run() {
                        Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, intel);
                    }
                });
            }
            return true;
        }
        return false;
    }
}
