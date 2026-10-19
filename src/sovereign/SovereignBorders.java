package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Once a second, tell unwelcome warships to leave. They may or may not listen.
public final class SovereignBorders {

    private static final Random RANDOM = new Random();
    private static final String QUIET = "sc_quiet";
    private static final String MEM_CUSTOMS = "$sc_customs";

    private SovereignBorders() {
    }

    public static void tick() {
        Logger log = Global.getLogger(SovereignBorders.class);
        try {
            rememberWhereThePlayerHasBeen();
            clearStaleVisits();
            if (!SovereignSettings.laws() || !SovereignSettings.closedBorders() && !SovereignSettings.customs()) {
                return;
            }
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (system == null || !covered(system)) {
                    continue;
                }
                float local = localStrength(system);
                List<CampaignFleetAPI> fleets = new ArrayList<CampaignFleetAPI>(system.getFleets());
                for (CampaignFleetAPI fleet : fleets) {
                    try {
                        consider(fleet, system, local);
                    } catch (Exception ex) {
                        log.error("Closed borders skipped a fleet in " + system.getName(), ex);
                        markHandled(fleet, system);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Closed borders tick failed", ex);
        }
    }

    private static boolean hasFriendlyMarket(StarSystemAPI system) {
        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market != null && SovereignState.countsAsFriendly(market.getFactionId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean covered(StarSystemAPI system) {
        int rule = SovereignSettings.systemRule(system.getId());
        if (rule == 1) {
            return hasFriendlyMarket(system);
        }
        if (rule == 2) {
            return false;
        }
        if (SovereignSettings.scope() == 0) {
            return isSovereign(system);
        }
        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market != null && SovereignState.countsAsFriendly(market.getFactionId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSovereign(StarSystemAPI system) {
        List<MarketAPI> markets = Misc.getMarketsInLocation(system);
        if (markets == null || markets.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (MarketAPI market : markets) {
            if (market == null) {
                continue;
            }
            any = true;
            if (!SovereignState.countsAsFriendly(market.getFactionId())) {
                return false;
            }
        }
        return any;
    }

    static List<CampaignFleetAPI> allFleets() {
        List<CampaignFleetAPI> fleets = new ArrayList<CampaignFleetAPI>();
        LocationAPI hyper = Global.getSector().getHyperspace();
        if (hyper != null) {
            fleets.addAll(hyper.getFleets());
        }
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system != null) {
                fleets.addAll(system.getFleets());
            }
        }
        return fleets;
    }

    private static void rememberWhereThePlayerHasBeen() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null && player.getStarSystem() != null) {
            SovereignState.markVisited(player.getStarSystem().getId());
        }
    }

    private static void clearStaleVisits() {
        for (CampaignFleetAPI fleet : allFleets()) {
            if (fleet == null || fleet.getMemoryWithoutUpdate() == null) {
                continue;
            }
            MemoryAPI memory = fleet.getMemoryWithoutUpdate();
            if (!memory.contains(SovereignState.MEM_HANDLED)) {
                continue;
            }
            String handled = memory.getString(SovereignState.MEM_HANDLED);
            StarSystemAPI current = fleet.getStarSystem();
            if (current == null || handled == null || !handled.equals(current.getId())) {
                memory.unset(SovereignState.MEM_HANDLED);
                memory.unset(SovereignState.MEM_EGRESS);
                memory.unset(MEM_CUSTOMS);
                fleet.getStats().getDetectedRangeMod().unmodify(QUIET);
            }
        }
    }

    private static void consider(CampaignFleetAPI fleet, StarSystemAPI system, float local) {
        if (fleet == null || fleet.isPlayerFleet() || !fleet.isAlive() || fleet.isDespawning()) {
            return;
        }
        if (fleet.isStationMode() || fleet.getBattle() != null) {
            return;
        }
        FactionAPI faction = fleet.getFaction();
        if (faction == null || Factions.DERELICT.equals(faction.getId()) || "derelict".equals(faction.getId())) {
            return;
        }
        if (SovereignState.countsAsFriendly(faction.getId())) {
            return;
        }
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        if (memory == null) {
            return;
        }
        boolean handledHere = memory.contains(SovereignState.MEM_HANDLED)
                && system.getId() != null
                && system.getId().equals(memory.getString(SovereignState.MEM_HANDLED));
        if (handledHere) {
            if (memory.contains(SovereignState.MEM_EGRESS) && memory.getBoolean(SovereignState.MEM_EGRESS)) {
                tryCompleteEgress(fleet, system);
            }
            return;
        }
        if (isTradeOrRelief(fleet)) {
            customs(fleet, system, faction);
            return;
        }
        if (isStory(fleet) || !SovereignSettings.closedBorders()) {
            return;
        }
        boolean hostile = fleet.isHostileTo(Global.getSector().getPlayerFleet())
                || faction.isHostileTo(Factions.PLAYER);
        if (!hostile && !isClearlyMilitary(fleet)) {
            return;
        }
        int incoming = Math.max(0, fleet.getFleetPoints());
        if (incoming <= 0) {
            return;
        }
        // already on the way out
        if (alreadyLeaving(fleet)) {
            return;
        }
        String group = groupOf(faction, hostile);
        if (!SovereignSettings.bars(group)) {
            markHandled(fleet, system);
            return;
        }
        float ratio = SovereignSettings.forceRatio();
        if (incoming >= ratio * local) {
            forcePassage(fleet, system, faction, group);
            return;
        }
        float roll = RANDOM.nextFloat();
        if (roll < compliance(group, incoming, local)) {
            turnAway(fleet, system, faction);
        } else if (RANDOM.nextFloat() < ("outlaw".equals(group) ? 0.7f : 0.4f)) {
            slipPast(fleet, system);
        } else {
            ignore(fleet, system, faction);
        }
    }

    // traders get a customs check once per system, against the commodities you outlawed
    private static void customs(CampaignFleetAPI fleet, StarSystemAPI system, FactionAPI faction) {
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        if (!SovereignSettings.customs() || memory.contains(MEM_CUSTOMS)
                && system.getId().equals(memory.getString(MEM_CUSTOMS))) {
            return;
        }
        memory.set(MEM_CUSTOMS, system.getId());
        if (fleet.getCargo() == null) {
            return;
        }
        FactionAPI player = Global.getSector().getPlayerFaction();
        List<String> found = new ArrayList<String>();
        float value = 0f;
        for (String id : player.getIllegalCommodities()) {
            float amount = fleet.getCargo().getCommodityQuantity(id);
            if (amount >= 5f) {
                found.add(id);
                value += amount * Global.getSettings().getCommoditySpec(id).getBasePrice();
            }
        }
        if (found.isEmpty()) {
            return;
        }
        boolean smuggler = fleet.getMemoryWithoutUpdate().contains(MemFlags.MEMORY_KEY_SMUGGLER);
        float enforcement = SovereignSettings.enforcement() == 0 ? 0.6f : SovereignSettings.enforcement() == 2 ? 1.35f : 1f;
        float detect = Math.min(0.95f, (smuggler ? 0.35f : 0.6f) * enforcement);
        if (RANDOM.nextFloat() > detect) {
            return;
        }
        int penalty = SovereignSettings.customsPenalty();
        String name = faction.getDisplayName() + " trader";
        if (penalty == 0) {
            turnAway(fleet, system, faction);
            border(name + " turned back at customs in " + system.getName() + ".");
            return;
        }
        float share = penalty == 1 ? 0.5f : 1f;
        float taken = 0f;
        for (String id : found) {
            float amount = fleet.getCargo().getCommodityQuantity(id);
            float cut = (float) Math.floor(amount * share);
            if (cut > 0f) {
                fleet.getCargo().removeCommodity(id, cut);
                taken += cut * Global.getSettings().getCommoditySpec(id).getBasePrice();
            }
        }
        int paid = Math.round(taken * (penalty == 1 ? 0.5f : 0.6f));
        if (paid > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(paid);
        }
        boolean outlaw = Factions.PIRATES.equals(faction.getId()) || Factions.LUDDIC_PATH.equals(faction.getId());
        if (!outlaw && DiplomacyManager.getManager() != null) {
            DiplomacyManager.adjustRelations(player, faction, penalty == 1 ? -0.01f : -0.03f, null, null, null);
        }
        border("Customs " + (penalty == 1 ? "fined" : "seized the cargo of") + " a " + name + " in " + system.getName()
                + " (" + SovereignKnowledge.credits(paid) + ").");
    }

    private static String groupOf(FactionAPI faction, boolean hostile) {
        String id = faction.getId();
        if (Factions.PIRATES.equals(id) || Factions.LUDDIC_PATH.equals(id)) {
            return "outlaw";
        }
        if (hostile) {
            return "hostile";
        }
        RepLevel level = Global.getSector().getPlayerFaction().getRelationshipLevel(faction);
        if (level != null && !level.isAtWorst(RepLevel.NEUTRAL)) {
            return "poor";
        }
        return "others";
    }

    // chance a fleet obeys the order to leave
    public static float compliance(String group, int incoming, float local) {
        float base = 0.85f;
        if ("poor".equals(group)) {
            base = 0.6f;
        } else if ("hostile".equals(group)) {
            base = 0.3f;
        } else if ("outlaw".equals(group)) {
            base = 0.12f;
        }
        float enforcement = SovereignSettings.enforcement() == 0 ? 0.6f : SovereignSettings.enforcement() == 2 ? 1.35f : 1f;
        float weight = Math.min(1.15f, 0.7f + 0.15f * (local / Math.max(1f, incoming)));
        return Math.max(0.02f, Math.min(0.97f, base * enforcement * weight));
    }

    private static void ignore(CampaignFleetAPI fleet, StarSystemAPI system, FactionAPI faction) {
        markHandled(fleet, system);
        border(faction.getDisplayName() + " fleet " + fleet.getName() + " ignored the order to leave " + system.getName() + ".");
    }

    private static void slipPast(CampaignFleetAPI fleet, StarSystemAPI system) {
        markHandled(fleet, system);
        fleet.getStats().getDetectedRangeMod().modifyMult(QUIET, 0.55f, "Running quiet");
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null && player.getContainingLocation() == system) {
            border("A fleet slipped past the patrols in " + system.getName() + ".");
        }
    }

    private static float localStrength(StarSystemAPI system) {
        float strength = 0f;
        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market == null || !SovereignState.countsAsFriendly(market.getFactionId())) {
                continue;
            }
            try {
                strength += InvasionFleetManager.estimateStationStrength(market);
            } catch (Exception ex) {
                Global.getLogger(SovereignBorders.class).warn("Station estimate failed for " + market.getName(), ex);
            }
        }
        for (CampaignFleetAPI fleet : system.getFleets()) {
            if (fleet == null || !fleet.isAlive() || fleet.isStationMode()) {
                continue;
            }
            if (fleet.isPlayerFleet()) {
                strength += Math.max(0, fleet.getFleetPoints());
                continue;
            }
            if (fleet.getFaction() == null || !SovereignState.countsAsFriendly(fleet.getFaction().getId())) {
                continue;
            }
            if (isTradeOrRelief(fleet)) {
                continue;
            }
            strength += Math.max(0, fleet.getFleetPoints());
        }
        if (strength > 0f) {
            return strength;
        }
        float floor = 0f;
        float per = SovereignConfig.get().emptyDefensePerMarketSize;
        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market != null && SovereignState.countsAsFriendly(market.getFactionId())) {
                floor += market.getSize() * per;
            }
        }
        return floor;
    }

    private static void border(String message) {
        SovereignLog.add(SovereignLog.BORDER, message);
        if (SovereignSettings.borderNotices() && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().addMessage(message, Misc.getHighlightColor());
        }
    }

    private static void turnAway(CampaignFleetAPI fleet, StarSystemAPI system, FactionAPI faction) {
        JumpPointAPI jump = jumpInSystem(fleet, system);
        if (jump == null) {
            Global.getLogger(SovereignBorders.class).warn("No jump point to turn a fleet out of " + system.getName());
            markHandled(fleet, system);
            return;
        }
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, jump, 60f, "leaving held space");
        fleet.getMemoryWithoutUpdate().set(SovereignState.MEM_EGRESS, true);
        markHandled(fleet, system);
        border(faction.getDisplayName() + " fleet " + fleet.getName() + " turned away from " + system.getName() + ".");
    }

    private static void forcePassage(CampaignFleetAPI fleet, StarSystemAPI system, FactionAPI faction, String group) {
        markHandled(fleet, system);
        if ("outlaw".equals(group)) {
            border(faction.getDisplayName() + " fleet " + fleet.getName() + " pushed into " + system.getName() + ".");
            return;
        }
        FactionAPI player = Global.getSector().getPlayerFaction();
        float penalty = SovereignSettings.relationPenalty();
        if (player != null && DiplomacyManager.getManager() != null && penalty > 0f) {
            DiplomacyManager.adjustRelations(player, faction, -penalty, null, null, null);
            if (SovereignSettings.warOnForcedEntry() && !player.isHostileTo(faction)) {
                float delta = SovereignActions.anchor(RepLevel.HOSTILE) - player.getRelationship(faction.getId());
                if (delta < 0f) {
                    DiplomacyManager.adjustRelations(player, faction, delta, null, null, RepLevel.HOSTILE);
                }
            }
        }
        String message = faction.getDisplayName() + " fleet " + fleet.getName() + " forced its way into "
                + system.getName() + ".";
        if (SovereignSettings.warOnForcedEntry() && player != null && player.isHostileTo(faction)) {
            message = message + " War follows.";
        }
        border(message);
    }

    private static void tryCompleteEgress(CampaignFleetAPI fleet, StarSystemAPI system) {
        JumpPointAPI jump = null;
        if (fleet.getAI() != null && fleet.getAI().getCurrentAssignment() != null
                && fleet.getAI().getCurrentAssignment().getTarget() instanceof JumpPointAPI) {
            jump = (JumpPointAPI) fleet.getAI().getCurrentAssignment().getTarget();
        }
        if (jump == null) {
            jump = jumpInSystem(fleet, system);
        }
        if (jump == null || Misc.getDistance(fleet, jump) > 450f) {
            return;
        }
        JumpPointAPI.JumpDestination destination = hyperspaceDestination(jump, system);
        if (destination == null) {
            return;
        }
        Global.getSector().doHyperspaceTransition(fleet, jump, destination);
    }

    private static JumpPointAPI.JumpDestination hyperspaceDestination(JumpPointAPI jump, StarSystemAPI system) {
        if (jump.getDestinations() != null) {
            for (JumpPointAPI.JumpDestination destination : jump.getDestinations()) {
                if (destination == null || destination.getDestination() == null) {
                    continue;
                }
                LocationAPI location = destination.getDestination().getContainingLocation();
                if (location != null && location.isHyperspace()) {
                    return destination;
                }
            }
        }
        Vector2f at = system.getLocation();
        if (at == null) {
            return null;
        }
        SectorEntityToken token = Global.getSector().getHyperspace().createToken(at.x, at.y);
        return new JumpPointAPI.JumpDestination(token, "hyperspace");
    }

    private static JumpPointAPI jumpInSystem(CampaignFleetAPI fleet, StarSystemAPI system) {
        JumpPointAPI best = null;
        float bestDistance = Float.MAX_VALUE;
        for (SectorEntityToken entity : system.getJumpPoints()) {
            if (!(entity instanceof JumpPointAPI)) {
                continue;
            }
            float distance = Misc.getDistance(fleet, entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (JumpPointAPI) entity;
            }
        }
        if (best == null) {
            best = Misc.findNearestJumpPointTo(fleet);
            if (best != null && best.getContainingLocation() != system) {
                return null;
            }
        }
        return best;
    }

    private static boolean alreadyLeaving(CampaignFleetAPI fleet) {
        if (fleet.getAI() == null) {
            return false;
        }
        FleetAssignmentDataAPI assignment = fleet.getAI().getCurrentAssignment();
        if (assignment == null || assignment.getTarget() == null) {
            return false;
        }
        FleetAssignment type = assignment.getAssignment();
        if (type != FleetAssignment.GO_TO_LOCATION && type != FleetAssignment.GO_TO_LOCATION_AND_DESPAWN) {
            return false;
        }
        return assignment.getTarget() instanceof JumpPointAPI;
    }

    private static void markHandled(CampaignFleetAPI fleet, StarSystemAPI system) {
        if (fleet == null || system == null || system.getId() == null || fleet.getMemoryWithoutUpdate() == null) {
            return;
        }
        fleet.getMemoryWithoutUpdate().set(SovereignState.MEM_HANDLED, system.getId());
    }

    private static boolean isTradeOrRelief(CampaignFleetAPI fleet) {
        if (Misc.isTrader(fleet)) {
            return true;
        }
        String type = fleetType(fleet);
        if (FleetTypes.TRADE.equals(type) || FleetTypes.TRADE_SMUGGLER.equals(type)
                || FleetTypes.TRADE_SMALL.equals(type) || FleetTypes.TRADE_LINER.equals(type)
                || FleetTypes.FOOD_RELIEF_FLEET.equals(type) || FleetTypes.SUPPLY_FLEET.equals(type)
                || "shuttle".equals(type) || "foodRelief".equals(type) || "trade".equals(type)) {
            return true;
        }
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        return memory.contains(MemFlags.MEMORY_KEY_TRADE_FLEET) || memory.contains(MemFlags.MEMORY_KEY_SMUGGLER);
    }

    private static boolean isStory(CampaignFleetAPI fleet) {
        if (isClearlyMilitary(fleet)) {
            return false;
        }
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        return memory.contains(MemFlags.STORY_CRITICAL)
                || memory.contains(MemFlags.MEMORY_KEY_MISSION_IMPORTANT)
                || memory.contains(MemFlags.ENTITY_MISSION_IMPORTANT)
                || memory.contains(MemFlags.ACADEMY_FLEET);
    }

    private static boolean isClearlyMilitary(CampaignFleetAPI fleet) {
        String type = fleetType(fleet).toLowerCase();
        if (type.contains("patrol") || type.contains("hunter") || type.contains("raid")
                || type.contains("invasion") || type.contains("inspect") || type.contains("investigat")
                || type.contains("task") || type.contains("merc") || type.contains("bounty")
                || type.contains("war") || type.contains("military") || type.contains("blockade")
                || type.contains("enforcer") || type.contains("armada") || type.contains("punitive")) {
            return true;
        }
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        return memory.contains(MemFlags.MEMORY_KEY_PATROL_FLEET)
                || memory.contains(MemFlags.MEMORY_KEY_WAR_FLEET)
                || memory.contains(MemFlags.MEMORY_KEY_RAIDER)
                || memory.contains(MemFlags.MEMORY_KEY_CUSTOMS_INSPECTOR)
                || memory.contains(MemFlags.MEMORY_KEY_PIRATE)
                || memory.contains(MemFlags.MEMORY_KEY_MAKE_HOSTILE)
                || memory.contains(MemFlags.FLEET_MILITARY_RESPONSE);
    }

    private static String fleetType(CampaignFleetAPI fleet) {
        String type = Misc.getFleetType(fleet);
        if (type != null && !type.isEmpty()) {
            return type;
        }
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        if (memory != null && memory.contains(MemFlags.MEMORY_KEY_FLEET_TYPE)) {
            String raw = memory.getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
            return raw == null ? "" : raw;
        }
        return "";
    }
}
