package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.RecordErrors;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.reader.StreamBinaryDataReader;
import io.nop.record.resource.ModelBasedBinaryRecordInput;
import io.nop.record.resource.ModelBasedBinaryRecordOutput;
import io.nop.record.resource.ModelBasedTextRecordOutput;
import io.nop.record.writer.AppendableTextDataWriter;
import io.nop.record.writer.StreamBinaryDataWriter;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRecordRegionBoundary extends BaseTestCase {

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

    // 区域残留：body 对象 length=20 但字段只消费 15，第 2 条记录必须对齐解析（惰性 reader）
    @Test
    public void testRegionLeftoverAlignedWithLazyReader() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-region-boundary.record-file.xml");
        byte[] data = "aaaaabbbbbccccc01234dddddeeeeefffff01234".getBytes(StandardCharsets.UTF_8);
        StreamBinaryDataReader in = new StreamBinaryDataReader(new ByteArrayInputStream(data));
        ModelBasedBinaryRecordInput<Map<String, Object>> input = new ModelBasedBinaryRecordInput<>(in, fileMeta);

        Map<String, Object> r1 = input.next();
        assertEquals("aaaaa", r1.get("a"));
        assertEquals("bbbbb", r1.get("b"));
        assertEquals("ccccc", r1.get("c"));

        Map<String, Object> r2 = input.next();
        assertEquals("ddddd", r2.get("a"));
        assertEquals("eeeee", r2.get("b"));
        assertEquals("fffff", r2.get("c"));
        input.close();
    }

    // 急切 reader（ByteBuffer）：readObject 对齐后不双重跳过
    @Test
    public void testRegionLeftoverWithEagerReader() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-region-boundary.record-file.xml");
        byte[] data = "aaaaabbbbbccccc01234dddddeeeeefffff01234".getBytes(StandardCharsets.UTF_8);
        ModelBasedBinaryRecordInput<Map<String, Object>> input = new ModelBasedBinaryRecordInput<>(
                new ByteBufferBinaryDataReader(data), fileMeta);

        Map<String, Object> r1 = input.next();
        assertEquals("aaaaa", r1.get("a"));
        assertEquals("ccccc", r1.get("c"));
        Map<String, Object> r2 = input.next();
        assertEquals("ddddd", r2.get("a"));
        assertEquals("fffff", r2.get("c"));
        input.close();
    }

    // fixed 集合：子视图边界，items 读满区域 15（3 个 item），不越界解析 c
    @Test
    public void testFixedCollectionBoundary() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-fixed-collection-boundary.record-file.xml");
        byte[] data = "111112222233333CCCCC".getBytes(StandardCharsets.UTF_8);
        StreamBinaryDataReader in = new StreamBinaryDataReader(new ByteArrayInputStream(data));
        ModelBasedBinaryRecordInput<Map<String, Object>> input = new ModelBasedBinaryRecordInput<>(in, fileMeta);

        Map<String, Object> record = input.next();
        List<?> items = (List<?>) record.get("items");
        assertEquals(3, items.size());
        assertEquals("11111", ((Map<?, ?>) items.get(0)).get("i1"));
        assertEquals("22222", ((Map<?, ?>) items.get(1)).get("i1"));
        assertEquals("33333", ((Map<?, ?>) items.get(2)).get("i1"));
        assertEquals("CCCCC", record.get("c"));
        input.close();
    }

    // body 缺失：Input 构造抛 ERR_RECORD_BODY_NOT_DEFINED；Output beginWrite 抛
    @Test
    public void testBodyMissingFailsFast() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-no-body.record-file.xml");
        NopException e = assertThrows(NopException.class,
                () -> new ModelBasedBinaryRecordInput<>(new StreamBinaryDataReader(
                        new ByteArrayInputStream("hhhhhttttt".getBytes(StandardCharsets.UTF_8))), fileMeta));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_BODY_NOT_DEFINED.getErrorCode()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ModelBasedBinaryRecordOutput<Object> output = new ModelBasedBinaryRecordOutput<>(
                new StreamBinaryDataWriter(out), fileMeta);
        assertThrows(NopException.class, () -> output.beginWrite(Map.of()));
    }

    // 二进制 EOF：惰性 reader 短数据 → ERR_RECORD_NO_ENOUGH_DATA（字段级定长，对象无 length）
    @Test
    public void testBinaryEofStrictWithLazyReader() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-eof-short-data.record-file.xml");
        byte[] data = "abcde".getBytes(StandardCharsets.UTF_8);
        StreamBinaryDataReader in = new StreamBinaryDataReader(new ByteArrayInputStream(data));
        NopException e = assertThrows(NopException.class,
                () -> new ModelBasedBinaryRecordInput<>(in, fileMeta));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_NO_ENOUGH_DATA.getErrorCode()));
    }

    // 二进制 EOF：ByteBuffer reader 数据不足 → 统一 ERR_RECORD_NO_ENOUGH_DATA（非 BufferUnderflowException）
    @Test
    public void testBinaryEofStrictWithByteBuffer() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-eof-short-data.record-file.xml");
        byte[] data = "abcde".getBytes(StandardCharsets.UTF_8);
        NopException e = assertThrows(NopException.class,
                () -> new ModelBasedBinaryRecordInput<>(new ByteBufferBinaryDataReader(data), fileMeta));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_NO_ENOUGH_DATA.getErrorCode()));
    }

    // 流式对象 length>0 区域残留对齐（R2-1）：帧完成后续帧对齐（每条记录 3 个 item：字段数据/字段结束/记录结束）
    @Test
    public void testStreamingRegionLeftoverAligned() throws Exception {
        RecordFileMeta fileMeta = meta("/test/record/test-region-boundary.record-file.xml");
        byte[] data = "aaaaabbbbbccccc01234dddddeeeeefffff01234".getBytes(StandardCharsets.UTF_8);
        StreamBinaryDataReader in = new StreamBinaryDataReader(new ByteArrayInputStream(data));
        ModelBasedBinaryRecordInput<StreamingItem> input = new ModelBasedBinaryRecordInput<>(in, fileMeta, true);
        List<StreamingItem> items = input.readAll();
        assertEquals(2, items.stream().filter(it -> it.isEndOfObject()).count());
        assertEquals("ccccc", items.get(0).getStreamingData());
        assertEquals("fffff", items.get(3).getStreamingData());
        input.close();
    }
}
