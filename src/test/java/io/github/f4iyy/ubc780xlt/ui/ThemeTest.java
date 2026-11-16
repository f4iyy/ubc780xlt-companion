package io.github.f4iyy.ubc780xlt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThemeTest {

    @Test
    void fromIdFindsThemesAndDefaultsToMocha() {
        assertEquals(Theme.LATTE, Theme.fromId("latte"));
        assertEquals(Theme.MOCHA, Theme.fromId("mocha"));
        assertEquals(Theme.MOCHA, Theme.fromId("unknown"));
        assertEquals(Theme.MOCHA, Theme.fromId(null));
    }

    @Test
    void everyThemeHasItsPaletteThenTheSharedRules() {
        for (Theme t : Theme.values()) {
            var sheets = t.stylesheets();
            assertEquals(2, sheets.size());
            assertTrue(sheets.get(0).contains("catppuccin-" + t.id()));
            assertTrue(sheets.get(1).endsWith("common.css"));
        }
    }
}
