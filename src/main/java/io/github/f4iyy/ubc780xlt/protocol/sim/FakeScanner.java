package io.github.f4iyy.ubc780xlt.protocol.sim;

import io.github.f4iyy.ubc780xlt.protocol.lcd.Icon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Software model of a BC780XLT, for tests and for running the application without hardware.
 * It answers like the real scanner for the commands implemented so far.
 */
public final class FakeScanner {

    private final String model;
    private final String serial;
    private final String commandVersion;
    private final int baud;
    private final List<String> received = new ArrayList<>();
    private final Set<String> autoGeneratingOn = new HashSet<>();
    private boolean unplugged;
    private int garbled;

    // Simulated display: scanning bank 4, channel 183 (like the ProScan screenshot).
    private int mode;
    private String frequency = " 430.3500";
    private String channel = "183";
    private int signalBars = 3;
    private boolean remote = true;
    private final ScannerMemory memory = new ScannerMemory();
    private final ScannerSettingsSim settings = new ScannerSettingsSim();

    public FakeScanner(String model, String serial, String commandVersion, int baud) {
        this.model = model;
        this.serial = serial;
        this.commandVersion = commandVersion;
        this.baud = baud;
    }

    /** A BC780XLT like the one in the Uniden documentation, at the factory speed. */
    public static FakeScanner bc780xlt() {
        return new FakeScanner("BC780XLT", "1000000001", "103", 9600);
    }

    /** Speed the scanner is set to; at any other speed it only produces garbage (no answer). */
    public int baud() {
        return baud;
    }

    /** The next {@code count} commands are answered {@code ERR}, as when garbage precedes them on the line. */
    public synchronized void garbleNext(int count) {
        garbled = count;
    }

    /** Simulates removing the cable: every operation on an open link then fails. */
    public void unplug() {
        unplugged = true;
    }

    public void plugIn() {
        unplugged = false;
    }

    public boolean isUnplugged() {
        return unplugged;
    }

    /** Commands received so far, in order. */
    public synchronized List<String> received() {
        return Collections.unmodifiableList(new ArrayList<>(received));
    }

    /** True while an auto-generating command ({@code ID}, {@code QU}, {@code RI}) is switched on. */
    public synchronized boolean isAutoGenerating(String command) {
        return autoGeneratingOn.contains(command);
    }

    /** Opens or closes the simulated squelch (a reception starts when it opens). */
    public synchronized void setSquelchOpen(boolean open) {
        settings.setSquelchOpen(open);
    }

    /** What {@code SG} reports: signal strength 0 to 255 and the frequency in hundreds of hertz. */
    public synchronized void setSignal(int signal, long frequencyHundredsOfHz) {
        settings.setSignal(signal, frequencyHundredsOfHz);
    }

    /** Frequencies in the search skip memory, in hundreds of hertz. */
    public synchronized java.util.List<Long> skipList() {
        return settings.skip();
    }

    public synchronized void addSkip(long frequencyHundredsOfHz) {
        settings.addSkip(frequencyHundredsOfHz);
    }

    /** Fills the scanner memory from a programming, to test reading it. */
    public synchronized void loadMemory(io.github.f4iyy.ubc780xlt.model.Programming p) {
        memory.load(p);
    }

    /** The scanner memory as a programming, to check what was written to it. */
    public synchronized io.github.f4iyy.ubc780xlt.model.Programming memory() {
        return memory.dump();
    }

    /** Scanner mode as reported by {@code MD}: 0 scan, 1 manual, 8 manual frequency. */
    public synchronized int mode() {
        return mode;
    }

    public synchronized void setSignalBars(int bars) {
        signalBars = Math.max(0, Math.min(6, bars));
    }

    public synchronized String frequencyText() {
        return frequency.strip();
    }

    /** The 41 lines of the {@code LCD} answer for the current simulated state. */
    private String lcd() {
        boolean scanning = mode == 0;
        StringBuilder sb = new StringBuilder();
        for (Icon icon : Icon.values()) {
            char symbol = switch (icon) {
                case BANK, CHAN, FDOT, S, FM -> '+';
                case SCAN -> scanning ? '+' : '-';
                case HOLD -> mode == 1 ? '+' : '-';
                case RMT -> remote ? '+' : '-';
                default -> '-';
            };
            sb.append(icon.name()).append(' ').append(symbol).append("\n");
        }
        String blank16 = " ".repeat(16);
        String text = mode == 0 ? "> SCANNING" : mode == 1 ? "> MANUAL" : "> FREQUENCY";
        String line2 = (text + blank16).substring(0, 16);
        sb.append("BAR ").append("-".repeat(29)).append("\n");
        sb.append("SMT ").append("+".repeat(signalBars)).append("-".repeat(6 - signalBars)).append("\n");
        sb.append("BNK ---*------\n");
        sb.append("CHN [").append(channel).append("]\n");
        sb.append("FRQ [").append(frequency).append("]\n");
        sb.append("CTC [      ]\n");
        sb.append("LINE1 [").append(blank16).append("][").append(blank16).append("]\n");
        sb.append("LINE2 [").append(line2).append("][").append(blank16).append("]");
        return sb.toString();
    }

    /** Answers one command line; several lines are separated by {@code \n}, {@code null} means silence. */
    synchronized String handle(String command) {
        received.add(command);
        if (garbled > 0) {
            garbled--;
            return "ERR";
        }
        if (command.startsWith("KEY")) {
            return handleKey(command);
        }
        if (command.startsWith("RF") && command.length() == 10) {
            long v = Long.parseLong(command.substring(2));
            frequency = String.format("%4d.%04d", v / 10000, v % 10000);
            mode = 8;
            return "OK";
        }
        if (settings.accepts(command)) {
            return settings.handle(command);
        }
        if (memory.accepts(command)) {
            String answer = memory.handle(command);
            if (memory.wentManual(command) && !answer.equals("ERR")) {
                mode = 1;
            }
            return answer;
        }
        switch (command) {
            case "LCD":
                return lcd();
            case "MD":
                return String.format("MD%02d", mode);
            case "SI":
                return "SI " + model + "," + serial + "," + commandVersion;
            case "VR":
                return "VR1.00";
            case "ID":
            case "QU":
            case "RI":
                return command + (autoGeneratingOn.contains(command) ? "N" : "F");
            case "IDN":
            case "QUN":
            case "RIN":
                autoGeneratingOn.add(command.substring(0, 2));
                return "OK";
            case "IDF":
            case "QUF":
            case "RIF":
                autoGeneratingOn.remove(command.substring(0, 2));
                return "OK";
            default:
                return "ERR";
        }
    }

    private String handleKey(String command) {
        switch (command) {
            case "KEY00":
                mode = 0;
                break;
            case "KEY01":
                mode = 1;
                break;
            case "KEY04H":
                remote = false;
                return null; // leaving remote mode: no answer (Uniden documentation)
            default:
                break;
        }
        return "OK";
    }
}
