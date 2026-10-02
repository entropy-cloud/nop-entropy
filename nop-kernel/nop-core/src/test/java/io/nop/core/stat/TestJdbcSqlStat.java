package io.nop.core.stat;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJdbcSqlStat {

    @Test
    public void testExecuteTimeHistogramBuckets() {
        JdbcSqlStat stat = new JdbcSqlStat("select 1");

        // 0.5ms -> bucket [0,1)
        stat.addExecuteTime(500_000L);
        // 5ms -> bucket [1,10)
        stat.addExecuteTime(5_000_000L);
        // 50ms -> bucket [10,100)
        stat.addExecuteTime(50_000_000L);
        // 500ms -> bucket [100,1000)
        stat.addExecuteTime(500_000_000L);
        // 5s -> bucket [1000,10000)
        stat.addExecuteTime(5_000_000_000L);
        // 50s -> bucket [10000,100000)
        stat.addExecuteTime(50_000_000_000L);
        // 300s -> bucket [100000,1000000)
        stat.addExecuteTime(300_000_000_000L);
        // 2000s -> bucket 1e6以上
        stat.addExecuteTime(2_000_000_000_000L);

        long[] hist = stat.getHistogramValues();
        assertEquals(8, hist.length);
        for (int i = 0; i < 8; i++) {
            assertEquals(1, hist[i], "bucket " + i + " should have exactly one sample");
        }
        assertEquals(8, stat.getHistogramSum());

        // 总耗时 = 0.5 + 5 + 50 + 500 + 5000 + 50000 + 300000 + 2000000 = 2355555.5ms（毫秒截断）
        assertEquals(2355555L, stat.getExecuteMillisTotal());
        assertEquals(2000000L, stat.getExecuteMillisMax());
        assertNotNull(stat.getExecuteNanoSpanMaxOccurTime());
    }

    @Test
    public void testExecuteCountAndError() {
        JdbcSqlStat stat = new JdbcSqlStat("update t set a=1");

        stat.incrementExecuteSuccessCount();
        stat.incrementExecuteSuccessCount();
        stat.error(new RuntimeException("boom"));
        assertEquals(2, stat.getExecuteSuccessCount());
        assertEquals(1, stat.getErrorCount());
        assertEquals(3, stat.getExecuteCount());
        assertNotNull(stat.getExecuteErrorLast(), "error() should record last error message");
        assertNotNull(stat.getExecuteErrorLastTime());

        // reset 后错误信息清空
        stat.reset();
        assertEquals(0, stat.getExecuteCount());
        assertNull(stat.getExecuteErrorLast());
    }

    @Test
    public void testRunningCountAndConcurrentMax() {
        JdbcSqlStat stat = new JdbcSqlStat("select 2");

        stat.incrementRunningCount();
        stat.incrementRunningCount();
        assertEquals(2, stat.getRunningCount());
        assertEquals(2, stat.getConcurrentMax());

        stat.decrementRunningCount();
        assertEquals(1, stat.getRunningCount());
        // concurrentMax 是历史峰值，不随 runningCount 下降
        assertEquals(2, stat.getConcurrentMax());

        // reset 不清 runningCount（在途状态非统计累计），只清 concurrentMax
        stat.incrementRunningCount();
        stat.reset();
        assertEquals(2, stat.getRunningCount(), "reset should not touch live runningCount");
        assertEquals(0, stat.getConcurrentMax());
        stat.decrementRunningCount();
        stat.decrementRunningCount();
    }

    @Test
    public void testFetchRowCountHistogramAndMax() {
        JdbcSqlStat stat = new JdbcSqlStat("select 3");

        stat.addFetchRowCount(5);
        stat.addFetchRowCount(50);
        stat.addFetchRowCount(500);
        stat.addFetchRowCount(5000);
        stat.addFetchRowCount(50_000);
        assertEquals(55_555, stat.getFetchRowCount());
        assertEquals(50_000, stat.getFetchRowCountMax());

        long[] hist = stat.getFetchRowCountHistogramValues();
        assertEquals(0, hist[0]);
        assertEquals(1, hist[1]); // [1,10)
        assertEquals(1, hist[2]); // [10,100)
        assertEquals(1, hist[3]); // [100,1000)
        assertEquals(1, hist[4]); // [1000,10000)
        assertEquals(1, hist[5]); // 10000以上

        // delta<=0 也计入 [0,1) 桶，但不增加总量、不推高 max
        stat.addFetchRowCount(0);
        assertEquals(1, stat.getFetchRowCountHistogramValues()[0]);
        assertEquals(55_555, stat.getFetchRowCount());
        assertEquals(50_000, stat.getFetchRowCountMax());
    }

    @Test
    public void testUpdateCountHistogramAndMax() {
        JdbcSqlStat stat = new JdbcSqlStat("insert into t values(1)");

        stat.addUpdateCount(3);
        stat.addUpdateCount(300);
        assertEquals(303, stat.getUpdateCount());
        assertEquals(300, stat.getUpdateCountMax());

        long[] hist = stat.getUpdateCountHistogramValues();
        assertEquals(1, hist[1]); // [1,10)
        assertEquals(1, hist[3]); // [100,1000)

        // delta<=0 不累计总量，但计入 [0,1) 桶
        stat.addUpdateCount(0);
        assertEquals(1, stat.getUpdateCountHistogramValues()[0]);
        assertEquals(303, stat.getUpdateCount());
    }

    @Test
    public void testBatchCountTotalAndMax() {
        JdbcSqlStat stat = new JdbcSqlStat("batch insert");

        stat.addExecuteBatchCount(10);
        stat.addExecuteBatchCount(3);
        assertEquals(13, stat.getExecuteBatchSizeTotal());
        assertEquals(10, stat.getExecuteBatchSizeMax());
    }

    @Test
    public void testGetValueResetSemantics() {
        JdbcSqlStat stat = new JdbcSqlStat("select 4");
        stat.setDbType("mysql");
        stat.setName("n1");
        stat.setExecuteLastStartTime(123L);
        stat.incrementExecuteSuccessCount();
        stat.addExecuteTime(2_000_000L);
        stat.setLastSlowParameters("p1");
        stat.incrementInTransactionCount();

        JdbcSqlStatValue value = stat.getValueAndReset();
        assertEquals("select 4", value.getSql());
        assertEquals("mysql", value.getDbType());
        assertEquals("n1", value.getName());
        assertEquals(1, value.getExecuteSuccessCount());
        assertEquals(2_000_000L, value.getExecuteSpanNanoMax(), "span max 以纳秒计（2ms）");
        assertEquals(1, value.getInTransactionCount());
        assertEquals("p1", value.getLastSlowParameters());
        assertEquals(1, value.getHistogram_1_10());

        // reset 语义：累计字段归零，维度字段保留
        assertEquals(0, stat.getExecuteSuccessCount());
        assertEquals(0, stat.getExecuteMillisTotal());
        assertNull(stat.getLastSlowParameters());
        assertEquals(0, stat.getInTransactionCount());
        assertNull(stat.getExecuteLastStartTime(), "executeLastStartTime<=0 should return null");
        assertEquals("select 4", stat.getSql());

        JdbcSqlStatValue second = stat.getValue(false);
        assertEquals(0, second.getExecuteSuccessCount());
    }

    @Test
    public void testSqlHashIsCachedAndStable() {
        JdbcSqlStat stat = new JdbcSqlStat("select 5");
        long hash1 = stat.getSqlHash();
        long hash2 = stat.getSqlHash();
        assertEquals(hash1, hash2);
        assertNotEquals(0L, hash1);
    }

    @Test
    public void testCompareToOrderByStatId() {
        JdbcSqlStat s1 = new JdbcSqlStat("select a");
        JdbcSqlStat s2 = new JdbcSqlStat("select b");
        // 计算 sqlHash 后，相同 hash 返回 0
        s2.getSqlHash();
        // 不同 id 按 id 排序（id 单调递增）
        assertTrue(s1.getId() != s2.getId());
        assertEquals(s1.getId() < s2.getId() ? -1 : 1, s1.compareTo(s2));
    }

    @Test
    public void testBlobClobAndReadLengths() {
        JdbcSqlStat stat = new JdbcSqlStat("select 6");
        stat.incrementClobOpenCount();
        stat.incrementClobOpenCount();
        stat.incrementBlobOpenCount();
        stat.addStringReadLength(100);
        stat.addReadBytesLength(200);
        stat.addReaderOpenCount(2);
        stat.addInputStreamOpenCount(3);

        assertEquals(2, stat.getClobOpenCount());
        assertEquals(1, stat.getBlobOpenCount());
        assertEquals(100, stat.getReadStringLength());
        assertEquals(200, stat.getReadBytesLength());
        assertEquals(2, stat.getReaderOpenCount());
        assertEquals(3, stat.getInputStreamOpenCount());

        stat.reset();
        assertEquals(0, stat.getClobOpenCount());
        assertEquals(0, stat.getBlobOpenCount());
        assertEquals(0, stat.getReadStringLength());
        assertEquals(0, stat.getReadBytesLength());
    }

    @Test
    public void testResultSetHoldTime() {
        JdbcSqlStat stat = new JdbcSqlStat("select 7");
        stat.addResultSetHoldTimeNano(1_000_000L, 2_000_000L);
        assertEquals(2, stat.getResultSetHoldTimeMilis());
        assertEquals(3, stat.getExecuteAndResultSetHoldTimeMilis());
        // addResultSetHoldTimeNano 同时在 updateCount_0_1 桶计数
        assertEquals(1, stat.getUpdateCountHistogramValues()[0]);
        assertEquals(3_000_000L, stat.getExecuteAndResultSetHoldTimeNano());

        stat.addResultSetHoldTimeNano(5_000_000L);
        assertEquals(7, stat.getResultSetHoldTimeMilis());
    }

    @Test
    public void testExecuteAndResultHoldTimeHistogramOnlyForUpdate() {
        JdbcSqlStat stat = new JdbcSqlStat("select 8");

        // ExecuteQuery 类型不记录 execute+result hold time 直方图
        stat.addExecuteTime(StatementExecuteType.ExecuteQuery, true, 2_000_000L);
        assertEquals(0, stat.getExecuteAndResultHoldTimeHistogramSum());

        // 非 ExecuteQuery 且 firstResultSet=false 时记录
        stat.addExecuteTime(StatementExecuteType.ExecuteUpdate, false, 2_000_000L);
        assertEquals(1, stat.getExecuteAndResultHoldTimeHistogramSum());
        assertEquals(1, stat.getExecuteAndResultHoldTimeHistogramValues()[1]);

        // ExecuteQuery 但 firstResultSet=false 也记录（判断条件是 != ExecuteQuery && !firstResultSet）
        stat.addExecuteTime(StatementExecuteType.ExecuteUpdate, true, 2_000_000L);
        assertEquals(1, stat.getExecuteAndResultHoldTimeHistogramSum());
    }

    @Test
    public void testRemovedFlagAndSetters() {
        JdbcSqlStat stat = new JdbcSqlStat("select 9");
        stat.setRemoved(true);
        assertTrue(stat.isRemoved());
        stat.setDataSource("ds1");
        assertEquals("ds1", stat.getDataSource());
        stat.setFile("/a/b.sql");
        assertEquals("/a/b.sql", stat.getFile());

        stat.setExecuteLastStartTime(1000L);
        Date d = stat.getExecuteLastStartTime();
        assertNotNull(d);
        stat.getValueAndReset();
        assertNull(stat.getExecuteLastStartTime());
    }
}
