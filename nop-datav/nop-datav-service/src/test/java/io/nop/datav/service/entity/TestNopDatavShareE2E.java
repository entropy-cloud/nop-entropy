package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavDashboardShareBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavPanel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_NOT_DASHBOARD_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_DISABLED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_EXPIRED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_PASSWORD_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分享全链路端到端测试（D3-2 Phase 4）。
 *
 * <p>从「owner 创建看板 → 发布（产生快照）→ 创建分享（带密码 + 有效期）→ 以匿名上下文凭 token+password
 * 调 getSharedDashboard → 断言返回已发布快照内容」完整跑通。同时覆盖吊销/过期/密码失败/越权管理
 * 等分支，以及管理→公共访问隔离（管理需 owner，公共访问无需登录）。</p>
 */
public class TestNopDatavShareE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    /**
     * 全链路正向：owner 创建+发布看板 → 创建带密码+有效期分享 → 匿名凭 token+password 访问 → 返回快照内容。
     * 证明 createShare → NopDatavDashboardShare 持久化 → getSharedDashboard 读取 链路运行时连通。
     */
    @Test
    public void testFullChainOwnerPublishShareAnonymousAccess() {
        // 1. owner 创建看板 + 面板
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-e2e-1", "e2e-dashboard", "alice");
        dash.setLayoutConfig(JsonTool.stringify(Map.of("grid", "3x2")));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dash);
        savePanel("panel-e2e-1", dash.getDashboardId(), "Sales Chart");

        // 2. 发布，产生快照
        NopDatavDashboardSnapshot snapshot = dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);
        assertNotNull(snapshot.getSnapshotContent());
        assertTrue(snapshot.getSnapshotContent().contains("Sales Chart"),
                "snapshot content includes the published panel");

        // 3. 创建带密码 + 未来有效期的分享
        Timestamp future = new Timestamp(System.currentTimeMillis() + 86_400_000L);
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), "e2e-pwd", future, ownerCtx);
        assertNotNull(share.getShareToken());
        // createShare returns null passwordHash (mask-on-return); password hashing
        // is verified implicitly by step 4 below (getSharedDashboard succeeds with the right password).
        assertNull(share.getPasswordHash(),
                "createShare return value must never expose passwordHash");

        // 4. 匿名上下文凭 token + password 访问
        NopDatavDashboardSnapshot accessed = shareBiz.getSharedDashboard(
                share.getShareToken(), "e2e-pwd", anonymousContext());
        assertEquals(snapshot.getSnapshotVersion(), accessed.getSnapshotVersion());
        assertTrue(accessed.getSnapshotContent().contains("Sales Chart"),
                "anonymous access returns the real published snapshot content");
    }

    /**
     * 吊销端到端：分享创建后 toggle disabled → 匿名访问被拒（ERR_DATAV_SHARE_DISABLED）。
     */
    @Test
    public void testE2eDisabledShareRejectsAnonymousAccess() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-e2e-2", "e2e-disable", "alice");
        dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);

        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, null, ownerCtx);
        shareBiz.toggleShare(share.getShareId(), false, ownerCtx);

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_DISABLED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 过期端到端：分享 expireTime 设为过去 → 匿名访问被拒（ERR_DATAV_SHARE_EXPIRED）。
     */
    @Test
    public void testE2eExpiredShareRejectsAnonymousAccess() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-e2e-3", "e2e-expire", "alice");
        dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);

        Timestamp past = new Timestamp(System.currentTimeMillis() - 60_000L);
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, past, ownerCtx);

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_EXPIRED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 密码端到端：错误密码 → 匿名访问被拒（ERR_DATAV_SHARE_PASSWORD_MISMATCH）。
     */
    @Test
    public void testE2eWrongPasswordRejectsAnonymousAccess() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-e2e-4", "e2e-pwd-fail", "alice");
        dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);

        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), "rightPwd", null, ownerCtx);

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), "wrongPwd", anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_PASSWORD_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 管理→公共访问隔离端到端：非 owner 调 createShare 被拒（D3-1 owner 校验），
     * 而匿名（无登录）调 getSharedDashboard 放行——两者权限模型独立且均生效。
     */
    @Test
    public void testE2eManagementRequiresOwnerButPublicAccessIsAnonymous() {
        IServiceContext ownerCtx = ownerContext("alice");
        IServiceContext otherCtx = ownerContext("bob");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-e2e-5", "e2e-isolation", "alice");
        dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);

        // 非 owner 调管理 action 被拒
        NopException mgmtEx = assertThrows(NopException.class,
                () -> shareBiz.createShare(dash.getDashboardId(), null, null, otherCtx));
        assertEquals(ERR_DATAV_NOT_DASHBOARD_OWNER.getErrorCode(), mgmtEx.getErrorCode());

        // owner 创建分享后，匿名上下文（无登录用户）可访问
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, null, ownerCtx);
        NopDatavDashboardSnapshot snapshot = shareBiz.getSharedDashboard(
                share.getShareToken(), null, anonymousContext());
        assertNotNull(snapshot, "anonymous (no-login) access succeeds for public share");
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

    private void savePanel(String id, String dashboardId, String name) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(id);
        p.setDashboardId(dashboardId);
        p.setPanelName(name);
        p.setDisplayName(name);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("test");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("test");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
    }
}
