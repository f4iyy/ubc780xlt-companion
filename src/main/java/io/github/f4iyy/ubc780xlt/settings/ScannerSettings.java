package io.github.f4iyy.ubc780xlt.settings;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * The scanner settings that apply to the whole scanner. The volume and squelch knobs are not remote controlled,
 * and delay and attenuator belong to each channel (the scanner refuses them outside manual mode).
 *
 * @param backlight display backlight ({@code LT})
 * @param beep key beep and beep alert ({@code BP})
 * @param priority priority scanning ({@code PR})
 * @param dataSkip skip channels that carry data ({@code DS})
 * @param mute speaker muting ({@code MU})
 * @param editLock keypad edit lock ({@code EL})
 * @param screenMask hide part of the display ({@code LM})
 * @param ctcss CTCSS/DCS function ({@code CT})
 * @param scanBanks scan banks that are scanned, 1 to 10 ({@code SB})
 * @param searchBanks search banks used by the chain search, 1 to 10 ({@code CB})
 * @param skipList frequencies skipped during searches, in MHz ({@code SS})
 */
public record ScannerSettings(
        Backlight backlight,
        boolean beep,
        boolean priority,
        boolean dataSkip,
        MuteMode mute,
        boolean editLock,
        boolean screenMask,
        CtcssFunction ctcss,
        Set<Integer> scanBanks,
        Set<Integer> searchBanks,
        List<BigDecimal> skipList) {

    /** Number of frequencies the skip memory can hold. */
    public static final int SKIP_CAPACITY = 200;

    /** {@code LT}: N normal, D dim, F off. */
    public enum Backlight {
        NORMAL('N'),
        DIM('D'),
        OFF('F');

        private final char code;

        Backlight(char code) {
            this.code = code;
        }

        public char code() {
            return code;
        }

        /** The value for a code; {@code null} when unknown. */
        public static Backlight of(char code) {
            for (Backlight v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            return null;
        }
    }

    /** {@code MU}: A automatic, N noise (squelch open), F silent (squelch closed). */
    public enum MuteMode {
        AUTO('A'),
        OPEN('N'),
        CLOSED('F');

        private final char code;

        MuteMode(char code) {
            this.code = code;
        }

        public char code() {
            return code;
        }

        /** The value for a code; {@code null} when unknown. */
        public static MuteMode of(char code) {
            for (MuteMode v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            return null;
        }
    }

    /** {@code CT}: N on, F off, S search. */
    public enum CtcssFunction {
        OFF('F'),
        ON('N'),
        SEARCH('S');

        private final char code;

        CtcssFunction(char code) {
            this.code = code;
        }

        public char code() {
            return code;
        }

        /** The value for a code; {@code null} when unknown. */
        public static CtcssFunction of(char code) {
            for (CtcssFunction v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            return null;
        }
    }
}
