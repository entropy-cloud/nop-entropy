package io.nop.record.resource;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.record.writer.AppendableTextDataWriter;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 基于模型（record-file.xml）的文本记录 encode→decode roundtrip 恒等：
 * 写出侧按字段定长补齐，读回侧按同一模型解析，字段值必须还原。
 */
public class TestRecordTextRoundtrip extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    RecordFileMeta meta() {
        // body: a = int(5, 空格补齐), b = String(10)
        return (RecordFileMeta) new DslModelParser().parseFromVirtualPath("/test/record/test.record-file.xml");
    }

    static Map<String, Object> record(int a, String b) {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("a", a);
        ret.put("b", b);
        return ret;
    }

    // 单条记录：encode 产物为定长文本（a 5 字符 + b 10 字符），decode 还原字段值
    @Test
    public void testSingleRecordRoundtrip() throws Exception {
        StringBuilder sb = new StringBuilder();
        ModelBasedTextRecordOutput<Map<String, Object>> output =
                new ModelBasedTextRecordOutput<>(new AppendableTextDataWriter(sb), meta());
        output.beginWrite(null);
        output.write(record(1, "BB"));
        output.endWrite(null);
        output.close();

        // 每条记录文本宽度 = 字段宽度之和（5 + 10 = 15）
        String text = sb.toString();
        assertEquals(15, text.length());

        ModelBasedTextRecordInput<Map<String, Object>> input =
                new ModelBasedTextRecordInput<>(new SimpleTextDataReader(text), meta());
        Map<String, Object> restored = input.next();
        // 回归覆盖 wi9#6（plan 2306 项 28）：字段未配置 codec 时文本路径按声明的
        // stdDataType 反推类型，int 字段还原为 Integer 而非 String（与 FLS 二进制路径对称）
        assertEquals(1, ((Number) restored.get("a")).intValue());
        assertEquals("BB", restored.get("b"));
        input.close();
    }

    // 多条记录 roundtrip：顺序与值恒等，EOF 后 next() 返回 null
    @Test
    public void testMultipleRecordsRoundtripInOrder() throws Exception {
        StringBuilder sb = new StringBuilder();
        ModelBasedTextRecordOutput<Map<String, Object>> output =
                new ModelBasedTextRecordOutput<>(new AppendableTextDataWriter(sb), meta());
        output.beginWrite(null);
        output.write(record(1, "one"));
        output.write(record(22, "two"));
        output.write(record(333, "three"));
        output.endWrite(null);
        output.close();

        ModelBasedTextRecordInput<Map<String, Object>> input =
                new ModelBasedTextRecordInput<>(new SimpleTextDataReader(sb.toString()), meta());
        Map<String, Object> r1 = input.next();
        assertEquals(1, ((Number) r1.get("a")).intValue());
        assertEquals("one", r1.get("b"));
        Map<String, Object> r2 = input.next();
        assertEquals(22, ((Number) r2.get("a")).intValue());
        assertEquals("two", r2.get("b"));
        Map<String, Object> r3 = input.next();
        assertEquals(333, ((Number) r3.get("a")).intValue());
        assertEquals("three", r3.get("b"));
        // EOF：不再有记录（next() 在 EOF 抛 NoSuchElementException，用 hasNext 判定）
        assertEquals(false, input.hasNext());
        input.close();
    }

    // 边界值：字段宽度占满（b 用满 10 字符）时 roundtrip 仍恒等
    @Test
    public void testFieldWidthBoundaryRoundtrip() throws Exception {
        StringBuilder sb = new StringBuilder();
        ModelBasedTextRecordOutput<Map<String, Object>> output =
                new ModelBasedTextRecordOutput<>(new AppendableTextDataWriter(sb), meta());
        output.beginWrite(null);
        output.write(record(99999, "0123456789"));
        output.endWrite(null);
        output.close();

        assertEquals(15, sb.length());

        ModelBasedTextRecordInput<Map<String, Object>> input =
                new ModelBasedTextRecordInput<>(new SimpleTextDataReader(sb.toString()), meta());
        Map<String, Object> restored = input.next();
        assertEquals(99999, ((Number) restored.get("a")).intValue());
        assertEquals("0123456789", restored.get("b"));
        input.close();
    }
}
