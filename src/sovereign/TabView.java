package sovereign;

import com.fs.starfarer.api.ui.CustomPanelAPI;

abstract class TabView {

    protected final SovereignWindow w;

    TabView(SovereignWindow window) {
        this.w = window;
    }

    abstract void build(CustomPanelAPI body, float bw, float bh);

    abstract boolean handle(String id);
}
