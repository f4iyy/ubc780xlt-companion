package io.github.f4iyy.ubc780xlt.model.xlsx;

import io.github.f4iyy.ubc780xlt.model.Channel;
import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.ScanBank;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBank;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tone;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * The programming as a native XLSX workbook with three sheets: {@code SCAN}, {@code SEARCH} and
 * {@code CHANNELS}. Headers are always in English. Frequencies are numbers (MHz), flags are booleans.
 */
public final class XlsxStore {

    public static final String SCAN = "SCAN";
    public static final String SEARCH = "SEARCH";
    public static final String CHANNELS = "CHANNELS";

    public static final List<String> SCAN_HEADERS = List.of("Bank", "Text Tag");
    public static final List<String> SEARCH_HEADERS =
            List.of("Bank", "Lower Limit Freq", "Upper Limit Freq", "Text Tag", "Mod", "Steps");
    public static final List<String> CHANNEL_HEADERS = List.of("Channel", "Frequency", "Tone", "Text Tag", "Att", "LO",
            "Beep", "Rec", "Trunk", "Pri", "Delay", "Mod", "Steps");

    private XlsxStore() {
    }

    // ---- writing ----------------------------------------------------------------------------

    public static void write(Programming p, Path file) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Styles styles = new Styles(wb);

            Sheet scan = sheet(wb, SCAN, SCAN_HEADERS, styles);
            for (ScanBank b : p.scanBanks()) {
                Row r = scan.createRow(b.number());
                r.createCell(0).setCellValue(b.number());
                r.createCell(1).setCellValue(b.tag().get());
            }

            Sheet search = sheet(wb, SEARCH, SEARCH_HEADERS, styles);
            for (SearchBank b : p.searchBanks()) {
                SearchBankData d = b.data();
                Row r = search.createRow(b.number());
                r.createCell(0).setCellValue(b.number());
                frequencyCell(r, 1, d.lower(), styles);
                frequencyCell(r, 2, d.upper(), styles);
                r.createCell(3).setCellValue(d.tag());
                r.createCell(4).setCellValue(d.modulation().name());
                r.createCell(5).setCellValue(d.step().command());
            }

