package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full-window colony list, grouped by faction, with the highlighted colony's details beside it. */
final class PickerView extends TabView {

    interface Listener {
        void picked(MarketAPI market);
    }

    private final String title;
    private final List<MarketAPI> options;
    private final boolean filters;
    private final Listener listener;
    private String filter = "all";
    private String highlighted;

    PickerView(SovereignWindow window, String title, List<MarketAPI> options, MarketAPI current,
               boolean filters, Listener listener) {
        super(window);
        this.title = title;
        this.options = options;
        this.filters = filters;
        this.listener = listener;
        if (current != null && options.contains(current)) {
            highlighted = current.getId();
        }
    }

    private boolean passes(MarketAPI m) {
        FactionAPI f = m.getFaction();
        if (f == null || "all".equals(filter)) {
            return true;
        }
        boolean war = Global.getSector().getPlayerFaction().isHostileTo(f);
        boolean ours = SovereignState.countsAsFriendly(f.getId());
        if ("war".equals(filter)) {
            return war;
        }
        if ("ours".equals(filter)) {
            return ours;
        }
        return !war && !ours;
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        float leftW = bw * 0.56f;
        SovereignWindow.Card list = w.card(body, 0f, 0f, leftW, bh, title);
        if (filters) {
            w.chipRow(list, new String[]{"All", "At war", "Yours and allies", "Others"},
                    new String[]{"pkf:all", "pkf:war", "pkf:ours", "pkf:other"}, "pkf:" + filter);
        }
        Map<String, List<MarketAPI>> groups = new LinkedHashMap<String, List<MarketAPI>>();
        List<MarketAPI> shown = new ArrayList<MarketAPI>();
        for (MarketAPI m : options) {
            if (m.getFaction() != null && passes(m)) {
                shown.add(m);
            }
        }
        Collections.sort(shown, new Comparator<MarketAPI>() {
            @Override
            public int compare(MarketAPI a, MarketAPI b) {
                int byFaction = a.getFaction().getDisplayName().compareToIgnoreCase(b.getFaction().getDisplayName());
                if (byFaction != 0) {
                    return byFaction;
                }
                return b.getSize() - a.getSize();
            }
        });
        for (MarketAPI m : shown) {
            List<MarketAPI> group = groups.get(m.getFactionId());
            if (group == null) {
                group = new ArrayList<MarketAPI>();
                groups.put(m.getFactionId(), group);
            }
            group.add(m);
        }
        if (shown.isEmpty()) {
            w.empty(list.tm, "Nothing to pick", null);
        }
        if (highlighted == null || !containsId(shown, highlighted)) {
            highlighted = shown.isEmpty() ? null : shown.get(0).getId();
        }
        for (List<MarketAPI> group : groups.values()) {
            FactionAPI f = group.get(0).getFaction();
            list.tm.addPara(f.getDisplayName(), f.getBaseUIColor(), 10f);
            for (MarketAPI m : group) {
                w.listRow(list, "pk:" + m.getId(), SovereignKnowledge.nameOf(m), SovereignKnowledge.systemName(m),
                        "Size " + SovereignKnowledge.sizeOf(m), Theme.text(), m.getId().equals(highlighted),
                        f.getBaseUIColor(), f.getDarkUIColor());
            }
        }
        w.done(list);

        SovereignWindow.Card detail = w.card(body, leftW + 8f, 0f, bw - leftW - 8f, bh, "Colony");
        MarketAPI m = highlighted == null ? null : Global.getSector().getEconomy().getMarket(highlighted);
        if (m == null) {
            w.empty(detail.tm, "Nothing selected", null);
        } else {
            ColonyView.show(w, detail, m);
        }
        w.buttonPair(detail, "Cancel", "pk_cancel", "Select", "pk_ok", m != null);
        w.done(detail);
    }

    private boolean containsId(List<MarketAPI> list, String id) {
        for (MarketAPI m : list) {
            if (m.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    boolean handle(String id) {
        if (id.startsWith("pkf:")) {
            filter = id.substring(4);
            return true;
        }
        if ("pk_cancel".equals(id)) {
            w.closePicker();
            return true;
        }
        if ("pk_ok".equals(id)) {
            MarketAPI m = highlighted == null ? null : Global.getSector().getEconomy().getMarket(highlighted);
            w.closePicker();
            if (m != null) {
                listener.picked(m);
            }
            return true;
        }
        if (id.startsWith("pk:")) {
            highlighted = id.substring(3);
            return true;
        }
        return false;
    }
}
