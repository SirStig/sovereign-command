package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.SpecialForcesTask;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.TaskType;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class SovereignActions {

    public static final class Outcome {
        public final boolean ok;
        public final String text;

        Outcome(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }
    }

    private enum Move {
        NONE, FORM, JOIN, INVITE, LEAVE
    }

    public static final class AllianceMove {
        public String label = "Alliance";
        public String hint = "";
        public String reason = "";
        public boolean allowed;
        private Move move = Move.NONE;
        private FactionAPI other;

        public Outcome perform() {
            return doAlliance(this);
        }
    }

    private SovereignActions() {
    }

    private static Outcome ok(String text) {
        return new Outcome(true, text);
    }

    private static Outcome no(String text) {
        return new Outcome(false, text);
    }

    // ---- relations --------------------------------------------------------------------------------------

    static Outcome raise(FactionAPI other, float delta, RepLevel limit, String verb) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (DiplomacyManager.getManager() == null) {
            return no("Nexerelin diplomacy is off.");
        }
        try {
            ExerelinReputationAdjustmentResult result = DiplomacyManager.adjustRelations(
                    player, other, delta, null, null, limit);
            float applied = result == null ? 0f : result.delta;
            if (Math.abs(applied) < 0.001f) {
                return no(other.getDisplayName() + " will not move any further.");
            }
            String text = verb.isEmpty() ? "" : verb + ": " + other.getDisplayName() + " " + signed(applied) + ".";
            if (!text.isEmpty()) {
                SovereignLog.add(SovereignLog.DIPLOMACY, text);
            }
            return ok(text);
        } catch (Exception ex) {
            return failed("Relation change failed", ex);
        }
    }

    public static Outcome declareWar(FactionAPI other) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (player == null || other == null) {
            return no("Pick a faction.");
        }
        if (player.isHostileTo(other)) {
            return no("Already at war with " + other.getDisplayName() + ".");
        }
        if (DiplomacyManager.getManager() == null) {
            return no("Nexerelin diplomacy is off.");
        }
        float delta = anchor(RepLevel.HOSTILE) - player.getRelationship(other.getId());
        return applyDelta(player, other, delta, RepLevel.HOSTILE, "War declared");
    }

    public static Outcome makePeace(FactionAPI other) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (player == null || other == null) {
            return no("Pick a faction.");
        }
        if (!player.isHostileTo(other)) {
            return no("Not at war with " + other.getDisplayName() + ".");
        }
        if (DiplomacyManager.getManager() == null) {
            return no("Nexerelin diplomacy is off.");
        }
        float delta = anchor(RepLevel.NEUTRAL) - player.getRelationship(other.getId());
        return applyDelta(player, other, delta, RepLevel.NEUTRAL, "Peace made");
    }

    private static Outcome applyDelta(FactionAPI player, FactionAPI other, float delta, RepLevel limit, String verb) {
        try {
            ExerelinReputationAdjustmentResult result = DiplomacyManager.adjustRelations(
                    player, other, delta, null, null, limit);
            RepLevel now = player.getRelationshipLevel(other);
            float applied = result == null ? 0f : result.delta;
            String text;
            if (Math.abs(applied) < 0.001f) {
                text = other.getDisplayName() + " will not move any further.";
                return no(text);
            }
            text = verb + ": " + other.getDisplayName() + " is now " + (now == null ? "unchanged" : now.getDisplayName())
                    + " (" + signed(applied) + ").";
            SovereignLog.add(SovereignLog.DIPLOMACY, text);
            return ok(text);
        } catch (Exception ex) {
            return failed("Relation change failed", ex);
        }
    }

    // RepLevel.getMin() is not signed, so aim a little inside each band
    static float anchor(RepLevel level) {
        if (level == RepLevel.VENGEFUL) {
            return -0.80f;
        }
        if (level == RepLevel.HOSTILE) {
            return -0.55f;
        }
        if (level == RepLevel.INHOSPITABLE) {
            return -0.30f;
        }
        if (level == RepLevel.SUSPICIOUS) {
            return -0.15f;
        }
        if (level == RepLevel.NEUTRAL) {
            return 0f;
        }
        if (level == RepLevel.FAVORABLE) {
            return 0.15f;
        }
        if (level == RepLevel.WELCOMING) {
            return 0.30f;
        }
        if (level == RepLevel.FRIENDLY) {
            return 0.55f;
        }
        return 0.80f;
    }

    // ---- alliances --------------------------------------------------------------------------------------

    public static AllianceMove allianceMove(FactionAPI other) {
        AllianceMove m = new AllianceMove();
        m.other = other;
        try {
            FactionAPI player = Global.getSector().getPlayerFaction();
            String pid = PlayerFactionStore.getPlayerFactionId();
            if (other == null || AllianceManager.getManager() == null) {
                m.reason = "Alliances are off.";
                return m;
            }
            String oid = other.getId();
            if (AllianceManager.INVALID_FACTIONS.contains(oid) || Factions.NEUTRAL.equals(oid)
                    || Factions.DERELICT.equals(oid) || SovereignState.isPlayerFactionId(oid) || oid.equals(pid)) {
                m.reason = "Can't ally with them.";
                return m;
            }
            Alliance mine = AllianceManager.getFactionAlliance(pid);
            Alliance theirs = AllianceManager.getFactionAlliance(oid);
            if (mine != null && mine == theirs) {
                m.label = "Leave alliance";
                m.move = Move.LEAVE;
                m.allowed = Nex_IsFactionRuler.isRuler(pid);
                m.reason = "Rulers only.";
                m.hint = "Leave " + mine.getName();
                return m;
            }
            if (mine == null && theirs == null) {
                m.label = "Form alliance";
                m.move = Move.FORM;
            } else if (theirs == null) {
                m.label = "Invite to alliance";
                m.move = Move.INVITE;
            } else {
                m.label = "Join alliance";
                m.move = Move.JOIN;
            }
            m.hint = m.label + (theirs != null ? ": " + theirs.getName() : mine != null ? ": " + mine.getName() : "");
            if (!Nex_IsFactionRuler.isRuler(pid)) {
                m.reason = "Rulers only.";
                return m;
            }
            if (player.isHostileTo(other)) {
                m.reason = "Not while at war.";
                return m;
            }
            float rel = player.getRelationship(oid);
            if (rel < AllianceManager.MIN_RELATIONSHIP_TO_STAY) {
                m.reason = "Needs " + RepLevel.WELCOMING.getDisplayName() + " relations.";
                return m;
            }
            if (m.move == Move.JOIN && !trusted(player, theirs)) {
                m.reason = "The members don't trust you yet.";
                return m;
            }
            if (m.move == Move.INVITE) {
                if (!trusted(other, mine)) {
                    m.reason = "They don't trust your alliance.";
                    return m;
                }
                if (!AllianceManager.isAlignmentCompatible(oid, mine)) {
                    m.reason = "They don't share your goals.";
                    return m;
                }
            }
            if (m.move == Move.FORM && AllianceManager.getBestAlignment(oid, pid) == null) {
                m.reason = "You have no common cause.";
                return m;
            }
            m.allowed = true;
        } catch (Exception ex) {
            Global.getLogger(SovereignActions.class).warn("Alliance check failed", ex);
            m.allowed = false;
            m.reason = "Alliance data unavailable.";
        }
        return m;
    }

    private static boolean trusted(FactionAPI faction, Alliance alliance) {
        if (alliance == null) {
            return true;
        }
        for (String member : alliance.getMembersCopy()) {
            if (faction.isHostileTo(member)) {
                return false;
            }
        }
        return alliance.getAverageRelationshipWithFaction(faction.getId()) >= AllianceManager.MIN_RELATIONSHIP_TO_STAY;
    }

    private static Outcome doAlliance(AllianceMove m) {
        if (!m.allowed || m.other == null) {
            return no(m.reason.isEmpty() ? "Not possible right now." : m.reason);
        }
        try {
            String pid = PlayerFactionStore.getPlayerFactionId();
            String oid = m.other.getId();
            Alliance mine = AllianceManager.getFactionAlliance(pid);
            Alliance theirs = AllianceManager.getFactionAlliance(oid);
            List<MarketAPI> markets = Misc.getFactionMarkets(m.other);
            AllianceManager.setPlayerInteractionTarget(
                    markets.isEmpty() ? null : markets.get(0).getPrimaryEntity());
            String text;
            try {
                switch (m.move) {
                    case FORM:
                        Alliance made = AllianceManager.createAlliance(pid, oid, AllianceManager.getBestAlignment(oid, pid));
                        text = "Formed " + (made == null ? "an alliance" : made.getName()) + " with " + m.other.getDisplayName() + ".";
                        break;
                    case INVITE:
                        AllianceManager.joinAllianceStatic(oid, mine);
                        text = m.other.getDisplayName() + " joined " + mine.getName() + ".";
                        break;
                    case JOIN:
                        if (mine != null && mine != theirs) {
                            AllianceManager.leaveAlliance(pid, false);
                        }
                        AllianceManager.joinAllianceStatic(pid, theirs);
                        text = "You joined " + theirs.getName() + ".";
                        break;
                    case LEAVE:
                        String name = mine == null ? "the alliance" : mine.getName();
                        AllianceManager.leaveAlliance(pid, false);
                        text = "You left " + name + ".";
                        break;
                    default:
                        return no("Not possible right now.");
                }
            } finally {
                AllianceManager.setPlayerInteractionTarget(null);
            }
            SovereignLog.add(SovereignLog.DIPLOMACY, text);
            return ok(text);
        } catch (Exception ex) {
            return failed("Alliance change failed", ex);
        }
    }

    public static Outcome leaveOwnAlliance() {
        try {
            String pid = PlayerFactionStore.getPlayerFactionId();
            Alliance mine = AllianceManager.getManager() == null ? null : AllianceManager.getFactionAlliance(pid);
            if (mine == null) {
                return no("You are not in an alliance.");
            }
            if (!Nex_IsFactionRuler.isRuler(pid)) {
                return no("Rulers only.");
            }
            String name = mine.getName();
            AllianceManager.leaveAlliance(pid, false);
            String text = "You left " + name + ".";
            SovereignLog.add(SovereignLog.DIPLOMACY, text);
            return ok(text);
        } catch (Exception ex) {
            return failed("Could not leave the alliance", ex);
        }
    }

    // ---- task forces ------------------------------------------------------------------------------------

    public static List<SpecialForcesIntel> playerTaskForces() {
        List<SpecialForcesIntel> found = new ArrayList<SpecialForcesIntel>();
        for (IntelInfoPlugin plugin : Global.getSector().getIntelManager().getIntel()) {
            if (plugin instanceof SpecialForcesIntel) {
                SpecialForcesIntel intel = (SpecialForcesIntel) plugin;
                if (intel.isPlayer() && !intel.isEnded()) {
                    found.add(intel);
                }
            }
        }
        return found;
    }

    public static String taskForceName(SpecialForcesIntel intel) {
        String name = intel == null ? null : intel.getFleetName();
        return name == null || name.isEmpty() ? "Task force" : name;
    }

    public static MarketAPI largestPlayerMilitaryMarket() {
        MarketAPI best = null;
        for (MarketAPI market : Misc.getPlayerMarkets(false)) {
            if (!SovereignState.isPlayerMarket(market)) {
                continue;
            }
            if (best == null) {
                best = market;
                continue;
            }
            boolean military = Misc.isMilitary(market);
            boolean bestMilitary = Misc.isMilitary(best);
            if (military && !bestMilitary || military == bestMilitary && market.getSize() > best.getSize()) {
                best = market;
            }
        }
        return best;
    }

    public static Outcome patrol(SpecialForcesIntel intel, MarketAPI market) {
        if (market == null) {
            return no("Pick a colony first.");
        }
        SpecialForcesRouteAI ai = route(intel);
        if (ai == null) {
            return no("It can't take orders now.");
        }
        try {
            SpecialForcesTask task = ai.generatePatrolTask(market, 1000f);
            task.playerIssued = true;
            task.setMarket(market);
            task.time = 90f;
            ai.assignTask(task, false);
            return ordered(intel, "patrols " + market.getName());
        } catch (Exception ex) {
            return failed("Patrol order failed", ex);
        }
    }

    public static Outcome orbit(SpecialForcesIntel intel, MarketAPI market) {
        if (market == null || market.getPrimaryEntity() == null) {
            return no("Pick a colony first.");
        }
        return give(intel, TaskType.WAIT_ORBIT, market, market.getPrimaryEntity(), 90f, "holds at " + market.getName());
    }

    public static Outcome follow(SpecialForcesIntel intel) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) {
            return no("Your fleet is not in play.");
        }
        return give(intel, TaskType.FOLLOW_PLAYER, null, player, 100000f, "follows you");
    }

    public static Outcome holdHere(SpecialForcesIntel intel) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) {
            return no("Your fleet is not in play.");
        }
        SectorEntityToken spot = player.getContainingLocation().createToken(player.getLocation());
        spot.addTag("nex_player_location_token");
        return give(intel, TaskType.WAIT_ORBIT, null, spot, 100000f, "holds your position");
    }

    public static Outcome rebuild(SpecialForcesIntel intel, MarketAPI market) {
        if (market == null || market.getPrimaryEntity() == null) {
            return no("Pick a colony first.");
        }
        return give(intel, TaskType.REBUILD, market, market.getPrimaryEntity(), 90f, "refits at " + market.getName());
    }

    public static Outcome auto(SpecialForcesIntel intel) {
        SpecialForcesRouteAI ai = route(intel);
        if (ai == null) {
            return no("It can't take orders now.");
        }
        try {
            SpecialForcesTask task = ai.pickTask(false);
            if (task == null) {
                return no("Nothing needs doing.");
            }
            ai.assignTask(task);
            return ordered(intel, "acts on its own: " + task.getText());
        } catch (Exception ex) {
            return failed("Order failed", ex);
        }
    }

    private static Outcome give(SpecialForcesIntel intel, TaskType type, MarketAPI market, SectorEntityToken entity,
                                float time, String phrase) {
        SpecialForcesRouteAI ai = route(intel);
        if (ai == null) {
            return no("It can't take orders now.");
        }
        try {
            SpecialForcesTask task = new SpecialForcesTask(type, 1000f);
            task.playerIssued = true;
            task.time = time;
            if (market != null) {
                task.setMarket(market);
                task.system = market.getStarSystem();
            } else {
                task.setEntity(entity);
                task.system = entity.getStarSystem();
            }
            ai.assignTask(task, false);
            return ordered(intel, phrase);
        } catch (Exception ex) {
            return failed("Order failed", ex);
        }
    }

    private static Outcome ordered(SpecialForcesIntel intel, String phrase) {
        String text = taskForceName(intel) + " " + phrase + ".";
        SovereignLog.add(SovereignLog.ORDER, text);
        return ok(text);
    }

    private static SpecialForcesRouteAI route(SpecialForcesIntel intel) {
        if (intel == null) {
            return null;
        }
        try {
            return intel.getRouteAI();
        } catch (Exception ex) {
            Global.getLogger(SovereignActions.class).warn("Task force route unavailable", ex);
            return null;
        }
    }

    // ---- inquiries --------------------------------------------------------------------------------------

    public static Outcome investigate(MarketAPI target) {
        if (target == null || target.getStarSystem() == null) {
            return no("Pick a colony in a star system.");
        }
        StarSystemAPI system = target.getStarSystem();
        if (SovereignState.isPending(system.getId())) {
            return no("A survey party is already out in " + system.getName() + ".");
        }
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getCargo() == null) {
            return no("Your fleet is not in play.");
        }
        int cost = SovereignSettings.investigateCost();
        if (player.getCargo().getCredits().get() < cost) {
            return no("You need " + SovereignKnowledge.credits(cost) + ".");
        }
        Logger log = Global.getLogger(SovereignActions.class);
        boolean paid = false;
        CampaignFleetAPI fleet = null;
        try {
            player.getCargo().getCredits().subtract(cost);
            paid = true;
            MarketAPI home = largestPlayerMilitaryMarket();
            FleetParamsV3 params = new FleetParamsV3(home, player.getLocationInHyperspace(), Factions.PLAYER, null,
                    FleetTypes.PATROL_SMALL, 25f, 10f, 10f, 0f, 0f, 0f, 0f);
            fleet = FleetFactoryV3.createFleet(params);
            if (fleet == null || fleet.getNumMembersFast() <= 0) {
                player.getCargo().getCredits().add(cost);
                return no("No ships to spare. Refunded.");
            }
            fleet.setFaction(Factions.PLAYER, true);
            fleet.setName("Survey party");
            fleet.setNoAutoDespawn(Boolean.TRUE);
            fleet.getMemoryWithoutUpdate().set(SovereignState.MEM_INVESTIGATE, system.getId());
            fleet.getMemoryWithoutUpdate().set(SovereignState.MEM_STAGE, "go");
            if (home != null) {
                fleet.getMemoryWithoutUpdate().set(SovereignState.MEM_HOME, home.getId());
            }
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true);
            if (player.getContainingLocation() != null) {
                player.getContainingLocation().spawnFleet(player, 140f, 30f, fleet);
            }
            if (fleet.getContainingLocation() == null && player.getContainingLocation() != null) {
                player.getContainingLocation().addEntity(fleet);
                fleet.setLocation(player.getLocation().x + 140f, player.getLocation().y + 30f);
            }
            SectorEntityToken destination = system.getCenter() != null ? system.getCenter() : system.getStar();
            if (destination == null) {
                fleet.despawn();
                player.getCargo().getCredits().add(cost);
                return no("Nothing there to survey. Refunded.");
            }
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, destination, 365f, "surveying " + system.getName());
            SovereignState.markPending(system.getId());
            String text = "Survey party sent to " + system.getName() + " (" + SovereignKnowledge.credits(cost) + ").";
            SovereignLog.add(SovereignLog.INTEL, text);
            return ok(text);
        } catch (Exception ex) {
            log.error("Survey party failed", ex);
            if (fleet != null) {
                try {
                    fleet.despawn();
                } catch (Exception despawnEx) {
                    log.warn("Could not remove the survey party", despawnEx);
                }
            }
            if (paid) {
                player.getCargo().getCredits().add(cost);
            }
            SovereignState.clearPending(system.getId());
            return no("The survey party didn't launch. Refunded.");
        }
    }

    public static void advanceInvestigations() {
        Logger log = Global.getLogger(SovereignActions.class);
        try {
            for (CampaignFleetAPI fleet : SovereignBorders.allFleets()) {
                if (fleet == null || fleet.getMemoryWithoutUpdate() == null
                        || !fleet.getMemoryWithoutUpdate().contains(SovereignState.MEM_INVESTIGATE)) {
                    continue;
                }
                if (!fleet.isAlive() || fleet.isDespawning()) {
                    String systemId = fleet.getMemoryWithoutUpdate().getString(SovereignState.MEM_INVESTIGATE);
                    if (!inquiryStillOut(systemId, fleet)) {
                        SovereignState.clearPending(systemId);
                    }
                    continue;
                }
                updateInquiry(fleet);
            }
            Object raw = SovereignState.data().get(SovereignState.KEY_PENDING);
            if (raw instanceof List) {
                for (Object id : new ArrayList<Object>((List<?>) raw)) {
                    if (id instanceof String && !inquiryStillOut((String) id, null)) {
                        SovereignState.clearPending((String) id);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Survey update failed", ex);
        }
    }

    private static void updateInquiry(CampaignFleetAPI fleet) {
        String systemId = fleet.getMemoryWithoutUpdate().getString(SovereignState.MEM_INVESTIGATE);
        String stage = fleet.getMemoryWithoutUpdate().contains(SovereignState.MEM_STAGE)
                ? fleet.getMemoryWithoutUpdate().getString(SovereignState.MEM_STAGE) : "go";
        StarSystemAPI system = systemId == null ? null : Global.getSector().getStarSystem(systemId);
        if ("go".equals(stage)) {
            if (system != null && arrived(fleet, system)) {
                SovereignKnowledge.recordSystem(system);
                fleet.getMemoryWithoutUpdate().set(SovereignState.MEM_STAGE, "return");
                sendHome(fleet);
                SovereignLog.announce(SovereignLog.INTEL, "Survey of " + system.getName() + " complete.");
            }
            return;
        }
        if (closeToHome(fleet)) {
            fleet.despawn(CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION, null);
        }
    }

    private static boolean arrived(CampaignFleetAPI fleet, StarSystemAPI system) {
        if (fleet.getStarSystem() == null || !system.getId().equals(fleet.getStarSystem().getId())) {
            return false;
        }
        if (system.getCenter() != null && Misc.getDistance(fleet, system.getCenter()) < 2000f) {
            return true;
        }
        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market != null && market.getPrimaryEntity() != null
                    && Misc.getDistance(fleet, market.getPrimaryEntity()) < 800f) {
                return true;
            }
        }
        return false;
    }

    private static MarketAPI homeOf(CampaignFleetAPI fleet) {
        String homeId = fleet.getMemoryWithoutUpdate().contains(SovereignState.MEM_HOME)
                ? fleet.getMemoryWithoutUpdate().getString(SovereignState.MEM_HOME) : null;
        return homeId == null ? null : Global.getSector().getEconomy().getMarket(homeId);
    }

    private static void sendHome(CampaignFleetAPI fleet) {
        MarketAPI home = homeOf(fleet);
        if (home == null) {
            home = largestPlayerMilitaryMarket();
        }
        fleet.clearAssignments();
        if (home != null && home.getPrimaryEntity() != null) {
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, home.getPrimaryEntity(), 365f,
                    "returning from survey");
            return;
        }
        fleet.despawn(CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION, null);
    }

    private static boolean closeToHome(CampaignFleetAPI fleet) {
        MarketAPI home = homeOf(fleet);
        return home != null && home.getPrimaryEntity() != null
                && fleet.getContainingLocation() == home.getPrimaryEntity().getContainingLocation()
                && Misc.getDistance(fleet, home.getPrimaryEntity()) < 600f;
    }

    private static boolean inquiryStillOut(String systemId, CampaignFleetAPI except) {
        if (systemId == null) {
            return false;
        }
        for (CampaignFleetAPI fleet : SovereignBorders.allFleets()) {
            if (fleet == null || fleet == except || !fleet.isAlive() || fleet.isDespawning()
                    || !fleet.getMemoryWithoutUpdate().contains(SovereignState.MEM_INVESTIGATE)) {
                continue;
            }
            if (systemId.equals(fleet.getMemoryWithoutUpdate().getString(SovereignState.MEM_INVESTIGATE))) {
                return true;
            }
        }
        return false;
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    static Outcome failed(String prefix, Exception ex) {
        Global.getLogger(SovereignActions.class).error(prefix, ex);
        return no(prefix + ".");
    }

    private static String signed(float delta) {
        int points = Math.round(delta * 100f);
        return points > 0 ? "+" + points : String.valueOf(points);
    }
}
