package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.fleets.InvasionFleetManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SovereignKnowledge {

    public enum Level {
        FULL, INVESTIGATED, ESTIMATED, SHALLOW
    }

    public static final class Dossier {
        public Level level;
        public String name;
        public String system;
        public String faction;
        public String size;
        public String stability;
        public String industries;
        public String income;
        public String defense;
        public String conditions;
        public String source;
    }

    public static final class Strength {
        public float value;
        public int exact;
        public int guessed;
        public int hidden;
    }

    private SovereignKnowledge() {
    }

    public static Level levelOf(MarketAPI market) {
        if (market == null) {
            return Level.SHALLOW;
        }
        if (!SovereignSettings.fog() || SovereignState.isPlayerMarket(market)) {
            return Level.FULL;
        }
        if (SovereignState.snapshot(market.getId()) != null) {
            return Level.INVESTIGATED;
        }
        return seen(market) ? Level.ESTIMATED : Level.SHALLOW;
    }

    public static boolean seen(MarketAPI market) {
        if (market == null) {
            return false;
        }
        MarketAPI.SurveyLevel survey = market.getSurveyLevel();
        if (survey != null && survey != MarketAPI.SurveyLevel.NONE) {
            return true;
        }
        StarSystemAPI system = market.getStarSystem();
        if (system == null) {
            return false;
        }
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null && player.getContainingLocation() == system) {
            return true;
        }
        return SovereignState.hasVisited(system.getId());
    }

    public static String systemName(MarketAPI market) {
        if (market == null || market.getStarSystem() == null || market.getStarSystem().getName() == null) {
            return "Unknown system";
        }
        return market.getStarSystem().getName().replace(" Star System", "");
    }

    public static String nameOf(MarketAPI market) {
        if (market == null) {
            return "Unknown";
        }
        Level level = levelOf(market);
        if (level == Level.SHALLOW) {
            return "Unknown colony";
        }
        if (level == Level.INVESTIGATED) {
            Map<String, Object> row = SovereignState.snapshot(market.getId());
            if (row != null && row.get("name") != null) {
                return String.valueOf(row.get("name"));
            }
        }
        return market.getName() == null ? "Colony" : market.getName();
    }

    public static String sizeOf(MarketAPI market) {
        Level level = levelOf(market);
        if (level == Level.SHALLOW) {
            return "?";
        }
        if (level == Level.INVESTIGATED) {
            Map<String, Object> row = SovereignState.snapshot(market.getId());
            if (row != null && row.get("size") != null) {
                return String.valueOf(row.get("size"));
            }
        }
        return String.valueOf(market.getSize());
    }

    public static float estimatedIncome(MarketAPI market) {
        int size = market == null ? 0 : market.getSize();
        return size * size * 10000f;
    }

    public static float estimatedPatrol(MarketAPI market) {
        return market == null ? 0f : market.getSize() * 15f;
    }

    public static float estimatedStation(MarketAPI market) {
        return market == null ? 0f : market.getSize() * 10f;
    }

    public static void recordSystem(StarSystemAPI system) {
        if (system == null) {
            return;
        }
        Logger log = Global.getLogger(SovereignKnowledge.class);
        for (MarketAPI market : Misc.getMarketsInLocation(system)) {
            if (market == null || market.getId() == null) {
                continue;
            }
            try {
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("name", safe(market.getName()));
                row.put("system", systemName(market));
                row.put("faction", market.getFaction() == null ? "Unknown" : safe(market.getFaction().getDisplayName()));
                row.put("size", market.getSize());
                row.put("stability", Math.round(market.getStabilityValue()));
                row.put("income", estimatedIncome(market));
                row.put("patrol", patrolLive(market));
                row.put("station", stationLive(market));
                row.put("industries", industryLine(market));
                row.put("conditions", surveyedConditions(market));
                row.put("date", Global.getSector().getClock().getShortDate());
                SovereignState.putSnapshot(market.getId(), row);
            } catch (Exception ex) {
                log.warn("Could not record " + market.getName(), ex);
            }
        }
        SovereignState.clearPending(system.getId());
    }

    public static Dossier dossier(MarketAPI market) {
        Dossier d = new Dossier();
        d.level = levelOf(market);
        d.name = nameOf(market);
        d.system = systemName(market);
        d.faction = market == null || market.getFaction() == null ? "Unknown" : market.getFaction().getDisplayName();
        d.size = sizeOf(market);
        d.stability = "?";
        d.industries = "?";
        d.income = "?";
        d.defense = "?";
        d.conditions = "?";
        if (market == null) {
            d.source = "";
            return d;
        }
        try {
            switch (d.level) {
                case SHALLOW:
                    d.source = "Never seen";
                    break;
                case ESTIMATED:
                    d.source = "Visited, figures estimated";
                    d.income = "~" + credits(estimatedIncome(market));
                    d.defense = "~" + Math.round(estimatedPatrol(market) + estimatedStation(market));
                    d.conditions = surveyedConditions(market);
                    break;
                case INVESTIGATED:
                    Map<String, Object> row = SovereignState.snapshot(market.getId());
                    d.source = "Inquiry, " + text(row, "date");
                    d.stability = text(row, "stability");
                    d.industries = text(row, "industries");
                    d.income = "~" + credits(asFloat(row.get("income")));
                    d.defense = String.valueOf(Math.round(asFloat(row.get("patrol")) + asFloat(row.get("station"))));
                    d.conditions = row.get("conditions") == null ? "?" : text(row, "conditions");
                    break;
                default:
                    d.source = SovereignState.isPlayerMarket(market) ? "Your colony" : "Full intel";
                    d.stability = String.valueOf(Math.round(market.getStabilityValue()));
                    d.industries = industryLine(market);
                    d.income = credits(industryIncome(market));
                    d.defense = String.valueOf(Math.round(patrolLive(market) + stationLive(market)));
                    d.conditions = allConditions(market);
                    break;
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignKnowledge.class).warn("Dossier incomplete for " + market.getName(), ex);
        }
        return d;
    }

    public static Strength strength(FactionAPI faction) {
        Strength s = new Strength();
        if (faction == null) {
            return s;
        }
        Logger log = Global.getLogger(SovereignKnowledge.class);
        for (MarketAPI market : Misc.getFactionMarkets(faction)) {
            if (market == null) {
                continue;
            }
            Level level = levelOf(market);
            try {
                if (level == Level.FULL) {
                    s.value += patrolLive(market) + stationLive(market);
                    s.exact++;
                } else if (level == Level.INVESTIGATED) {
                    Map<String, Object> row = SovereignState.snapshot(market.getId());
                    s.value += asFloat(row.get("patrol")) + asFloat(row.get("station"));
                    s.exact++;
                } else if (level == Level.ESTIMATED) {
                    s.value += estimatedPatrol(market) + estimatedStation(market);
                    s.guessed++;
                } else {
                    s.hidden++;
                }
            } catch (Exception ex) {
                log.warn("Strength skipped for " + market.getName(), ex);
                s.hidden++;
            }
        }
        return s;
    }

    public static List<FactionAPI> warsOf(FactionAPI faction) {
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        if (faction == null || DiplomacyManager.getManager() == null) {
            return out;
        }
        try {
            List<String> ids = DiplomacyManager.getFactionsAtWarWithFaction(faction, true, true, false);
            if (ids != null) {
                for (String id : ids) {
                    FactionAPI other = Global.getSector().getFaction(id);
                    if (other != null) {
                        out.add(other);
                    }
                }
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignKnowledge.class).warn("War list failed", ex);
        }
        return out;
    }

    public static Alliance allianceOf(FactionAPI faction) {
        if (faction == null || AllianceManager.getManager() == null) {
            return null;
        }
        try {
            return AllianceManager.getFactionAlliance(faction.getId());
        } catch (Exception ex) {
            Global.getLogger(SovereignKnowledge.class).warn("Alliance lookup failed", ex);
            return null;
        }
    }

    public static List<FactionAPI> alliesOf(FactionAPI faction) {
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        Alliance alliance = allianceOf(faction);
        if (alliance == null) {
            return out;
        }
        for (String id : alliance.getMembersCopy()) {
            if (id.equals(faction.getId())) {
                continue;
            }
            FactionAPI other = Global.getSector().getFaction(id);
            if (other != null) {
                out.add(other);
            }
        }
        return out;
    }

    public static float weariness(FactionAPI faction) {
        if (faction == null || DiplomacyManager.getManager() == null) {
            return 0f;
        }
        try {
            return DiplomacyManager.getWarWeariness(faction.getId());
        } catch (Exception ex) {
            Global.getLogger(SovereignKnowledge.class).warn("War weariness failed", ex);
            return 0f;
        }
    }

    public static String relationWords(FactionAPI faction) {
        if (faction == null || Global.getSector().getPlayerFaction() == null) {
            return "Unknown";
        }
        RepLevel level = Global.getSector().getPlayerFaction().getRelationshipLevel(faction);
        return level == null ? "Unknown" : level.getDisplayName();
    }

    public static int relationPoints(FactionAPI faction) {
        if (faction == null || Global.getSector().getPlayerFaction() == null) {
            return 0;
        }
        return Math.round(Global.getSector().getPlayerFaction().getRelationship(faction.getId()) * 100f);
    }

    public static float patrolLive(MarketAPI market) {
        return InvasionFleetManager.estimatePatrolStrength(market, 0f);
    }

    public static float stationLive(MarketAPI market) {
        return InvasionFleetManager.estimateStationStrength(market);
    }

    private static float industryIncome(MarketAPI market) {
        float total = 0f;
        if (market.getIndustries() == null) {
            return total;
        }
        for (Industry industry : market.getIndustries()) {
            if (industry != null && industry.getIncome() != null) {
                total += industry.getIncome().getModifiedValue();
            }
        }
        return total;
    }

    private static String industryLine(MarketAPI market) {
        List<String> names = new ArrayList<String>();
        if (market.getIndustries() != null) {
            for (Industry industry : market.getIndustries()) {
                if (industry == null) {
                    continue;
                }
                String name = industry.getCurrentName();
                if (name == null || name.isEmpty()) {
                    name = industry.getId();
                }
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names.isEmpty() ? "None" : join(names);
    }

    private static String surveyedConditions(MarketAPI market) {
        List<String> names = new ArrayList<String>();
        boolean surveyed = market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL
                || market.getSurveyLevel() == MarketAPI.SurveyLevel.PRELIMINARY;
        if (market.getConditions() != null) {
            for (MarketConditionAPI condition : market.getConditions()) {
                if (condition != null && condition.getName() != null && (surveyed || condition.isSurveyed())) {
                    names.add(condition.getName());
                }
            }
        }
        return names.isEmpty() ? "None known" : join(names);
    }

    private static String allConditions(MarketAPI market) {
        List<String> names = new ArrayList<String>();
        if (market.getConditions() != null) {
            for (MarketConditionAPI condition : market.getConditions()) {
                if (condition != null && condition.getName() != null) {
                    names.add(condition.getName());
                }
            }
        }
        return names.isEmpty() ? "None" : join(names);
    }

    public static String join(List<String> parts) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(parts.get(i));
        }
        return text.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String text(Map<String, Object> row, String key) {
        return row == null || row.get(key) == null ? "?" : String.valueOf(row.get(key));
    }

    private static float asFloat(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    public static String credits(float value) {
        return Misc.getDGSCredits(value);
    }
}
