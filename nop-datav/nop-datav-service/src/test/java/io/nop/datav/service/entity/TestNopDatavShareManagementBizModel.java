package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardShareBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_NOT_DASHBOARD_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分享管理 API 接线测试（D3-2 Phase 2）。
 *
 * <p>通过注入 {@link INopDatavDashboardShareBiz} 代理调用 createShare / listShares / revokeShare /
 * toggleShare，验证：分享记录持久化、passwordHash 已哈希（非明文）、出参不含 passwordHash、
 * owner 校验拒绝越权、dashboard 不存在/分享不存在分支显式抛异常。</p>
 */
public class TestNopDatavShareManagementBizModel extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    @Test
    public void testCreateShareWithoutPassword() {
        IServiceContext ownerCtx = newContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-1", "share-no-pwd", "alice");

        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, null, ownerCtx);

        assertNotNull(share);
        assertNotNull(share.getShareId());
        assertNotNull(share.getShareToken());
        assertEquals(32, share.getShareToken().length(), "token is 32-char hex UUID");
        assertEquals(dash.getDashboardId(), share.getDashboardId());
        assertNull(share.getPasswordHash(), "no password -> null hash");
        assertNull(share.getExpireTime(), "no expire -> null");
        assertEquals((byte) 1, share.getEnabled(), "enabled defaults to 1");
    }

    @Test
    public void testCreateShareHashesPasswordAndNeverStoresPlain() {
        IServiceContext ownerCtx = newContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-2", "share-pwd", "alice");
        String password = "mySecret123";

        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), password, null, ownerCtx);

        assertNotNull(share.getPasswordHash());
        assertNotEquals(password, share.getPasswordHash(), "plaintext password must never be stored");
        assertTrue(share.getPasswordHash().startsWith("$2a"),
                "CompositePasswordEncoder(BCrypt) output starts with $2a");
    }

    @Test
    public void testCreateShareWithExpireTime() {
        IServiceContext ownerCtx = newContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-3", "share-expire", "alice");
        Timestamp future = new Timestamp(System.currentTimeMillis() + 3600_000L);

        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, future, ownerCtx);

        assertEquals(future, share.getExpireTime());
    }

    @Test
    public void testListSharesExcludesPasswordHash() {
        IServiceContext ownerCtx = newContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-4", "share-list", "alice");

        shareBiz.createShare(dash.getDashboardId(), "pwdA", null, ownerCtx);
        shareBiz.createShare(dash.getDashboardId(), null, null, ownerCtx);
        shareBiz.createShare(dash.getDashboardId(), "pwdB", null, ownerCtx);

        List<NopDatavDashboardShare> shares = shareBiz.listShares(dash.getDashboardId(), ownerCtx);

        assertEquals(3, shares.size());
        for (NopDatavDashboardShare share : shares) {
            assertNull(share.getPasswordHash(),
                    "list output must never expose passwordHash");
        }
    }

    @Test
    public void testListSharesOnlyReturnsSharesForGivenDashboard() {
        IServiceContext ownerCtx = newContext("alice");
        NopDatavDashboard dash1 = saveDashboardOwnedBy("dash-mgr-5a", "share-isolation-1", "alice");
        NopDatavDashboard dash2 = saveDashboardOwnedBy("dash-mgr-5b", "share-isolation-2", "alice");

        shareBiz.createShare(dash1.getDashboardId(), null, null, ownerCtx);
        shareBiz.createShare(dash1.getDashboardId(), null, null, ownerCtx);
        shareBiz.createShare(dash2.getDashboardId(), null, null, ownerCtx);

        List<NopDatavDashboardShare> sharesOfDash1 = shareBiz.listShares(dash1.getDashboardId(), ownerCtx);
        assertEquals(2, sharesOfDash1.size());
        for (NopDatavDashboardShare share : sharesOfDash1) {
            assertEquals(dash1.getDashboardId(), share.getDashboardId());
        }
    }

    @Test
    public void testRevokeShareDisablesButKeepsRow() {
        IServiceContext ownerCtx = newContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-6", "share-revoke", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, null, ownerCtx);
        assertEquals((byte) 1, share.getEnabled());

        NopDatavDashboardShare revoked = shareBiz.revokeShare(share.getShareId(), ownerCtx);

        assertEquals((byte) 0, revoked.getEnabled(), "revoke sets enabled=0");
        NopDatavDashboardShare reloaded = daoProvider.daoFor(NopDatavDashboardShare.class)
                .getEntityById(share.getShareId());
        assertNotNull(reloaded, "row is preserved (soft disable, not delete)");
        assertEquals((byte) 0, reloaded.getEnabled());
    }

    @Test
    public void testToggleShareCanReEnable() {
        IServiceContext ownerCtx = newContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-7", "share-toggle", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, null, ownerCtx);

        NopDatavDashboardShare disabled = shareBiz.toggleShare(share.getShareId(), false, ownerCtx);
        assertEquals((byte) 0, disabled.getEnabled());

        NopDatavDashboardShare reEnabled = shareBiz.toggleShare(share.getShareId(), true, ownerCtx);
        assertEquals((byte) 1, reEnabled.getEnabled());
    }

    @Test
    public void testCreateShareRejectsNonOwner() {
        IServiceContext aliceCtx = newContext("alice");
        IServiceContext bobCtx = newContext("bob");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-8", "share-deny", "alice");

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.createShare(dash.getDashboardId(), null, null, bobCtx));
        assertEquals(ERR_DATAV_NOT_DASHBOARD_OWNER.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testListSharesRejectsNonOwner() {
        IServiceContext aliceCtx = newContext("alice");
        IServiceContext bobCtx = newContext("bob");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-9", "share-deny-list", "alice");

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.listShares(dash.getDashboardId(), bobCtx));
        assertEquals(ERR_DATAV_NOT_DASHBOARD_OWNER.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testCreateShareRejectsMissingDashboard() {
        IServiceContext ownerCtx = newContext("alice");
        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.createShare("missing-dash-id", null, null, ownerCtx));
        assertEquals(ERR_DATAV_DASHBOARD_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testRevokeShareRejectsMissingShare() {
        IServiceContext ownerCtx = newContext("alice");
        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.revokeShare("missing-share-id", ownerCtx));
        assertEquals(ERR_DATAV_SHARE_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testRevokeShareRejectsNonOwnerOfUnderlyingDashboard() {
        IServiceContext aliceCtx = newContext("alice");
        IServiceContext bobCtx = newContext("bob");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-mgr-10", "share-revoke-deny", "alice");
        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), null, null, aliceCtx);

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.revokeShare(share.getShareId(), bobCtx));
        assertEquals(ERR_DATAV_NOT_DASHBOARD_OWNER.getErrorCode(), ex.getErrorCode());

        NopDatavDashboardShare reloaded = daoProvider.daoFor(NopDatavDashboardShare.class)
                .getEntityById(share.getShareId());
        assertEquals((byte) 1, reloaded.getEnabled(), "non-owner revoke attempt does not mutate state");
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        if (userName != null) {
            context.getContext().setUserName(userName);
        }
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
