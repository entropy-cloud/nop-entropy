package io.nop.record.serialization;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.RecordConstants;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.impl.DefaultFieldCodecContext;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.record.resource.ModelBasedBinaryRecordInput;
import io.nop.record.resource.ModelBasedTextRecordInput;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.4-C5-R2-001：rawVarName 诊断变量只承载有界前缀，不对整个区域做 peek+hex 放大驻留。
 */
public class TestRawDataDiagTruncation_C5_R2_001 extends BaseTestCase {
    static final String TRUNCATED_MARK = "...(truncated)";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    RecordFileMeta meta(String path) {
        return (RecordFileMeta) new DslModelParser().parseFromVirtualPath(path);
    }

    Object decodeBinary(String model, byte[] data, boolean streaming, Map<String, Object>[] holder) throws Exception {
        RecordFileMeta fileMeta = meta(model);
        DefaultFieldCodecContext context = new DefaultFieldCodecContext(fileMeta::getType);
        ModelBasedBinaryRecordInput<Map<String, Object>> input = new ModelBasedBinaryRecordInput<>(
                new ByteBufferBinaryDataReader(data), fileMeta, streaming, context, FieldCodecRegistry.DEFAULT);
        holder[0] = input.next();
        input.close();
        return context.getValue("raw");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testShortRegionRawVarIsFullHex() throws Exception {
        Map<String, Object>[] holder = new Map[1];
        Object raw = decodeBinary("/test/record/test-raw-var-name-short.record-file.xml",
                "hello".getBytes(StandardCharsets.UTF_8), false, holder);
        assertEquals("68656c6c6f", raw);
        assertEquals("hello", holder[0].get("a"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testLongRegionRawVarIsTruncated() throws Exception {
        Map<String, Object>[] holder = new Map[1];
        Object raw = decodeBinary("/test/record/test-raw-var-name-long.record-file.xml",
                "A".repeat(5000).getBytes(StandardCharsets.UTF_8), false, holder);
        assertTruncatedHex((String) raw);
        assertEquals(5000, ((String) holder[0].get("a")).length());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testLongRegionRawVarIsTruncatedInStreamingMode() throws Exception {
        Map<String, Object>[] holder = new Map[1];
        Object raw = decodeBinary("/test/record/test-raw-var-name-long.record-file.xml",
                "A".repeat(5000).getBytes(StandardCharsets.UTF_8), true, holder);
        assertTruncatedHex((String) raw);
    }

    @Test
    public void testLongTextRegionRawVarIsTruncated() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-raw-var-name-text.record-file.xml");
        DefaultFieldCodecContext context = new DefaultFieldCodecContext(fileMeta::getType);
        ModelBasedTextRecordInput<Map<String, Object>> input = new ModelBasedTextRecordInput<>(
                new SimpleTextDataReader("B".repeat(5000)), fileMeta, false, context, FieldCodecRegistry.DEFAULT);
        Map<String, Object> record = input.next();
        input.close();

        String raw = (String) context.getValue("raw");
        assertEquals(RecordConstants.MAX_RAW_DATA_DIAG_CHARS + TRUNCATED_MARK.length(), raw.length());
        assertTrue(raw.startsWith("BBBB"));
        assertTrue(raw.endsWith(TRUNCATED_MARK));
        assertEquals(5000, ((String) record.get("a")).length());
    }

    void assertTruncatedHex(String raw) {
        assertEquals(RecordConstants.MAX_RAW_DATA_DIAG_CHARS + TRUNCATED_MARK.length(), raw.length());
        assertTrue(raw.startsWith("41414141"));
        assertTrue(raw.endsWith(TRUNCATED_MARK));
    }
}
