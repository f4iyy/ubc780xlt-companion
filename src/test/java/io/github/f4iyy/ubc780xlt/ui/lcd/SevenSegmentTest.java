package io.github.f4iyy.ubc780xlt.ui.lcd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SevenSegmentTest {

    @Test
    void digitsLightTheRightNumberOfSegments() {
        int[] expected = {6, 2, 5, 5, 4, 5, 6, 3, 7, 6};
        for (int d = 0; d <= 9; d++) {
            assertEquals(expected[d], SevenSegment.count(SevenSegment.segmentsFor((char) ('0' + d))), "digit " + d);
        }
    }

    @Test
    void knownShapes() {
        assertEquals(SevenSegment.B | SevenSegment.C, SevenSegment.segmentsFor('1'));
        assertEquals(SevenSegment.G, SevenSegment.segmentsFor('-'));
        assertEquals(SevenSegment.D, SevenSegment.segmentsFor('_'));
        assertEquals(127, SevenSegment.segmentsFor('8'));
    }

    @Test
    void lettersAreCaseInsensitive() {
        for (char c : "abcdefhlnoprtu".toCharArray()) {
            assertEquals(SevenSegment.segmentsFor(c), SevenSegment.segmentsFor(Character.toUpperCase(c)));
            assertEquals(true, SevenSegment.segmentsFor(c) != 0, "letter " + c);
        }
    }

    @Test
    void blanksAndUnknownCharactersAreDark() {
        assertEquals(0, SevenSegment.segmentsFor(' '));
        assertEquals(0, SevenSegment.segmentsFor('#'));
        assertEquals(0, SevenSegment.segmentsFor('.'));
    }
}
