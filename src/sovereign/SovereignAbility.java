package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

// zero-length ability, so the button just opens the window
public class SovereignAbility extends BaseDurationAbility {

    @Override
    protected void activateImpl() {
        SovereignDialog.openNow(null);
    }

    @Override
    protected void applyEffect(float amount, float level) {
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    @Override
    public boolean isUsable() {
        if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) {
            return false;
        }
        if (Global.getSector().getCampaignUI().isShowingDialog()) {
            return false;
        }
        if (Global.getSector().getPlayerFleet() == null) {
            return false;
        }
        return super.isUsable();
    }

    @Override
    public boolean hasTooltip() {
        return true;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addPara("Opens the command window.", 0f);
        tooltip.addPara("Key: %s", 4f, com.fs.starfarer.api.util.Misc.getHighlightColor(),
                org.lwjgl.input.Keyboard.getKeyName(SovereignSettings.hotkey()));
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
    }

    @Override
    public float getTooltipWidth() {
        return 260f;
    }
}
