package sovereign;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class SovereignSettings {

    private static final String KEY = "sovereigncommand_settings";
    private static final String PREFS = "sovereigncommand_prefs.json";

    private static Integer hotkeyOverride;

    private SovereignSettings() {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map() {
        Object raw = Global.getSector().getPersistentData().get(KEY);
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        Map<String, Object> created = new HashMap<String, Object>();
        Global.getSector().getPersistentData().put(KEY, created);
        return created;
    }

    private static boolean flag(String key, boolean fallback) {
        Object v = map().get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static float number(String key, float fallback) {
        Object v = map().get(key);
        return v instanceof Number ? ((Number) v).floatValue() : fallback;
    }

    public static boolean closedBorders() {
        return flag("borders", SovereignConfig.get().closedBorders);
    }

    public static void setClosedBorders(boolean on) {
        map().put("borders", on);
    }

    public static boolean fog() {
        return flag("fog", SovereignConfig.get().fog);
    }

    public static void setFog(boolean on) {
        map().put("fog", on);
    }

    public static boolean borderNotices() {
        return flag("notices", SovereignConfig.get().borderNotices);
    }

    public static void setBorderNotices(boolean on) {
        map().put("notices", on);
    }

    public static boolean warOnForcedEntry() {
        return flag("warOnForced", SovereignConfig.get().warOnForcedEntry);
    }

    public static void setWarOnForcedEntry(boolean on) {
        map().put("warOnForced", on);
    }

    public static float forceRatio() {
        return number("forceRatio", SovereignConfig.get().forceRatio);
    }

    public static void setForceRatio(float value) {
        map().put("forceRatio", Math.max(0.25f, Math.min(1.5f, value)));
    }

    public static float relationPenalty() {
        return number("penalty", SovereignConfig.get().relationPenalty);
    }

    public static void setRelationPenalty(float value) {
        map().put("penalty", Math.max(0f, Math.min(0.5f, value)));
    }

    public static int investigateCost() {
        return Math.round(number("investigate", SovereignConfig.get().investigateCost));
    }

    public static void setInvestigateCost(int value) {
        map().put("investigate", (float) Math.max(0, Math.min(200000, value)));
    }

    // who gets ordered out: hostile, outlaws, poor, others
    public static boolean bars(String group) {
        return flag("bar_" + group, "hostile".equals(group) || "outlaw".equals(group) || "poor".equals(group));
    }

    public static void setBars(String group, boolean on) {
        map().put("bar_" + group, on);
    }

    // 0 systems you hold outright, 1 any system with a colony of yours or an ally's
    public static int scope() {
        return Math.round(number("scope", 0f));
    }

    public static void setScope(int value) {
        map().put("scope", (float) (value % 2));
    }

    // 0 lax, 1 standard, 2 strict
    public static int enforcement() {
        return Math.round(number("enforcement", 1f));
    }

    public static void setEnforcement(int value) {
        map().put("enforcement", (float) (value % 3));
    }

    public static boolean customs() {
        return flag("customs", true);
    }

    public static void setCustoms(boolean on) {
        map().put("customs", on);
    }

    // 0 turn back, 1 fine, 2 seize
    public static int customsPenalty() {
        return Math.round(number("customsPenalty", 1f));
    }

    public static void setCustomsPenalty(int value) {
        map().put("customsPenalty", (float) (value % 3));
    }

    // 0 follow the general rule, 1 always patrolled, 2 never patrolled
    public static int systemRule(String systemId) {
        return Math.round(number("sys_" + systemId, 0f));
    }

    public static void setSystemRule(String systemId, int value) {
        map().put("sys_" + systemId, (float) (value % 3));
    }

    // optional modules, all off until the player picks them in setup
    public static boolean diplomacy() {
        return flag("mod_diplomacy", false);
    }

    public static void setDiplomacy(boolean on) {
        map().put("mod_diplomacy", on);
    }

    public static boolean laws() {
        return flag("mod_laws", false);
    }

    public static void setLaws(boolean on) {
        map().put("mod_laws", on);
    }

    public static boolean setupDone() {
        return flag("setup_done", false);
    }

    public static void setSetupDone(boolean done) {
        map().put("setup_done", done);
    }

    public static void reset() {
        map().clear();
    }

    public static int hotkey() {
        if (hotkeyOverride == null) {
            hotkeyOverride = SovereignConfig.get().hotkey;
            try {
                if (Global.getSettings().fileExistsInCommon(PREFS)) {
                    JSONObject json = Global.getSettings().readJSONFromCommon(PREFS, false);
                    hotkeyOverride = json.optInt("hotkey", hotkeyOverride);
                }
            } catch (Exception ex) {
                Global.getLogger(SovereignSettings.class).warn("Could not read saved hotkey", ex);
            }
        }
        return hotkeyOverride;
    }

    public static void setHotkey(int key) {
        hotkeyOverride = key;
        Logger log = Global.getLogger(SovereignSettings.class);
        try {
            JSONObject json = new JSONObject();
            json.put("hotkey", key);
            Global.getSettings().writeJSONToCommon(PREFS, json, false);
        } catch (Exception ex) {
            log.warn("Could not save hotkey", ex);
        }
    }
}
