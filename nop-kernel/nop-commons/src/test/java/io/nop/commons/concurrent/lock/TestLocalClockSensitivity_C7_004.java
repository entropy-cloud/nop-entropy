/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.lock;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IClock;
import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-32（2.1-C7-004，plan 2566）单调时钟载体钉：LocalResourceLockManager 锁表时间域整域
 * 换源 CoreMetrics.nanoTime() 后——
 * <ol>
 *   <li>回拨腿：墙钟回拨 1h 后租约仍按真实（单调）流逝过期，不因回拨变长寿约；</li>
 *   <li>前跳腿：墙钟前跳不提前误删有效租约。</li>
 * </ol>
 * StubClock 双针（wall/nanoTime 独立推进回拨）经 registerClock 注入 + finally 恢复
 * （设计 §1.3 缝纪律：全局兜底必 finally 恢复，先例 NopJunitExtension）。
 */
public class TestLocalClockSensitivity_C7_004 {

    /**
     * 双针可控 stub clock（package-private，非公共 API，不入 nop-api-core）
     */
    static class StubClock implements IClock {
        long wallMillis = 1_700_000_000_000L;
        long nanoTicks = 1_000_000_000L;

        void advanceNanos(long deltaNanos) {
            nanoTicks += deltaNanos;
        }

        void rollbackWall(long deltaMillis) {
            wallMillis -= deltaMillis;
        }

        void forwardWall(long deltaMillis) {
            wallMillis += deltaMillis;
        }

        @Override
        public long currentTimeMillis() {
            return wallMillis;
        }

        @Override
        public long nanoTime() {
            return nanoTicks;
        }

        @Override
        public LocalDate currentDate() {
            return LocalDate.now();
        }

        @Override
        public LocalDateTime currentDateTime() {
            return LocalDateTime.now();
        }
    }

    StubClock clock;
    LocalResourceLockManager lockManager;

    @BeforeEach
    public void setUp() {
        clock = new StubClock();
        CoreMetrics.registerClock(clock);
        lockManager = new LocalResourceLockManager();
    }

    @AfterEach
    public void tearDown() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
        if (lockManager != null)
            lockManager.destroy();
    }

    /**
     * 回拨腿：获取租约后墙钟回拨 1h，单调流逝超过租期 → 租约过期（修复前 wall 回拨使
     * expireTime 仍在未来 → 续租应成功而实际断言失败 RED）。
     */
    @Test
    public void testWallRollbackDoesNotExtendLease() {
        IResourceLockState state = lockManager.tryLockWithLease("rb-a", "h1", 0, 10_000, null);
        assertNotNull(state);

        // 墙钟回拨 1h（修复前：expireTime=wall+10s 仍在未来 → isHoldingLock=true → 断言失败）
        clock.rollbackWall(3_600_000);

        // 单调流逝 20s > 租期 10s → 过期
        clock.advanceNanos(TimeUnit.SECONDS.toNanos(20));
        assertFalse(lockManager.isHoldingLock(state), "lease must expire by real monotonic elapse despite wall rollback");
        assertNull(lockManager.getLockState("rb-a"), "expired lease must be evicted from lock table");

        // 过期后续租失败
        assertFalse(lockManager.tryResetLease(state, 10_000));
    }

    /**
     * 前跳腿：墙钟前跳 1h 不提前误删有效租约。
     */
    @Test
    public void testWallForwardJumpDoesNotExpireLeaseEarly() {
        IResourceLockState state = lockManager.tryLockWithLease("fw-a", "h1", 0, 10_000, null);
        assertNotNull(state);

        clock.forwardWall(3_600_000);
        // 单调未流逝（租期 10s 内）→ 仍持有（修复前 wall 前跳使 now > expireTime → 误判过期 RED）
        assertTrue(lockManager.isHoldingLock(state), "wall forward jump must not expire a valid lease early");
        assertNotNull(lockManager.getLockState("fw-a"));

        // 单调流逝超过租期后仍正常过期
        clock.advanceNanos(TimeUnit.SECONDS.toNanos(11));
        assertFalse(lockManager.isHoldingLock(state));
        assertNull(lockManager.getLockState("fw-a"));
    }

    /**
     * 单调续租腿：租约有效期内续租成功，续租后过期判定按单调流逝。
     */
    @Test
    public void testResetLeaseFollowsMonotonicClock() {
        IResourceLockState state = lockManager.tryLockWithLease("rl-a", "h1", 0, 1_000, null);
        assertNotNull(state);

        // 墙钟干扰不影响判定
        clock.rollbackWall(60_000);

        // 仍在租期内续租成功
        assertTrue(lockManager.tryResetLease(state, 10_000));
        assertTrue(lockManager.isHoldingLock(state));

        // 单调流逝 11s > 续租后租期 10s → 过期
        clock.advanceNanos(TimeUnit.SECONDS.toNanos(11));
        assertFalse(lockManager.isHoldingLock(state));
        assertNull(lockManager.getLockState("rl-a"));
    }
}
