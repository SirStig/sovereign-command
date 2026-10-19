package sovereign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class SovereignCampaignScript implements EveryFrameScript {

    private float accumulator;
    private boolean keyWasDown;
    private boolean announced;

    public static void install() {
        Global.getSector().removeScriptsOfClass(SovereignCampaignScript.class);
        Global.getSector().removeTransientScriptsOfClass(SovereignCampaignScript.class);
        Global.getSector().addTransientScript(new SovereignCampaignScript());
    }

    private void checkHotkey() {
        boolean down = Keyboard.isKeyDown(SovereignSettings.hotkey());
        boolean pressed = down && !keyWasDown;
        keyWasDown = down;
        if (!pressed) {
            return;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui != null && !ui.isShowingDialog() && ui.getCurrentCoreTab() == null && !ui.isShowingMenu()) {
            SovereignDialog.requestOpen(null);
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        Logger log = Global.getLogger(SovereignCampaignScript.class);
        try {
            SovereignDialog.flushPending();
        } catch (Exception ex) {
            log.error("Could not open Sovereign Command", ex);
        }
        if (!announced) {
            announced = true;
            log.info("Sovereign Command script running");
        }
        checkHotkey();
        if (amount <= 0f) {
            return;
        }
        accumulator += amount;
        if (accumulator < 1f) {
            return;
        }
        accumulator = 0f;
        try {
            SovereignActions.advanceInvestigations();
            SovereignBorders.tick();
        } catch (Exception ex) {
            log.error("Sovereign campaign tick failed", ex);
        }
    }
}
