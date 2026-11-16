package io.github.f4iyy.ubc780xlt.protocol.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The global settings of the simulated scanner, the squelch and the signal meter. */
final class ScannerSettingsSim {

    /** Settings that are one letter after the command name, with the letters the scanner accepts. */
    private static final Map<String, String> ALLOWED = Map.of(
            "LT", "NDF", "BP", "NF", "PR", "NF", "DS", "NF", "MU", "ANF", "EL", "NF", "LM", "NF", "CT", "NFS");

    private final Map<String, Character> letters = new LinkedHashMap<>();
    private String scanBanks = "ABCDEFGHIJ";
    private String searchBanks = "A";
    private final List<Long> skip = new ArrayList<>();
    private boolean squelchOpen;
    private int signal = 107;
    private long frequency = 1450150;

    ScannerSettingsSim() {
        letters.put("LT", 'N');
        letters.put("BP", 'F');
        letters.put("PR", 'F');
        letters.put("DS", 'N');
        letters.put("MU", 'A');
        letters.put("EL", 'F');
        letters.put("LM", 'F');
        letters.put("CT", 'F');
    }

    void setSquelchOpen(boolean open) {
        squelchOpen = open;
    }

    void setSignal(int signal, long frequencyHundredsOfHz) {
        this.signal = signal;
        this.frequency = frequencyHundredsOfHz;
    }

    List<Long> skip() {
        return List.copyOf(skip);
    }

    void addSkip(long f) {
        skip.add(f);
    }

    boolean accepts(String c) {
        if (c.equals("SQ") || c.equals("SG") || c.equals("SB") || c.equals("CB") || c.startsWith("SB ")
                || c.startsWith("CB ") || c.equals("WA")) {
            return true;
        }
        if (c.matches("SS\\d{3}") || c.matches("SS\\d{8}")) {
            return true;
        }
        if (c.length() == 2 && letters.containsKey(c)) {
            return true;
        }
        return c.length() == 3 && letters.containsKey(c.substring(0, 2));
    }

    String handle(String c) {
        switch (c) {
            case "SQ":
                return squelchOpen ? "+" : "-";
            case "SG":
                return String.format("S%03d F%08d", signal, frequency);
            case "WA":
                return "ERR"; // weather alert only exists in weather mode
            case "SB":
                return "SB " + scanBanks;
            case "CB":
                return "CB " + searchBanks;
            default:
                break;
        }
        if (c.startsWith("SB ")) {
            return banks(c.substring(3), true);
        }
        if (c.startsWith("CB ")) {
            return banks(c.substring(3), false);
        }
        if (c.matches("SS\\d{3}")) {
            int i = Integer.parseInt(c.substring(2));
            if (i < 1 || i > 200) {
                return "ERR";
            }
            return String.format("SS%08d", i <= skip.size() ? skip.get(i - 1) : 0L);
        }
        if (c.matches("SS\\d{8}")) {
            long f = Long.parseLong(c.substring(2));
            if (skip.contains(f)) {
                return "ON";
            }
            if (skip.size() >= 200) {
                return "ERR";
            }
            skip.add(f);
            return c;
        }
        String key = c.substring(0, 2);
        if (c.length() == 2) {
            return key + letters.get(key);
        }
        char v = c.charAt(2);
        if (ALLOWED.get(key).indexOf(v) < 0) {
            return "ERR";
        }
        letters.put(key, v);
        return "OK";
    }

    private String banks(String letters, boolean scan) {
        if (letters.isBlank() || !letters.matches("[A-J]+")) {
            return "ERR";
        }
        if (scan) {
            scanBanks = letters;
        } else {
            searchBanks = letters;
        }
        return "OK";
    }

}
