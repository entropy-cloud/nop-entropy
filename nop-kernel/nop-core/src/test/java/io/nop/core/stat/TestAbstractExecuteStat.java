package io.nop.core.stat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestAbstractExecuteStat {

    static class MyStat extends AbstractExecuteStat {
    }

    @Test
    public void testAddExecuteTimeUpdatesTotalMaxAndHistogram() {
        AbstractExecuteStat stat = new MyStat();

        stat.addExecuteTime(3_000_000L); // 3ms -> bucket [1,10)
        stat.addExecuteTime(300_000_000L); // 300ms -> bucket [100,1000)
        assertEquals(303, stat.getExecuteSpanNanoTotal() / 1000 / 1000);
        assertEquals(300, stat.getExecuteSpanNanoMax() / 1000 / 1000);
        assertNotNull(stat.getExecuteNanoSpanMaxOccurTime());

        long[] hist = stat.getHistogramValues();
        assertEquals(1, hist[1]);
        assertEquals(1, hist[3]);

        // 重复小耗时不应推高 max
        stat.addExecuteTime(1_000_000L);
        assertEquals(300, stat.getExecuteSpanNanoMax() / 1000 / 1000);
    }

    @Test
    public void testExecuteAvgTimeZeroWhenNoExecution() {
        AbstractExecuteStat stat = new MyStat();
        assertEquals(0, stat.getExecuteAvgTime());

        stat.incrementExecuteSuccessCount();
        stat.setExecuteSpanNanoTotal(10_000_000L);
        assertEquals(10_000_000L, stat.getExecuteAvgTime());
    }

    @Test
    public void testErrorRecordsErrorInfo() {
        AbstractExecuteStat stat = new MyStat();
        assertNull(stat.getExecuteErrorLast());

        stat.error(new RuntimeException("test-error"));
        assertEquals(1, stat.getExecuteErrorCount());
        assertNotNull(stat.getExecuteErrorLast());
        assertEquals(1, stat.getExecuteCount());
        assertNotZero(stat.getExecuteErrorLastTime());
    }

    private void assertNotZero(long value) {
        if (value == 0) {
            throw new AssertionError("value should not be zero");
        }
    }

    @Test
    public void testRunningCountAndConcurrentMax() {
        AbstractExecuteStat stat = new MyStat();
        stat.incrementRunningCount();
        stat.incrementRunningCount();
        stat.incrementRunningCount();
        assertEquals(3, stat.getRunningCount());
        assertEquals(3, stat.getConcurrentMax());

        stat.decrementRunningCount();
        assertEquals(2, stat.getRunningCount());
        assertEquals(3, stat.getConcurrentMax(), "concurrentMax keeps historical peak");

        stat.decrementExecutingCount();
        assertEquals(1, stat.getRunningCount());
    }

    @Test
    public void testResetClearsAllCounters() {
        AbstractExecuteStat stat = new MyStat();
        stat.incrementExecuteSuccessCount();
        stat.error(new RuntimeException("e"));
        stat.addExecuteTime(2_000_000L);
        stat.setExecuteLastStartTime(123L);

        stat.reset();
        assertEquals(0, stat.getExecuteSuccessCount());
        assertEquals(0, stat.getExecuteErrorCount());
        assertNull(stat.getExecuteErrorLast());
        assertEquals(0, stat.getExecuteSpanNanoTotal());
        assertEquals(0, stat.getExecuteSpanNanoMax());
        assertEquals(0, stat.getExecuteLastStartTime());
        for (long bucket : stat.getHistogramValues()) {
            assertEquals(0, bucket, "histogram bucket should be zero after reset");
        }
    }

    @Test
    public void testSetterRoundTrip() {
        AbstractExecuteStat stat = new MyStat();
        stat.setExecuteSuccessCount(5);
        stat.setExecuteSpanNanoTotal(100);
        stat.setExecuteSpanNanoMax(50);
        stat.setExecuteErrorCount(2);
        stat.setExecuteLastStartTime(7L);
        stat.setHistogram_0_1(1);
        stat.setHistogram_1000000_more(4);

        assertEquals(5, stat.getExecuteSuccessCount());
        assertEquals(100, stat.getExecuteSpanNanoTotal());
        assertEquals(50, stat.getExecuteSpanNanoMax());
        assertEquals(2, stat.getExecuteErrorCount());
        assertEquals(7, stat.getExecuteLastStartTime());
        assertEquals(1, stat.getHistogram_0_1());
        assertEquals(4, stat.getHistogram_1000000_more());
        assertEquals(7, stat.getExecuteCount());
    }
}
