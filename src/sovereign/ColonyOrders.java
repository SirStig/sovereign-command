package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.campaign.econ.ReinforcedDefenses;

/** Orders you can give your own colonies: Nexerelin's reinforced defenses, and the free port switch. */
final class ColonyOrders {

    static final String[] GOODS = {Commodities.SUPPLIES, Commodities.MARINES, Commodities.HAND_WEAPONS};
    private static final float[] PER_UNIT = {2f, 2.5f, 0.5f};

    private ColonyOrders() {
    }

    private static int sizeMult(MarketAPI m) {
        return (int) (10 * Math.pow(2, m.getSize() - 3));
    }

    static int credits(MarketAPI m) {
        return sizeMult(m) * 200;
    }

    static int need(MarketAPI m, int i) {
        return (int) (sizeMult(m) * PER_UNIT[i]);
    }

    static boolean affordable(MarketAPI m) {
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

    static boolean reinforced(MarketAPI m) {
        return m.hasCondition(ReinforcedDefenses.CONDITION_ID);
    }

    static SovereignActions.Outcome reinforce(MarketAPI m) {
        if (!affordable(m)) {
            return new SovereignActions.Outcome(false, "Not enough credits or cargo.");
        }
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if (reinforced(m)) {
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
        return new SovereignActions.Outcome(true, text);
    }

    static SovereignActions.Outcome toggleFreePort(MarketAPI m) {
        m.setFreePort(!m.isFreePort());
        String text = m.getName() + (m.isFreePort() ? " is now a free port." : " is no longer a free port.");
        SovereignLog.add(SovereignLog.ORDER, text);
        return new SovereignActions.Outcome(true, text);
    }
}
