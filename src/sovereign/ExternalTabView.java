package sovereign;

import com.fs.starfarer.api.ui.CustomPanelAPI;

final class ExternalTabView extends TabView {

    private final ExternalTab page;

    ExternalTabView(SovereignWindow window, ExternalTab page) {
        super(window);
        this.page = page;
    }

    @Override
    void build(CustomPanelAPI body, float bw, float bh) {
        page.build(body, bw, bh);
    }

    // the page runs its own buttons, so nothing from the window's queue is meant for it
    @Override
    boolean handle(String id) {
        return false;
    }

    boolean tick() {
        return page.advance();
    }

    void enter() {
        page.enter();
    }
}
