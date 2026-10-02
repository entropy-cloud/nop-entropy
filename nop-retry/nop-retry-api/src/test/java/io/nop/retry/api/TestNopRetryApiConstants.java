package io.nop.retry.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性验证重试引擎 API 默认值的自洽性：
 * 退避间隔上限不小于初始间隔、抖动比例在 [0,1] 区间、缺省即时重试不抢在调度之前。
 */
public class TestNopRetryApiConstants {

    @Test
    public void testDefaultMaxRetryCount() {
        assertEquals(3, NopRetryApiConstants.DEFAULT_MAX_RETRY_COUNT);
    }

    @Test
    public void testDefaultIntervalsAreSane() {
        assertTrue(NopRetryApiConstants.DEFAULT_INITIAL_INTERVAL_MS > 0,
                "initial interval must be positive");
        assertTrue(NopRetryApiConstants.DEFAULT_MAX_INTERVAL_MS >= NopRetryApiConstants.DEFAULT_INITIAL_INTERVAL_MS,
                "max interval must not be smaller than initial interval");
        assertTrue(NopRetryApiConstants.DEFAULT_IMMEDIATE_RETRY_INTERVAL_MS > 0);
    }

    @Test
    public void testDefaultJitterRatioWithinUnitRange() {
        assertTrue(NopRetryApiConstants.DEFAULT_JITTER_RATIO >= 0
                        && NopRetryApiConstants.DEFAULT_JITTER_RATIO <= 1,
                "jitter ratio must be a fraction");
    }

    @Test
    public void testDefaultDeadlineCoversRetryingWindow() {
        assertTrue(NopRetryApiConstants.DEFAULT_DEADLINE_TIMEOUT_MS
                        >= NopRetryApiConstants.DEFAULT_RETRYING_TIMEOUT_MS,
                "overall deadline must cover the retrying window");
    }

    @Test
    public void testDefaultImmediateRetryCountIsZero() {
        // 缺省不做即时重试：所有重试都走调度循环，避免与定时扫描重复触发
        assertEquals(0, NopRetryApiConstants.DEFAULT_IMMEDIATE_RETRY_COUNT);
    }

    @Test
    public void testPartitionCountPositive() {
        assertTrue(NopRetryApiConstants.DEFAULT_PARTITION_COUNT > 0,
                "partition count must be positive for sharded scanning");
    }

    @Test
    public void testBoolFlags() {
        assertEquals("1", NopRetryApiConstants.BOOL_YES);
        assertEquals("0", NopRetryApiConstants.BOOL_NO);
    }
}
