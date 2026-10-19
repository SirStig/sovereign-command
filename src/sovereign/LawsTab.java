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

final class LawsTab extends TabView {

    private static final String[] RULES = {"Follow the rule", "Always patrolled", "Never patrolled"};

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
        float gap = 8f;
        float w1 = bw * 0.26f;
        float w2 = bw * 0.40f;
        float w3 = bw - w1 - w2 - 2f * gap;
        FactionAPI player = Global.getSector().getPlayerFaction();

        SovereignWindow.Card contraband = w.card(body, 0f, 0f, w1, bh, "Contraband");
        for (CommoditySpecAPI spec : goods()) {
            w.toggleRow(contraband, spec.getName(), "law:ban:" + spec.getId(), player.isIllegal(spec.getId()), null,
                    "Banned", "Legal");
        }
        w.done(contraband);

        SovereignWindow.Card borders = w.card(body, w1 + gap, 0f, w2, bh, "Closed borders");
        w.toggleRow(borders, "Closed borders", "law:borders", SovereignSettings.closedBorders(),
                "Warships in your space are ordered out. Some leave, some don't.");
        borders.tm.addPara("Order out", Theme.dim(), 10f);
        w.toggleRow(borders, "Hostile factions", "law:bar:hostile", SovereignSettings.bars("hostile"), "Factions at war with you");
        w.toggleRow(borders, "Pirates and Pathers", "law:bar:outlaw", SovereignSettings.bars("outlaw"), "They rarely listen");
        w.toggleRow(borders, "Poor relations", "law:bar:poor", SovereignSettings.bars("poor"), "Disliked but not hostile");
        w.toggleRow(borders, "Everyone else", "law:bar:others", SovereignSettings.bars("others"),
                "Neutral or friendly, but not in your alliance");
        borders.tm.addPara("Rules", Theme.dim(), 10f);
        w.choiceRow(borders, "Where", "law:scope",
                SovereignSettings.scope() == 0 ? "Systems you hold" : "Any system of yours",
                "Held means every colony there is yours or an ally's.");
        String[] levels = {"Lax", "Standard", "Strict"};
        w.choiceRow(borders, "Enforcement", "law:enf", levels[SovereignSettings.enforcement()], "How often fleets obey");
        w.stepper(borders, "Strong fleets force through at", Math.round(SovereignSettings.forceRatio() * 100f) + "%",
                "law:force-", "law:force+", true);
        w.tip(borders.tm, "A fleet this big next to your local defense just comes through, and it costs you relations.");
        w.stepper(borders, "Relations lost per forced entry", String.valueOf(Math.round(SovereignSettings.relationPenalty() * 100f)),
                "law:pen-", "law:pen+", true);
        w.toggleRow(borders, "Forced entry means war", "law:war", SovereignSettings.warOnForcedEntry(), null);
        w.toggleRow(borders, "Border messages", "law:notices", SovereignSettings.borderNotices(), null);
        w.done(borders);

        SovereignWindow.Card customs = w.card(body, w1 + w2 + 2f * gap, 0f, w3, bh, "Customs");
        w.toggleRow(customs, "Inspect traders", "law:customs", SovereignSettings.customs(),
                "Trader cargo is checked against your banned list. Smugglers are harder to catch.");
        String[] penalties = {"Turn back", "Fine", "Seize cargo"};
        w.choiceRow(customs, "Penalty", "law:penalty", penalties[SovereignSettings.customsPenalty()],
                "A fine takes half the banned cargo, a seizure takes all of it. You get paid and they like you a bit less.");
        customs.tm.addPara("Your systems", Theme.dim(), 12f);
        List<StarSystemAPI> systems = yourSystems();
        if (systems.isEmpty()) {
            customs.tm.addPara("None yet", Theme.dim(), 4f);
        }
        for (StarSystemAPI system : systems) {
            w.choiceRow(customs, system.getName().replace(" Star System", ""), "law:sys:" + system.getId(),
                    RULES[SovereignSettings.systemRule(system.getId())], null);
        }
        w.done(customs);
    }

    @Override
    boolean handle(String id) {
        if (!id.startsWith("law:")) {
            return false;
        }
        String k = id.substring(4);
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (k.startsWith("ban:")) {
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
