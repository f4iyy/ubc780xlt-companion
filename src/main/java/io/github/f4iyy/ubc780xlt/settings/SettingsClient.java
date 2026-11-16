package io.github.f4iyy.ubc780xlt.settings;

import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import io.github.f4iyy.ubc780xlt.protocol.FrequencyRanges;
import io.github.f4iyy.ubc780xlt.protocol.Response;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.Backlight;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.CtcssFunction;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.MuteMode;
import io.github.f4iyy.ubc780xlt.transfer.ScannerFormat;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Reads and writes the global scanner settings. */
public final class SettingsClient {

    /** What the scanner said to a setting. */
    public enum Outcome {
        OK,
        /** {@code NG}: the setting exists but is not allowed in the current scanner mode. */
        NOT_NOW,
        /** {@code ERR} or anything else. */
        ERROR
    }

    /** The settings read, and what could not be read. */
    public record ReadResult(ScannerSettings settings, List<String> warnings) {
    }

    private final CommandSender scanner;

    public SettingsClient(CommandSender scanner) {
        this.scanner = scanner;
    }

    // ---- reading ----------------------------------------------------------------------------

    public ReadResult read() throws IOException {
        List<String> warnings = new ArrayList<>();
        Backlight backlight = oneOf("LT", Backlight::of, Backlight.NORMAL, warnings);
        boolean beep = flag("BP", false, warnings);
        boolean priority = flag("PR", false, warnings);
        boolean dataSkip = flag("DS", true, warnings);
        MuteMode mute = oneOf("MU", MuteMode::of, MuteMode.AUTO, warnings);
        boolean editLock = flag("EL", false, warnings);
        boolean mask = flag("LM", false, warnings);
        CtcssFunction ctcss = oneOf("CT", CtcssFunction::of, CtcssFunction.OFF, warnings);
        Set<Integer> scan = banks("SB", warnings);
        Set<Integer> search = banks("CB", warnings);
        List<BigDecimal> skip = skipList(warnings);
        return new ReadResult(
                new ScannerSettings(backlight, beep, priority, dataSkip, mute, editLock, mask, ctcss, scan, search,
                        skip),
                List.copyOf(warnings));
    }

    private String ask(String command) throws IOException {
        return scanner.send(command).text();
    }

    /** {@code LTN} gives the code after the command name. */
    private <E> E oneOf(String name, java.util.function.Function<Character, E> parse, E fallback,
            List<String> warnings) throws IOException {
        String a = ask(name);
        if (a.length() == name.length() + 1 && a.startsWith(name)) {
            E v = parse.apply(a.charAt(name.length()));
            if (v != null) {
                return v;
            }
        }
        warnings.add(name + ": unexpected answer " + a);
        return fallback;
    }

    private boolean flag(String name, boolean fallback, List<String> warnings) throws IOException {
        String a = ask(name);
        return ScannerFormat.parseOnOff(a, name).orElseGet(() -> {
            warnings.add(name + ": unexpected answer " + a);
            return fallback;
        });
    }

    /** {@code SB DG} gives {4, 7}. */
    private Set<Integer> banks(String name, List<String> warnings) throws IOException {
        String a = ask(name);
        Set<Integer> banks = new TreeSet<>();
        if (!a.startsWith(name)) {
            warnings.add(name + ": unexpected answer " + a);
            return banks;
        }
        for (char c : a.substring(name.length()).toCharArray()) {
            if (c >= 'A' && c <= 'J') {
                banks.add(c - 'A' + 1);
            }
        }
        return banks;
    }

    private List<BigDecimal> skipList(List<String> warnings) throws IOException {
        List<BigDecimal> skip = new ArrayList<>();
        for (int i = 1; i <= ScannerSettings.SKIP_CAPACITY; i++) {
            String a = ask(String.format("SS%03d", i));
            if (!a.matches("SS\\d{8}")) {
                warnings.add("SS" + i + ": unexpected answer " + a);
                break;
            }
            BigDecimal f = ScannerFormat.mhz(a.substring(2));
            if (f.signum() != 0) {
                skip.add(f);
            }
        }
        return skip;
    }

    // ---- writing ----------------------------------------------------------------------------

    public Outcome setBacklight(Backlight v) throws IOException {
        return write("LT" + v.code());
    }

    public Outcome setBeep(boolean on) throws IOException {
        return write("BP" + (on ? 'N' : 'F'));
    }

    public Outcome setPriority(boolean on) throws IOException {
        return write("PR" + (on ? 'N' : 'F'));
    }

    public Outcome setDataSkip(boolean on) throws IOException {
        return write("DS" + (on ? 'N' : 'F'));
    }

    public Outcome setMute(MuteMode v) throws IOException {
        return write("MU" + v.code());
    }

    public Outcome setEditLock(boolean on) throws IOException {
        return write("EL" + (on ? 'N' : 'F'));
    }

    public Outcome setScreenMask(boolean on) throws IOException {
        return write("LM" + (on ? 'N' : 'F'));
    }

    public Outcome setCtcss(CtcssFunction v) throws IOException {
        return write("CT" + v.code());
    }

    /** Selects the scan banks (at least one). */
    public Outcome setScanBanks(Set<Integer> banks) throws IOException {
        return write("SB " + letters(banks));
    }

    /** Selects the search banks of the chain search (at least one). */
    public Outcome setSearchBanks(Set<Integer> banks) throws IOException {
        return write("CB " + letters(banks));
    }

    /**
     * Adds a frequency to the search skip memory. There is no command to remove one (Uniden documentation).
     *
     * @return {@link Outcome#OK} when added or already there
     */
    public Outcome addSkipFrequency(BigDecimal mhz) throws IOException {
        Response r = scanner.send("SS" + FrequencyRanges.toScannerFormat(mhz));
        boolean ok = r.text().equals("ON") || r.text().matches("SS\\d{8}");
        return ok ? Outcome.OK : outcome(r);
    }

    private Outcome write(String command) throws IOException {
        return outcome(scanner.send(command));
    }

    private static Outcome outcome(Response r) {
        if (r.isOk()) {
            return Outcome.OK;
        }
        return r.text().equals("NG") ? Outcome.NOT_NOW : Outcome.ERROR;
    }

    private static String letters(Set<Integer> banks) {
        StringBuilder sb = new StringBuilder();
        new TreeSet<>(banks).forEach(b -> sb.append(ScannerFormat.letter(b)));
        return sb.toString();
    }
}
