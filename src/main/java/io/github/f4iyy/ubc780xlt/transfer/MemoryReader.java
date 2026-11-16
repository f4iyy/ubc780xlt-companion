package io.github.f4iyy.ubc780xlt.transfer;

import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Reads the scanner memory (scan bank names, search banks, 500 channels, priority channels) into a new
 * {@link Programming}. The scanner stops scanning: visiting a channel to read its delay, modulation and step puts
 * it in manual mode. Unused channels are read with {@code PM} only, which keeps the transfer short.
 */
public final class MemoryReader extends TransferSupport {

    /** Steps: 10 scan banks + 10 search banks + 500 channels + 10 priority channels. */
    private static final int TOTAL = 3 * Programming.BANKS + Programming.CHANNELS;

    public MemoryReader(CommandSender sender, Progress progress, BooleanSupplier cancelled) {
        super(sender, progress, cancelled, TOTAL);
    }

    /**
     * @return the programming read from the scanner; {@link #warnings()} lists what could not be read
     * @throws IOException if the link breaks
     * @throws TransferCancelledException if cancelled
     */
    public Programming read() throws IOException, TransferCancelledException {
        Programming p = new Programming();
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            readScanBank(p, bank);
        }
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            readSearchBank(p, bank);
        }
        for (int n = 1; n <= Programming.CHANNELS; n++) {
            readChannel(p, n);
        }
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            readPriority(p, bank);
        }
        p.markClean();
        finished("Done");
        return p;
    }

    private void readScanBank(Programming p, int bank) throws IOException, TransferCancelledException {
        step("Scan bank " + bank);
        String letter = ScannerFormat.letter(bank);
        String prefix = "TA B " + letter + " ";
        Optional<String> tag = ScannerFormat.parseTag(ask("TA B " + letter), prefix);
        if (tag.isEmpty()) {
            warn("Scan bank " + bank + ": unexpected answer to the name");
        }
        p.scanBanks().get(bank - 1).set(new ScanBankData(tag.orElse("")));
    }

    private void readSearchBank(Programming p, int bank) throws IOException, TransferCancelledException {
        step("Search bank " + bank);
        String letter = ScannerFormat.letter(bank);
        BigDecimal lower = limit("LL " + letter, "Search bank " + bank + " lower limit");
        BigDecimal upper = limit("LU " + letter, "Search bank " + bank + " upper limit");
        Optional<String> tag = ScannerFormat.parseTag(ask("TA S " + letter), "TA S " + letter + " ");
        if (tag.isEmpty()) {
            warn("Search bank " + bank + ": unexpected answer to the name");
        }
        // The documentation has no command for the modulation and step of a search bank: they stay on AUTO.
        p.searchBanks().get(bank - 1).set(new SearchBankData(lower, upper, tag.orElse(""), Modulation.AUTO, Step.AUTO));
    }

    private BigDecimal limit(String command, String what) throws IOException {
        String answer = ask(command);
        Optional<BigDecimal> value = ScannerFormat.parseLimit(answer);
        if (value.isEmpty()) {
            warn(what + ": unexpected answer " + answer);
        }
        return value.orElse(BigDecimal.ZERO);
    }

    private void readChannel(Programming p, int n) throws IOException, TransferCancelledException {
        step("Channel " + n);
        String answer = ask(String.format("PM%03d", n));
        Optional<ScannerFormat.ChannelLine> parsed = ScannerFormat.parseChannel(answer);
        if (parsed.isEmpty()) {
            warn("Channel " + n + ": unexpected answer " + answer);
            return;
        }
        ScannerFormat.ChannelLine line = parsed.get();
        if (line.mhz().signum() == 0) {
            p.channel(n).set(new ChannelData(null, line.tone(), "", line.att(), line.lockout(), false, line.rec(),
                    line.trunk(), false, Delay.NONE, Modulation.AUTO, Step.AUTO));
            return;
        }

        // Visit the channel to read what PM does not give: delay, modulation, step. Tag and beep are by number.
        String nnn = String.format("%03d", n);
        ask("MA" + nnn);
        Delay delay = line.delayOn() ? parse(ScannerFormat.parseDelay(ask("DL")), n, "delay", Delay.S2) : Delay.NONE;
        Modulation mod = parse(ScannerFormat.parseModulation(ask("RM")), n, "modulation", Modulation.AUTO);
        Step step = parse(ScannerFormat.parseStep(ask("ST")), n, "step", Step.AUTO);
        String tag = ScannerFormat.parseTag(ask("TA C " + nnn), "TA C " + nnn + " ").orElse("");
        boolean beep = parse(ScannerFormat.parseOnOff(ask("BA C " + nnn), "BA"), n, "beep alert", false);

        p.channel(n).set(new ChannelData(line.mhz(), line.tone(), tag, line.att(), line.lockout(), beep, line.rec(),
                line.trunk(), false, delay, mod, step));
    }

    private <T> T parse(Optional<T> value, int channel, String what, T fallback) {
        if (value.isEmpty()) {
            warn("Channel " + channel + ": could not read the " + what);
        }
        return value.orElse(fallback);
    }

    private void readPriority(Programming p, int bank) throws IOException, TransferCancelledException {
        step("Priority " + bank);
        String answer = ask("PC " + ScannerFormat.letter(bank));
        Optional<Integer> channel = ScannerFormat.parsePriority(answer, bank);
        if (channel.isPresent()) {
            p.channel(channel.get()).set(p.channel(channel.get()).data().withPriority(true));
        } else if (!answer.strip().equals("NG")) {
            warn("Bank " + bank + ": unexpected answer to the priority channel: " + answer);
        }
    }
}
