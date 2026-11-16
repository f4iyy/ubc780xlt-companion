package io.github.f4iyy.ubc780xlt.transfer;

import io.github.f4iyy.ubc780xlt.model.Channel;
import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import io.github.f4iyy.ubc780xlt.protocol.FrequencyRanges;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/**
 * Writes a {@link Programming} to the scanner memory. For each channel the order matters (Uniden documentation):
 * move to the channel, set the step, program the frequency ({@code PM}, which resets modulation, delay, attenuator
 * and recording), then the modulation and the other settings. The scanner is left in manual mode.
 */
public final class MemoryWriter extends TransferSupport {

    private static final int TOTAL = 3 * Programming.BANKS + Programming.CHANNELS;

    public MemoryWriter(CommandSender sender, Progress progress, BooleanSupplier cancelled) {
        super(sender, progress, cancelled, TOTAL);
    }

    /**
     * @throws IOException if the link breaks
     * @throws TransferCancelledException if cancelled; the scanner then holds a mix of old and new data
     */
    public void write(Programming p) throws IOException, TransferCancelledException {
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            writeScanBank(p, bank);
        }
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            writeSearchBank(p, bank);
        }
        for (Channel c : p.channels()) {
            writeChannel(c.number(), c.data());
        }
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            writePriority(p, bank);
        }
        finished("Done");
    }

    private void writeScanBank(Programming p, int bank) throws IOException, TransferCancelledException {
        step("Scan bank " + bank);
        ScanBankData d = p.scanBanks().get(bank - 1).data();
        tell(tagCommand("TA B " + ScannerFormat.letter(bank), d.tag()), "Scan bank " + bank);
    }

    private void writeSearchBank(Programming p, int bank) throws IOException, TransferCancelledException {
        step("Search bank " + bank);
        String letter = ScannerFormat.letter(bank);
        SearchBankData d = p.searchBanks().get(bank - 1).data();
        String what = "Search bank " + bank;
        // An unused bank reads back as 0 / 0, which the scanner refuses to take as a limit (ERR): leave it as it is.
        if (d.lower().signum() != 0 || d.upper().signum() != 0) {
            tell("LL" + FrequencyRanges.toScannerFormat(d.lower()) + " " + letter, what);
            tell("LU" + FrequencyRanges.toScannerFormat(d.upper()) + " " + letter, what);
        }
        tell(tagCommand("TA S " + letter, d.tag()), what);
    }

    private void writeChannel(int n, ChannelData d) throws IOException, TransferCancelledException {
        step("Channel " + n);
        String nnn = String.format("%03d", n);
        String what = "Channel " + n;
        if (d.isEmpty()) {
            tellChannel("PM" + nnn + " 00000000", what);
            tell(tagCommand("TA C " + nnn, ""), what);
            return;
        }
        tellChannel("MA" + nnn, what);
        tell("ST " + d.step().command(), what);
        tellChannel("PM" + nnn + (d.trunk() ? "T" : " ") + FrequencyRanges.toScannerFormat(d.frequency()), what);
        // PM reset the modulation (back to the band plan's, e.g. FM -> NFM at 430 MHz, AM at 124.9 MHz), the delay,
        // attenuator and recording: set them now.
        tell("RM " + d.modulation().name(), what);
        if (d.delay() == Delay.NONE) {
            tell("DLF", what);
        } else {
            tell("DLN " + d.delay().command(), what);
        }
        tell("AT" + flag(d.att()), what);
        tell("AR" + flag(d.rec()), what);
        tell("LO" + flag(d.lockout()), what);
        tell("BA" + flag(d.beep()) + " C " + nnn, what);
        if (d.tone().isOff()) {
            // The scanner answers NG when no tone applies to the channel (e.g. AM): nothing to switch off then.
            ask("CS" + d.tone().scannerCode());
        } else {
            tell("CS" + d.tone().scannerCode(), what);
        }
        tell(tagCommand("TA C " + nnn, d.tag()), what);
    }

    /** Every bank has a priority channel: the one marked, or the first channel of the bank (the factory default). */
    private void writePriority(Programming p, int bank) throws IOException, TransferCancelledException {
        step("Priority " + bank);
        int chosen = p.bankChannels(bank).stream().filter(c -> c.data().priority()).map(Channel::number).findFirst()
                .orElse((bank - 1) * Programming.CHANNELS_PER_BANK + 1);
        tell(String.format("PC %s%03d", ScannerFormat.letter(bank), chosen), "Bank " + bank + " priority");
    }

    private static String flag(boolean on) {
        return on ? "N" : "F";
    }

    /**
     * {@code TA ... text}. A bare {@code TA ...} is a read (the documentation uses the same command to "confirm"
     * and to "clear"), so an empty tag is written as 16 blanks.
     */
    private static String tagCommand(String prefix, String tag) {
        return prefix + " " + (tag.isBlank() ? " ".repeat(16) : tag);
    }
}
