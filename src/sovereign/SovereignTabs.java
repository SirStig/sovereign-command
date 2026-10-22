package sovereign;

// Where other mods hand over a page for the command window. One slot for now: a later registration replaces the earlier.
public final class SovereignTabs {

    private static ExternalTab tab;

    private SovereignTabs() {
    }

    public static void register(ExternalTab page) {
        tab = page;
    }

    static ExternalTab current() {
        return tab;
    }
}
