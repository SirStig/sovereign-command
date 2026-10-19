package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

// Hotkey opens the window; right-clicking a star in hyperspace opens it on that system.
public class SovereignInput implements CampaignInputListener {

    private static final float STAR_PICK_PIXELS = 40f;

    public static void install() {
        Global.getSector().getListenerManager().removeListenerOfClass(SovereignInput.class);
        Global.getSector().getListenerManager().addListener(new SovereignInput(), false);
    }

    @Override
    public int getListenerInputPriority() {
        return 1000;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (events == null || Global.getSector() == null) {
            return;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui == null) {
            return;
        }
        boolean blocked = ui.isShowingDialog() || ui.getCurrentCoreTab() != null || ui.isShowingMenu();
        int hotkey = SovereignSettings.hotkey();
        for (InputEventAPI event : events) {
            if (event == null || event.isConsumed() || event.isRepeat()) {
                continue;
            }
            if (event.isKeyDownEvent() && event.getEventValue() == hotkey) {
                if (blocked) {
                    continue;
                }
                event.consume();
                SovereignDialog.requestOpen(null);
                return;
            }
            if (!blocked && event.isRMBDownEvent()) {
                trySystemClick(event);
            }
        }
    }

    private void trySystemClick(InputEventAPI event) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !player.isInHyperspace()) {
            return;
        }
        ViewportAPI view = Global.getSector().getViewport();
        if (view == null) {
            return;
        }
        StarSystemAPI best = null;
        float bestDist = STAR_PICK_PIXELS;
        try {
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (system == null || system.getLocation() == null) {
                    continue;
                }
                float sx = view.convertWorldXtoScreenX(system.getLocation().x);
                float sy = view.convertWorldYtoScreenY(system.getLocation().y);
                float dx = sx - event.getX();
                float dy = sy - event.getY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= bestDist) {
                    bestDist = dist;
                    best = system;
                }
            }
        } catch (Exception ex) {
            Global.getLogger(SovereignInput.class).warn("Hyperspace pick failed", ex);
            return;
        }
        if (best == null) {
            return;
        }
        event.consume();
        SovereignDialog.requestOpen(best);
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
    }
}
