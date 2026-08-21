package io.nop.record.serialization;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.ByteBufferBinaryDataReader;
import io.nop.record.resource.ModelBasedBinaryRecordInput;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 流式反序列化对 baseType 的处理：基类字段读完后必须继续读派生类型自身字段
 */
public class TestBaseTypeStreaming extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static final byte[] DATA = "aaaaabbbbbccccc01234dddddeeeeefffff01234".getBytes(StandardCharsets.UTF_8);

    RecordFileMeta meta() {
        return (RecordFileMeta) new DslModelParser().parseFromVirtualPath("/test/record/test-base-type-streaming.record-file.xml");
    }

    // 非流式对照：基类+派生字段全部读取
    @Test
    public void testNonStreaming() throws Exception {
        ModelBasedBinaryRecordInput<Map<String, Object>> input =
                new ModelBasedBinaryRecordInput<>(new ByteBufferBinaryDataReader(DATA), meta());
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

    // 流式：基类处理后派生字段（含流式字段c）不能丢失，且第二条记录对齐正确
    @Test
    public void testStreamingBaseTypeFieldsNotLost() throws Exception {
        ModelBasedBinaryRecordInput<StreamingItem> input =
                new ModelBasedBinaryRecordInput<>(new ByteBufferBinaryDataReader(DATA), meta(), true);
        List<StreamingItem> items = input.readAll();

        List<Object> values = items.stream().map(StreamingItem::getStreamingData).collect(Collectors.toList());

        // 每个流式字段产生 [数据条目, 字段结束条目] 两项，记录结束再产生 endOfObject 条目。
        // 第一条记录：基类流式字段a → a数据；派生流式字段c → c数据（修复前派生字段全部丢失）
        assertEquals("aaaaa", values.get(0));
        assertEquals("ccccc", values.get(2));

        // 每条记录恰好一个 endOfObject（基类帧不再额外产生 endOfObject）
        List<StreamingItem> eoo = items.stream().filter(StreamingItem::isEndOfObject).collect(Collectors.toList());
        assertEquals(2, eoo.size());
        // 非流式字段 b（派生类型自身字段）保存在 endOfObject 条目的 nonStreamingFields 中
        assertEquals("bbbbb", eoo.get(0).getNonStreamingField("b"));
        assertEquals("eeeee", eoo.get(1).getNonStreamingField("b"));
        // 第二条记录对齐正确
        assertEquals("ddddd", values.get(5));
        assertEquals("fffff", values.get(7));
        input.close();
    }
}
