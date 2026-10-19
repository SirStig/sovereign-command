package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.utilities.NexUtilsMarket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SovereignDiplomacy {

    private static final float GIFT_DAYS = 30f;
    private static final float TALKS_DAYS = 15f;
    private static final float INCOME_PER_SIZE_SQUARED = 2000f;

    private SovereignDiplomacy() {
    }

    public static final class Assessment {
        public float balance;
        public float military;
        public float economy;
        public float battles;
        public float land;
        public float weariness;
        public float ourMonthly;
        public float theirMonthly;
        public int askCredits;
        public List<MarketAPI> askColonies = new ArrayList<MarketAPI>();
        public int tributeCap;
        public int taken;
        public List<String> backers = new ArrayList<String>();
        public int step;
        public int fee;
    }

    private static Map<String, Object> data() {
        return SovereignState.data();
    }

    private static float daysLeft(String key, float total) {
        Object v = data().get(key);
        if (!(v instanceof Long)) {
            return 0f;
        }
        return Math.max(0f, total - Global.getSector().getClock().getElapsedDaysSince((Long) v));
    }

    private static void start(String key) {
        data().put(key, Global.getSector().getClock().getTimestamp());
    }

    private static float wallet() {
        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    // ---- sides ------------------------------------------------------------------------------------------

    private static Set<String> sideOf(FactionAPI base, boolean ours) {
        Set<String> ids = new HashSet<String>();
        ids.add(base.getId());
        Alliance alliance = AllianceManager.getManager() == null ? null : AllianceManager.getFactionAlliance(base.getId());
        if (alliance != null) {
            ids.addAll(alliance.getMembersCopy());
        }
        if (ours) {
            String commission = Misc.getCommissionFactionId();
            if (commission != null) {
                ids.add(commission);
                Alliance other = AllianceManager.getManager() == null ? null : AllianceManager.getFactionAlliance(commission);
                if (other != null) {
                    ids.addAll(other.getMembersCopy());
                }
            }
        }
        return ids;
    }

    private static float income(MarketAPI m) {
        return Math.max(0f, m.getIndustryIncome() + m.getExportIncome(false));
    }

    private static float guess(MarketAPI m) {
        return m.getSize() * m.getSize() * INCOME_PER_SIZE_SQUARED;
    }

    // monthly credits the side's colonies earn; falls back to a size estimate if the game reports nothing
    private static float monthly(Set<String> ids) {
        float real = 0f;
        float estimate = 0f;
        for (String id : ids) {
            FactionAPI f = Global.getSector().getFaction(id);
            for (MarketAPI m : Misc.getFactionMarkets(f)) {
                real += income(m);
                estimate += guess(m);
            }
        }
        return real > 0f ? real : estimate;
    }

    private static float[] fleetPower(Set<String> ids) {
        float capacity = 0f;
        float ready = 0f;
        FleetPoolManager pools = FleetPoolManager.getManager();
        for (String id : ids) {
            float max = 0f;
            float now = 0f;
            if (pools != null) {
                max = pools.getMaxPool(id);
                now = Math.min(max, pools.getCurrentPool(id));
            }
            if (pools == null || max >= 100000f) {
                FactionAPI f = Global.getSector().getFaction(id);
                max = 0f;
                for (MarketAPI m : Misc.getFactionMarkets(f)) {
                    max += SovereignKnowledge.patrolLive(m) + SovereignKnowledge.stationLive(m);
                }
                now = max;
            }
            capacity += max;
            ready += now;
        }
        return new float[]{capacity, ready};
    }

    public static int totalSize(FactionAPI f) {
        int total = 0;
        for (MarketAPI m : Misc.getFactionMarkets(f)) {
            total += m.getSize();
        }
        return total;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // what a colony is worth in a deal: a year and a half of what it earns
    public static float colonyValue(MarketAPI m) {
        float earned = income(m);
        return (earned > 0f ? earned : guess(m)) * 18f;
    }

    // ---- assessment -------------------------------------------------------------------------------------

    public static Assessment assess(FactionAPI f) {
        Assessment a = new Assessment();
        FactionAPI player = Global.getSector().getPlayerFaction();
        Set<String> us = sideOf(player, true);
        Set<String> them = sideOf(f, false);
        them.removeAll(us);
        for (String id : them) {
            if (!id.equals(f.getId())) {
                a.backers.add(Global.getSector().getFaction(id).getDisplayName());
            }
        }
        float[] ourPower = fleetPower(us);
        float[] theirPower = fleetPower(them);
        float ourFight = ourPower[0] * (0.35f + 0.65f * (ourPower[0] > 0 ? ourPower[1] / ourPower[0] : 0f));
        float theirFight = theirPower[0] * (0.35f + 0.65f * (theirPower[0] > 0 ? theirPower[1] / theirPower[0] : 0f));
        a.military = ourFight / Math.max(1f, ourFight + theirFight);

        a.ourMonthly = monthly(us);
        a.theirMonthly = monthly(them);
        a.economy = a.ourMonthly / Math.max(1f, a.ourMonthly + a.theirMonthly);

        float dmgThem = Math.min(1f, SovereignLedger.killed(f.getId()) / (0.5f * theirPower[0] + 50f));
        float dmgUs = Math.min(1f, SovereignLedger.lost(f.getId()) / (0.5f * ourPower[0] + 50f));
        a.battles = clamp(0.5f + 0.5f * (dmgThem - dmgUs), 0f, 1f);

        float seized = 0f;
        float lost = 0f;
        for (String id : them) {
            for (MarketAPI m : Misc.getFactionMarkets(Global.getSector().getFaction(id))) {
                if (us.contains(NexUtilsMarket.getOriginalOwner(m))) {
                    lost += m.getSize();
                }
            }
        }
        List<MarketAPI> held = captured(f);
        for (MarketAPI m : held) {
            seized += m.getSize();
        }
        a.taken = held.size();
        float everything = Math.max(1f, totalSize(f) + seized + lost);
        a.land = clamp(0.5f + 0.5f * clamp(3f * (seized - lost) / everything, -1f, 1f), 0f, 1f);

        float theirs = SovereignKnowledge.weariness(f);
        float ours = SovereignKnowledge.weariness(player);
        a.weariness = clamp(0.5f + 0.5f * clamp((theirs - ours) / 6000f, -1f, 1f), 0f, 1f);

        a.balance = 0.40f * a.military + 0.20f * a.economy + 0.20f * a.battles + 0.12f * a.land + 0.08f * a.weariness;

        a.step = Math.max(10000, Math.round(a.ourMonthly * 0.1f / 10000f) * 10000);
        a.fee = Math.max(5000, Math.round(a.ourMonthly * 0.02f / 1000f) * 1000);

        if (a.balance < 0.5f) {
            float d = clamp((0.5f - a.balance) / 0.35f, 0f, 1f);
            float cap = 0.9f * Math.max(wallet(), 3f * a.ourMonthly);
            a.askCredits = Math.round(Math.min(cap, d * 6f * a.ourMonthly) / 10000f) * 10000;
            if (d > 0.45f && !held.isEmpty()) {
                List<MarketAPI> sorted = new ArrayList<MarketAPI>(held);
                Collections.sort(sorted, new Comparator<MarketAPI>() {
                    @Override
                    public int compare(MarketAPI x, MarketAPI y) {
                        return y.getSize() - x.getSize();
                    }
                });
                int count = Math.max(1, Math.round(d * sorted.size()));
                a.askColonies.addAll(sorted.subList(0, Math.min(count, sorted.size())));
            }
        }
        boolean foughtWar = a.taken > 0 || SovereignLedger.killed(f.getId()) >= 50f;
        if (a.balance > 0.62f && foughtWar && daysLeft("sc_tribute_" + f.getId(), 180f) <= 0f) {
            a.tributeCap = Math.round((a.balance - 0.62f) / 0.38f * 4f * a.theirMonthly / 10000f) * 10000;
        }
        return a;
    }

    public static float chance(Assessment a, int reparations, List<MarketAPI> ceded) {
        float base = 0.04f + 0.93f * smooth(0.30f, 0.78f, a.balance);
        float offer = Math.max(0, reparations);
        for (MarketAPI m : ceded) {
            offer += colonyValue(m);
        }
        float ask = a.askCredits;
        for (MarketAPI m : a.askColonies) {
            ask += colonyValue(m);
        }
        float c;
        if (ask > 0f) {
            float cover = Math.min(1f, offer / ask);
            c = base + (0.95f - base) * (float) Math.pow(cover, 1.3);
        } else {
            c = base + Math.min(0.15f, offer / (a.ourMonthly * 12f + 1f));
        }
        if (reparations < 0) {
            float demand = -reparations;
            if (demand > a.tributeCap) {
                c *= Math.max(0.1f, 1f - (demand - a.tributeCap) / (0.5f * a.theirMonthly + 1f));
            } else if (a.tributeCap > 0) {
                c *= 1f - 0.1f * demand / a.tributeCap;
            }
        }
        return clamp(c, 0.03f, 0.97f);
    }

    public static String balanceWord(float balance) {
        if (balance >= 0.8f) {
            return "Crushing";
        }
        if (balance >= 0.65f) {
            return "Winning";
        }
        if (balance >= 0.45f) {
            return "Even";
        }
        if (balance >= 0.3f) {
            return "Losing";
        }
        return "Dire";
    }

    public static String mood(Assessment a, float chance) {
        if (chance >= 0.9f && a.balance >= 0.7f) {
            return "They cannot afford to refuse";
        }
        if (chance >= 0.75f) {
            return "Eager for peace";
        }
        if (chance >= 0.5f) {
            return "Willing to talk";
        }
        if (chance >= 0.3f) {
            return "Reluctant";
        }
        return "Not interested";
    }

    // ---- gifts ------------------------------------------------------------------------------------------

    public static int giftCost(FactionAPI f) {
        Set<String> side = new HashSet<String>();
        side.add(f.getId());
        return Math.max(10000, Math.round(0.12f * monthly(side) / 500f) * 500);
    }

    public static float giftGain(FactionAPI f) {
        return Global.getSector().getPlayerFaction().getRelationship(f.getId()) < -0.25f ? 0.04f : 0.06f;
    }

    public static String giftBlock(FactionAPI f) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (player.isHostileTo(f)) {
            return "At war";
        }
        if (player.getRelationship(f.getId()) >= AllianceManager.MIN_RELATIONSHIP_TO_STAY) {
            return "Relations are already good";
        }
        float left = daysLeft("sc_gift_" + f.getId(), GIFT_DAYS);
        if (left > 0f) {
            return "Ready in " + Math.round(Math.ceil(left)) + " days";
        }
        if (wallet() < giftCost(f)) {
            return "Needs " + SovereignKnowledge.credits(giftCost(f));
        }
        return null;
    }

    public static SovereignActions.Outcome gift(FactionAPI f) {
        String block = giftBlock(f);
        if (block != null) {
            return new SovereignActions.Outcome(false, block + ".");
        }
        int cost = giftCost(f);
        SovereignActions.Outcome out = SovereignActions.raise(f, giftGain(f), RepLevel.WELCOMING, "Gift sent");
        if (out.ok) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
            start("sc_gift_" + f.getId());
            return new SovereignActions.Outcome(true, out.text + " It cost " + SovereignKnowledge.credits(cost) + ".");
        }
        return out;
    }

    // ---- war --------------------------------------------------------------------------------------------

    public static String warBlock(FactionAPI f) {
        String pid = PlayerFactionStore.getPlayerFactionId();
        if (f.getId().equals(pid) || f.getId().equals(Misc.getCommissionFactionId())) {
            return "You serve them";
        }
        try {
            if (AllianceManager.getManager() != null && AllianceManager.areFactionsAllied(pid, f.getId(), false)) {
                return "Leave the alliance first";
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignDiplomacy.class).warn("Alliance check failed", ex);
        }
        return null;
    }

    // ---- peace ------------------------------------------------------------------------------------------

    public static String talksBlock(FactionAPI f) {
        try {
            String pid = PlayerFactionStore.getPlayerFactionId();
            if (DiplomacyManager.getManager().getMaxRelationship(pid, f.getId()) <= -0.5f) {
                return "They won't receive envoys";
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignDiplomacy.class).warn("Peace check failed", ex);
        }
        float left = daysLeft("sc_talks_" + f.getId(), TALKS_DAYS);
        if (left > 0f) {
            return "Closed to you for " + Math.round(Math.ceil(left)) + " more days";
        }
        return null;
    }

    public static List<MarketAPI> captured(FactionAPI f) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (SovereignState.isPlayerMarket(m) && f.getId().equals(NexUtilsMarket.getOriginalOwner(m))) {
                out.add(m);
            }
        }
        return out;
    }

    public static SovereignActions.Outcome propose(FactionAPI f, int reparations, List<MarketAPI> ceded) {
        String block = talksBlock(f);
        if (block != null) {
            return new SovereignActions.Outcome(false, block + ".");
        }
        Assessment a = assess(f);
        float need = a.fee + Math.max(0, reparations);
        if (wallet() < need) {
            return new SovereignActions.Outcome(false, "You need " + SovereignKnowledge.credits(need) + ".");
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(a.fee);
        float chance = chance(a, reparations, ceded);
        if (Math.random() > chance) {
            start("sc_talks_" + f.getId());
            float offer = Math.max(0, reparations);
            for (MarketAPI m : ceded) {
                offer += colonyValue(m);
            }
            float ask = a.askCredits;
            for (MarketAPI m : a.askColonies) {
                ask += colonyValue(m);
            }
            if (ask > 0f && offer < 0.3f * ask) {
                SovereignActions.raise(f, -0.04f, null, "");
            }
            String text = f.getDisplayName() + " turned down the terms.";
            SovereignLog.add(SovereignLog.DIPLOMACY, text);
            return new SovereignActions.Outcome(false, text);
        }
        if (reparations > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(reparations);
        } else if (reparations < 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(-reparations);
            start("sc_tribute_" + f.getId());
        }
        FactionAPI player = Global.getSector().getPlayerFaction();
        List<String> names = new ArrayList<String>();
        for (MarketAPI m : ceded) {
            try {
                SectorManager.transferMarket(m, f, player, true, false, null, 0f);
                names.add(m.getName());
            } catch (Exception ex) {
                Global.getLogger(SovereignDiplomacy.class).error("Could not hand over " + m.getName(), ex);
            }
        }
        SovereignActions.Outcome out = SovereignActions.makePeace(f);
        if (out.ok) {
            SovereignLedger.clear(f.getId());
        }
        String text = out.ok ? "Peace with " + f.getDisplayName() + "." : out.text;
        if (!names.isEmpty()) {
            text += " Handed over " + SovereignKnowledge.join(names) + ".";
        }
        if (reparations > 0) {
            text += " You paid " + SovereignKnowledge.credits(reparations) + ".";
        } else if (reparations < 0) {
            text += " They paid " + SovereignKnowledge.credits(-reparations) + ".";
        }
        SovereignLog.add(SovereignLog.DIPLOMACY, text);
        return new SovereignActions.Outcome(out.ok, text);
    }
}