            Sheet channels = sheet(wb, CHANNELS, CHANNEL_HEADERS, styles);
            for (Channel c : p.channels()) {
                ChannelData d = c.data();
                Row r = channels.createRow(c.number());
                r.createCell(0).setCellValue(c.number());
                frequencyCell(r, 1, d.frequency(), styles);
                r.createCell(2).setCellValue(d.tone().label());
                r.createCell(3).setCellValue(d.tag());
                r.createCell(4).setCellValue(d.att());
                r.createCell(5).setCellValue(d.lockout());
                r.createCell(6).setCellValue(d.beep());
                r.createCell(7).setCellValue(d.rec());
                r.createCell(8).setCellValue(d.trunk());
                r.createCell(9).setCellValue(d.priority());
                r.createCell(10).setCellValue(d.delay().label());
                r.createCell(11).setCellValue(d.modulation().name());
                r.createCell(12).setCellValue(d.step().command());
            }
            wb.write(out);
        }
    }

    private static Sheet sheet(Workbook wb, String name, List<String> headers, Styles styles) {
        Sheet sheet = wb.createSheet(name);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = header.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(styles.header);
            sheet.setColumnWidth(i, Math.max(12, headers.get(i).length() + 4) * 256);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private static void frequencyCell(Row row, int column, BigDecimal mhz, Styles styles) {
        Cell c = row.createCell(column);
        c.setCellValue(mhz.doubleValue());
        c.setCellStyle(styles.frequency);
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle frequency;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            header = wb.createCellStyle();
            header.setFont(bold);
            DataFormat format = wb.createDataFormat();
            frequency = wb.createCellStyle();
            frequency.setDataFormat(format.getFormat("0.0000"));
        }
    }

    // ---- reading ----------------------------------------------------------------------------

    /**
     * Reads a workbook written by {@link #write}.
     *
     * @throws XlsxException if a sheet or column is missing, or a cell has an invalid value (the message gives
     *     the sheet, row and column)
     */
    public static Programming read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file); Workbook wb = new XSSFWorkbook(in)) {
            Programming p = new Programming();

            Sheet scan = requireSheet(wb, SCAN);
            Map<String, Integer> cols = columns(scan, SCAN, SCAN_HEADERS);
            for (Row r : rows(scan)) {
                int bank = number(r, cols.get("Bank"), SCAN, 10);
                p.scanBanks().get(bank - 1).set(new ScanBankData(text(r, cols.get("Text Tag"))));
            }

            Sheet search = requireSheet(wb, SEARCH);
            cols = columns(search, SEARCH, SEARCH_HEADERS);
            for (Row r : rows(search)) {
                int bank = number(r, cols.get("Bank"), SEARCH, 10);
                p.searchBanks().get(bank - 1).set(new SearchBankData(
                        frequency(r, cols.get("Lower Limit Freq"), SEARCH, "Lower Limit Freq"),
                        frequency(r, cols.get("Upper Limit Freq"), SEARCH, "Upper Limit Freq"),
                        text(r, cols.get("Text Tag")),
                        enumValue(r, cols.get("Mod"), SEARCH, "Mod", Modulation::parse),
                        enumValue(r, cols.get("Steps"), SEARCH, "Steps", Step::parse)));
            }

            Sheet channels = requireSheet(wb, CHANNELS);
            final Map<String, Integer> ch = columns(channels, CHANNELS, CHANNEL_HEADERS);
            for (Row r : rows(channels)) {
                int n = number(r, ch.get("Channel"), CHANNELS, Programming.CHANNELS);
                Tone tone = Tone.parse(text(r, ch.get("Tone")))
                        .orElseThrow(() -> bad(CHANNELS, r, "Tone", text(r, ch.get("Tone"))));
                p.channel(n).set(new ChannelData(
                        frequency(r, ch.get("Frequency"), CHANNELS, "Frequency"),
                        tone,
                        text(r, ch.get("Text Tag")),
                        flag(r, ch.get("Att")),
                        flag(r, ch.get("LO")),
                        flag(r, ch.get("Beep")),
                        flag(r, ch.get("Rec")),
                        flag(r, ch.get("Trunk")),
                        flag(r, ch.get("Pri")),
                        enumValue(r, ch.get("Delay"), CHANNELS, "Delay", Delay::parse),
                        enumValue(r, ch.get("Mod"), CHANNELS, "Mod", Modulation::parse),
                        enumValue(r, ch.get("Steps"), CHANNELS, "Steps", Step::parse)));
            }
            p.markClean();
            return p;
        } catch (XlsxException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new XlsxException("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static Sheet requireSheet(Workbook wb, String name) throws XlsxException {
        Sheet s = wb.getSheet(name);
        if (s == null) {
            throw new XlsxException("Sheet " + name + " is missing");
        }
        return s;
    }

    /** Maps each expected header to its column; extra columns are ignored, missing ones are an error. */
    private static Map<String, Integer> columns(Sheet sheet, String name, List<String> expected) throws XlsxException {
        Row header = sheet.getRow(0);
        Map<String, Integer> found = new HashMap<>();
        if (header != null) {
            for (Cell c : header) {
                if (c.getCellType() == CellType.STRING) {
                    found.put(c.getStringCellValue().strip().toLowerCase(Locale.ROOT), c.getColumnIndex());
                }
            }
        }
        Map<String, Integer> result = new HashMap<>();
        for (String h : expected) {
            Integer index = found.get(h.toLowerCase(Locale.ROOT));
            if (index == null) {
                throw new XlsxException("Sheet " + name + ": column \"" + h + "\" is missing");
            }
            result.put(h, index);
        }
        return result;
    }

    private static List<Row> rows(Sheet sheet) {
        java.util.ArrayList<Row> out = new java.util.ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row r = sheet.getRow(i);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    private static XlsxException bad(String sheet, Row row, String column, String value) {
        return new XlsxException(
                "Sheet " + sheet + ", row " + (row.getRowNum() + 1) + ", column " + column + ": invalid value \"" + value + "\"");
    }

    private static String text(Row row, int column) {
        Cell c = row.getCell(column);
        if (c == null) {
            return "";
        }
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(c.getBooleanCellValue());
            default -> "";
        };
    }

    private static int number(Row row, int column, String sheet, int max) throws XlsxException {
        Cell c = row.getCell(column);
        String raw = text(row, column);
        try {
            int n = c != null && c.getCellType() == CellType.NUMERIC ? (int) c.getNumericCellValue() : Integer.parseInt(raw.strip());
            if (n < 1 || n > max) {
                throw bad(sheet, row, "number", raw);
            }
            return n;
        } catch (NumberFormatException e) {
            throw bad(sheet, row, "number", raw);
        }
    }

    private static BigDecimal frequency(Row row, int column, String sheet, String name) throws XlsxException {
        Cell c = row.getCell(column);
        if (c == null || c.getCellType() == CellType.BLANK) {
            return BigDecimal.ZERO;
        }
        try {
            if (c.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(c.getNumericCellValue());
            }
            String t = text(row, column).strip();
            return t.isEmpty() ? BigDecimal.ZERO : new BigDecimal(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw bad(sheet, row, name, text(row, column));
        }
    }

    private static boolean flag(Row row, int column) {
        Cell c = row.getCell(column);
        if (c == null) {
            return false;
        }
        if (c.getCellType() == CellType.BOOLEAN) {
            return c.getBooleanCellValue();
        }
        String t = text(row, column).strip().toLowerCase(Locale.ROOT);
        return t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("x");
    }

    private static <T> T enumValue(Row row, int column, String sheet, String name, Function<String, T> parser)
            throws XlsxException {
        String t = text(row, column);
        if (t.isBlank()) {
            return parser.apply("AUTO") != null ? parser.apply("AUTO") : parser.apply("NONE");
        }
        T v = parser.apply(t);
        if (v == null) {
            throw bad(sheet, row, name, t);
        }
        return v;
    }
}
