package io.github.f4iyy.ubc780xlt.protocol.sim;

import io.github.f4iyy.ubc780xlt.model.Channel;
import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tone;
import io.github.f4iyy.ubc780xlt.transfer.ScannerFormat;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The memory of the simulated scanner and the commands that read and write it. Like the real scanner, step,
 * modulation, delay, attenuator, recording, lockout, beep alert and tone act on the channel the scanner is on.
 */
final class ScannerMemory {

    private static final class Slot {
        long freq; // hundreds of hertz
        boolean trunk;
        boolean delayOn;
        boolean lockout = true;
        boolean att;
        boolean rec;
        boolean beep;
        int tone;
        boolean toneLock;
        String tag = "";
        Delay delay = Delay.S2;
        Modulation mod = Modulation.AUTO;
        Step step = Step.AUTO;
    }

    private static final Pattern PM_READ = Pattern.compile("PM(\\d{3})");
    private static final Pattern PM_WRITE = Pattern.compile("PM(\\d{3})([T ])(\\d{8})");
    private static final Pattern MA = Pattern.compile("MA(\\d{3})");
    private static final Pattern TA = Pattern.compile("TA ([BSC]) (\\w{1,3})(?: (.*))?");
    private static final Pattern LIMIT_READ = Pattern.compile("L([LU]) ([A-J])");
    private static final Pattern LIMIT_WRITE = Pattern.compile("L([LU])(\\d{8}) ([A-J])");
    private static final Pattern BEEP = Pattern.compile("BA([NF]?) C (\\d{3})");
    private static final Pattern PRIORITY = Pattern.compile("PC ?([A-J])(\\d{3})?");
    private static final Pattern DELAY = Pattern.compile("DL(?:([NF]?)(?: (\\S+))?)?");
    private static final Pattern TONE = Pattern.compile("CS(\\d{3})(L?)");
    private static final Pattern FLAG = Pattern.compile("(AT|AR|LO)([NF])");

    private final Slot[] channels = new Slot[Programming.CHANNELS + 1];
    private final String[] scanTags = new String[Programming.BANKS];
    private final String[] searchTags = new String[Programming.BANKS];
    private final long[] lower = new long[Programming.BANKS];
    private final long[] upper = new long[Programming.BANKS];
    private final int[] priority = new int[Programming.BANKS]; // channel number 1-500, like the scanner
    private int current;
    private Step globalStep = Step.AUTO;
    private Modulation globalMod = Modulation.AUTO;

    ScannerMemory() {
        for (int i = 1; i < channels.length; i++) {
            channels[i] = new Slot();
        }
        Arrays.fill(scanTags, "");
        Arrays.fill(searchTags, "");
        for (int b = 0; b < Programming.BANKS; b++) {
            priority[b] = b * Programming.CHANNELS_PER_BANK + 1;
        }
    }

    /** Fills the memory from a programming (used to prepare a scanner for a test). */
    void load(Programming p) {
        for (Channel c : p.channels()) {
            ChannelData d = c.data();
            Slot s = channels[c.number()];
            s.freq = d.frequency().movePointRight(4).longValueExact();
            s.trunk = d.trunk();
            s.lockout = d.lockout();
            s.att = d.att();
            s.rec = d.rec();
            s.beep = d.beep();
            s.tone = d.tone().code();
            s.toneLock = d.tone().locked();
            s.tag = d.tag();
            s.delayOn = d.delay() != Delay.NONE;
            s.delay = d.delay() == Delay.NONE ? Delay.S2 : d.delay();
            s.mod = d.modulation();
            s.step = d.step();
        }
        for (int b = 0; b < Programming.BANKS; b++) {
            scanTags[b] = p.scanBanks().get(b).data().tag();
            searchTags[b] = p.searchBanks().get(b).data().tag();
            lower[b] = p.searchBanks().get(b).data().lower().movePointRight(4).longValueExact();
            upper[b] = p.searchBanks().get(b).data().upper().movePointRight(4).longValueExact();
            priority[b] = b * Programming.CHANNELS_PER_BANK + 1;
        }
        for (Channel c : p.channels()) {
            if (c.data().priority()) {
                priority[c.bank() - 1] = c.number();
            }
        }
    }

