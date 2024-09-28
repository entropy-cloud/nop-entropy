package io.nop.record.resource;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.output.IRecordTextOutput;
import io.nop.record.output.SimpleTextOutput;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class TestModelBasedRecordOutput extends JunitBaseTestCase {
    @Test
    public void testOutput() {
        IRecordTextOutput out = new SimpleTextOutput();
        RecordFileMeta fileMeta = (RecordFileMeta) new DslModelParser().parseFromVirtualPath("/test/record/test.record-file.xml");
        ModelBasedTextRecordOutput<Object> output = new ModelBasedTextRecordOutput<>(out, fileMeta);
        output.write(Map.of("a", 1, "b", "BB"));
        output.flush();
        output.close();
    }
}
