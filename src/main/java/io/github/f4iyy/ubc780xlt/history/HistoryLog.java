package io.github.f4iyy.ubc780xlt.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The reception history as one CSV file per day, {@code history-YYYY-MM-DD.csv}, in English. Each reception is
 * appended and flushed at once, so a crash loses nothing; a new day starts a new file with its header.
 */
public final class HistoryLog {

    public static final List<String> HEADERS =
            List.of("Date", "Time", "Duration (s)", "Bank", "Channel", "Frequency", "Text Tag", "Mod", "Tone",
                    "Signal Max");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path directory;
    private final ZoneId zone;

    public HistoryLog(Path directory, ZoneId zone) {
        this.directory = directory;
        this.zone = zone;
    }

    /** The file a reception that started at that moment goes to. */
    public Path fileFor(Reception r) {
        return directory.resolve("history-" + DATE.format(r.start().atZone(zone).toLocalDate()) + ".csv");
    }

    public void append(Reception r) throws IOException {
        Files.createDirectories(directory);
        Path file = fileFor(r);
        boolean isNew = !Files.exists(file) || Files.size(file) == 0;
        StringBuilder sb = new StringBuilder();
        if (isNew) {
            sb.append(row(HEADERS));
        }
        sb.append(row(fields(r)));
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /** The columns of a reception, as in {@link #HEADERS}. */
    List<String> fields(Reception r) {
        var local = r.start().atZone(zone);
        return List.of(
                DATE.format(local.toLocalDate()),
                TIME.format(local.toLocalTime()),
                seconds(r),
                r.bank() == 0 ? "" : Integer.toString(r.bank()),
                r.channel() == 0 ? "" : Integer.toString(r.channel()),
                r.frequency(),
                r.tag(),
                r.modulation(),
                r.tone(),
                Integer.toString(r.maxSignal()));
    }

    /** Duration in seconds with one decimal ({@code 3.4}). */
    static String seconds(Reception r) {
        return String.format(java.util.Locale.ROOT, "%.1f", r.duration().toMillis() / 1000.0);
    }

    /** A CSV line (RFC 4180): fields with a comma, a quote or a line break are quoted. */
    static String row(List<String> fields) {
        return String.join(",", fields.stream().map(HistoryLog::escape).toList()) + "\r\n";
    }

    private static String escape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /** The day a file belongs to, from its name; {@code null} if it is not a history file. */
    public static LocalDate dayOf(Path file) {
        String name = file.getFileName().toString();
        if (!name.startsWith("history-") || !name.endsWith(".csv")) {
            return null;
        }
        try {
            return LocalDate.parse(name.substring("history-".length(), name.length() - ".csv".length()));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
