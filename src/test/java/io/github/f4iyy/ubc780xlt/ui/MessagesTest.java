package io.github.f4iyy.ubc780xlt.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void englishAndFrenchHaveTheSameKeys() {
        var en = new TreeSet<>(Collections.list(Messages.of("en").bundle().getKeys()));
        var fr = new TreeSet<>(Collections.list(Messages.of("fr").bundle().getKeys()));
        assertEquals(en, fr);
        assertFalse(en.isEmpty());
    }

    @Test
    void languageSelectsTheBundle() {
        assertEquals("Disconnected", Messages.of("en").get("state.disconnected"));
        assertEquals("Déconnecté", Messages.of("fr").get("state.disconnected"));
        assertEquals("Disconnected", Messages.of("de").get("state.disconnected"));
    }

    @Test
    void argumentsAreFormatted() {
        assertEquals("Scanner found on COM3 at 9600 baud", Messages.of("en").get("status.found", "COM3", "9600"));
        assertTrue(Messages.of("fr").get("status.noPort").contains("d'abord"));
    }

    @Test
    void missingKeyIsVisible() {
        assertEquals("!nope!", Messages.of("en").get("nope"));
    }

    @Test
    void everyMessageFormatsInBothLanguages() {
        for (String lang : new String[] {"en", "fr"}) {
            Messages m = Messages.of(lang);
            for (String key : Collections.list(m.bundle().getKeys())) {
                String text = m.get(key, "a", "b", "c");
                assertFalse(text.isBlank(), key);
                assertFalse(text.startsWith("!"), key);
            }
        }
    }
}
