package io.nop.datav.service.entity;

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
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_DISABLED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_EXPIRED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_MISMATCH;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_REQUIRED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_TOKEN_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 公共访问 API（{@code getSharedDashboard}）接线测试（D3-2 Phase 3）。
 *
 * <p>验证 {@code @Auth(publicAccess=true)} action：在无登录上下文时（匿名）经 token + 密码 + 有效期 +
 * 启用校验后返回已发布快照；所有失败分支（令牌不存在/禁用/过期/密码缺失/密码不匹配/无快照）显式抛异常。</p>
 */
public class TestNopDatavSharedDashboardAccessBizModel extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    @Test
    public void testAnonymousAccessWithValidTokenNoPassword() {
        PublishedDashboard published = publishDashboard("dash-pub-1", "pub-1", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                published.dashboardId, null, null, ownerContext("alice"));

        NopDatavDashboardSnapshot snapshot = shareBiz.getSharedDashboard(
                share.getShareToken(), null, anonymousContext());

        assertNotNull(snapshot);
        assertEquals(published.snapshotVersion, snapshot.getSnapshotVersion());
    }

    @Test
    public void testAnonymousAccessWithCorrectPassword() {
        PublishedDashboard published = publishDashboard("dash-pub-2", "pub-2", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                published.dashboardId, "topSecret", null, ownerContext("alice"));

        NopDatavDashboardSnapshot snapshot = shareBiz.getSharedDashboard(
                share.getShareToken(), "topSecret", anonymousContext());

        assertNotNull(snapshot);
        assertEquals(published.snapshotVersion, snapshot.getSnapshotVersion());
    }

    @Test
    public void testAnonymousAccessRejectsUnknownToken() {
        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard("nonexistent-token", null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_TOKEN_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testAnonymousAccessRejectsEmptyToken() {
        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard("", null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_TOKEN_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testAnonymousAccessRejectsDisabledShare() {
        PublishedDashboard published = publishDashboard("dash-pub-3", "pub-3", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                published.dashboardId, null, null, ownerContext("alice"));
        shareBiz.revokeShare(share.getShareId(), ownerContext("alice"));

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_DISABLED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testAnonymousAccessRejectsExpiredShare() {
        PublishedDashboard published = publishDashboard("dash-pub-4", "pub-4", "alice");
        Timestamp past = new Timestamp(System.currentTimeMillis() - 3600_000L);
        NopDatavDashboardShare share = shareBiz.createShare(
                published.dashboardId, null, past, ownerContext("alice"));

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_EXPIRED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testAnonymousAccessRejectsMissingPassword() {
        PublishedDashboard published = publishDashboard("dash-pub-5", "pub-5", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                published.dashboardId, "requiredPwd", null, ownerContext("alice"));

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_PASSWORD_REQUIRED.getErrorCode(), ex.getErrorCode());

        NopException ex2 = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), "", anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_PASSWORD_REQUIRED.getErrorCode(), ex2.getErrorCode(),
                "empty-string password must not bypass the required-password gate");
    }

    @Test
    public void testAnonymousAccessRejectsWrongPassword() {
        PublishedDashboard published = publishDashboard("dash-pub-6", "pub-6", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                published.dashboardId, "correctPwd", null, ownerContext("alice"));

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testAnonymousAccessRejectsShareForUnpublishedDashboard() {
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-pub-7", "no-snapshot", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, null, ownerContext("alice"));

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SNAPSHOT_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    // ==================== Helpers ====================

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    /**
     * 匿名上下文：不设置 userName，且无 userContext。模拟 publicAccess action 的运行时环境。
     */
    private IServiceContext anonymousContext() {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        return context;
    }

    private PublishedDashboard publishDashboard(String id, String name, String owner) {
        NopDatavDashboard dash = saveDashboardOwnedBy(id, name, owner);
        IServiceContext ctx = ownerContext(owner);
        NopDatavDashboardSnapshot snapshot = dashboardBiz.publishDashboard(dash.getDashboardId(), ctx);
        return new PublishedDashboard(dash.getDashboardId(), snapshot.getSnapshotVersion());
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

    private static final class PublishedDashboard {
        final String dashboardId;
        final long snapshotVersion;

        PublishedDashboard(String dashboardId, long snapshotVersion) {
            this.dashboardId = dashboardId;
            this.snapshotVersion = snapshotVersion;
        }
    }
}
