package io.nop.report.core.record;

import io.nop.api.core.json.JSON;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.record.IRecordOutputProvider;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import io.nop.ooxml.xlsx.parse.XlsxToRecordOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestExcelResourceIO extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testRead() throws IOException {
        ExcelResourceIO<Map<String, Object>> io = new ExcelResourceIO<>();
        IRecordInput<Map<String, Object>> input = io.openInput(new ClassPathResource("classpath:io/nop/report/core/excel-input.xlsx"), null);
        input.beforeRead(new HashMap<>());

        List<Map<String, Object>> list = input.readAll();
        System.out.println(JSON.serialize(list, true));
        assertEquals(1, list.get(0).get("a"));
        assertEquals(9001, list.get(9).get("c"));
        input.close();
    }

    @Test
    public void testReadSpecifiedHeader() throws IOException {
        ExcelResourceIO<Map<String, Object>> io = new ExcelResourceIO<>();
        ExcelIOConfig config = new ExcelIOConfig();
        io.setIOConfig(config);
        // 指定 headers
        io.setHeaders(Arrays.asList("a","b","c"));

        IRecordInput<Map<String, Object>> input = io.openInput(new ClassPathResource("classpath:io/nop/report/core/excel-input.xlsx"), null);
        input.beforeRead(new HashMap<>());

        List<Map<String, Object>> list = input.readAll();
        System.out.println(JSON.serialize(list, true));
        assertEquals(1, list.get(0).get("a"));
        assertEquals(1, list.get(0).get("b"));
        assertEquals(1, list.get(0).get("c"));
        assertEquals(10, list.get(9).get("a"));
        assertEquals(901, list.get(9).get("b"));
        assertEquals(9001, list.get(9).get("c"));
        input.close();
    }

    @Test
    public void testReadHeaderWithNull() throws IOException {
        ExcelResourceIO<Map<String, Object>> io = new ExcelResourceIO<>();
        ExcelIOConfig config = new ExcelIOConfig();
        // header 为空的列，就不会处理
        io.setHeaders(Arrays.asList("a",null/*b*/,"c",null/*d*/,null/*e*/,"f"));
        io.setIOConfig(config);

        IRecordInput<Map<String, Object>> input = io.openInput(new ClassPathResource("classpath:io/nop/report/core/excel-input.xlsx"), null);
        input.beforeRead(new HashMap<>());

        List<Map<String, Object>> list = input.readAll();
        System.out.println(JSON.serialize(list, true));
        assertEquals(1, list.get(0).get("a"));
        assertNull(list.get(0).get("b"));
        assertEquals(1, list.get(0).get("c"));
        assertNull(list.get(0).get("d"));
        assertNull(list.get(0).get("e"));
        assertEquals(11, list.get(0).get("f"));
        input.close();
    }

    @Test
    public void testReadMultiHeader() throws IOException {
        ExcelResourceIO<Map<String, Object>> io = new ExcelResourceIO<>();
        ExcelIOConfig config = new ExcelIOConfig();
        // Excel文件的前两行是header
        config.setHeaderRowCount(2);
        io.setIOConfig(config);

        IRecordInput<Map<String, Object>> input = io.openInput(new ClassPathResource("classpath:io/nop/report/core/excel-input.xlsx"), null);
        input.beforeRead(new HashMap<>());

        List<Map<String, Object>> list = input.readAll();
        System.out.println(JSON.serialize(list, true));
        assertEquals(1001, list.get(0).get("1"));
        assertEquals(9001, list.get(8).get("1"));
        input.close();
    }

    @Test
    public void testWrite() throws IOException {
        ExcelResourceIO<Object> io = new ExcelResourceIO<>();
        io.setHeaders(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"));

        IResource resource = getTargetResource("excel-output.xlsx");

        IRecordOutput<Object> output = io.openOutput(resource, null);

        Map<String, Object> headerMeta = new HashMap<>();
        output.beginWrite(headerMeta);

        for (int i = 0; i < 10; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("a", i + 1);
            data.put("b", i * 100 + 1);
            data.put("c", i * 1000 + 1);

            output.write(data);
        }

        Map<String, Object> trailerMeta = new HashMap<>();
        output.endWrite(trailerMeta);

        output.close();
    }

    @Test
    public void testWriteMultiSheet() throws IOException {
        ExcelResourceIO<Object> io = new ExcelResourceIO<>();
        io.setHeaders(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"));

        ExcelIOConfig config = new ExcelIOConfig();
        config.setMaxCountPerSheet(3);
        io.setIOConfig(config);

        IResource resource = getTargetResource("excel-output-multisheet.xlsx");

        IRecordOutput<Object> output = io.openOutput(resource, null);

        Map<String, Object> headerMeta = new HashMap<>();
        output.beginWrite(headerMeta);

        for (int i = 0; i < 7; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("a", i + 1);
            data.put("b", i * 100 + 1);
            data.put("c", i * 1000 + 1);

            output.write(data);
        }

        Map<String, Object> trailerMeta = new HashMap<>();
        output.endWrite(trailerMeta);

        long globalWriteCount = output.getWriteCount();

        output.close();

        // Verify multi-sheet output by reading each sheet back
        Map<String, List<List<Object>>> sheetData = new LinkedHashMap<>();

        IRecordOutputProvider<List<Object>> provider = name -> new IRecordOutput<>() {
            private final List<List<Object>> rows = new ArrayList<>();

            @Override
            public void write(List<Object> record) {
                rows.add(new ArrayList<>(record));
            }

            @Override
            public long getWriteCount() {
                return rows.size();
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
                sheetData.put(name, rows);
            }

            @Override
            public void beginWrite(Map<String, Object> attributes) {
            }

            @Override
            public void endWrite(Map<String, Object> trailerMeta) throws IOException {
            }
        };

        XlsxToRecordOutput parser = new XlsxToRecordOutput(provider);
        parser.loadFromResource(resource);
        parser.parseSheet("Data");
        parser.parseSheet("Data-2");
        parser.parseSheet("Data-3");
        parser.close();

        assertEquals(3, sheetData.size(), "Should have 3 data sheets");

        // Sheet 1: header row + 3 data rows
        assertEquals(4, sheetData.get("Data").size());
        // Sheet 2: header row + 3 data rows
        assertEquals(4, sheetData.get("Data-2").size());
        // Sheet 3: header row + 1 data row
        assertEquals(2, sheetData.get("Data-3").size());

        // Verify headers in first sheet
        assertEquals("a", sheetData.get("Data").get(0).get(0));
        assertEquals("b", sheetData.get("Data").get(0).get(1));

        // Verify data values across sheets
        assertEquals(1, ((Number) sheetData.get("Data").get(1).get(0)).intValue());
        assertEquals(2, ((Number) sheetData.get("Data").get(2).get(0)).intValue());
        assertEquals(3, ((Number) sheetData.get("Data").get(3).get(0)).intValue());

        assertEquals(4, ((Number) sheetData.get("Data-2").get(1).get(0)).intValue());
        assertEquals(5, ((Number) sheetData.get("Data-2").get(2).get(0)).intValue());
        assertEquals(6, ((Number) sheetData.get("Data-2").get(3).get(0)).intValue());

        assertEquals(7, ((Number) sheetData.get("Data-3").get(1).get(0)).intValue());

        // Verify global getWriteCount() returns total
        assertEquals(7, globalWriteCount);
    }
}
