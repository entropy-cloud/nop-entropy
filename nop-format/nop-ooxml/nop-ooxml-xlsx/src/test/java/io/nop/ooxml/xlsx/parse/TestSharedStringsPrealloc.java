package io.nop.ooxml.xlsx.parse;

import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.ooxml.xlsx.model.SharedStringsPart;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归：uniqueCount 是不可信的上传属性，不能按其预分配集合容量。
 * 修复前 uniqueCount="2000000000" 会立即申请约8GB数组
 */
public class TestSharedStringsPrealloc {

    static SharedStringsPart parse(String xml) {
        return new SharedStringsTableParser(true).parseFromResource(
                new ByteArrayResource("/sharedStrings.xml", xml.getBytes(StandardCharsets.UTF_8), 0));
    }

    @Test
    public void testHugeUniqueCountDoesNotPreallocate() {
        String xml = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "count=\"2\" uniqueCount=\"2000000000\">"
                + "<si><t>hello</t></si>"
                + "<si><t>world</t></si>"
                + "</sst>";

        // 修复前此处抛 OutOfMemoryError（或负值抛 IllegalArgumentException）
        SharedStringsPart part = parse(xml);
        assertEquals(2, part.getItems().size());
        assertEquals("hello", part.getItemAt(0));
        assertEquals("world", part.getItemAt(1));
    }

    @Test
    public void testNegativeUniqueCountTolerated() {
        String xml = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "count=\"1\" uniqueCount=\"-5\">"
                + "<si><t>x</t></si>"
                + "</sst>";

        SharedStringsPart part = parse(xml);
        assertEquals(1, part.getItems().size());
    }
}
