package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FactionsTab extends TabView {

    private String factionId;
    private String armed;
    private boolean negotiating;
    private int reparations;
    private final Set<String> returned = new HashSet<String>();

    FactionsTab(SovereignWindow window, StarSystemAPI focus) {
        super(window);
        if (focus != null) {
            MarketAPI best = null;
            for (MarketAPI market : Misc.getMarketsInLocation(focus)) {
                if (market != null && (best == null || market.getSize() > best.getSize())) {
                    best = market;
                }
            }
            if (best != null && best.getFaction() != null) {
                factionId = best.getFaction().getId();
                w.targetId = best.getId();
            }
        }
        if (factionId == null) {
            factionId = Global.getSector().getPlayerFaction().getId();
        }
    }

    private FactionAPI faction() {
        FactionAPI f = factionId == null ? null : Global.getSector().getFaction(factionId);
        return f != null ? f : Global.getSector().getPlayerFaction();
    }

    private boolean isSelf(FactionAPI f) {
        return f != null && SovereignState.isPlayerFactionId(f.getId());
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        float leftW = 290f;
        float gap = 8f;
        buildList(body, 0f, 0f, leftW, bh);

        float rx = leftW + gap;
        float rw = bw - rx;
        FactionAPI f = faction();
        float headH = 132f;
        float midH = 170f;
        buildHeader(body, f, rx, 0f, rw, headH);

        float dipW = Math.min(300f, rw * 0.38f);
        buildDiplomacy(body, f, rx, headH + gap, dipW, midH);
        buildStanding(body, f, rx + dipW + gap, headH + gap, rw - dipW - gap, midH);

        float lowY = headH + midH + gap * 2f;
        float lowH = bh - lowY;
        if (negotiating && !isSelf(f) && Global.getSector().getPlayerFaction().isHostileTo(f)) {
            buildPeace(body, f, rx, lowY, rw, lowH);
            return;
        }
        negotiating = false;
        float listW = rw * 0.56f;
        buildColonies(body, f, rx, lowY, listW, lowH);
        buildDetail(body, rx + listW + gap, lowY, rw - listW - gap, lowH);
    }

    // ---- left list --------------------------------------------------------------------------------------

    private void buildList(CustomPanelAPI body, float x, float y, float cw, float ch) {
        List<FactionAPI> factions = listedFactions();
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Factions");
        if (factions.isEmpty()) {
            w.empty(c.tm, "No factions", null);
        }
        for (FactionAPI f : factions) {
            boolean self = isSelf(f);
            String rel = self ? "You" : SovereignKnowledge.relationWords(f);
            Color relColor = self ? Theme.hl() : Theme.relation(Global.getSector().getPlayerFaction().getRelationship(f.getId()));
            w.listRow(c, "fac:" + f.getId(), f.getDisplayName(), null, rel, relColor,
                    f.getId().equals(faction().getId()), f.getBaseUIColor(), f.getDarkUIColor());
        }
        w.done(c);
    }

    private List<FactionAPI> listedFactions() {
        List<FactionAPI> list = new ArrayList<FactionAPI>();
        Set<String> seen = new HashSet<String>();
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (player != null && seen.add(player.getId())) {
            list.add(player);
        }
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f == null || !seen.add(f.getId())) {
                continue;
            }
            List<MarketAPI> markets = Misc.getFactionMarkets(f);
            if (markets != null && !markets.isEmpty()) {
                list.add(f);
            }
        }
        Collections.sort(list, new Comparator<FactionAPI>() {
            @Override
            public int compare(FactionAPI a, FactionAPI b) {
                boolean ap = isSelf(a);
                boolean bp = isSelf(b);
                if (ap != bp) {
                    return ap ? -1 : 1;
                }
                return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            }
        });
        return list;
    }

    // ---- header -----------------------------------------------------------------------------------------

    private void buildHeader(CustomPanelAPI body, FactionAPI f, float x, float y, float cw, float ch) {
        CustomPanelAPI panel = w.frame(body, x, y, cw, ch);
        float imgW = 100f;
        String sprite = f.getCrest() != null ? f.getCrest() : f.getLogo();
        if (sprite != null) {
            try {
                TooltipMakerAPI img = panel.createUIElement(imgW, ch - 12f, false);
                img.addImage(sprite, 88f, 88f, 0f);
                panel.addUIElement(img).inTL(10f, 12f);
            } catch (Exception ex) {
                Global.getLogger(FactionsTab.class).warn("Faction image missing for " + f.getId(), ex);
            }
        }
        float tw = cw - imgW - 24f;
        TooltipMakerAPI tm = panel.createUIElement(tw, ch - 8f, false);
        tm.setTitleOrbitronLarge();
        tm.addTitle(f.getDisplayName(), f.getBaseUIColor());
        boolean self = isSelf(f);
        if (self) {
            tm.addPara("Your faction", Theme.hl(), 3f);
        } else {
            float rel = Global.getSector().getPlayerFaction().getRelationship(f.getId());
            int pts = SovereignKnowledge.relationPoints(f);
            tm.addPara(SovereignKnowledge.relationWords(f) + "  " + (pts > 0 ? "+" : "") + pts,
                    Theme.relation(rel), 3f);
        }
        int colonies = Misc.getFactionMarkets(f).size();
        int totalSize = 0;
        for (MarketAPI m : Misc.getFactionMarkets(f)) {
            totalSize += m.getSize();
        }
        SovereignKnowledge.Strength s = SovereignKnowledge.strength(f);
        String defense = s.exact + s.guessed == 0 ? "?" : (s.guessed > 0 || s.hidden > 0 ? "~" : "")
                + Misc.getWithDGS(Math.round(s.value));
        int wars = SovereignKnowledge.warsOf(f).size();
        String[] values = {String.valueOf(colonies), String.valueOf(totalSize), defense, String.valueOf(wars)};
        String[] labels = {"Colonies", "Total size", "Defense", "Wars"};
        float tileW = Math.min(120f, tw / 4f);
        for (int i = 0; i < 4; i++) {
            TooltipMakerAPI tile = panel.createUIElement(tileW, 40f, false);
            tile.setParaFontOrbitron();
            tile.addPara(values[i], i == 3 && wars > 0 ? Theme.bad() : Theme.bright(), 0f);
            tile.setParaFontDefault();
            tile.addPara(labels[i], Theme.dim(), 2f);
            panel.addUIElement(tile).inTL(imgW + 18f + i * tileW, 50f);
        }
        if (!self) {
            TooltipMakerAPI bar = panel.createUIElement(Math.min(tw - 8f, 420f), 30f, false);
            bar.addRelationshipBar(f, Math.min(tw - 8f, 420f), 0f);
            panel.addUIElement(bar).inTL(imgW + 18f, 96f);
        }
        panel.addUIElement(tm).inTL(imgW + 18f, 8f);
    }

    // ---- diplomacy --------------------------------------------------------------------------------------

    private void buildDiplomacy(CustomPanelAPI body, FactionAPI f, float x, float y, float cw, float ch) {
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Diplomacy");
        float bw = c.rowWidth();
        TooltipMakerAPI tm = c.tm;
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (isSelf(f)) {
            w.empty(tm, "Your own faction", null);
            w.done(c);
            return;
        }
        if (!SovereignSettings.diplomacy()) {
            w.button(tm, "Diplomacy profile", "profile", bw, 24f, 6f, true);
            w.tip(tm, "Nexerelin's own page for this faction");
        } else if (player.isHostileTo(f)) {
            String talks = SovereignDiplomacy.talksBlock(f);
            w.confirm(tm, "Negotiate peace", "talks", bw, 24f, 6f, talks == null);
            w.tip(tm, talks == null ? "Terms depend on how the war stands" : talks);
        } else {
            int cost = SovereignDiplomacy.giftCost(f);
            String block = SovereignDiplomacy.giftBlock(f);
            w.button(tm, "Send gift  " + SovereignKnowledge.credits(cost), "gift", bw, 24f, 6f, block == null);
            w.tip(tm, block == null ? "Adds about " + Math.round(SovereignDiplomacy.giftGain(f) * 100) + " to relations" : block);
            String warBlock = SovereignDiplomacy.warBlock(f);
            if ("war".equals(armed) && warBlock == null) {
                w.danger(tm, "Confirm war on " + f.getDisplayName(), "war", bw, 24f, 8f, true);
            } else {
                w.danger(tm, "Declare war", "war", bw, 24f, 8f, warBlock == null);
                w.tip(tm, warBlock == null ? "Their allies may join in" : warBlock);
            }
        }
        SovereignActions.AllianceMove move = SovereignActions.allianceMove(f);
        w.button(tm, move.label, "alliance", bw, 24f, 8f, move.allowed);
        w.tip(tm, move.allowed ? move.hint : move.reason);
        w.done(c);
    }

    private void buildPeace(CustomPanelAPI body, FactionAPI f, float x, float y, float cw, float ch) {
        SovereignDiplomacy.Assessment a = SovereignDiplomacy.assess(f);
        List<MarketAPI> held = SovereignDiplomacy.captured(f);
        List<MarketAPI> giving = new ArrayList<MarketAPI>();
        for (MarketAPI m : held) {
            if (returned.contains(m.getId())) {
                giving.add(m);
            }
        }
        reparations = Math.max(-a.tributeCap, reparations);
        float chance = SovereignDiplomacy.chance(a, reparations, giving);
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Peace terms: " + f.getDisplayName());
        TooltipMakerAPI tm = c.tm;
        Color moodColor = chance >= 0.5f ? Theme.good() : chance >= 0.3f ? Theme.hl() : Theme.bad();
        tm.addPara(SovereignDiplomacy.mood(a, chance) + ", about " + Math.round(chance * 100f) + "%", moodColor, 6f);

        List<String> asks = new ArrayList<String>();
        if (a.askCredits > 0) {
            asks.add(SovereignKnowledge.credits(a.askCredits));
        }
        List<String> askNames = new ArrayList<String>();
        for (MarketAPI m : a.askColonies) {
            askNames.add(m.getName());
        }
        if (!askNames.isEmpty()) {
            asks.add(SovereignKnowledge.join(askNames));
        }
        String backing = a.backers.isEmpty() ? "None" : SovereignKnowledge.join(a.backers);
        w.facts(tm, c.rowWidth(),
                new String[]{"Balance of the war", "Their backing", "Colonies you took", "They ask"},
                new String[]{SovereignDiplomacy.balanceWord(a.balance), backing, String.valueOf(a.taken),
                        asks.isEmpty() ? "Nothing" : SovereignKnowledge.join(asks)},
                null, 8f);

        String offer = reparations == 0 ? "None" : reparations > 0
                ? "You pay " + SovereignKnowledge.credits(reparations)
                : "They pay " + SovereignKnowledge.credits(-reparations);
        w.stepper(c, "Reparations", offer, "rep:-", "rep:+", true);
        tm.addPara("Colonies to hand over", Theme.dim(), 8f);
        if (held.isEmpty()) {
            tm.addPara("You hold none of theirs", Theme.dim(), 2f);
        }
        for (MarketAPI m : held) {
            boolean asked = a.askColonies.contains(m);
            w.chip(tm, m.getName() + ", size " + m.getSize() + (asked ? "  (asked)" : ""), "ret:" + m.getId(),
                    returned.contains(m.getId()), c.rowWidth(), 22f, 3f, true);
        }
        float credits = Global.getSector().getPlayerFleet().getCargo().getCredits().get();
        float total = a.fee + Math.max(0, reparations);
        w.facts(tm, c.rowWidth(), new String[]{"Envoy fee", "Your credits"},
                new String[]{SovereignKnowledge.credits(a.fee), SovereignKnowledge.credits(credits)},
                new Color[]{null, credits >= total ? null : Theme.bad()}, 10f);
        float half = (c.rowWidth() - 6f) / 2f;
        w.confirm(tm, "Send envoys", "propose", half, 26f, 12f, credits >= total);
        w.button(tm, "Back", "back", half, 26f, 6f, true);
        w.done(c);
    }

    // ---- standing ---------------------------------------------------------------------------------------

    private void buildStanding(CustomPanelAPI body, FactionAPI f, float x, float y, float cw, float ch) {
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Standing");
        TooltipMakerAPI tm = c.tm;
        List<FactionAPI> wars = SovereignKnowledge.warsOf(f);
        tm.addPara("At war with", Theme.dim(), 6f);
        if (wars.isEmpty()) {
            tm.addPara("Nobody", Theme.good(), 2f);
        } else {
            tm.addPara(names(wars), Theme.bad(), 2f);
        }
        List<FactionAPI> allies = SovereignKnowledge.alliesOf(f);
        exerelin.campaign.alliances.Alliance alliance = SovereignKnowledge.allianceOf(f);
        tm.addPara(alliance == null ? "Alliance" : "Alliance: " + alliance.getName(), Theme.dim(), 8f);
        if (allies.isEmpty()) {
            tm.addPara(alliance == null ? "None" : "No other members", Theme.text(), 2f);
        } else {
            tm.addPara(names(allies), Theme.good(), 2f);
        }
        if (!isSelf(f)) {
            float wear = SovereignKnowledge.weariness(f);
            tm.addPara("War weariness", Theme.dim(), 8f);
            tm.addPara(Math.round(wear / 100f) + "%", wear > 0 ? Theme.hl() : Theme.text(), 2f);
        }
        w.done(c);
    }

    private String names(List<FactionAPI> list) {
        List<String> out = new ArrayList<String>();
        for (FactionAPI f : list) {
            out.add(f.getDisplayName());
        }
        return SovereignKnowledge.join(out);
    }

    // ---- colonies ---------------------------------------------------------------------------------------

    private List<MarketAPI> colonies(FactionAPI f) {
        List<MarketAPI> list = new ArrayList<MarketAPI>(Misc.getFactionMarkets(f));
        Collections.sort(list, new Comparator<MarketAPI>() {
            @Override
            public int compare(MarketAPI a, MarketAPI b) {
                if (a.getSize() != b.getSize()) {
                    return b.getSize() - a.getSize();
                }
                return SovereignKnowledge.nameOf(a).compareToIgnoreCase(SovereignKnowledge.nameOf(b));
            }
        });
        return list;
    }

    private void buildColonies(CustomPanelAPI body, FactionAPI f, float x, float y, float cw, float ch) {
        List<MarketAPI> list = colonies(f);
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Colonies (" + list.size() + ")");
        if (list.isEmpty()) {
            w.empty(c.tm, "No colonies", null);
        }
        for (MarketAPI m : list) {
            boolean picked = m.getId().equals(w.targetId);
            w.listRow(c, "mkt:" + m.getId(), SovereignKnowledge.nameOf(m), SovereignKnowledge.systemName(m),
                    "Size " + SovereignKnowledge.sizeOf(m), Theme.text(), picked, f.getBaseUIColor(), f.getDarkUIColor());
        }
        w.done(c);
    }

    private void buildDetail(CustomPanelAPI body, float x, float y, float cw, float ch) {
        MarketAPI m = w.target();
        SovereignWindow.Card c = w.card(body, x, y, cw, ch, "Colony");
        TooltipMakerAPI tm = c.tm;
        if (m == null) {
            w.empty(tm, "No colony selected", null);
            w.done(c);
            return;
        }
        SovereignKnowledge.Dossier d = SovereignKnowledge.dossier(m);
        tm.addPara(d.name, m.getFaction() == null ? Theme.text() : m.getFaction().getBaseUIColor(), 6f);
        tm.addPara(d.faction + "  /  " + d.source, Theme.dim(), 2f);
        float fw = c.rowWidth();
        w.facts(tm, fw,
                new String[]{"System", "Size", "Stability", "Income", "Defense"},
                new String[]{d.system, d.size, d.stability, d.income, d.defense},
                null, 8f);
        tm.addPara("Industries", Theme.dim(), 8f);
        tm.addPara(d.industries, Theme.text(), 2f);
        tm.addPara("Conditions", Theme.dim(), 8f);
        tm.addPara(d.conditions, Theme.text(), 2f);

        float bw = c.rowWidth();
        boolean pending = m.getStarSystem() != null && SovereignState.isPending(m.getStarSystem().getId());
        if (d.level != SovereignKnowledge.Level.FULL) {
            if (pending) {
                tm.addPara("Inquiry under way", Theme.hl(), 10f);
            } else {
                int cost = SovereignSettings.investigateCost();
                w.button(tm, "Investigate  " + SovereignKnowledge.credits(cost), "investigate", bw, 24f, 10f, true);
                w.tip(tm, "Sends a small fleet to look the system over");
            }
        }
        boolean foreign = !SovereignState.isPlayerMarket(m);
        float half = (bw - 6f) / 2f;
        w.button(tm, "Plan operation", "plan", half, 24f, 8f, foreign);
        w.button(tm, "Send task force", "send", half, 24f, 6f, true);
        w.done(c);
    }

    // ---- actions ----------------------------------------------------------------------------------------

    @Override
    boolean handle(String id) {
        String wasArmed = armed;
        armed = null;
        FactionAPI f = faction();
        if (id.startsWith("fac:")) {
            factionId = id.substring(4);
            w.targetId = null;
            return true;
        }
        if (id.startsWith("mkt:")) {
            w.targetId = id.substring(4);
            return true;
        }
        if ("profile".equals(id)) {
            final com.fs.starfarer.api.campaign.comm.IntelInfoPlugin profile =
                    exerelin.campaign.DiplomacyManager.getManager().getDiplomacyProfile(f.getId());
            if (profile == null) {
                w.say("No profile for that faction.", false);
                return true;
            }
            w.closeThen(new Runnable() {
                @Override
                public void run() {
                    Global.getSector().getCampaignUI().showCoreUITab(com.fs.starfarer.api.campaign.CoreUITabId.INTEL, profile);
                }
            });
            return true;
        }
        if ("gift".equals(id)) {
            report(SovereignDiplomacy.gift(f));
            return true;
        }
        if ("war".equals(id)) {
            if (!"war".equals(wasArmed)) {
                armed = "war";
                return true;
            }
            report(SovereignActions.declareWar(f));
            return true;
        }
        if ("talks".equals(id)) {
            negotiating = true;
            reparations = 0;
            returned.clear();
            return true;
        }
        if ("back".equals(id)) {
            negotiating = false;
            return true;
        }
        if ("rep:-".equals(id) || "rep:+".equals(id)) {
            SovereignDiplomacy.Assessment a = SovereignDiplomacy.assess(f);
            int maxPay = Math.round(12f * a.ourMonthly / 10000f) * 10000;
            reparations = "rep:-".equals(id)
                    ? Math.max(-a.tributeCap, reparations - a.step)
                    : Math.min(maxPay, reparations + a.step);
            return true;
        }
        if (id.startsWith("ret:")) {
            String key = id.substring(4);
            if (!returned.remove(key)) {
                returned.add(key);
            }
            return true;
        }
        if ("propose".equals(id)) {
            List<MarketAPI> giving = new ArrayList<MarketAPI>();
            for (MarketAPI m : SovereignDiplomacy.captured(f)) {
                if (returned.contains(m.getId())) {
                    giving.add(m);
                }
            }
            SovereignActions.Outcome out = SovereignDiplomacy.propose(f, reparations, giving);
            report(out);
            negotiating = false;
            return true;
        }
        if ("alliance".equals(id)) {
            report(SovereignActions.allianceMove(f).perform());
            return true;
        }
        if ("investigate".equals(id)) {
            report(SovereignActions.investigate(w.target()));
            return true;
        }
        if ("plan".equals(id)) {
            w.show(SovereignWindow.TabId.OPERATIONS);
            return true;
        }
        if ("send".equals(id)) {
            w.show(SovereignWindow.TabId.FORCES);
            return true;
        }
        armed = wasArmed;
        return false;
    }

    private void report(SovereignActions.Outcome out) {
        w.say(out.text, out.ok);
    }
}
