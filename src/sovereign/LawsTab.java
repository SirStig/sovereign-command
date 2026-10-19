package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** What your faction forbids and how it polices its space. One section at a time. */
final class LawsTab extends TabView {

    private static final String[] RULES = {"Follow the general rule", "Always patrolled", "Never patrolled"};

    private String section = "borders";
    private boolean fineTuning;

    LawsTab(SovereignWindow window) {
        super(window);
    }

    private List<CommoditySpecAPI> goods() {
        List<CommoditySpecAPI> list = new ArrayList<CommoditySpecAPI>();
        for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) {
            if (!spec.isMeta() && !spec.isNonEcon() && !"crew".equals(spec.getId())) {
                list.add(spec);
            }
        }
        Collections.sort(list, new Comparator<CommoditySpecAPI>() {
            @Override
            public int compare(CommoditySpecAPI a, CommoditySpecAPI b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return list;
    }

    private List<StarSystemAPI> yourSystems() {
        List<StarSystemAPI> list = new ArrayList<StarSystemAPI>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (MarketAPI m : Misc.getMarketsInLocation(system)) {
                if (SovereignState.isPlayerMarket(m)) {
                    list.add(system);
                    break;
                }
            }
        }
        Collections.sort(list, new Comparator<StarSystemAPI>() {
            @Override
            public int compare(StarSystemAPI a, StarSystemAPI b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return list;
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        float navW = 190f;
        SovereignWindow.Card nav = w.card(body, 0f, 0f, navW, bh, "Laws");
        String[][] sections = {{"borders", "Border patrols"}, {"customs", "Customs"}, {"goods", "Banned goods"},
                {"systems", "Your systems"}};
        for (String[] sec : sections) {
            w.chip(nav.tm, sec[1], "law:go:" + sec[0], sec[0].equals(section), nav.rowWidth(), 26f, 4f, true);
        }
        w.done(nav);

        float x = navW + 8f;
        float cw = bw - x;
        if ("customs".equals(section)) {
            customs(body, x, cw, bh);
        } else if ("goods".equals(section)) {
            banned(body, x, cw, bh);
        } else if ("systems".equals(section)) {
            systems(body, x, cw, bh);
        } else {
            borders(body, x, cw, bh);
        }
    }

    private void borders(CustomPanelAPI body, float x, float cw, float bh) {
        SovereignWindow.Card c = w.card(body, x, 0f, cw, bh, "Border patrols");
        w.toggleRow(c, "Turn warships away", "law:borders", SovereignSettings.closedBorders(),
                "Warships entering your space are told to leave. Some do, some don't.");
        c.tm.addPara("Who gets turned away", Theme.dim(), 12f);
        w.toggleRow(c, "Hostile factions", "law:bar:hostile", SovereignSettings.bars("hostile"), "Factions at war with you.");
        w.toggleRow(c, "Pirates and Pathers", "law:bar:outlaw", SovereignSettings.bars("outlaw"), "They rarely listen.");
        w.toggleRow(c, "Unfriendly factions", "law:bar:poor", SovereignSettings.bars("poor"),
                "Not at war, but they don't like you.");
        w.toggleRow(c, "Everyone else", "law:bar:others", SovereignSettings.bars("others"),
                "Neutral or friendly factions that aren't in your alliance.");
        c.tm.addPara("Where and how hard", Theme.dim(), 12f);
        w.choiceRow(c, "Where", "law:scope",
                SovereignSettings.scope() == 0 ? "Systems you hold" : "Any system with a colony of yours",
                "Held means every colony in the system is yours or an ally's.");
        String[] levels = {"Lax", "Normal", "Strict"};
        w.choiceRow(c, "Enforcement", "law:enf", levels[SovereignSettings.enforcement()], "How often fleets obey.");
        w.button(c.tm, fineTuning ? "Hide fine tuning" : "Fine tuning", "law:tune", c.rowWidth(), 24f, 14f, true);
        if (fineTuning) {
            w.stepper(c, "Fleets this big get through", Math.round(SovereignSettings.forceRatio() * 100f) + "%",
                    "law:force-", "law:force+", true);
            w.tip(c.tm, "Compared with the local defense. A fleet this size isn't stopped, and it costs you relations.");
            w.stepper(c, "Relations lost when they do", String.valueOf(Math.round(SovereignSettings.relationPenalty() * 100f)),
                    "law:pen-", "law:pen+", true);
            w.toggleRow(c, "Breaking through means war", "law:war", SovereignSettings.warOnForcedEntry(), null);
            w.toggleRow(c, "Show messages", "law:notices", SovereignSettings.borderNotices(),
                    "Report turned-away and ignored fleets on the campaign map.");
        }
        w.done(c);
    }

    private void customs(CustomPanelAPI body, float x, float cw, float bh) {
        SovereignWindow.Card c = w.card(body, x, 0f, cw, bh, "Customs");
        w.toggleRow(c, "Check trader cargo", "law:customs", SovereignSettings.customs(),
                "Traders in your space are checked against your banned goods. Smugglers are harder to catch.");
        String[] penalties = {"Turn back", "Fine", "Seize cargo"};
        w.choiceRow(c, "When they find something", "law:penalty", penalties[SovereignSettings.customsPenalty()],
                "A fine takes half the banned cargo and a seizure takes all of it. You get paid either way, "
                        + "and the faction likes you a bit less.");
        String[] levels = {"Lax", "Normal", "Strict"};
        w.choiceRow(c, "Enforcement", "law:enf", levels[SovereignSettings.enforcement()],
                "Shared with border patrols. Higher means more traders get caught.");
        w.done(c);
    }

    private void banned(CustomPanelAPI body, float x, float cw, float bh) {
        SovereignWindow.Card c = w.card(body, x, 0f, cw, bh, "Banned goods");
        FactionAPI player = Global.getSector().getPlayerFaction();
        c.tm.addPara("Banned goods are illegal in all your colonies, and customs looks for them.", Theme.dim(), 6f);
        for (CommoditySpecAPI spec : goods()) {
            w.toggleRow(c, spec.getName(), "law:ban:" + spec.getId(), player.isIllegal(spec.getId()), null, "Banned", "Legal");
        }
        w.done(c);
    }

    private void systems(CustomPanelAPI body, float x, float cw, float bh) {
        SovereignWindow.Card c = w.card(body, x, 0f, cw, bh, "Your systems");
        c.tm.addPara("Override the general patrol rule for one system.", Theme.dim(), 6f);
        List<StarSystemAPI> systems = yourSystems();
        if (systems.isEmpty()) {
            w.empty(c.tm, "You have no colonies yet", null);
        }
        for (StarSystemAPI system : systems) {
            w.choiceRow(c, system.getName().replace(" Star System", ""), "law:sys:" + system.getId(),
                    RULES[SovereignSettings.systemRule(system.getId())], null);
        }
        w.done(c);
    }

    @Override
    boolean handle(String id) {
        if (!id.startsWith("law:")) {
            return false;
        }
        String k = id.substring(4);
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (k.startsWith("go:")) {
            section = k.substring(3);
        } else if ("tune".equals(k)) {
            fineTuning = !fineTuning;
        } else if (k.startsWith("ban:")) {
            String commodity = k.substring(4);
            if (player.isIllegal(commodity)) {
                player.makeCommodityLegal(commodity);
            } else {
                player.makeCommodityIllegal(commodity);
            }
        } else if (k.startsWith("bar:")) {
            String g = k.substring(4);
            SovereignSettings.setBars(g, !SovereignSettings.bars(g));
        } else if (k.startsWith("sys:")) {
            String sys = k.substring(4);
            SovereignSettings.setSystemRule(sys, SovereignSettings.systemRule(sys) + 1);
        } else if ("borders".equals(k)) {
            SovereignSettings.setClosedBorders(!SovereignSettings.closedBorders());
        } else if ("scope".equals(k)) {
            SovereignSettings.setScope(SovereignSettings.scope() + 1);
        } else if ("enf".equals(k)) {
            SovereignSettings.setEnforcement(SovereignSettings.enforcement() + 1);
        } else if ("force-".equals(k)) {
            SovereignSettings.setForceRatio(SovereignSettings.forceRatio() - 0.05f);
        } else if ("force+".equals(k)) {
            SovereignSettings.setForceRatio(SovereignSettings.forceRatio() + 0.05f);
        } else if ("pen-".equals(k)) {
            SovereignSettings.setRelationPenalty(SovereignSettings.relationPenalty() - 0.05f);
        } else if ("pen+".equals(k)) {
            SovereignSettings.setRelationPenalty(SovereignSettings.relationPenalty() + 0.05f);
        } else if ("war".equals(k)) {
            SovereignSettings.setWarOnForcedEntry(!SovereignSettings.warOnForcedEntry());
        } else if ("notices".equals(k)) {
            SovereignSettings.setBorderNotices(!SovereignSettings.borderNotices());
        } else if ("customs".equals(k)) {
            SovereignSettings.setCustoms(!SovereignSettings.customs());
        } else if ("penalty".equals(k)) {
            SovereignSettings.setCustomsPenalty(SovereignSettings.customsPenalty() + 1);
        } else {
            return false;
        }
        return true;
    }
}
