package io.github.f4iyy.ubc780xlt.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable content of one channel; used for snapshots, undo, the clipboard and the XLSX file. */
public record ChannelData(
        BigDecimal frequency,
        Tone tone,
        String tag,
        boolean att,
        boolean lockout,
        boolean beep,
        boolean rec,
        boolean trunk,
        boolean priority,
        Delay delay,
        Modulation modulation,
        Step step) {

    public ChannelData {
        frequency = frequency == null
                ? BigDecimal.ZERO.setScale(4)
                : frequency.setScale(4, RoundingMode.HALF_UP);
        tone = tone == null ? Tone.OFF : tone;
        tag = tag == null ? "" : tag;
        delay = delay == null ? Delay.NONE : delay;
        modulation = modulation == null ? Modulation.AUTO : modulation;
        step = step == null ? Step.AUTO : step;
    }

    /** An unused channel: no frequency, locked out (like a factory-fresh scanner). */
    public static ChannelData empty() {
        return new ChannelData(null, null, null, false, true, false, false, false, false, null, null, null);
    }

    /** A channel with no frequency is unused. */
    public boolean isEmpty() {
        return frequency.signum() == 0;
    }

    public ChannelData withFrequency(BigDecimal f) {
        return new ChannelData(f, tone, tag, att, lockout, beep, rec, trunk, priority, delay, modulation, step);
    }

    public ChannelData withTag(String t) {
        return new ChannelData(frequency, tone, t, att, lockout, beep, rec, trunk, priority, delay, modulation, step);
    }

    public ChannelData withTone(Tone t) {
        return new ChannelData(frequency, t, tag, att, lockout, beep, rec, trunk, priority, delay, modulation, step);
    }

    public ChannelData withAtt(boolean v) {
        return new ChannelData(frequency, tone, tag, v, lockout, beep, rec, trunk, priority, delay, modulation, step);
    }

    public ChannelData withLockout(boolean v) {
        return new ChannelData(frequency, tone, tag, att, v, beep, rec, trunk, priority, delay, modulation, step);
    }

    public ChannelData withBeep(boolean v) {
        return new ChannelData(frequency, tone, tag, att, lockout, v, rec, trunk, priority, delay, modulation, step);
    }

    public ChannelData withRec(boolean v) {
        return new ChannelData(frequency, tone, tag, att, lockout, beep, v, trunk, priority, delay, modulation, step);
    }

    public ChannelData withTrunk(boolean v) {
        return new ChannelData(frequency, tone, tag, att, lockout, beep, rec, v, priority, delay, modulation, step);
    }

    public ChannelData withDelay(Delay v) {
        return new ChannelData(frequency, tone, tag, att, lockout, beep, rec, trunk, priority, v, modulation, step);
    }

    public ChannelData withModulation(Modulation v) {
        return new ChannelData(frequency, tone, tag, att, lockout, beep, rec, trunk, priority, delay, v, step);
    }

    public ChannelData withStep(Step v) {
        return new ChannelData(frequency, tone, tag, att, lockout, beep, rec, trunk, priority, delay, modulation, v);
    }

    public ChannelData withPriority(boolean p) {
        return new ChannelData(frequency, tone, tag, att, lockout, beep, rec, trunk, p, delay, modulation, step);
    }

    private static String flag(boolean b) {
        return b ? "1" : "0";
    }

    /** One tab-separated line, for the clipboard. */
    public String toTsv() {
        return String.join("\t",
                frequency.toPlainString(), tone.label(), tag, flag(att), flag(lockout), flag(beep), flag(rec),
                flag(trunk), flag(priority), delay.label(), modulation.name(), step.command());
    }

    /**
     * Reads a line written by {@link #toTsv()}.
     *
     * @throws IllegalArgumentException if the line is not a channel
     */
    public static ChannelData fromTsv(String line) {
        String[] f = line.split("\t", -1);
        if (f.length != 12) {
            throw new IllegalArgumentException("Expected 12 columns, got " + f.length);
        }
        BigDecimal freq;
        try {
            freq = new BigDecimal(f[0].strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad frequency: " + f[0]);
        }
        Tone tone = Tone.parse(f[1]).orElseThrow(() -> new IllegalArgumentException("Bad tone: " + f[1]));
        Delay delay = require(Delay.parse(f[9]), "delay", f[9]);
        Modulation mod = require(Modulation.parse(f[10]), "modulation", f[10]);
        Step step = require(Step.parse(f[11]), "step", f[11]);
        return new ChannelData(freq, tone, f[2], f[3].equals("1"), f[4].equals("1"), f[5].equals("1"),
                f[6].equals("1"), f[7].equals("1"), f[8].equals("1"), delay, mod, step);
    }

    private static <T> T require(T value, String what, String text) {
        if (value == null) {
            throw new IllegalArgumentException("Bad " + what + ": " + text);
        }
        return value;
    }
}
