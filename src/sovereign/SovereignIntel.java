package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

public class SovereignIntel extends BaseIntelPlugin {

    public static final String BUTTON_OPEN = "sovereign_open";

    public static void ensure() {
        if (Global.getSector().getIntelManager().hasIntelOfClass(SovereignIntel.class)) {
            SovereignState.data().put(SovereignState.KEY_INTEL, Boolean.TRUE);
            return;
        }
        Global.getSector().getIntelManager().addIntel(new SovereignIntel(), false);
        SovereignState.data().put(SovereignState.KEY_INTEL, Boolean.TRUE);
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        info.addPara("Factions, fleets, borders.", 3f);
    }

    @Override
    protected String getName() {
        return "Sovereign Command";
    }

    @Override
    public String getSmallDescriptionTitle() {
        return "Sovereign Command";
    }

    @Override
    public boolean hasSmallDescription() {
        return true;
    }

    @Override
    public boolean hasLargeDescription() {
        return false;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float pad = 8f;
        info.addPara("Closed borders: %s", pad, Misc.getHighlightColor(),
                SovereignSettings.closedBorders() ? "on" : "off");
        addGenericButton(info, width, "Open", BUTTON_OPEN);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (!BUTTON_OPEN.equals(buttonId) || ui == null) {
            return;
        }
        SectorEntityToken player = Global.getSector().getPlayerFleet();
        ui.showDialog(player, new SovereignDialog(null));
    }

    @Override
    public String getIcon() {
        return "graphics/icons/abilities/sensor_burst.png";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new HashSet<String>();
        tags.add(Tags.INTEL_MILITARY);
        return tags;
    }

    @Override
    public IntelInfoPlugin.IntelSortTier getSortTier() {
        return IntelInfoPlugin.IntelSortTier.TIER_3;
    }

    @Override
    public String getSortString() {
        return "Sovereign Command";
    }

    @Override
    public Color getTitleColor(IntelInfoPlugin.ListInfoMode mode) {
        return Global.getSector().getPlayerFaction().getBaseUIColor();
    }

    @Override
    public boolean isHidden() {
        return false;
    }
}
