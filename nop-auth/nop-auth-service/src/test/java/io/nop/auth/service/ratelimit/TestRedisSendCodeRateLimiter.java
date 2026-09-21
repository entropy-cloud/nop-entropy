/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.ratelimit;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.service.mfa.store.FakeNosqlService;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.auth.service.NopAuthErrors.ARG_PHONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RedisSendCodeRateLimiter} 委托契约（plan 2274 Phase 1）：经 {@link FakeNosqlService}
 * 验证对 nop-nosql 原语的委托（counter.increment / setTimeoutAsync / putIfAbsentExAsync /
 * putExAsync）与集群语义（多实例共享计数）——与 {@code RedisMfaChallengeStore} 测试形态一致。
 */
public class TestRedisSendCodeRateLimiter extends BaseTestCase {

    private static final FakeNosqlService NOSQL = new FakeNosqlService();

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testFirstSendDelegatesToCounterAndSetsTtlAndMarker() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSendCodeRateLimiter limiter = new RedisSendCodeRateLimiter(nosql);

        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null);

        // 委托契约：日计数走 INosqlCounter（单键原子），首次递增设 TTL（PEXPIRE 先例）
        assertTrue(nosql.callCount("counter.increment") >= 1, "daily count must use INosqlCounter.increment");
        assertTrue(nosql.callCount("setTimeoutAsync") >= 1, "first increment must set TTL via setTimeoutAsync");
        // 间隔门 = SETNX+PX 单键原子（含当日首次——INCR 与 SETNX 固定先后序消除双写竞态）
        assertTrue(nosql.callCount("putIfAbsentExAsync") >= 1,
                "interval gate must be SETNX+PX (putIfAbsentExAsync)");
    }

    @Test
    public void testSecondWithinIntervalRejectedViaSetnx() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSendCodeRateLimiter limiter = new RedisSendCodeRateLimiter(nosql);

        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null);
        NopException err = assertThrows(NopException.class,
                () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null));
        assertTrue(err.getErrorCode().contains("rate-limit"), "got: " + err.getErrorCode());
        assertEquals("*******8000", String.valueOf(err.getParam(ARG_PHONE)), "param must be masked");
        assertTrue(nosql.callCount("putIfAbsentExAsync") >= 1,
                "interval rejection path must be guarded by SETNX+PX (putIfAbsentExAsync)");
    }

    /**
     * 集群语义（plan 2274 Phase 1 集群 Proof）：两个"实例"（模拟两个节点）共享同一 nosql 后端时，
     * 节点 A 的发送使节点 B 的同目标请求被间隔拒绝——限流状态跨实例共享。
     */
    @Test
    public void testCrossInstanceSharedCounting() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSendCodeRateLimiter nodeA = new RedisSendCodeRateLimiter(nosql);
        RedisSendCodeRateLimiter nodeB = new RedisSendCodeRateLimiter(nosql);

        nodeA.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138001", null);
        assertThrows(NopException.class,
                () -> nodeB.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138001", null),
                "state written by node A must be visible to node B (shared backend)");
    }

    @Test
    public void testIpLayerCountedOnSeparateKey() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSendCodeRateLimiter limiter = new RedisSendCodeRateLimiter(nosql);

        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138002", "10.2.2.2");
        int before = nosql.callCount("counter.increment");
        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138003", "10.2.2.2");
        int after = nosql.callCount("counter.increment");
        // 第二次调用：目标计数（新手机号）+ IP 计数 = 2 次递增（IP 键独立于目标键）
        assertEquals(before + 2, after, "ip dimension must be counted on its own counter key");
    }

    @Test
    public void testScopeIsolatedOnRedisKeys() {
        FakeNosqlService nosql = new FakeNosqlService();
        RedisSendCodeRateLimiter limiter = new RedisSendCodeRateLimiter(nosql);

        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138004", null);
        // bind scope 独立计数：首次仍通过（等价原两类各自 Map 拓扑）
        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_BIND, "13800138004", null);
        assertThrows(NopException.class,
                () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138004", null));
    }
}
