package io.github.f4iyy.ubc780xlt;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Integration test placeholder: proves that Failsafe runs and its coverage is merged with unit tests. */
class AppInfoIT {

    @Test
    void identityIsAvailableFromTheBuiltClasses() {
        assertNotNull(AppInfo.version());
    }
}
