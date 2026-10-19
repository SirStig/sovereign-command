package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SovereignDialog implements InteractionDialogPlugin {

    private static final String LEAVE = "sovereign_leave";

    private static boolean pendingOpen;
    private static StarSystemAPI pendingSystem;
    private static final List<Runnable> afterClose = new ArrayList<Runnable>();

    private final StarSystemAPI focus;
    private final Map<String, MemoryAPI> memory = new HashMap<String, MemoryAPI>();

    public SovereignDialog(StarSystemAPI focus) {
        this.focus = focus;
    }

    public static void openNow(StarSystemAPI system) {
        Global.getLogger(SovereignDialog.class).info("Opening Sovereign Command");
        pendingOpen = false;
        pendingSystem = null;
        if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) {
            return;
        }
        if (Global.getSector().getCampaignUI().isShowingDialog()) {
            return;
        }
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null) {
            Global.getSector().getCampaignUI().showInteractionDialog(new SovereignDialog(system), player);
        }
    }

    public static void requestOpen(StarSystemAPI system) {
        pendingOpen = true;
        pendingSystem = system;
    }

    public static void later(Runnable action) {
        afterClose.add(action);
    }

    public static void flushPending() {
        if (pendingOpen) {
            openNow(pendingSystem);
        }
        if (!afterClose.isEmpty() && !Global.getSector().getCampaignUI().isShowingDialog()) {
            List<Runnable> run = new ArrayList<Runnable>(afterClose);
            afterClose.clear();
            for (Runnable r : run) {
                try {
                    r.run();
                } catch (Exception ex) {
                    Global.getLogger(SovereignDialog.class).error("Follow-up action failed", ex);
                }
            }
        }
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        try {
            dialog.hideTextPanel();
            dialog.hideVisualPanel();
            dialog.getOptionPanel().clearOptions();
            dialog.getOptionPanel().addOption("Leave", LEAVE);
            dialog.setOptionOnEscape("Leave", LEAVE);
            SovereignWindow window = new SovereignWindow(dialog, focus);
            dialog.showCustomVisualDialog(window.width, window.height, window);
        } catch (Exception ex) {
            Global.getLogger(SovereignDialog.class).error("Could not open Sovereign Command", ex);
            dialog.dismiss();
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (LEAVE.equals(optionData)) {
            Global.getSector().getCampaignUI().getCurrentInteractionDialog().dismiss();
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return memory;
    }
}
