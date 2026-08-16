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
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_MISMATCH;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_RATE_LIMITED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分享访问统计 focused tests（plan 2026-08-15-0004-2 Phase 3，裁定 R4 见
 * permission-sharing-design.md「访问限流与访问统计」）。
 *
 * <p>口径：仅成功访问计数（全链路校验通过且快照返回）；写策略：定向 SQL 数据库端原子自增
 * （并发零丢失，不走实体 update → 无 version bump/审计列改写）；暴露：owner 经 listShares 可见
 * （不新增权限面）。全部经真实调用链 {@code shareBiz.getSharedDashboard}（接线验证）。</p>
 */
public class TestNopDatavShareVisitStats extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    @Inject
    IOrmTemplate ormTemplate;

    /**
     * 两次成功访问 → listShares 返回计数 2、lastVisitTime 非空且晚于访问前（owner 可见统计；
     * passwordHash 屏蔽不回归）。
     */
    @Test
    public void testTwoSuccessfulVisitsUpdateStatsVisibleInListShares() {
        NopDatavDashboardShare share = newPublishedShare("dash-stats-1", null);
        long beforeVisit = System.currentTimeMillis();

        assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));

        List<NopDatavDashboardShare> shares = shareBiz.listShares(share.getDashboardId(), ownerContext("alice"));
        assertEquals(1, shares.size());
        NopDatavDashboardShare listed = shares.get(0);
        assertEquals(2L, listed.getVisitCount(), "two successful visits counted");
        assertNotNull(listed.getLastVisitTime(), "lastVisitTime populated");
        assertTrue(listed.getLastVisitTime().getTime() >= beforeVisit,
                "lastVisitTime is not earlier than the visits");
        assertNull(listed.getPasswordHash(), "passwordHash masking in listShares not regressed");
    }

    /**
     * 失败/被限流访问不计数（R4 口径）：2 次密码失败 + 1 次被限流（正确密码也被总速率拒绝）后，
     * visitCount 仍为 0、lastVisitTime 仍为 null（无一次成功访问）。
     */
    @Test
    public void testFailedAndLimitedAccessDoNotCount() {
        Integer origMax = CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW, 2);
        try {
            NopDatavDashboardShare share = newPublishedShare("dash-stats-2", "rightPwd");

            for (int i = 0; i < 2; i++) {
                NopException ex = assertThrows(NopException.class, () ->
                        shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
                assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode());
            }
            // 第 3 次（超总速率预算）正确密码也被拒——被限流访问不计数
            NopException limited = assertThrows(NopException.class, () ->
                    shareBiz.getSharedDashboard(share.getShareToken(), "rightPwd", anonymousContext()));
            assertEquals(ERR_DATAV_SHARE_RATE_LIMITED.getErrorCode(), limited.getErrorCode());

            List<NopDatavDashboardShare> shares = shareBiz.listShares(share.getDashboardId(), ownerContext("alice"));
            assertEquals(0L, shares.get(0).getVisitCount(), "no successful visit -> count stays 0");
            assertNull(shares.get(0).getLastVisitTime(), "no successful visit -> lastVisitTime stays null");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_SHARE_RATE_LIMIT_MAX_ACCESS_PER_WINDOW, origMax);
        }
    }

    /**
     * 并发访问计数不丢失（R4 并发策略：数据库端原子自增）：8 个线程各自独立 ORM session 并发成功访问，
     * 最终计数恰为 8（若走读-改-写实体更新会丢失更新）。
     */
    @Test
    public void testConcurrentVisitsLoseNoCounts() throws Exception {
        NopDatavDashboardShare share = newPublishedShare("dash-stats-3", null);
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<NopDatavDashboardSnapshot>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> ormTemplate.runInNewSession(
                        session -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()))));
            }
            for (Future<NopDatavDashboardSnapshot> future : futures) {
                assertNotNull(future.get(30, TimeUnit.SECONDS), "concurrent access succeeds");
            }
        } finally {
            pool.shutdownNow();
        }

        NopDatavDashboardShare reloaded = daoProvider.daoFor(NopDatavDashboardShare.class)
                .getEntityById(share.getShareId());
        assertEquals((long) threads, reloaded.getVisitCount(),
                "atomic SQL increment loses no counts under concurrent visits");
        assertNotNull(reloaded.getLastVisitTime());
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

    private NopDatavDashboardShare newPublishedShare(String dashboardId, String password) {
        long now = System.currentTimeMillis();
        NopDatavDashboard dash = new NopDatavDashboard();
        dash.setDashboardId(dashboardId);
        dash.setDashboardName("stats-dash-" + dashboardId);
        dash.setDisplayName("stats-dash-" + dashboardId);
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
}
