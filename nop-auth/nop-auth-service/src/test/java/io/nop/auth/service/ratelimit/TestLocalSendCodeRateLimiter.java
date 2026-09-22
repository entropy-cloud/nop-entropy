/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.ratelimit;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.auth.service.NopAuthErrors.ARG_CHANNEL;
import static io.nop.auth.service.NopAuthErrors.ARG_PHONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalSendCodeRateLimiter} 三层语义回归（plan 2274 Phase 1）：
 * 平移原 LoginServiceImpl/NopAuthUserBizModel 限流 Map 的行为等价基线——
 * 顺序间隔拒绝、IP 日配额、scope 隔离（等价原两类各自 Map 实例）、
 * 32 线程并发突发恰好放行 1 个（自 TestLoginRateLimitAndAudit 平移，断言语义不变）、
 * 错误 param 统一脱敏（design §3.1 唯一用户可见变化）。
 */
public class TestLocalSendCodeRateLimiter extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testSmsSequentialSecondWithinIntervalRejected() {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null);
        NopException err = assertThrows(NopException.class,
                () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null));
        assertTrue(err.getErrorCode().contains("rate-limit") || err.getErrorCode().contains("daily-limit"),
                "second send within interval must be rejected, got: " + err.getErrorCode());
    }

    /** 错误 param 统一脱敏（design §3.1：ARG_PHONE 携带 mask 后的值）。 */
    @Test
    public void testSmsRejectedParamIsMasked() {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null);
        NopException err = assertThrows(NopException.class,
                () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null));
        assertEquals("*******8000", String.valueOf(err.getParam(ARG_PHONE)),
                "rejected param must be masked phone");
    }

    /** email 侧拒绝 param 经 ARG_CHANNEL key 携带脱敏值（两侧 key 归一，design §3.1）。 */
    @Test
    public void testEmailSequentialSecondWithinIntervalRejectedWithMaskedChannelParam() {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        limiter.checkEmailAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "alice@example.com", null);
        NopException err = assertThrows(NopException.class,
                () -> limiter.checkEmailAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "alice@example.com", null));
        assertTrue(err.getErrorCode().contains("rate-limit"), "got: " + err.getErrorCode());
        assertEquals("al***@example.com", String.valueOf(err.getParam(ARG_CHANNEL)),
                "email rejected param must be masked via ARG_CHANNEL key");
    }

    /** 平移自 TestLoginRateLimitAndAudit（plan 2274 Phase 1，断言语义不变）：并发突发恰好放行 1 个。 */
    @Test
    public void testConcurrentBurstAllowsExactlyOne() throws Exception {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        int threads = 32;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger passed = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null);
                    passed.incrementAndGet();
                } catch (NopException e) {
                    // 预期的RATE_LIMITED/DAILY_LIMIT拒绝
                } catch (Throwable e) {
                    unexpected.add(e);
                }
            });
        }
        for (Thread t : ts)
            t.start();
        for (Thread t : ts)
            t.join(30000);

        assertEquals(List.of(), unexpected, "no unexpected exceptions");
        assertEquals(1, passed.get(), "concurrent burst on same phone must allow exactly one request");
    }

    /**
     * scope 隔离（等价原拓扑：LoginServiceImpl 与 NopAuthUserBizModel 各自独立 Map）：
     * 同手机号在 login scope 通过后，bind scope 首次仍放行；两 scope 各自第二次被拒。
     */
    @Test
    public void testScopeIsolationMatchesLegacyTopology() {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        // login 首次通过
        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null);
        // bind scope 首次仍通过（独立计数）
        limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_BIND, "13800138000", null);
        // 两 scope 各自第二次均被间隔拒绝
        assertThrows(NopException.class,
                () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null));
        assertThrows(NopException.class,
                () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_BIND, "13800138000", null));
    }

    /**
     * IP 日配额：不同手机号（目标层互不影响）同 IP 连续发送，超过 ip-daily-limit（默认 50）后拒绝。
     */
    @Test
    public void testIpDailyLimitRejected() {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        String ip = "10.1.1.7";
        for (int i = 0; i < 50; i++) {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138" + String.format("%04d", i), ip);
        }
        NopException err = assertThrows(NopException.class,
                () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "138001389999", ip));
        assertTrue(err.getErrorCode().contains("daily-limit"), "51st distinct phone from same IP must hit IP daily limit");
    }

    /** IP 计数 channel 隔离（等价原 sms-IP 与 email-IP 独立 Map）：同 IP 的 sms 与 email 计数互不累计。 */
    @Test
    public void testIpCounterIsChannelIsolated() {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        String ip = "10.1.1.8";
        for (int i = 0; i < 50; i++) {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13900139" + String.format("%04d", i), ip);
        }
        // sms IP 已达上限，但 email channel 的 IP 计数独立——首次 email 仍放行
        limiter.checkEmailAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "bob@example.com", ip);
    }

    /**
     * 同目标日配额拒绝（Minor-1 补测）：interval 置 0（配置覆盖）绕过间隔层后，
     * 同手机号连续发送在超过 daily-limit（默认 20）时被 DAILY_LIMIT 拒绝。
     */
    @Test
    public void testTargetDailyLimitRejected() {
        io.nop.api.core.config.IConfigProvider provider = io.nop.api.core.config.AppConfig.getConfigProvider();
        int original = provider.getConfigValue("nop.auth.sms-code.send-interval-seconds", 60);
        provider.assignConfigValue("nop.auth.sms-code.send-interval-seconds", 0);
        try {
            LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
            for (int i = 0; i < 20; i++) {
                limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13600136000", null);
            }
            NopException err = assertThrows(NopException.class,
                    () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13600136000", null));
            assertTrue(err.getErrorCode().contains("daily-limit"),
                    "21st send must hit target daily limit, got: " + err.getErrorCode());
        } finally {
            provider.assignConfigValue("nop.auth.sms-code.send-interval-seconds", original);
        }
    }

    /** clientIp 为空跳过 IP 层（bind-sms proof 路径现状无 IP 维度）。 */
    @Test
    public void testNullClientIpSkipsIpLayer() {
        LocalSendCodeRateLimiter limiter = new LocalSendCodeRateLimiter();
        // 无 IP 层、每手机号首次——60 次全部通过（若有 IP 层则早在第 51 次抛 DAILY_LIMIT）
        for (int i = 0; i < 60; i++) {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_BIND, "13700137" + String.format("%04d", i), null);
        }
    }
}
