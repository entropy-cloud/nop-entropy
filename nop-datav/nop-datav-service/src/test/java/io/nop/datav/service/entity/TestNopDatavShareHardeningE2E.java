package io.nop.datav.service.entity;

import io.nop.api.core.config.AppConfig;
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
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_DISABLED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_LOCKED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分享访问加固端到端测试（plan 2026-08-15-0004-2 Phase 4）。
 *
 * <p>单测试覆盖完整生命周期（从匿名访问入口到限流判定到统计落库到 listShares 暴露）：
 * 创建带密码分享 → 匿名正确密码访问成功且统计可见 → 连续错误密码至阈值 → 被限流（正确密码也被拒）
 * → 窗口推进后恢复（fake 时钟，非 sleep）→ 撤销分享后访问被拒。全程经真实
 * {@code shareBiz} 调用链（真实持久层 + 真实限流 guard bean + 真实统计 SQL）。</p>
 */
public class TestNopDatavShareHardeningE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    @Inject
    NopDatavShareAccessGuard shareAccessGuard;

    private final AtomicLong fakeNow = new AtomicLong();

    @AfterEach
    public void restoreRealClock() {
        if (shareAccessGuard != null) {
            shareAccessGuard.setClock(System::currentTimeMillis);
        }
    }

    @Test
    public void testFullLifecycleShareAccessHardening() {
        Integer origFailures = CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES.get();
        Integer origWindow = CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES, 3);
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS, 60);
        try {
            fakeNow.set(System.currentTimeMillis());
            shareAccessGuard.setClock(fakeNow::get);

            // 1. owner 创建 + 发布看板，创建带密码分享
            IServiceContext ownerCtx = ownerContext("alice");
            NopDatavDashboard dash = saveDashboardOwnedBy("dash-e2e-harden", "e2e-harden", "alice");
            NopDatavDashboardSnapshot published = dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);
            NopDatavDashboardShare share = shareBiz.createShare(
                    dash.getDashboardId(), "e2eSecret", null, ownerCtx);

            // 2. 匿名正确密码访问成功，统计落库并经 listShares 暴露给 owner
            NopDatavDashboardSnapshot accessed = shareBiz.getSharedDashboard(
                    share.getShareToken(), "e2eSecret", anonymousContext());
            assertEquals(published.getSnapshotVersion(), accessed.getSnapshotVersion(),
                    "anonymous access returns the published snapshot");
            NopDatavDashboardShare listed = shareBiz.listShares(dash.getDashboardId(), ownerCtx).get(0);
            assertEquals(1L, listed.getVisitCount(), "stats persisted and exposed via listShares (count=1)");
            assertNotNull(listed.getLastVisitTime(), "stats persisted and exposed via listShares (lastVisitTime)");

            // 3. 连续错误密码至阈值（3 次 MISMATCH，第 3 次置锁）
            for (int i = 0; i < 3; i++) {
                NopException ex = assertThrows(NopException.class, () ->
                        shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
                assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode());
            }

            // 4. 被限流：正确密码也被拒（密码锁定专用错误码）
            NopException locked = assertThrows(NopException.class, () ->
                    shareBiz.getSharedDashboard(share.getShareToken(), "e2eSecret", anonymousContext()));
            assertEquals(ERR_DATAV_SHARE_PASSWORD_LOCKED.getErrorCode(), locked.getErrorCode());

            // 5. 窗口推进（fake 时钟 +61s > 60s）后恢复：正确密码访问成功，统计继续累加
            fakeNow.addAndGet(61_000L);
            assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), "e2eSecret", anonymousContext()),
                    "access recovers after lock window passes");
            NopDatavDashboardShare listedAfter = shareBiz.listShares(dash.getDashboardId(), ownerCtx).get(0);
            assertEquals(2L, listedAfter.getVisitCount(),
                    "recovered successful visit counted (count=2); locked/failed attempts not counted");

            // 6. 撤销分享后访问被拒（软禁用语义，ERR_DATAV_SHARE_DISABLED）
            shareBiz.revokeShare(share.getShareId(), ownerCtx);
            NopException revoked = assertThrows(NopException.class, () ->
                    shareBiz.getSharedDashboard(share.getShareToken(), "e2eSecret", anonymousContext()));
            assertEquals(ERR_DATAV_SHARE_DISABLED.getErrorCode(), revoked.getErrorCode());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(
                    CFG_DATAV_SHARE_RATE_LIMIT_MAX_PASSWORD_FAILURES, origFailures);
            AppConfig.getConfigProvider().updateConfigValue(
                    CFG_DATAV_SHARE_RATE_LIMIT_WINDOW_SECONDS, origWindow);
        }
    }

    // ==================== Helpers ====================

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private IServiceContext anonymousContext() {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        return context;
    }

    private NopDatavDashboard saveDashboardOwnedBy(String id, String name, String owner) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy(owner);
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy(owner);
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }
}
