package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SovereignState {

    public static final String KEY_INTEL = "sovereigncommand_intel";
    public static final String KEY_VISITED = "sovereigncommand_visited";
    public static final String KEY_INVESTIGATIONS = "sovereigncommand_investigations";
    public static final String KEY_PENDING = "sovereigncommand_pendingInvest";

    public static final String MEM_HANDLED = "$sc_handled";
    public static final String MEM_EGRESS = "$sc_egress";
    public static final String MEM_INVESTIGATE = "$sc_investigate";
    public static final String MEM_HOME = "$sc_investigate_home";
    public static final String MEM_STAGE = "$sc_investigate_stage";

    private static boolean allianceWarned;

    private SovereignState() {
    }

    public static Map<String, Object> data() {
        return Global.getSector().getPersistentData();
    }

    public static void markVisited(String systemId) {
        if (systemId == null || systemId.isEmpty()) {
            return;
        }
        List<String> visited = stringList(KEY_VISITED);
        if (!visited.contains(systemId)) {
            visited.add(systemId);
        }
    }

    public static boolean hasVisited(String systemId) {
        Object raw = data().get(KEY_VISITED);
        return systemId != null && raw instanceof List && ((List<?>) raw).contains(systemId);
    }

    public static void markPending(String systemId) {
        if (systemId == null) {
            return;
        }
        List<String> pending = stringList(KEY_PENDING);
        if (!pending.contains(systemId)) {
            pending.add(systemId);
        }
    }

    public static void clearPending(String systemId) {
        Object raw = data().get(KEY_PENDING);
        if (raw instanceof List && systemId != null) {
            ((List<?>) raw).remove(systemId);
        }
    }

    public static boolean isPending(String systemId) {
        Object raw = data().get(KEY_PENDING);
        return systemId != null && raw instanceof List && ((List<?>) raw).contains(systemId);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Object>> investigations() {
        Object raw = data().get(KEY_INVESTIGATIONS);
        if (raw instanceof Map) {
            return (Map<String, Map<String, Object>>) raw;
        }
        Map<String, Map<String, Object>> created = new HashMap<String, Map<String, Object>>();
        data().put(KEY_INVESTIGATIONS, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> snapshot(String marketId) {
        if (marketId == null) {
            return null;
        }
        Object raw = data().get(KEY_INVESTIGATIONS);
        if (!(raw instanceof Map)) {
            return null;
        }
        Object row = ((Map<?, ?>) raw).get(marketId);
        return row instanceof Map ? (Map<String, Object>) row : null;
    }

    public static void putSnapshot(String marketId, Map<String, Object> row) {
        if (marketId != null && row != null) {
            investigations().put(marketId, row);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(String key) {
        Object raw = data().get(key);
        if (raw instanceof List) {
            return (List<String>) raw;
        }
        List<String> created = new ArrayList<String>();
        data().put(key, created);
        return created;
    }

    public static boolean isPlayerFactionId(String factionId) {
        if (factionId == null) {
            return false;
        }
        if (Factions.PLAYER.equals(factionId)) {
            return true;
        }
        SectorAPI sector = Global.getSector();
        return sector != null && sector.getPlayerFaction() != null
                && factionId.equals(sector.getPlayerFaction().getId());
    }

    public static boolean isPlayerSide(String factionId) {
        if (isPlayerFactionId(factionId)) {
            return true;
        }
        String commission = Misc.getCommissionFactionId();
        return commission != null && commission.equals(factionId);
    }

    public static boolean isPlayerMarket(MarketAPI market) {
        return market != null && (market.isPlayerOwned() || isPlayerFactionId(market.getFactionId()));
    }

    public static boolean countsAsFriendly(String factionId) {
        if (factionId == null) {
            return false;
        }
        if (isPlayerSide(factionId)) {
            return true;
        }
        Logger log = Global.getLogger(SovereignState.class);
        try {
            if (AllianceManager.getManager() != null) {
                if (AllianceManager.areFactionsAllied(Factions.PLAYER, factionId)) {
                    return true;
                }
                FactionAPI player = Global.getSector().getPlayerFaction();
                if (player != null && AllianceManager.areFactionsAllied(player.getId(), factionId)) {
                    return true;
                }
                String commission = Misc.getCommissionFactionId();
                if (commission != null && AllianceManager.areFactionsAllied(commission, factionId)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            if (!allianceWarned) {
                allianceWarned = true;
                log.warn("Alliance lookup failed, falling back to relations", ex);
            }
        }
        try {
            FactionAPI player = Global.getSector().getPlayerFaction();
            if (player == null) {
                return false;
            }
            RepLevel level = player.getRelationshipLevel(factionId);
            return level != null && level.isAtWorst(RepLevel.FRIENDLY);
        } catch (Exception ex) {
            log.warn("Relationship lookup failed for " + factionId, ex);
            return false;
        }
    }
}
