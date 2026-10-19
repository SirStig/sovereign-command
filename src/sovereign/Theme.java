package sovereign;

import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

final class Theme {

    static final Color WINDOW = new Color(8, 11, 16);
    static final Color CARD = new Color(15, 21, 29);
    static final Color ROW = new Color(22, 30, 40);

    private Theme() {
    }

    static Color base() {
        return Misc.getBasePlayerColor();
    }

    static Color dark() {
        return Misc.getDarkPlayerColor();
    }

    static Color bright() {
        return Misc.getBrightPlayerColor();
    }

    static Color text() {
        return Misc.getTextColor();
    }

    static Color dim() {
        return Misc.getGrayColor();
    }

    static Color good() {
        return Misc.getPositiveHighlightColor();
    }

    static Color bad() {
        return Misc.getNegativeHighlightColor();
    }

    static Color hl() {
        return Misc.getHighlightColor();
    }

    static Color relation(float rel) {
        return Misc.getRelColor(rel);
    }

    static void fill(float x, float y, float w, float h, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    static void outline(float x, float y, float w, float h, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x + 0.5f, y + 0.5f);
        GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
        GL11.glVertex2f(x + w - 0.5f, y + h - 0.5f);
        GL11.glVertex2f(x + 0.5f, y + h - 0.5f);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
