package sovereign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/** The colony summary used by the faction page and the picker. */
final class ColonyView {

    private ColonyView() {
    }

    static void show(SovereignWindow w, SovereignWindow.Card c, MarketAPI m) {
        TooltipMakerAPI tm = c.tm;
        SovereignKnowledge.Dossier d = SovereignKnowledge.dossier(m);
        tm.addPara(d.name, m.getFaction() == null ? Theme.text() : m.getFaction().getBaseUIColor(), 6f);
        tm.addPara(d.faction + ", " + d.source, Theme.dim(), 2f);
        w.facts(tm, c.rowWidth(),
                new String[]{"System", "Size", "Stability", "Income", "Defense"},
                new String[]{d.system, d.size, d.stability, d.income, d.defense}, null, 8f);
        tm.addPara("Industries", Theme.dim(), 8f);
        tm.addPara(d.industries, Theme.text(), 2f);
        tm.addPara("Conditions", Theme.dim(), 8f);
        tm.addPara(d.conditions, Theme.text(), 2f);
    }
}
