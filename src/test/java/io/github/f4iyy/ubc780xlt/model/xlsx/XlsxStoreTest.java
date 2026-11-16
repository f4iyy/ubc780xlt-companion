package io.github.f4iyy.ubc780xlt.model.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tone;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XlsxStoreTest {

    @TempDir
    Path dir;

    private static Programming sample() {
        Programming p = new Programming();
        p.scanBanks().get(0).tag().set("WORK");
        p.scanBanks().get(9).tag().set("AERO");
        p.searchBanks().get(0).set(new SearchBankData(new BigDecimal("144"), new BigDecimal("146"), "RA VHF",
                Modulation.AUTO, Step.AUTO));
        p.searchBanks().get(4).set(new SearchBankData(new BigDecimal("108"), new BigDecimal("136"), "AERO",
                Modulation.AM, Step.K25));
        p.channel(1).set(new ChannelData(new BigDecimal("145.475"), new Tone(18, true), "CALL", true, false, true,
                false, true, true, Delay.S2, Modulation.NFM, Step.K12_5));
        p.channel(500).set(new ChannelData(new BigDecimal("1296.5"), new Tone(142, false), "LAST", false, true,
                false, true, false, false, Delay.M10, Modulation.WFM, Step.K100));
        p.markClean();
        return p;
    }

    @Test
    void roundTripKeepsEverything() throws IOException {
        Path file = dir.resolve("scanner.xlsx");
        Programming original = sample();
        XlsxStore.write(original, file);

        Programming loaded = XlsxStore.read(file);

        assertEquals(original.snapshot(), loaded.snapshot());
        for (int i = 0; i < 10; i++) {
            assertEquals(original.scanBanks().get(i).data(), loaded.scanBanks().get(i).data());
            assertEquals(original.searchBanks().get(i).data(), loaded.searchBanks().get(i).data());
        }
        assertFalse(loaded.isModified());
    }

    @Test
    void theWorkbookHasThreeSheetsWithEnglishHeadersAndTypedCells() throws IOException {
        Path file = dir.resolve("scanner.xlsx");
        XlsxStore.write(sample(), file);

        try (Workbook wb = new XSSFWorkbook(Files.newInputStream(file))) {
            assertEquals(3, wb.getNumberOfSheets());
            assertEquals("SCAN", wb.getSheetAt(0).getSheetName());
            assertEquals("SEARCH", wb.getSheetAt(1).getSheetName());
            assertEquals("CHANNELS", wb.getSheetAt(2).getSheetName());

            Row header = wb.getSheet("CHANNELS").getRow(0);
            for (int i = 0; i < XlsxStore.CHANNEL_HEADERS.size(); i++) {
                assertEquals(XlsxStore.CHANNEL_HEADERS.get(i), header.getCell(i).getStringCellValue());
            }
            assertEquals(501, wb.getSheet("CHANNELS").getLastRowNum() + 1);
            assertEquals(11, wb.getSheet("SEARCH").getLastRowNum() + 1);

            Row ch1 = wb.getSheet("CHANNELS").getRow(1);
            assertEquals(CellType.NUMERIC, ch1.getCell(1).getCellType());
            assertEquals(145.475, ch1.getCell(1).getNumericCellValue(), 1e-9);
            assertEquals("123.0 Hz L", ch1.getCell(2).getStringCellValue());
            assertEquals(CellType.BOOLEAN, ch1.getCell(4).getCellType());
            assertTrue(ch1.getCell(4).getBooleanCellValue());
            assertEquals("+2", ch1.getCell(10).getStringCellValue());
            assertEquals("NFM", ch1.getCell(11).getStringCellValue());
            assertEquals("12.5K", ch1.getCell(12).getStringCellValue());
        }
    }

    // ---- reading problems ----

    private Path workbook(String name, WorkbookEdit edit) throws IOException {
        Path file = dir.resolve(name);
        XlsxStore.write(sample(), file);
        Workbook wb;
        try (var in = Files.newInputStream(file)) {
            wb = new XSSFWorkbook(in);
        }
        edit.apply(wb);
        try (OutputStream out = Files.newOutputStream(file)) {
            wb.write(out);
        }
        wb.close();
        return file;
    }

    private interface WorkbookEdit {
        void apply(Workbook wb);
    }

    @Test
    void aMissingSheetIsReported() throws IOException {
        Path f = workbook("nosheet.xlsx", wb -> wb.removeSheetAt(1));
        XlsxException e = assertThrows(XlsxException.class, () -> XlsxStore.read(f));
        assertTrue(e.getMessage().contains("SEARCH"), e.getMessage());
    }

    @Test
    void aMissingColumnIsReported() throws IOException {
        Path f = workbook("nocol.xlsx", wb -> wb.getSheet("CHANNELS").getRow(0).getCell(2).setCellValue("Tonality"));
        XlsxException e = assertThrows(XlsxException.class, () -> XlsxStore.read(f));
        assertTrue(e.getMessage().contains("\"Tone\""), e.getMessage());
    }

    @Test
    void extraColumnsAndColumnOrderDoNotMatter() throws IOException {
        Path f = workbook("extra.xlsx", wb -> {
            Sheet s = wb.getSheet("SCAN");
            s.getRow(0).createCell(5).setCellValue("Comment");
            s.getRow(1).createCell(5).setCellValue("ignored");
            // Swap the header names of the two columns and their contents.
            s.getRow(0).getCell(0).setCellValue("text tag");
            s.getRow(0).getCell(1).setCellValue("BANK");
            for (int r = 1; r <= 10; r++) {
                Cell a = s.getRow(r).getCell(0);
                Cell b = s.getRow(r).getCell(1);
                double bank = a.getNumericCellValue();
                String tag = b.getStringCellValue();
                a.setCellValue(tag);
                b.setCellValue(bank);
            }
        });
        Programming p = XlsxStore.read(f);
        assertEquals("WORK", p.scanBanks().get(0).data().tag());
        assertEquals("AERO", p.scanBanks().get(9).data().tag());
    }

    @Test
    void invalidValuesNameTheCell() throws IOException {
        Path badTone = workbook("tone.xlsx", wb -> wb.getSheet("CHANNELS").getRow(3).getCell(2).setCellValue("77 Hz"));
        XlsxException e = assertThrows(XlsxException.class, () -> XlsxStore.read(badTone));
        assertTrue(e.getMessage().contains("row 4") && e.getMessage().contains("Tone"), e.getMessage());

        Path badMod = workbook("mod.xlsx", wb -> wb.getSheet("CHANNELS").getRow(2).getCell(11).setCellValue("SSB"));
        assertTrue(assertThrows(XlsxException.class, () -> XlsxStore.read(badMod)).getMessage().contains("Mod"));

        Path badFreq = workbook("freq.xlsx", wb -> wb.getSheet("SEARCH").getRow(1).getCell(1).setCellValue("abc"));
        assertTrue(assertThrows(XlsxException.class, () -> XlsxStore.read(badFreq)).getMessage()
                .contains("Lower Limit Freq"));

        Path badNumber = workbook("num.xlsx", wb -> wb.getSheet("CHANNELS").getRow(1).getCell(0).setCellValue(900));
        assertTrue(assertThrows(XlsxException.class, () -> XlsxStore.read(badNumber)).getMessage().contains("number"));

        Path textNumber = workbook("txt.xlsx", wb -> wb.getSheet("SCAN").getRow(1).getCell(0).setCellValue("x"));
        assertThrows(XlsxException.class, () -> XlsxStore.read(textNumber));
    }

    @Test
    void lenientAboutBlanksTextFrequenciesAndFlags() throws IOException {
        Path f = workbook("lenient.xlsx", wb -> {
            Row r = wb.getSheet("CHANNELS").getRow(2);
            r.getCell(1).setCellValue("430,5");       // frequency typed as text with a comma
            r.getCell(4).setCellValue("yes");          // flag as text
            r.getCell(5).setCellValue("0");
            r.getCell(10).setBlank();                  // blank enum: default
            r.getCell(11).setBlank();
            wb.getSheet("CHANNELS").getRow(3).getCell(1).setBlank(); // blank frequency: unused
            wb.getSheet("CHANNELS").getRow(4).getCell(1).setCellValue("");
            wb.getSheet("CHANNELS").removeRow(wb.getSheet("CHANNELS").getRow(10)); // missing row: default
        });
        Programming p = XlsxStore.read(f);
        ChannelData c2 = p.channel(2).data();
        assertEquals(new BigDecimal("430.5000"), c2.frequency());
        assertTrue(c2.att());
        assertFalse(c2.lockout());
        assertEquals(Delay.NONE, c2.delay());
        assertEquals(Modulation.AUTO, c2.modulation());
        assertTrue(p.channel(3).data().isEmpty());
        assertTrue(p.channel(4).data().isEmpty());
        assertEquals(ChannelData.empty(), p.channel(9).data());
    }

    @Test
    void aFileThatIsNotAWorkbookIsReported() throws IOException {
        Path f = dir.resolve("junk.xlsx");
        Files.writeString(f, "this is not a zip file");
        XlsxException e = assertThrows(XlsxException.class, () -> XlsxStore.read(f));
        assertTrue(e.getMessage().contains("junk.xlsx"), e.getMessage());
    }

    @Test
    void aMissingFileIsAnIoError() {
        assertThrows(IOException.class, () -> XlsxStore.read(dir.resolve("absent.xlsx")));
    }
}
