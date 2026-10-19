package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest.FleetType;
import com.fs.starfarer.api.util.MutableValue;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.fleets.BlockadeWrapperIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FleetRequest {

    static final int MIN_FP = Math.round(Nex_FleetRequest.MIN_FP);
    static final int FP_STEP = 50;
    static final int MARINE_STEP = 100;
    static final int MIN_MARINES = 100;

    private FleetRequest() {
    }

    static List<FleetType> kinds() {
        List<FleetType> list = new ArrayList<FleetType>();
        if (NexConfig.enableInvasions) {
            list.add(FleetType.INVASION);
        }
        list.add(FleetType.RAID);
        list.add(FleetType.BLOCKADE);
        list.add(FleetType.DEFENSE);
        list.add(FleetType.BASESTRIKE);
        return list;
    }

    static String label(FleetType type) {
        switch (type) {
            case INVASION:
                return "Invasion";
            case RAID:
                return "Raid";
            case BLOCKADE:
                return "Blockade";
            case DEFENSE:
                return "Defense";
            case BASESTRIKE:
                return "Base strike";
            default:
                return type.name();
        }
    }

    static String blurb(FleetType type) {
        switch (type) {
            case INVASION:
                return "Take the colony";
            case RAID:
                return "Hit and withdraw";
            case BLOCKADE:
                return "Choke the system's trade";
            case DEFENSE:
                return "Guard a friendly colony";
            case BASESTRIKE:
                return "Wreck a hidden pirate or Pather base";
            default:
                return "";
        }
    }

    static List<MarketAPI> sources() {
        Set<MarketAPI> found = new HashSet<MarketAPI>();
        found.addAll(NexUtilsFaction.getFactionMarkets(Factions.PLAYER));
        Alliance alliance = AllianceManager.getPlayerAlliance(true);
        if (alliance != null) {
            found.addAll(alliance.getAllianceMarkets());
        } else {
            String commissioner = PlayerFactionStore.getPlayerFactionId();
            if (!commissioner.equals(Factions.PLAYER)) {
                found.addAll(NexUtilsFaction.getFactionMarkets(commissioner));
            }
        }
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI market : found) {
            if (NexUtilsMarket.hasWorkingSpaceport(market)) {
                out.add(market);
            }
        }
        Collections.sort(out, new Comparator<MarketAPI>() {
            @Override
            public int compare(MarketAPI a, MarketAPI b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }

    static String targetProblem(FleetType type, MarketAPI target) {
        if (target == null) {
            return "Choose a target";
        }
        if (target.getContainingLocation() == null || target.getContainingLocation().isHyperspace()) {
            return "No route there";
        }
        if (target.isHidden() && !type.allowTargetHidden()) {
            return "That colony is hidden";
        }
        boolean friendly = SovereignState.countsAsFriendly(target.getFactionId());
        switch (type) {
            case INVASION:
                return NexUtilsMarket.canBeInvaded(target, true) ? null : "Cannot be invaded";
            case BASESTRIKE:
                return Nex_FleetRequest.getHiddenBases().contains(target) ? null : "Not a known hidden base";
            case DEFENSE:
                return friendly ? null : "Pick one of yours or an ally's";
            default:
                return friendly ? "Pick someone else's colony" : null;
        }
    }

    static int maxFp() {
        float stock = InvasionFleetManager.getManager().getFleetRequestStock();
        if (Global.getSettings().isDevMode()) {
            stock = 5000f;
        }
        float cap = Global.getSettings().getFloat("nex_fleetRequestMaxFP");
        return Math.round(Math.max(MIN_FP, Math.min(stock, cap)));
    }

    static int pool() {
        return Math.round(InvasionFleetManager.getManager().getFleetRequestStock());
    }

    static int cost(FleetType type, int fp, int marines) {
        float cost = fp * NexConfig.fleetRequestCostPerFP;
        if (type == FleetType.INVASION) {
            cost += marines * Global.getSettings().getCommoditySpec(Commodities.MARINES).getBasePrice()
                    * Nex_FleetRequest.MARINE_COST_MULT;
        }
        return Math.round(cost);
    }

    static float days(FleetType type, int fp) {
        return Nex_FleetRequest.getTimeToLaunch(fp, type);
    }

    static SovereignActions.Outcome launch(FleetType type, MarketAPI source, MarketAPI target, int fp, int marines) {
        String problem = targetProblem(type, target);
        if (source == null) {
            return new SovereignActions.Outcome(false, "Choose a launch colony.");
        }
        if (problem != null) {
            return new SovereignActions.Outcome(false, problem + ".");
        }
        if (pool() < MIN_FP && !Global.getSettings().isDevMode()) {
            return new SovereignActions.Outcome(false, "The fleet pool is too low.");
        }
        int cost = cost(type, fp, marines);
        MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
        if (cost > credits.get() && !Global.getSettings().isDevMode()) {
            return new SovereignActions.Outcome(false, "You need " + SovereignKnowledge.credits(cost) + ".");
        }
        try {
            FactionAPI faction = source.getFaction();
            float launchTime = days(type, fp);
            OffensiveFleetIntel intel;
            switch (type) {
                case INVASION:
                    intel = new InvasionIntel(faction, source, target, fp, launchTime);
                    break;
                case RAID:
                    intel = new NexRaidIntel(faction, source, target, fp, launchTime);
                    break;
                case BASESTRIKE:
                    intel = new BaseStrikeIntel(faction, source, target, fp, launchTime);
                    break;
                case DEFENSE:
                    intel = new DefenseFleetIntel(faction, source, target, fp, launchTime);
                    break;
                case BLOCKADE:
                    intel = new BlockadeWrapperIntel(faction, source, target, fp, launchTime);
                    break;
                default:
                    return new SovereignActions.Outcome(false, "Not available.");
            }
            credits.subtract(cost);
            intel.setPlayerFee(cost);
            intel.setPlayerSpawned(true);
            intel.init();
            if (type == FleetType.INVASION) {
                ((InvasionIntel) intel).setMarinesTotal(marines);
            }
            InvasionFleetManager.getManager().modifyFleetRequestStock(-fp);
            if (type.isAggressive && !target.getFaction().isHostileTo(Factions.PLAYER)) {
                CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
                impact.delta = 0;
                impact.ensureAtBest = type == FleetType.BLOCKADE ? RepLevel.INHOSPITABLE : RepLevel.HOSTILE;
                Global.getSector().adjustPlayerReputation(
                        new RepActionEnvelope(RepActions.CUSTOM, impact, null, null, false),
                        target.getFaction().getId());
            }
            String text = label(type) + " launched from " + source.getName() + " against " + target.getName()
                    + " (" + fp + " FP, " + SovereignKnowledge.credits(cost) + ").";
            SovereignLog.add(SovereignLog.ORDER, text);
            return new SovereignActions.Outcome(true, text);
        } catch (Exception ex) {
            return SovereignActions.failed("Launch failed", ex);
        }
    }
}
