package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

public final class SovereignLog {

    public static final String BORDER = "border";
    public static final String DIPLOMACY = "diplomacy";
    public static final String ORDER = "order";
    public static final String INTEL = "intel";

    private static final String KEY = "sovereigncommand_log";
    private static final int MAX = 80;

    private SovereignLog() {
    }

    @SuppressWarnings("unchecked")
    public static List<String[]> entries() {
        Object raw = Global.getSector().getPersistentData().get(KEY);
        if (raw instanceof List) {
            return (List<String[]>) raw;
        }
        List<String[]> created = new ArrayList<String[]>();
        Global.getSector().getPersistentData().put(KEY, created);
        return created;
    }

    public static void add(String kind, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<String[]> list = entries();
        list.add(new String[]{Global.getSector().getClock().getShortDate(), kind, text});
        while (list.size() > MAX) {
            list.remove(0);
        }
    }

    public static void clear() {
        entries().clear();
    }

    public static void announce(String kind, String text) {
        add(kind, text);
        if (Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().addMessage(text, Misc.getHighlightColor());
        }
    }
}
