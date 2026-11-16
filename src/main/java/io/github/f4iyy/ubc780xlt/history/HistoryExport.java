package io.github.f4iyy.ubc780xlt.history;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Exports receptions to an XLSX workbook: one sheet {@code HISTORY}, English headers, numbers as numbers. */
public final class HistoryExport {

    public static final String SHEET = "HISTORY";

    private HistoryExport() {
    }

    public static void write(List<Reception> receptions, Path file, ZoneId zone) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = wb.createSheet(SHEET);
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            Row h = sheet.createRow(0);
            for (int i = 0; i < HistoryLog.HEADERS.size(); i++) {
                h.createCell(i).setCellValue(HistoryLog.HEADERS.get(i));
                h.getCell(i).setCellStyle(header);
                sheet.setColumnWidth(i, 16 * 256);
            }
            sheet.createFreezePane(0, 1);

            HistoryLog format = new HistoryLog(Path.of("."), zone);
            int rowIndex = 1;
            for (Reception r : receptions) {
                List<String> f = format.fields(r);
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(f.get(0));
                row.createCell(1).setCellValue(f.get(1));
                row.createCell(2).setCellValue(r.duration().toMillis() / 1000.0);
                number(row, 3, r.bank());
                number(row, 4, r.channel());
                row.createCell(5).setCellValue(Double.parseDouble(r.frequency().isEmpty() ? "0" : r.frequency()));
                row.createCell(6).setCellValue(r.tag());
                row.createCell(7).setCellValue(r.modulation());
                row.createCell(8).setCellValue(r.tone());
                row.createCell(9).setCellValue(r.maxSignal());
            }
            wb.write(out);
        }
    }

    /** Zero means unknown: the cell stays empty. */
    private static void number(Row row, int column, int value) {
        if (value != 0) {
            row.createCell(column).setCellValue(value);
        }
    }
}
