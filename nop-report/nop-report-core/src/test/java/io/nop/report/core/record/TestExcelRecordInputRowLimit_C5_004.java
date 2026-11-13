package io.nop.report.core.record;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import io.nop.report.core.XptErrors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 5.4-C5-004：Excel 导入复用 ExcelIOConfig.maxCountPerSheet 作为读侧数据行上界，超限在解析期显式抛错
 * （collector 不再无界物化整张 sheet）；表头行不计入，与写侧口径一致；maxCountPerSheet <= 0 不设上界。
 */
public class TestExcelRecordInputRowLimit_C5_004 extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    IResource writeRows(String name, int rows) throws IOException {
        ExcelResourceIO<Object> io = new ExcelResourceIO<>();
        io.setHeaders(Arrays.asList("a", "b", "c"));
        IResource resource = getTargetResource(name);
        IRecordOutput<Object> output = io.openOutput(resource, null);
        output.beginWrite(new HashMap<>());
        for (int i = 0; i < rows; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("a", i + 1);
            data.put("b", i * 10 + 1);
            data.put("c", i * 100 + 1);
            output.write(data);
        }
        output.endWrite(new HashMap<>());
        output.close();
        return resource;
    }

    List<Map<String, Object>> read(IResource resource, ExcelIOConfig config) throws IOException {
        ExcelResourceIO<Map<String, Object>> io = new ExcelResourceIO<>();
        io.setIOConfig(config);
        IRecordInput<Map<String, Object>> input = io.openInput(resource, null);
        try {
            input.beforeRead(new HashMap<>());
            return input.readAll();
        } finally {
            input.close();
        }
    }

    static ExcelIOConfig limit(int maxCount) {
        ExcelIOConfig config = new ExcelIOConfig();
        config.setMaxCountPerSheet(maxCount);
        return config;
    }

    @Test
    public void testRowCountOverLimitRejected() throws Exception {
        IResource resource = writeRows("c5-004-over-limit.xlsx", 5);
        NopException e = assertThrows(NopException.class, () -> read(resource, limit(3)));
        assertEquals(XptErrors.ERR_XPT_EXCEL_READ_ROW_COUNT_EXCEED_LIMIT.getErrorCode(), e.getErrorCode());
        assertEquals(3, ((Number) e.getParam(XptErrors.ARG_MAX_COUNT)).intValue());
    }

    // 表头行不计入上界：5 个数据行 + 1 个表头行，上界 5 时全部读取
    @Test
    public void testRowCountEqualToLimitReadsAllDataRows() throws Exception {
        IResource resource = writeRows("c5-004-equal-limit.xlsx", 5);
        List<Map<String, Object>> list = read(resource, limit(5));
        assertEquals(5, list.size());
        assertEquals(1, list.get(0).get("a"));
        assertEquals(401, list.get(4).get("c"));
    }

    @Test
    public void testNonPositiveLimitDisablesBound() throws Exception {
        IResource resource = writeRows("c5-004-no-limit.xlsx", 5);
        assertEquals(5, read(resource, limit(0)).size());
    }

    @Test
    public void testDefaultLimitKeepsSmallSheetsUnaffected() throws Exception {
        IResource resource = writeRows("c5-004-default.xlsx", 5);
        assertEquals(5, read(resource, new ExcelIOConfig()).size());
    }
}
