package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Task force orders, new fleet requests, and the operations already under way, in one list. */
final class FleetsTab extends TabView {

    private static final String[] STAGES = {"Organizing", "Assembling", "En route", "Attacking", "Returning"};

    private String sel = "new";
    private FleetType type;
    private String sourceId;
    private int fp = 200;
    private int marines = 200;
    private boolean armed;
    private final List<SpecialForcesIntel> forces = new ArrayList<SpecialForcesIntel>();
    private final List<OffensiveFleetIntel> mine = new ArrayList<OffensiveFleetIntel>();
    private final List<OffensiveFleetIntel> incoming = new ArrayList<OffensiveFleetIntel>();

    FleetsTab(SovereignWindow window) {
        super(window);
    }

    void showRequest() {
        sel = "new";
    }

    void showOrders() {
        sel = SovereignActions.playerTaskForces().isEmpty() ? "new" : "f:tf:0";
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

    private void gather() {
        forces.clear();
        forces.addAll(SovereignActions.playerTaskForces());
        mine.clear();
        incoming.clear();
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
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        gather();
        float leftW = 300f;
        buildList(body, leftW, bh);
        float x = leftW + 8f;
        float rw = bw - x;
        if (sel.startsWith("f:tf:")) {
            buildForce(body, x, rw, bh);
        } else if (sel.startsWith("f:op:") || sel.startsWith("f:in:")) {
            buildOperation(body, x, rw, bh);
        } else {
            buildRequest(body, x, rw, bh);
        }
    }

    // ---- list -------------------------------------------------------------------------------------------

    private void buildList(CustomPanelAPI body, float cw, float ch) {
        SovereignWindow.Card c = w.card(body, 0f, 0f, cw, ch, "Fleets");
        TooltipMakerAPI tm = c.tm;
        w.chip(tm, "New fleet request", "f:new", "new".equals(sel), c.rowWidth(), 26f, 6f, true);
        tm.addPara("Task forces", Theme.dim(), 12f);
        if (forces.isEmpty()) {
            tm.addPara("None. Raise one at a military market.", Theme.dim(), 2f);
        }
        for (int i = 0; i < forces.size(); i++) {
            w.listRow(c, "f:tf:" + i, SovereignActions.taskForceName(forces.get(i)), null, null, null,
                    sel.equals("f:tf:" + i), Theme.base(), Theme.dark());
        }
        tm.addPara("Your operations", Theme.dim(), 12f);
        if (mine.isEmpty()) {
            tm.addPara("None", Theme.dim(), 2f);
        }
        for (int i = 0; i < mine.size(); i++) {
            row(c, "f:op:" + i, mine.get(i));
        }
        tm.addPara("Coming for you", Theme.dim(), 12f);
        if (incoming.isEmpty()) {
            tm.addPara("Quiet", Theme.dim(), 2f);
        }
        for (int i = 0; i < incoming.size(); i++) {
            row(c, "f:in:" + i, incoming.get(i));
        }
        w.done(c);
    }

    private void row(SovereignWindow.Card c, String id, OffensiveFleetIntel intel) {
        FactionAPI f = intel.getFaction();
        w.listRow(c, id, intel.getName(), null, STAGES[stage(intel)], Theme.dim(), sel.equals(id),
                f == null ? Theme.base() : f.getBaseUIColor(), f == null ? Theme.dark() : f.getDarkUIColor());
    }

    private int stage(OffensiveFleetIntel intel) {
        return Math.max(0, Math.min(STAGES.length - 1, intel.getCurrentStage()));
    }

    // ---- new request ------------------------------------------------------------------------------------

    private List<MarketAPI> validTargets(FleetType t) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getPrimaryEntity() != null && FleetRequest.targetProblem(t, m) == null) {
                out.add(m);
            }
        }
        return out;
    }

    private void buildRequest(CustomPanelAPI body, float x, float cw, float ch) {
        SovereignWindow.Card c = w.card(body, x, 0f, cw, ch, "New fleet request");
        TooltipMakerAPI tm = c.tm;
        float rw = c.rowWidth();
        FleetType t = type();
        List<MarketAPI> sources = FleetRequest.sources();
        MarketAPI source = source(sources);
        MarketAPI target = w.target();

        List<FleetType> kinds = FleetRequest.kinds();
        String[] labels = new String[kinds.size()];
        String[] ids = new String[kinds.size()];
        for (int i = 0; i < kinds.size(); i++) {
            labels[i] = FleetRequest.label(kinds.get(i));
            ids[i] = "type:" + kinds.get(i).name();
        }
        w.chipRow(c, labels, ids, "type:" + t.name());
        tm.addPara(FleetRequest.blurb(t), Theme.dim(), 2f);

        tm.addPara("Launch from", Theme.dim(), 12f);
        if (source == null) {
            tm.addPara("No colony with a working spaceport", Theme.bad(), 2f);
        } else {
            tm.addPara(source.getName() + ", " + SovereignKnowledge.systemName(source), Theme.text(), 2f);
            w.button(tm, "Choose colony", "src:pick", rw, 22f, 4f, sources.size() > 1);
        }

        tm.addPara("Target", Theme.dim(), 12f);
        String problem = FleetRequest.targetProblem(t, target);
        if (target == null) {
            tm.addPara("None chosen", Theme.dim(), 2f);
        } else {
            FactionAPI tf = target.getFaction();
            tm.addPara(SovereignKnowledge.nameOf(target) + ", " + SovereignKnowledge.systemName(target)
                    + (tf == null ? "" : " (" + tf.getDisplayName() + ")"), tf == null ? Theme.text() : tf.getBaseUIColor(), 2f);
            if (problem != null) {
                tm.addPara(problem, Theme.bad(), 2f);
            }
        }
        w.button(tm, target == null ? "Choose target" : "Change target", "tgt:pick", rw, 22f, 4f, true);

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
        w.facts(tm, rw, new String[]{"Cost", "Fleet pool", "Ready in"},
                new String[]{SovereignKnowledge.credits(cost), pool + " FP", Math.round(FleetRequest.days(t, fp)) + " days"},
                new Color[]{afford ? Theme.text() : Theme.bad(), pool >= FleetRequest.MIN_FP ? Theme.text() : Theme.bad(), null},
                10f);

        boolean ready = source != null && problem == null && afford && pool >= FleetRequest.MIN_FP;
        boolean provokes = ready && t.isAggressive && !target.getFaction().isHostileTo(Factions.PLAYER);
        if (armed && provokes) {
            w.danger(tm, "Confirm: " + target.getFaction().getDisplayName() + " turns hostile", "launch", rw, 28f, 12f, true);
        } else {
            w.confirm(tm, "Launch", "launch", rw, 28f, 12f, ready);
        }
        if (!afford) {
            tm.addPara("Not enough credits", Theme.bad(), 4f);
        } else if (pool < FleetRequest.MIN_FP) {
            tm.addPara("The fleet pool is empty", Theme.bad(), 4f);
        }
        w.done(c);
    }

    // ---- task force -------------------------------------------------------------------------------------

    private SpecialForcesIntel force() {
        if (!sel.startsWith("f:tf:")) {
            return null;
        }
        int i = Integer.parseInt(sel.substring(5));
        return i >= 0 && i < forces.size() ? forces.get(i) : null;
    }

    private void buildForce(CustomPanelAPI body, float x, float cw, float ch) {
        SpecialForcesIntel f = force();
        SovereignWindow.Card c = w.card(body, x, 0f, cw, ch, "Task force");
        TooltipMakerAPI tm = c.tm;
        if (f == null) {
            w.empty(tm, "Nothing selected", null);
            w.done(c);
            return;
        }
        float rw = c.rowWidth();
        tm.addPara(SovereignActions.taskForceName(f), Theme.bright(), 6f);
        String commander = "Unknown";
        String points = "Not deployed";
        String doing = "Idle";
        try {
            if (f.getCommander() != null) {
                commander = f.getCommander().getNameString();
            }
            CampaignFleetAPI fleet = f.getRoute().getActiveFleet();
            if (fleet != null && fleet.isAlive()) {
                points = String.valueOf(fleet.getFleetPoints());
            }
            if (f.getRouteAI().getCurrentTask() != null) {
                doing = f.getRouteAI().getCurrentTask().getText();
            }
        } catch (Exception ex) {
            Global.getLogger(FleetsTab.class).warn("Task force details incomplete", ex);
        }
        w.facts(tm, rw, new String[]{"Commander", "Fleet points", "Doing"}, new String[]{commander, points, doing}, null, 8f);

        MarketAPI target = w.target();
        tm.addPara("Order target", Theme.dim(), 12f);
        tm.addPara(target == null ? "None chosen" : SovereignKnowledge.nameOf(target) + ", " + SovereignKnowledge.systemName(target),
                target == null ? Theme.dim() : Theme.text(), 2f);
        w.button(tm, target == null ? "Choose target" : "Change target", "tgt:pick", rw, 22f, 4f, true);

        float half = (rw - 6f) / 2f;
        boolean has = target != null;
        w.button(tm, "Patrol target", "o:patrol", half, 24f, 12f, has);
        w.button(tm, "Hold at target", "o:orbit", half, 24f, 6f, has);
        w.button(tm, "Follow me", "o:follow", half, 24f, 6f, true);
        w.button(tm, "Hold here", "o:here", half, 24f, 6f, true);
        w.button(tm, "Refit at target", "o:rebuild", half, 24f, 6f, has);
        w.tip(tm, "Rebuild losses at the target colony");
        w.button(tm, "Let it decide", "o:auto", half, 24f, 6f, true);
        w.button(tm, "Open in intel", "view", rw, 24f, 12f, true);
        w.done(c);
    }

    // ---- operation --------------------------------------------------------------------------------------

    private OffensiveFleetIntel operation() {
        List<OffensiveFleetIntel> list = sel.startsWith("f:op:") ? mine : incoming;
        if (!sel.startsWith("f:op:") && !sel.startsWith("f:in:")) {
            return null;
        }
        int i = Integer.parseInt(sel.substring(5));
        return i >= 0 && i < list.size() ? list.get(i) : null;
    }

    private void buildOperation(CustomPanelAPI body, float x, float cw, float ch) {
        OffensiveFleetIntel op = operation();
        SovereignWindow.Card c = w.card(body, x, 0f, cw, ch, sel.startsWith("f:op:") ? "Your operation" : "Incoming");
        TooltipMakerAPI tm = c.tm;
        if (op == null) {
            w.empty(tm, "Nothing selected", null);
            w.done(c);
            return;
        }
        float rw = c.rowWidth();
        tm.addPara(op.getName(), Theme.bright(), 6f);
        String eta = "";
        try {
            float days = op.getETA();
            if (days > 0f && stage(op) < 3) {
                eta = Math.round(days) + " days";
            }
        } catch (Exception ex) {
            eta = "";
        }
        MarketAPI target = op.getTarget();
        MarketAPI from = op.getMarketFrom();
        w.facts(tm, rw, new String[]{"Faction", "Status", "Arrives in", "From", "Target", "Fleet points"},
                new String[]{op.getFaction() == null ? "?" : op.getFaction().getDisplayName(), STAGES[stage(op)],
                        eta.isEmpty() ? "-" : eta, from == null ? "?" : from.getName(),
                        target == null ? "?" : target.getName(), String.valueOf(Math.round(op.getFP()))}, null, 8f);
        w.button(tm, "Open in intel", "view", rw, 24f, 12f, true);
        w.done(c);
    }

    // ---- actions ----------------------------------------------------------------------------------------

    @Override
    boolean handle(String id) {
        boolean wasArmed = armed;
        armed = false;
        if ("f:new".equals(id) || id.startsWith("f:tf:") || id.startsWith("f:op:") || id.startsWith("f:in:")) {
            sel = id;
            return true;
        }
        if (id.startsWith("type:")) {
            type = FleetType.valueOf(id.substring(5));
            return true;
        }
        if ("src:pick".equals(id)) {
            List<MarketAPI> sources = FleetRequest.sources();
            w.openPicker(new PickerView(w, "Launch from", sources, source(sources), false, new PickerView.Listener() {
                @Override
                public void picked(MarketAPI market) {
                    sourceId = market.getId();
                }
            }));
            return true;
        }
        if ("tgt:pick".equals(id)) {
            List<MarketAPI> options;
            String title;
            if ("new".equals(sel)) {
                options = validTargets(type());
                title = "Target for a " + FleetRequest.label(type()).toLowerCase();
            } else {
                options = new ArrayList<MarketAPI>();
                for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                    if (m.getPrimaryEntity() != null && m.getContainingLocation() != null
                            && !m.getContainingLocation().isHyperspace() && !m.isHidden()) {
                        options.add(m);
                    }
                }
                title = "Order target";
            }
            if (options.isEmpty()) {
                w.say("Nothing valid to pick for this request.", false);
                return true;
            }
            w.openPicker(new PickerView(w, title, options, w.target(), true, new PickerView.Listener() {
                @Override
                public void picked(MarketAPI market) {
                    w.targetId = market.getId();
                }
            }));
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
            marines = Math.min(InvasionIntel.MAX_MARINES_TOTAL, marines + FleetRequest.MARINE_STEP);
            return true;
        }
        if ("launch".equals(id)) {
            FleetType t = type();
            MarketAPI target = w.target();
            boolean provokes = target != null && t.isAggressive && !target.getFaction().isHostileTo(Factions.PLAYER);
            if (provokes && !wasArmed) {
                armed = true;
                return true;
            }
            SovereignActions.Outcome out = FleetRequest.launch(t, source(FleetRequest.sources()), target, fp, marines);
            w.say(out.text, out.ok);
            return true;
        }
        if ("view".equals(id)) {
            final IntelInfoPlugin intel = sel.startsWith("f:tf:") ? (IntelInfoPlugin) force() : (IntelInfoPlugin) operation();
            if (intel != null) {
                w.closeThen(new Runnable() {
                    @Override
                    public void run() {
                        Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, intel);
                    }
                });
            }
            return true;
        }
        if (id.startsWith("o:")) {
            SpecialForcesIntel f = force();
            MarketAPI target = w.target();
            String k = id.substring(2);
            SovereignActions.Outcome out;
            if ("patrol".equals(k)) {
                out = SovereignActions.patrol(f, target);
            } else if ("orbit".equals(k)) {
                out = SovereignActions.orbit(f, target);
            } else if ("follow".equals(k)) {
                out = SovereignActions.follow(f);
            } else if ("here".equals(k)) {
                out = SovereignActions.holdHere(f);
            } else if ("rebuild".equals(k)) {
                out = SovereignActions.rebuild(f, target);
            } else {
                out = SovereignActions.auto(f);
            }
            w.say(out.text, out.ok);
            return true;
        }
        return false;
    }
}
