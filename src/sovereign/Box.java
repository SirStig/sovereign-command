package sovereign;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;

final class Box extends BaseCustomUIPanelPlugin {

    private final SovereignWindow window;
    private final Color fill;
    private final float fillAlpha;
    private final Color line;
    private final float lineAlpha;
    private PositionAPI pos;

    Box(SovereignWindow window) {
        this(window, null, 0f, null, 0f);
    }

    Box(SovereignWindow window, Color fill, float fillAlpha, Color line, float lineAlpha) {
        this.window = window;
        this.fill = fill;
        this.fillAlpha = fillAlpha;
        this.line = line;
        this.lineAlpha = lineAlpha;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.pos = position;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (pos == null) {
            return;
        }
        if (fill != null) {
            Theme.fill(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), fill, fillAlpha * alphaMult);
        }
        if (line != null) {
            Theme.outline(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), line, lineAlpha * alphaMult);
        }
    }

    @Override
    public void buttonPressed(Object buttonId) {
        window.press(buttonId);
    }
}
