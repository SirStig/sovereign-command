package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public final class SovereignLedger extends BaseCampaignEventListener {

    public SovereignLedger() {
        super(false);
    }

    public static void install() {
        Global.getSector().addTransientListener(new SovereignLedger());
    }

    private static Map<String, Object> data() {
        return SovereignState.data();
    }

    private static float get(String key) {
        Object v = data().get(key);
        return v instanceof Number ? ((Number) v).floatValue() : 0f;
    }

    static float killed(String factionId) {
        return get("sc_killed_" + factionId);
    }

    static float lost(String factionId) {
        return get("sc_lost_" + factionId);
    }

    static void clear(String factionId) {
        data().remove("sc_killed_" + factionId);
        data().remove("sc_lost_" + factionId);
    }

    @Override
    public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle) {
        try {
            if (battle == null || !battle.isPlayerInvolved()) {
                return;
            }
            float share = battle.getPlayerInvolvementFraction();
            String mainEnemy = null;
            for (CampaignFleetAPI enemy : battle.getNonPlayerSide()) {
                if (enemy.getFaction() == null || SovereignState.countsAsFriendly(enemy.getFaction().getId())) {
                    continue;
                }
                String id = enemy.getFaction().getId();
                if (mainEnemy == null) {
                    mainEnemy = id;
                }
                float fp = 0f;
                for (FleetMemberAPI member : Misc.getSnapshotMembersLost(enemy)) {
                    fp += member.getFleetPointCost();
                }
                if (fp > 0f) {
                    data().put("sc_killed_" + id, killed(id) + fp * share);
                }
            }
            CampaignFleetAPI mine = Global.getSector().getPlayerFleet();
            if (mainEnemy != null && mine != null) {
                float fp = 0f;
                List<FleetMemberAPI> now = mine.getFleetData().getMembersListCopy();
                for (FleetMemberAPI member : mine.getFleetData().getSnapshot()) {
                    if (!now.contains(member)) {
                        fp += member.getFleetPointCost();
                    }
                }
                if (fp > 0f) {
                    data().put("sc_lost_" + mainEnemy, lost(mainEnemy) + fp);
                }
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignLedger.class).warn("Battle not recorded", ex);
        }
    }
}