    /** The memory as a programming (used by tests to check what was written). */
    Programming dump() {
        Programming p = new Programming();
        for (int n = 1; n <= Programming.CHANNELS; n++) {
            Slot s = channels[n];
            boolean pri = priority[(n - 1) / Programming.CHANNELS_PER_BANK] == n;
            p.channel(n).set(new ChannelData(BigDecimal.valueOf(s.freq, 4), new Tone(s.tone, s.toneLock), s.tag,
                    s.att, s.lockout, s.beep, s.rec, s.trunk, pri, s.delayOn ? s.delay : Delay.NONE, s.mod, s.step));
        }
        for (int b = 0; b < Programming.BANKS; b++) {
            p.scanBanks().get(b).set(new io.github.f4iyy.ubc780xlt.model.ScanBankData(scanTags[b]));
            p.searchBanks().get(b).set(new io.github.f4iyy.ubc780xlt.model.SearchBankData(
                    BigDecimal.valueOf(lower[b], 4), BigDecimal.valueOf(upper[b], 4), searchTags[b], null, null));
        }
        p.markClean();
        return p;
    }

    /** True if {@link #handle} knows the command. */
    boolean accepts(String c) {
        return PM_READ.matcher(c).matches() || PM_WRITE.matcher(c).matches() || MA.matcher(c).matches()
                || TA.matcher(c).matches() || LIMIT_READ.matcher(c).matches() || LIMIT_WRITE.matcher(c).matches()
                || BEEP.matcher(c).matches() || PRIORITY.matcher(c).matches() || DELAY.matcher(c).matches()
                || TONE.matcher(c).matches() || FLAG.matcher(c).matches() || c.equals("RM") || c.equals("ST")
                || c.startsWith("RM ") || c.startsWith("ST ");
    }

    /** True if the command moved the scanner to a channel in manual mode. */
    boolean wentManual(String c) {
        return MA.matcher(c).matches() || PM_WRITE.matcher(c).matches();
    }

    String handle(String c) {
        Matcher m;
        if ((m = PM_WRITE.matcher(c)).matches()) {
            return programFrequency(Integer.parseInt(m.group(1)), "T".equals(m.group(2)), Long.parseLong(m.group(3)));
        }
        if ((m = PM_READ.matcher(c)).matches()) {
            return line(Integer.parseInt(m.group(1)));
        }
        if ((m = MA.matcher(c)).matches()) {
            int n = Integer.parseInt(m.group(1));
            if (n < 1 || n > Programming.CHANNELS) {
                return "ERR";
            }
            current = n;
            return line(n);
        }
        if ((m = TA.matcher(c)).matches()) {
            return tag(m.group(1), m.group(2), m.group(3));
        }
        if ((m = LIMIT_WRITE.matcher(c)).matches()) {
            int b = m.group(3).charAt(0) - 'A';
            (m.group(1).equals("L") ? lower : upper)[b] = Long.parseLong(m.group(2));
            return "OK";
        }
        if ((m = LIMIT_READ.matcher(c)).matches()) {
            int b = m.group(2).charAt(0) - 'A';
            long v = (m.group(1).equals("L") ? lower : upper)[b];
            return String.format("L%s%08d %s", m.group(1), v, m.group(2));
        }
        if ((m = BEEP.matcher(c)).matches()) {
            return beep(m.group(1), Integer.parseInt(m.group(2)));
        }
        if ((m = PRIORITY.matcher(c)).matches()) {
            return priority(m.group(1).charAt(0) - 'A', m.group(2));
        }
        if ((m = TONE.matcher(c)).matches()) {
            return current == 0 || Integer.parseInt(m.group(1)) > io.github.f4iyy.ubc780xlt.model.ToneTable.MAX_CODE ? "NG" : tone(m);
        }
        if ((m = FLAG.matcher(c)).matches()) {
            return flag(m.group(1), "N".equals(m.group(2)));
        }
        if ((m = DELAY.matcher(c)).matches()) {
            return delay(m.group(1), m.group(2));
        }
        return modulationOrStep(c);
    }

    private String programFrequency(int n, boolean trunk, long freq) {
        if (n < 1 || n > Programming.CHANNELS) {
            return "ERR";
        }
        Slot s = channels[n];
        s.freq = freq;
        s.trunk = trunk;
        s.delayOn = false; // PM resets delay, attenuator and recording (Uniden documentation)
        s.att = false;
        s.rec = false;
        s.mod = Modulation.AUTO; // observed on a real scanner: the modulation goes back to the band plan's
        current = n;
        return line(n);
    }

