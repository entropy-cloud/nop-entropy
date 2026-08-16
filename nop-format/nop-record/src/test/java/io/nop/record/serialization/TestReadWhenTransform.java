package io.nop.record.serialization;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.RecordErrors;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.SimpleTextDataReader;
import io.nop.record.resource.ModelBasedTextRecordInput;
import io.nop.record.resource.ModelBasedTextRecordOutput;
import io.nop.record.writer.AppendableTextDataWriter;
import io.nop.record.writer.ITextDataWriter;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReadWhenTransform extends BaseTestCase {

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

    Map<String, Object> readFirst(String data, RecordFileMeta fileMeta) {
        try {
            ModelBasedTextRecordInput<Map<String, Object>> input = new ModelBasedTextRecordInput<>(
                    new SimpleTextDataReader(data), fileMeta);
            Map<String, Object> record = input.next();
            input.close();
            return record;
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    // mode='0'：a/f/g 全部 readWhen=false 跳过
    @Test
    public void testFieldReadWhenSkippedWithHeaderVar() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-when-transform.record-file.xml");
        Map<String, Object> record = readFirst("0aaaaabbbbbcccccon   GGGGG0", fileMeta);
        assertNull(record.get("a"));
        assertEquals("aaaaa", record.get("b"));
        assertEquals("bbbbbX", record.get("c"));
        assertEquals("ccccc", record.get("d"));
        assertEquals("on", record.get("e"));
        assertNull(record.get("f"));
        assertNull(record.get("g"));
        assertEquals("GGGGG", record.get("h"));
        assertTrue(((List<?>) record.get("items")).isEmpty());
    }

    // mode='1'：a/f/g/items 全部解析
    @Test
    public void testFieldReadWhenAllParsed() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-when-transform.record-file.xml");
        Map<String, Object> record = readFirst("111111bbbbbcccccdddddon   fffffiiiiihhhhh2iiiiijjjjj", fileMeta);
        assertEquals("11111", record.get("a"));
        assertEquals("bbbbb", record.get("b"));
        assertEquals("cccccX", record.get("c"));
        assertEquals("ddddd", record.get("d"));
        assertEquals("on", record.get("e"));
        assertEquals("fffff", record.get("f"));
        assertEquals("iiiii", ((Map<?, ?>) record.get("g")).get("i1"));
        assertEquals("hhhhh", record.get("h"));
        List<?> items = (List<?>) record.get("items");
        assertEquals(2, items.size());
        assertEquals("iiiii", ((Map<?, ?>) items.get(0)).get("i1"));
        assertEquals("jjjjj", ((Map<?, ?>) items.get(1)).get("i1"));
    }

    // d='off' 时 e 跳过（记录内字段引用）
    @Test
    public void testFieldReadWhenWithRecordField() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-when-transform.record-file.xml");
        Map<String, Object> record = readFirst("0aaaaabbbbboff  fffff0", fileMeta);
        assertEquals("bbbbbX", record.get("c"));
        assertEquals("off  ", record.get("d"));
        assertNull(record.get("e"));
        assertEquals("fffff", record.get("h"));
    }

    // 集合 item readWhen=false：null 不加入集合（count 分支；item 全跳过不消费，数据恰好 EOF）
    @Test
    public void testCollectionItemReadWhenNotAdded() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-when-transform.record-file.xml");
        Map<String, Object> record = readFirst("0aaaaabbbbbcccccon   GGGGG2", fileMeta);
        assertTrue(((List<?>) record.get("items")).isEmpty());
        assertEquals("GGGGG", record.get("h"));
    }

    // 集合 item readWhen=true：item 正常解析并加入集合
    @Test
    public void testCollectionItemReadWhenParsed() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-when-transform.record-file.xml");
        Map<String, Object> record = readFirst("111111bbbbbcccccdddddon   fffffiiiiihhhhh2iiiiijjjjj", fileMeta);
        List<?> items = (List<?>) record.get("items");
        assertEquals(2, items.size());
        assertEquals("iiiii", ((Map<?, ?>) items.get(0)).get("i1"));
        assertEquals("jjjjj", ((Map<?, ?>) items.get(1)).get("i1"));
    }

    // body 顶级 readWhen 快速失败
    @Test
    public void testBodyReadWhenFailsFast() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-top-level-when.record-file.xml");
        NopException e = assertThrows(NopException.class,
                () -> new ModelBasedTextRecordInput<>(new SimpleTextDataReader("aaaaa"), fileMeta));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_READWHEN_NOT_SUPPORTED_AT_TOP_LEVEL.getErrorCode()));
    }

    // header 级 readWhen=false：header 段不存在，body 从流首解析
    @Test
    public void testHeaderReadWhenNotPresent() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-header-when.record-file.xml");
        ModelBasedTextRecordInput<Map<String, Object>> input = new ModelBasedTextRecordInput<>(
                new SimpleTextDataReader("aaaaa"), fileMeta);
        assertNull(input.getHeaderMeta());
        Map<String, Object> record = input.next();
        assertEquals("aaaaa", record.get("a"));
        assertFalse(input.hasNext());
        input.close();
    }

    // 流式模式：supportStreaming 字段 readWhen 生效
    @Test
    public void testStreamingReadWhen() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-when-transform.record-file.xml");
        ModelBasedTextRecordInput<StreamingItem> input = new ModelBasedTextRecordInput<>(
                new SimpleTextDataReader("0aaaaabbbbbcccccon   GGGGG2iiiiijjjjj"), fileMeta, true);
        // 流式模式下以 StreamingItem 形式消费，字段级断言由非流式用例覆盖；此处断言流式路径不抛且能读取
        input.next();
        assertTrue(input.getReadCount() > 0);
        input.close();
    }

    // transformOut：写侧输出应用表达式
    @Test
    public void testTransformOut() throws IOException {
        RecordFileMeta fileMeta = meta("/test/record/test-when-transform.record-file.xml");
        StringBuilder sb = new StringBuilder();
        ITextDataWriter out = new AppendableTextDataWriter(sb);
        ModelBasedTextRecordOutput<Object> output = new ModelBasedTextRecordOutput<>(out, fileMeta);
        output.beginWrite(Map.of("mode", "1"));
        output.write(Map.of("a", "11111", "b", "bbbbb", "c", "cc", "d", "dd", "e", "ee",
                "f", "ff", "g", Map.of("i1", "iiiii"), "h", "hh"));
        output.endWrite(null);
        output.flush();
        String text = sb.toString();
        assertTrue(text.contains("ccO  "), text);
        assertTrue(text.contains("11111"), text);
        assertTrue(text.contains("bbbbb"), text);
    }
}
