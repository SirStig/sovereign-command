package sovereign;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI;

public class SovereignModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        SovereignConfig.reload();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SovereignConfig.reload();
        SovereignCampaignScript.install();
        SovereignInput.install();
        SovereignLedger.install();
        SovereignIntel.ensure();
        slotAbility();
        if (Global.getSector().getPlayerFleet() != null
                && !Global.getSector().getPlayerFleet().hasAbility("sovereign_command")) {
            Global.getSector().getPlayerFleet().addAbility("sovereign_command");
            Global.getSector().getCharacterData().addAbility("sovereign_command");
        }
        Global.getLogger(SovereignModPlugin.class).info("Sovereign Command loaded, ability="
                + (Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().hasAbility("sovereign_command"))
                + ", intel=" + Global.getSector().getIntelManager().hasIntelOfClass(SovereignIntel.class)
                + ", hotkey=" + SovereignSettings.hotkey());
    }

    private void slotAbility() {
        PersistentUIDataAPI.AbilitySlotsAPI slots = Global.getSector().getUIData().getAbilitySlotsAPI();
        int original = slots.getCurrBarIndex();
        boolean placed = false;
        for (int bar = 0; bar < 5 && !placed; bar++) {
            slots.setCurrBarIndex(bar);
            for (PersistentUIDataAPI.AbilitySlotAPI slot : slots.getCurrSlotsCopy()) {
                if ("sovereign_command".equals(slot.getAbilityId())) {
                    placed = true;
                    break;
                }
            }
        }
        for (int bar = 0; bar < 5 && !placed; bar++) {
            slots.setCurrBarIndex(bar);
            for (PersistentUIDataAPI.AbilitySlotAPI slot : slots.getCurrSlotsCopy()) {
                if (slot.getAbilityId() == null) {
                    slot.setAbilityId("sovereign_command");
                    placed = true;
                    break;
                }
            }
        }
        slots.setCurrBarIndex(original);
        Global.getLogger(SovereignModPlugin.class).info("Sovereign Command ability slotted: " + placed);
    }

    @Override
    public void onNewGame() {
        SovereignConfig.reload();
    }
}