    private String line(int n) {
        if (n < 1 || n > Programming.CHANNELS) {
            return "ERR";
        }
        Slot s = channels[n];
        return String.format("C%03d F%08d T%s D%s L%s A%s R%s N%03d%s", n, s.freq, f(s.trunk), f(s.delayOn),
                f(s.lockout), f(s.att), f(s.rec), s.tone, s.toneLock ? "L" : "");
    }

    private static String f(boolean on) {
        return on ? "N" : "F";
    }

    /** {@code TA k id} reads the tag; {@code TA k id text} (even blank text) writes it. */
    private String tag(String kind, String id, String text) {
        if (kind.equals("C")) {
            int index = Integer.parseInt(id);
            if (index < 1 || index > Programming.CHANNELS) {
                return "ERR";
            }
            if (text == null) {
                return "TA C " + id + " " + pad(channels[index].tag);
            }
            channels[index].tag = text.stripTrailing();
            return "OK";
        }
        String[] store = kind.equals("B") ? scanTags : searchTags;
        int index = id.charAt(0) - 'A';
        if (index < 0 || index >= Programming.BANKS) {
            return "ERR";
        }
        if (text == null) {
            return "TA " + kind + " " + id + " " + pad(store[index]);
        }
        store[index] = text.stripTrailing();
        return "OK";
    }

    private static String pad(String tag) {
        return (tag + " ".repeat(16)).substring(0, 16);
    }

    private String beep(String set, int n) {
        if (n < 1 || n > Programming.CHANNELS) {
            return "ERR";
        }
        if (set.isEmpty()) {
            return "BA" + f(channels[n].beep) + " C " + String.format("%03d", n);
        }
        channels[n].beep = set.equals("N");
        return "OK";
    }

    private String priority(int bank, String number) {
        if (number == null) {
            return String.format("PC %c%03d", (char) ('A' + bank), priority[bank]);
        }
        priority[bank] = Integer.parseInt(number);
        return "OK";
    }

    private String tone(Matcher m) {
        Slot s = channels[current];
        s.tone = Integer.parseInt(m.group(1));
        s.toneLock = !m.group(2).isEmpty();
        return "OK";
    }

    private String flag(String name, boolean on) {
        if (current == 0) {
            return "NG";
        }
        Slot s = channels[current];
        switch (name) {
            case "AT" -> s.att = on;
            case "AR" -> s.rec = on;
            default -> s.lockout = on;
        }
        return "OK";
    }

    private String delay(String onOff, String value) {
        if (current == 0) {
            return "NG";
        }
        Slot s = channels[current];
        if (onOff == null || (onOff.isEmpty() && value == null)) {
            return s.delayOn ? "DL " + s.delay.command() : "DLF";
        }
        if (onOff.equals("F")) {
            s.delayOn = false;
            return "OK";
        }
        s.delayOn = true;
        if (value != null) {
            Delay d = Delay.parse(value);
            if (d == null || d == Delay.NONE) {
                return "ERR";
            }
            s.delay = d;
        }
        return "OK";
    }

    private String modulationOrStep(String c) {
        boolean mod = c.startsWith("RM");
        String arg = c.length() > 3 ? c.substring(3) : null;
        if (arg == null) {
            Modulation m = current == 0 ? globalMod : channels[current].mod;
            Step st = current == 0 ? globalStep : channels[current].step;
            return mod ? "RM " + m.name() : "ST " + st.command();
        }
        if (mod) {
            Modulation m = Modulation.parse(arg);
            if (m == null) {
                return "ERR";
            }
            if (current == 0) {
                globalMod = m;
            } else {
                channels[current].mod = m;
            }
        } else {
            Step st = Step.parse(arg);
            if (st == null) {
                return "ERR";
            }
            if (current == 0) {
                globalStep = st;
            } else {
                channels[current].step = st;
            }
        }
        return "OK";
    }

    /** Formats a frequency the way {@link ScannerFormat#mhz} reads it (used in assertions). */
    static String digits(BigDecimal mhz) {
        return String.format("%08d", mhz.movePointRight(4).longValueExact());
    }
}
