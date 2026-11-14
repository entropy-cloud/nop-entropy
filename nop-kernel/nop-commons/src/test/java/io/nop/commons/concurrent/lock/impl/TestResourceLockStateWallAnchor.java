package io.nop.commons.concurrent.lock.impl;

import io.nop.commons.concurrent.lock.IResourceLock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2571（墙钟锚点投影）：锁记录的单调时间域必须能回答「对应墙钟哪个时刻」——
 * toString 输出锚点投影的 ISO-8601 墙钟 + 保留原始纳秒；wallMillisOf 投影正确；
 * 未设锚点回退原始值（投影返回 -1）。判定域（expireTime 单调比较）零改动。
 * 同包测试以直接访问 LocalResourceLockManager 的包可见锁表。
 */
public class TestResourceLockStateWallAnchor {

    @Test
    public void pin2571_lockStateToStringProjectsWallClock() {
        ResourceLockState state = new ResourceLockState();
        state.setResourceId("res-2571");
        state.setLockerId("a");

        long nano0 = System.nanoTime();
        long wall0 = System.currentTimeMillis();
        state.setWallAnchor(nano0, wall0);
        // createTime = 锚点后 2s（单调域）
        state.setCreateTime(nano0 + 2_000_000_000L);
        state.setExpireTime(nano0 + 12_000_000_000L);

        long projectedCreate = state.wallMillisOf(state.getCreateTime());
        assertTrue(projectedCreate > 0, "projection must be positive when anchor set");
        long expected = wall0 + 2000;
        assertTrue(Math.abs(projectedCreate - expected) <= 500,
                "projection must be anchorWall + delta: got " + projectedCreate + " expect ~" + expected);

        String s = state.toString();
        assertTrue(s.contains(String.valueOf(nano0 + 2_000_000_000L)),
                "raw nanos must be preserved: " + s);
        assertTrue(s.contains("wall="), "toString must contain projected wall clock: " + s);

        // 未设锚点：投影 -1，toString 回退原始值
        ResourceLockState bare = new ResourceLockState();
        bare.setCreateTime(123L);
        assertEquals(-1L, bare.wallMillisOf(123L));
        assertTrue(bare.toString().contains("123"));
    }

    @Test
    public void pin2571_realLockRecordCarriesWallProjection() {
        LocalResourceLockManager mgr = new LocalResourceLockManager();
        IResourceLock lock = mgr.getLock("res-2571-real", "a");
        assertTrue(lock.tryLockWithLease(0, 5000));
        try {
            Object state = mgr.locks.get("res-2571-real");
            assertNotNull(state, "lock record must exist in the table");
            String s = state.toString();
            assertTrue(s.contains("wall="), "real lock record must carry wall projection: " + s);
        } finally {
            lock.unlock();
        }
    }
}
