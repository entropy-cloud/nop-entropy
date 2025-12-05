package io.nop.record.resource;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.ITextDataReader;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestModelBasedRecordInput extends BaseTestCase {
    @BeforeAll
    public static void init(){
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy(){
        CoreInitialization.destroy();
    }

    @Test
    public void testInput() throws IOException {
        ITextDataReader in = new SimpleTextDataReader("5    aa12345678901234567890123");
        RecordFileMeta fileMeta = (RecordFileMeta) new DslModelParser().parseFromVirtualPath("/test/record/test.record-file.xml");
        ModelBasedTextRecordInput<Object> input = new ModelBasedTextRecordInput<>(in, fileMeta);
        input.beforeRead(new HashMap<>());
        Object record = input.next();
        assertEquals("{a=5, b=aa12345678}", record.toString());
        assertEquals("{a=90123, b=4567890123}", input.next().toString());
        assertFalse(input.hasNext());
    }
}
