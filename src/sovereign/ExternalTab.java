package sovereign;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.CustomPanelAPI;

/**
 * A page another mod adds to the command window. The mod keeps its own widgets and button handling;
 * the window only gives it a panel to draw into and calls advance() every frame while the page is showing.
 */
public interface ExternalTab {

    String title();

    void build(CustomPanelAPI body, float width, float height);

    /** Runs every frame while the page is open. Return true to have the window redraw it. */
    boolean advance();

    /** Esc was pressed. Return true if the page used it (to close something of its own) so the window stays open. */
    boolean back();

    /** Enter was pressed while the page is open. */
    void enter();

    /** The entity the window should be opened against, or null for the player's fleet. */
    SectorEntityToken preferredTarget(CampaignFleetAPI player);
}
