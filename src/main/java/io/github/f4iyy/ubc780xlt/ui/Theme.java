package io.github.f4iyy.ubc780xlt.ui;

import java.net.URL;
import java.util.List;

/** Catppuccin palettes. */
public enum Theme {
    LATTE("latte", "/ui/catppuccin-latte.css"),
    MOCHA("mocha", "/ui/catppuccin-mocha.css");

    private static final String COMMON = "/ui/common.css";

    private final String id;
    private final String palette;

    Theme(String id, String palette) {
        this.id = id;
        this.palette = palette;
    }

    public String id() {
        return id;
    }

    public static Theme fromId(String id) {
        for (Theme t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return MOCHA;
    }

    /** Stylesheet URLs to apply, in order: palette first, then the shared rules. */
    public List<String> stylesheets() {
        return List.of(url(palette), url(COMMON));
    }

    private static String url(String resource) {
        URL u = Theme.class.getResource(resource);
        if (u == null) {
            throw new IllegalStateException("Missing stylesheet " + resource);
        }
        return u.toExternalForm();
    }
}
