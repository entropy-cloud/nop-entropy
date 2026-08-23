package io.nop.record.writer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 修复 append(char[],start,end) 非 StringBuilder 分支对区间双重应用导致越界/错写的问题
 */
public class TestAppendableTextDataWriter {

    @Test
    public void testAppendCharArrayRangeWithStringBuilder() throws Exception {
        StringBuilder sb = new StringBuilder();
        AppendableTextDataWriter writer = new AppendableTextDataWriter(sb);
        writer.append(new char[]{'a', 'b', 'c', 'd', 'e'}, 1, 4);
        assertEquals("bcd", sb.toString());
        assertEquals(3, writer.length());
    }

    @Test
    public void testAppendCharArrayRangeWithNonStringBuilder() throws Exception {
        // StringBuffer 走 MutableString 分支（修复前区间应用两次：IndexOutOfBounds 或错字符）
        StringBuffer buf = new StringBuffer();
        AppendableTextDataWriter writer = new AppendableTextDataWriter(buf);
        writer.append(new char[]{'a', 'b', 'c', 'd', 'e'}, 1, 4);
        assertEquals("bcd", buf.toString());
        assertEquals(3, writer.length());

        writer.append(new char[]{'x', 'y', 'z'}, 0, 3);
        assertEquals("bcdxyz", buf.toString());
    }
}
