package io.github.f4iyy.ubc780xlt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryLogTest {

    @TempDir
    Path dir;

    private static Reception reception(String time, int channel, String tag) {
        return new Reception(Instant.parse(time), Duration.ofMillis(3450), channel == 0 ? 0 : 1, channel,
                "145.0150", tag, "FM", "67.0 Hz", 128, false);
    }

    private List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    @Test
    void oneFilePerDayWithAnEnglishHeader() throws IOException {
        HistoryLog log = new HistoryLog(dir, ZoneOffset.UTC);
        log.append(reception("2026-11-06T10:15:30Z", 12, "RELAY"));
        log.append(reception("2026-11-06T23:59:59Z", 13, "CALL"));
        log.append(reception("2026-11-07T00:00:01Z", 14, "NEXT"));

        Path day1 = dir.resolve("history-2026-11-06.csv");
        Path day2 = dir.resolve("history-2026-11-07.csv");
        assertTrue(Files.exists(day1));
        assertTrue(Files.exists(day2));

        List<String> l1 = lines(day1);
        assertEquals("Date,Time,Duration (s),Bank,Channel,Frequency,Text Tag,Mod,Tone,Signal Max", l1.get(0));
        assertEquals("2026-11-06,10:15:30,3.5,1,12,145.0150,RELAY,FM,67.0 Hz,128", l1.get(1));
        assertEquals(3, l1.size(), "header once, two receptions");
        assertEquals(2, lines(day2).size());
    }

    @Test
    void unknownChannelAndBankAreLeftEmpty() throws IOException {
        HistoryLog log = new HistoryLog(dir, ZoneOffset.UTC);
        log.append(reception("2026-11-06T10:00:00Z", 0, ""));
        assertEquals("2026-11-06,10:00:00,3.5,,,145.0150,,FM,67.0 Hz,128",
                lines(dir.resolve("history-2026-11-06.csv")).get(1));
    }

    @Test
    void theDayDependsOnTheTimeZone() {
        HistoryLog utc = new HistoryLog(dir, ZoneOffset.UTC);
        HistoryLog paris = new HistoryLog(dir, java.time.ZoneId.of("Europe/Paris"));
        Reception r = reception("2026-11-06T23:30:00Z", 1, "X");
        assertEquals("history-2026-11-06.csv", utc.fileFor(r).getFileName().toString());
        assertEquals("history-2026-11-07.csv", paris.fileFor(r).getFileName().toString());
    }

    @Test
    void fieldsWithCommasAndQuotesAreEscaped() throws IOException {
        HistoryLog log = new HistoryLog(dir, ZoneOffset.UTC);
        log.append(reception("2026-11-06T10:00:00Z", 1, "A, \"B\""));
        assertTrue(lines(dir.resolve("history-2026-11-06.csv")).get(1).contains("\"A, \"\"B\"\"\""));
    }

    @Test
    void createsTheFolderAndAppendsToAnExistingFile() throws IOException {
        Path sub = dir.resolve("deep/logs");
        HistoryLog log = new HistoryLog(sub, ZoneOffset.UTC);
        log.append(reception("2026-11-06T10:00:00Z", 1, "A"));

        HistoryLog again = new HistoryLog(sub, ZoneOffset.UTC);
        again.append(reception("2026-11-06T11:00:00Z", 2, "B"));
        assertEquals(3, lines(sub.resolve("history-2026-11-06.csv")).size());
    }

    @Test
    void anEmptyFileGetsItsHeader() throws IOException {
        Files.createFile(dir.resolve("history-2026-11-06.csv"));
        new HistoryLog(dir, ZoneOffset.UTC).append(reception("2026-11-06T10:00:00Z", 1, "A"));
        assertEquals("Date", lines(dir.resolve("history-2026-11-06.csv")).get(0).split(",")[0]);
    }

    @Test
    void aFolderThatCannotBeCreatedIsAnError() throws IOException {
        Path file = Files.createFile(dir.resolve("not-a-folder"));
        HistoryLog log = new HistoryLog(file.resolve("logs"), ZoneOffset.UTC);
        assertThrows(IOException.class, () -> log.append(reception("2026-11-06T10:00:00Z", 1, "A")));
    }

    @Test
    void recognisesHistoryFilesByName() {
        assertEquals(LocalDate.of(2026, 11, 6), HistoryLog.dayOf(Path.of("logs/history-2026-11-06.csv")));
        assertNull(HistoryLog.dayOf(Path.of("serial.log")));
        assertNull(HistoryLog.dayOf(Path.of("history-today.csv")));
        assertNull(HistoryLog.dayOf(Path.of("history-2026-11-06.txt")));
    }

    @Test
    void exportToXlsx() throws IOException {
        Path file = dir.resolve("h.xlsx");
        HistoryExport.write(List.of(
                reception("2026-11-06T10:15:30Z", 12, "RELAY"),
                reception("2026-11-06T10:20:00Z", 0, "")), file, ZoneOffset.UTC);

        try (Workbook wb = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet s = wb.getSheet(HistoryExport.SHEET);
            assertEquals(3, s.getLastRowNum() + 1);
            for (int i = 0; i < HistoryLog.HEADERS.size(); i++) {
                assertEquals(HistoryLog.HEADERS.get(i), s.getRow(0).getCell(i).getStringCellValue());
            }
            Row r = s.getRow(1);
            assertEquals("2026-11-06", r.getCell(0).getStringCellValue());
            assertEquals("10:15:30", r.getCell(1).getStringCellValue());
            assertEquals(3.45, r.getCell(2).getNumericCellValue(), 1e-9);
            assertEquals(CellType.NUMERIC, r.getCell(4).getCellType());
            assertEquals(12, r.getCell(4).getNumericCellValue(), 0);
            assertEquals(new BigDecimal("145.0150").doubleValue(), r.getCell(5).getNumericCellValue(), 1e-9);
            assertEquals("RELAY", r.getCell(6).getStringCellValue());
            assertEquals(128, r.getCell(9).getNumericCellValue(), 0);

            Row unknown = s.getRow(2);
            assertNull(unknown.getCell(3), "unknown bank: empty cell");
            assertNull(unknown.getCell(4));
        }
    }
}
