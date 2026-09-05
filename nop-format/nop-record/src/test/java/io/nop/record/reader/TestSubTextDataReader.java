package io.nop.record.reader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SubTextDataReader 的 readLine/skip 契约：子区间耗尽时 readLine 返回 null（EOF哨兵），
 * skip(0) 是无操作；修复前分别返回 "" 导致消费方死循环、误抛 NO_ENOUGH_DATA
 */
public class TestSubTextDataReader {

    @Test
    public void testReadLineReturnsNullWhenRegionExhausted() throws Exception {
        SimpleTextDataReader base = new SimpleTextDataReader("ab\ncd");
        ITextDataReader sub = base.subInput(3); // 区域只含 "ab\n"

        assertEquals("ab", sub.readLine(10));
        // 区域耗尽必须返回null，即使底层还有 "cd"
        assertNull(sub.readLine(10));
        // skip(0) 在区间耗尽后应为无操作而非抛错
        sub.skip(0);
        assertNull(sub.readLine(10));
        sub.close();
    }
}
