package io.nop.report.core.record;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dataset.record.IRecordOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
}
