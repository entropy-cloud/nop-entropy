package io.nop.record.resource;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.Test;

public class TestModelBasedRecordInpur extends JunitBaseTestCase {
    @Test
    public void testInput() {
        ITextDataReader in = new SimpleTextDataReader("5    aa");
        RecordFileMeta fileMeta = (RecordFileMeta) new DslModelParser().parseFromVirtualPath("/test/record/test.record-file.xml");
        //  ModelBasedTextRecordInput<Object> output = new ModelBasedTextRecordOutput<>(in, fileMeta);
        //  output.write(Map.of("a", 1, "b", "BB"));
        //  output.flush();
        // output.close();
    }
}
