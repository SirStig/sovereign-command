package sovereign;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.lwjgl.input.Keyboard;

public final class SovereignConfig {

    public final int hotkey;
    public final boolean fog;
    public final boolean closedBorders;
    public final boolean borderNotices;
    public final int investigateCost;
    public final float forceRatio;
    public final float relationPenalty;
    public final boolean warOnForcedEntry;
    public final float emptyDefensePerMarketSize;

    private static SovereignConfig current = defaults();

    private SovereignConfig(int hotkey, boolean fog, boolean closedBorders, boolean borderNotices,
                            int investigateCost, float forceRatio, float relationPenalty,
                            boolean warOnForcedEntry, float emptyDefensePerMarketSize) {
        this.hotkey = hotkey;
        this.fog = fog;
        this.closedBorders = closedBorders;
        this.borderNotices = borderNotices;
        this.investigateCost = investigateCost;
        this.forceRatio = forceRatio;
        this.relationPenalty = relationPenalty;
        this.warOnForcedEntry = warOnForcedEntry;
        this.emptyDefensePerMarketSize = emptyDefensePerMarketSize;
    }

    public static SovereignConfig get() {
        return current;
    }

    public static void reload() {
        current = load();
    }

    private static SovereignConfig defaults() {
        return new SovereignConfig(Keyboard.KEY_F8, false, true, true, 15000, 0.75f, 0.10f, false, 30f);
    }

    private static SovereignConfig load() {
        SovereignConfig d = defaults();
        Logger log = Global.getLogger(SovereignConfig.class);
        try {
            JSONObject json = Global.getSettings().loadJSON("data/config/sovereign_config.json", "sovereigncommand");
            if (json == null) {
                return d;
            }
            return new SovereignConfig(
                    json.optInt("hotkey", d.hotkey),
                    json.optBoolean("fogOfWar", d.fog),
                    json.optBoolean("closedBorders", d.closedBorders),
                    json.optBoolean("borderNotices", d.borderNotices),
                    json.optInt("investigateCost", d.investigateCost),
                    (float) json.optDouble("forceRatio", d.forceRatio),
                    (float) json.optDouble("relationPenalty", d.relationPenalty),
                    json.optBoolean("warOnForcedEntry", d.warOnForcedEntry),
                    (float) json.optDouble("emptyDefensePerMarketSize", d.emptyDefensePerMarketSize));
        } catch (Exception ex) {
            log.warn("sovereign_config.json unreadable, using defaults", ex);
            return d;
        }
    }
}
