package io.nop.excel.util;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 三引号多行值语法：修复前 nextUntil 不消费结束分隔符，外层循环把残留的 """
 * 当作变量名解析，必然抛 ERR_SCAN_INVALID_XML_NAME，该语法完全不可用
 */
public class TestMultiLineConfigParser {

    @Test
    public void testTripleQuotedValue() {
        Map<String, ValueWithLocation> config = MultiLineConfigParser.INSTANCE.parseConfig(
                SourceLocation.fromPath("test"), "expr = \"\"\"\na+b\n\"\"\"");
        assertEquals("a+b", config.get("expr").getValue());
    }

    @Test
    public void testTripleQuotedValueFollowedByMoreEntries() {
        Map<String, ValueWithLocation> config = MultiLineConfigParser.INSTANCE.parseConfig(
                SourceLocation.fromPath("test"), "a = 1\nexpr = \"\"\"\nx*y\n\"\"\"\nb = 2");
        assertEquals("1", config.get("a").getValue());
        assertEquals("x*y", config.get("expr").getValue());
        assertEquals("2", config.get("b").getValue());
    }

    @Test
    public void testTripleQuotedValueAtEndNoTrailingNewline() {
        Map<String, ValueWithLocation> config = MultiLineConfigParser.INSTANCE.parseConfig(
                SourceLocation.fromPath("test"), "expr = \"\"\"\nhello\n\"\"\"");
        assertEquals(1, config.size());
    }

    @Test
    public void testSingleLineValueStillWorks() {
        Map<String, ValueWithLocation> config = MultiLineConfigParser.INSTANCE.parseConfig(
                SourceLocation.fromPath("test"), "a = 1\nb = `x`");
        assertEquals("1", config.get("a").getValue());
        assertEquals("x", config.get("b").getValue());
    }

    @Test
    public void testUnclosedTripleQuoteStillFails() {
        // 结束分隔符缺失时保持原有的快速失败行为
        assertThrows(Exception.class, () -> MultiLineConfigParser.INSTANCE.parseConfig(
                SourceLocation.fromPath("test"), "expr = \"\"\"\na+b"));
    }
}
