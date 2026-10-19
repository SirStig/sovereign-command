package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.econ.ReinforcedDefenses;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class ColoniesTab extends TabView {

    private static final String[] GOODS = {Commodities.SUPPLIES, Commodities.MARINES, Commodities.HAND_WEAPONS};
    private static final float[] PER_UNIT = {2f, 2.5f, 0.5f};

    private String pickedId;

    ColoniesTab(SovereignWindow window) {
        super(window);
    }

    private List<MarketAPI> mine() {
        List<MarketAPI> list = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (SovereignState.isPlayerMarket(m) && !list.contains(m)) {
                list.add(m);
            }
        }
        Collections.sort(list, new Comparator<MarketAPI>() {
            @Override
            public int compare(MarketAPI a, MarketAPI b) {
                return a.getSize() != b.getSize() ? b.getSize() - a.getSize() : a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return list;
    }

    private MarketAPI picked(List<MarketAPI> list) {
        for (MarketAPI m : list) {
            if (m.getId().equals(pickedId)) {
                return m;
            }
        }
        return list.isEmpty() ? null : list.get(0);
    }

    private static int sizeMult(MarketAPI m) {
        return (int) (10 * Math.pow(2, m.getSize() - 3));
    }

    private static int credits(MarketAPI m) {
        return sizeMult(m) * 200;
    }

    private static int need(MarketAPI m, int i) {
        return (int) (sizeMult(m) * PER_UNIT[i]);
    }

    private boolean affordable(MarketAPI m) {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (cargo.getCredits().get() < credits(m)) {
            return false;
        }
        for (int i = 0; i < GOODS.length; i++) {
            if (cargo.getCommodityQuantity(GOODS[i]) < need(m, i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        List<MarketAPI> list = mine();
        MarketAPI m = picked(list);
        float leftW = 290f;
        SovereignWindow.Card l = w.card(body, 0f, 0f, leftW, bh, "Your colonies (" + list.size() + ")");
        if (list.isEmpty()) {
            w.empty(l.tm, "No colonies", null);
        }
        FactionAPI player = Global.getSector().getPlayerFaction();
        for (MarketAPI c : list) {
            w.listRow(l, "col:" + c.getId(), c.getName(), null, "Size " + c.getSize(), Theme.text(),
                    m != null && c.getId().equals(m.getId()), player.getBaseUIColor(), player.getDarkUIColor());
        }
        w.done(l);

        SovereignWindow.Card d = w.card(body, leftW + 8f, 0f, bw - leftW - 8f, bh, "Orders");
        TooltipMakerAPI tm = d.tm;
        if (m == null) {
            w.empty(tm, "Nothing to command", null);
            w.done(d);
            return;
        }
        SovereignKnowledge.Dossier info = SovereignKnowledge.dossier(m);
        float rw = d.rowWidth();
        tm.addPara(m.getName(), Theme.bright(), 6f);
        tm.addPara(info.system, Theme.dim(), 2f);
        w.facts(tm, rw, new String[]{"Size", "Stability", "Income", "Defense", "Free port"},
                new String[]{info.size, info.stability, info.income, info.defense, m.isFreePort() ? "Yes" : "No"}, null, 8f);
        tm.addPara("Industries", Theme.dim(), 8f);
        tm.addPara(info.industries, Theme.text(), 2f);

        boolean reinforced = m.hasCondition(ReinforcedDefenses.CONDITION_ID);
        tm.addPara("Reinforced defenses", Theme.dim(), 12f);
        tm.addPara(reinforced ? "In effect" : "Not in effect", reinforced ? Theme.good() : Theme.text(), 2f);
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        String[] keys = new String[GOODS.length + 1];
        String[] vals = new String[GOODS.length + 1];
        Color[] cols = new Color[GOODS.length + 1];
        keys[0] = "Credits";
        vals[0] = SovereignKnowledge.credits(credits(m));
        cols[0] = cargo.getCredits().get() >= credits(m) ? null : Theme.bad();
        for (int i = 0; i < GOODS.length; i++) {
            keys[i + 1] = Global.getSettings().getCommoditySpec(GOODS[i]).getName();
            float have = cargo.getCommodityQuantity(GOODS[i]);
            vals[i + 1] = need(m, i) + " (" + Math.round(have) + ")";
            cols[i + 1] = have >= need(m, i) ? null : Theme.bad();
        }
        w.facts(tm, rw, keys, vals, cols, 4f);
        w.confirm(tm, reinforced ? "Extend reinforcement" : "Reinforce defenses", "reinforce", rw, 26f, 10f, affordable(m));
        w.tip(tm, "Paid from your fleet's cargo and wallet. Lasts 60 days.");
        float half = (rw - 6f) / 2f;
        w.button(tm, m.isFreePort() ? "Close free port" : "Open free port", "freeport", half, 24f, 8f, true);
        w.tip(tm, "Banned goods can be sold here.");
        w.button(tm, "Send task force", "send", half, 24f, 6f, true);
        w.done(d);
    }

    @Override
    boolean handle(String id) {
        List<MarketAPI> list = mine();
        MarketAPI m = picked(list);
        if (id.startsWith("col:")) {
            pickedId = id.substring(4);
            return true;
        }
        if (m == null) {
            return false;
        }
        if ("reinforce".equals(id)) {
            if (!affordable(m)) {
                w.say("Not enough credits or cargo.", false);
                return true;
            }
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            if (m.hasCondition(ReinforcedDefenses.CONDITION_ID)) {
                ((ReinforcedDefenses) m.getCondition(ReinforcedDefenses.CONDITION_ID).getPlugin()).extend(ReinforcedDefenses.BASE_TIME);
            } else {
                m.addCondition(ReinforcedDefenses.CONDITION_ID);
            }
            for (int i = 0; i < GOODS.length; i++) {
                cargo.removeCommodity(GOODS[i], need(m, i));
            }
            cargo.getCredits().subtract(credits(m));
            String text = "Defenses at " + m.getName() + " reinforced.";
            SovereignLog.add(SovereignLog.ORDER, text);
            w.say(text, true);
            return true;
        }
        if ("freeport".equals(id)) {
            m.setFreePort(!m.isFreePort());
            String text = m.getName() + (m.isFreePort() ? " is now a free port." : " is no longer a free port.");
            SovereignLog.add(SovereignLog.ORDER, text);
            w.say(text, true);
            return true;
        }
        if ("send".equals(id)) {
            w.targetId = m.getId();
            w.show(SovereignWindow.TabId.FORCES);
            return true;
        }
        return false;
    }
}
