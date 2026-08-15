package io.nop.datav.service.entity;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavDashboardShareBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.service.share.NopDatavShareAccessGuard;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_ENABLED;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS;
import static io.nop.datav.service.NopDatavErrors.ARG_RETRY_AFTER_SECONDS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_LOCKED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_MISMATCH;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_REQUIRED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_RATE_LIMITED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_TOKEN_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分享访问两级限流 focused tests（plan 2026-08-15-0004-2 Phase 2，裁定 R1–R6 见
 * permission-sharing-design.md「访问限流与访问统计」）。
 *
 * <p>全部经真实调用链 {@code shareBiz.getSharedDashboard}（接线验证：限流检查在真实访问路径生效，
 * 非孤立组件）；窗口推进经注入 guard bean 的 fake 时钟（R2 硬要求，无 Thread.sleep 盲等）；
 * 错误码断言证明被拒请求快速失败（无静默降级）且拒绝先于快照/查询路径（R5）。</p>
 */
public class TestNopDatavShareRateLimit extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    /**
     * 与 BizModel 注入同一 singleton guard bean——测试经此时钟 seam 确定性推进窗口。
     */
    @Inject
    NopDatavShareAccessGuard shareAccessGuard;

    private final AtomicLong fakeNow = new AtomicLong();

    @AfterEach
    public void restoreRealClock() {
        if (shareAccessGuard != null) {
            shareAccessGuard.setClock(System::currentTimeMillis);
        }
    }

    /**
     * 密码爆破截断（Phase 2 Exit Criteria #1）：连续失败达阈值后<strong>即使密码正确也被拒</strong>
     * （防爆破有效性，非仅计数），错误码为锁定专用码（锁定检查先于密码比对——比对不可达即无查询放大）；
     * 窗口推进后自动恢复（fake 时钟驱动，非 sleep）。
     */
    @Test
    public void testBruteForcePasswordLockedThenRecoversAfterWindow() {
        withConfig(CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES, 3, () -> {
            withConfig(CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS, 60, () -> {
                installFakeClock();
                NopDatavDashboardShare share = newPublishedShare("dash-rl-1", "rightPwd");

                // 3 次错误密码：每次 MISMATCH（第 3 次达阈值置锁）
                for (int i = 0; i < 3; i++) {
                    NopException ex = assertThrows(NopException.class, () ->
                            shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
                    assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode(),
                            "failure #" + (i + 1) + " below threshold still reports mismatch");
                }

                // 第 4 次：密码正确也被拒（锁定专用错误码 + retryAfterSeconds param）
                NopException locked = assertThrows(NopException.class, () ->
                        shareBiz.getSharedDashboard(share.getShareToken(), "rightPwd", anonymousContext()));
                assertEquals(ERR_DATAV_SHARE_PASSWORD_LOCKED.getErrorCode(), locked.getErrorCode(),
                        "correct password must be rejected while locked (brute-force cutoff)");
                Object retryAfter = locked.getParam(ARG_RETRY_AFTER_SECONDS);
                assertNotNull(retryAfter, "locked error carries retryAfterSeconds");
                assertTrue(((Number) retryAfter).longValue() > 0, "retryAfterSeconds positive");

                // 窗口推进（fake 时钟 +61s > 60s 窗口）→ 自动恢复，正确密码成功
                fakeNow.addAndGet(61_000L);
                NopDatavDashboardSnapshot snapshot = shareBiz.getSharedDashboard(
                        share.getShareToken(), "rightPwd", anonymousContext());
                assertNotNull(snapshot, "access recovers after lock window passes");
            });
        });
    }

    /**
     * 缺密码（PASSWORD_REQUIRED）不消耗失败预算（未提供猜测，不计失败）——1 次缺密码 + 2 次错密码
     * 后正确密码仍成功（若缺密码也计数，此刻计数=3 已锁定，正确密码会被拒）。
     */
    @Test
    public void testMissingPasswordDoesNotConsumeFailureBudget() {
        withConfig(CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES, 3, () -> {
            installFakeClock();
            NopDatavDashboardShare share = newPublishedShare("dash-rl-2", "rightPwd");

            NopException missing = assertThrows(NopException.class, () ->
                    shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
            assertEquals(ERR_DATAV_SHARE_PASSWORD_REQUIRED.getErrorCode(), missing.getErrorCode());

            for (int i = 0; i < 2; i++) {
                NopException ex = assertThrows(NopException.class, () ->
                        shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
                assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode());
            }

            // 计数=2（缺密码未计）< 3 → 正确密码成功；若缺密码计数则此处应为 LOCKED
            NopDatavDashboardSnapshot snapshot = shareBiz.getSharedDashboard(
                    share.getShareToken(), "rightPwd", anonymousContext());
            assertNotNull(snapshot, "missing-password attempts do not consume the failure budget");
        });
    }

    /**
     * 总速率超限被拒 + 窗口恢复：被拒错误码为限流专用码（R5——限流拒绝先于 token 查库/快照读取，
     * 被拒请求不触发查询资源）。
     */
    @Test
    public void testTotalRateLimitRejectsAndRecoversAfterWindow() {
        withConfig(CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW, 3, () -> {
            withConfig(CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS, 60, () -> {
                installFakeClock();
                NopDatavDashboardShare share = newPublishedShare("dash-rl-3", null);

                for (int i = 0; i < 3; i++) {
                    assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()),
                            "access #" + (i + 1) + " within budget succeeds");
                }

                NopException limited = assertThrows(NopException.class, () ->
                        shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
                assertEquals(ERR_DATAV_SHARE_RATE_LIMITED.getErrorCode(), limited.getErrorCode(),
                        "over-budget request rejected with rate-limit specific code (fast fail, no query)");
                Object retryAfter = limited.getParam(ARG_RETRY_AFTER_SECONDS);
                assertNotNull(retryAfter, "rate-limit error carries retryAfterSeconds");
                assertTrue(((Number) retryAfter).longValue() > 0, "retryAfterSeconds positive");

                fakeNow.addAndGet(61_000L);
                assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()),
                        "access recovers after window rolls");
            });
        });
    }

    /**
     * token 探测防护（Phase 2 Exit Criteria #3）：不存在 token 的重复探测超限后错误码从
     * TOKEN_NOT_FOUND 翻转为 RATE_LIMITED（R5 裁定语义：前 M 次可区分、超限后统一限流拒绝）。
     */
    @Test
    public void testNonexistentTokenProbingGetsRateLimited() {
        withConfig(CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW, 3, () -> {
            installFakeClock();
            String probeToken = "probe-token-" + System.nanoTime();

            for (int i = 0; i < 3; i++) {
                NopException ex = assertThrows(NopException.class, () ->
                        shareBiz.getSharedDashboard(probeToken, null, anonymousContext()));
                assertEquals(ERR_DATAV_SHARE_TOKEN_NOT_FOUND.getErrorCode(), ex.getErrorCode(),
                        "probe #" + (i + 1) + " within budget reports token-not-found");
            }

            NopException limited = assertThrows(NopException.class, () ->
                    shareBiz.getSharedDashboard(probeToken, null, anonymousContext()));
            assertEquals(ERR_DATAV_SHARE_RATE_LIMITED.getErrorCode(), limited.getErrorCode(),
                    "probe flood over budget flips to rate-limited (token probing protection)");
        });
    }

    /**
     * R1 组合键（token|IP）：同一 token 不同来源互不误伤——来源 A 爆破锁定后，来源 B 正常访问不受影响；
     * A 的锁定仍生效（键隔离双向断言）。
     */
    @Test
    public void testDifferentSourceIpsDoNotAffectEachOther() {
        withConfig(CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES, 2, () -> {
            withConfig(CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS, 60, () -> {
                installFakeClock();
                NopDatavDashboardShare share = newPublishedShare("dash-rl-5", "rightPwd");

                // 来源 A（10.0.0.1）爆破至锁定（阈值 2）
                for (int i = 0; i < 2; i++) {
                    NopException ex = assertThrows(NopException.class, () ->
                            shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd",
                                    anonymousContextWithIp("10.0.0.1")));
                    assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode());
                }

                // 来源 B（10.0.0.2）正确密码 → 成功（A 的锁定不误伤 B）
                assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), "rightPwd",
                                anonymousContextWithIp("10.0.0.2")),
                        "different source IP is not affected by another source's lock");

                // 来源 A 仍锁定（正确密码也被拒）
                NopException stillLocked = assertThrows(NopException.class, () ->
                        shareBiz.getSharedDashboard(share.getShareToken(), "rightPwd",
                                anonymousContextWithIp("10.0.0.1")));
                assertEquals(ERR_DATAV_SHARE_PASSWORD_LOCKED.getErrorCode(), stillLocked.getErrorCode(),
                        "attacker's source stays locked");
            });
        });
    }

    /**
     * 开关关闭时行为与加固前等价（Phase 2 item 4）：总速率阈值压到 1、密码失败阈值压到 1，
     * enabled=false 后均不生效（探测恒 TOKEN_NOT_FOUND、错误密码恒 MISMATCH，无限流/锁定错误码）。
     */
    @Test
    public void testDisabledSwitchEquivalentToLegacyBehavior() {
        withConfig(CFG_DATAV_SHARE_RATE_LIMIT_ENABLED, false, () -> {
            withConfig(CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW, 1, () -> {
                withConfig(CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES, 1, () -> {
                    installFakeClock();
                    String probeToken = "probe-disabled-" + System.nanoTime();
                    for (int i = 0; i < 5; i++) {
                        NopException ex = assertThrows(NopException.class, () ->
                                shareBiz.getSharedDashboard(probeToken, null, anonymousContext()));
                        assertEquals(ERR_DATAV_SHARE_TOKEN_NOT_FOUND.getErrorCode(), ex.getErrorCode(),
                                "disabled: no rate limiting on probing (legacy behavior)");
                    }

                    NopDatavDashboardShare share = newPublishedShare("dash-rl-6", "rightPwd");
                    for (int i = 0; i < 5; i++) {
                        NopException ex = assertThrows(NopException.class, () ->
                                shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
                        assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode(),
                                "disabled: no password lockout (legacy behavior)");
                    }
                    assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), "rightPwd", anonymousContext()),
                            "disabled: correct password still succeeds");
                });
            });
        });
    }

    /**
     * 默认配置零误伤（Phase 2 Exit Criteria #4）：默认阈值（5 失败/60 请求每 10 分钟窗口）下，
     * 正常访问路径（多次浏览 + 正确密码 + 少量手误后纠正）全部成功。
     */
    @Test
    public void testDefaultConfigNormalAccessZeroFalsePositive() {
        installFakeClock();
        NopDatavDashboardShare share = newPublishedShare("dash-rl-7", "rightPwd");

        for (int i = 0; i < 10; i++) {
            assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), "rightPwd", anonymousContext()),
                    "normal browsing #" + (i + 1) + " with correct password succeeds under default config");
        }
        // 4 次手误（< 默认阈值 5）→ 正确密码仍成功
        for (int i = 0; i < 4; i++) {
            assertThrows(NopException.class, () ->
                    shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
        }
        assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), "rightPwd", anonymousContext()),
                "correct password after a few typos still succeeds (below default threshold)");
    }

    // ==================== Helpers ====================

    private void installFakeClock() {
        fakeNow.set(System.currentTimeMillis());
        shareAccessGuard.setClock(fakeNow::get);
    }

    private static <T> void withConfig(IConfigReference<T> ref, T value, Runnable body) {
        T orig = ref.get();
        AppConfig.getConfigProvider().updateConfigValue(ref, value);
        try {
            body.run();
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(ref, orig);
        }
    }

    private IServiceContext anonymousContext() {
        return anonymousContextWithIp(null);
    }

    /** 匿名上下文 + 网关注入的来源 IP 头（R1：{@code nop-client-addr}，ServiceContextImpl.setRequestHeaders 先例）。 */
    private IServiceContext anonymousContextWithIp(String clientIp) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        if (clientIp != null) {
            context.setRequestHeaders(Map.of("nop-client-addr", clientIp));
        }
        return context;
    }

    private NopDatavDashboardShare newPublishedShare(String dashboardId, String password) {
        long now = System.currentTimeMillis();
        NopDatavDashboard dash = new NopDatavDashboard();
        dash.setDashboardId(dashboardId);
        dash.setDashboardName("rl-dash-" + dashboardId);
        dash.setDisplayName("rl-dash-" + dashboardId);
        dash.setPublishStatus(0);
        dash.setVersion(0L);
        dash.setCreatedBy("alice");
        dash.setCreateTime(new Timestamp(now));
        dash.setUpdatedBy("alice");
        dash.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(dash);

        IServiceContext ownerCtx = ownerContext("alice");
        dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);
        return shareBiz.createShare(dash.getDashboardId(), password, null, ownerCtx);
    }

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }
}
