package io.github.f4iyy.ubc780xlt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppInfoTest {

    @Test
    void versionFallsBackToDevWhenNotPackaged() {
        assertEquals("dev", AppInfo.version());
    }

    @Test
    void versionOfHandlesNullBlankAndRealValues() {
        assertEquals("dev", AppInfo.versionOf(null));
        assertEquals("dev", AppInfo.versionOf("  "));
        assertEquals("1.2.3", AppInfo.versionOf("1.2.3"));
    }

    @Test
    void nameIsNotBlank() {
        assertFalse(AppInfo.NAME.isBlank());
    }

}
