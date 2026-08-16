package io.nop.datav.service.entity;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.datav.biz.INopDatavDashboardShareBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-1 回归测试（plan 2026-08-15-2146-2 Phase 1）：分享密码哈希 dirty-flush 修复。
 *
 * <p>缺陷机制：{@code doToggleShare} 返回前对 attached（MANAGED）实体执行
 * {@code setPasswordHash(null)}（mask-on-return 改写）——@BizMutation 事务 commit 时
 * {@code OrmTransactionListener.onBeforeCommit → flushSession} 对重新变脏的实体补发
 * {@code UPDATE ... SET PASSWORD_HASH=NULL}，带密码分享经任意 toggle 后哈希被物理抹除，
 * {@code verifySharePassword} 对空哈希直接放行 → 凭 token 匿名访问原受密码保护看板。</p>
 *
 * <p><b>接线验证</b>：本测试经 {@link IGraphQLEngine} 以 mutation 调用 {@code toggleShare}
 * （真实事务装饰器路径——GraphQL mutation 经 {@code GraphQLTransactionOperationInvoker} 包裹
 * REQUIRED 事务），而非 {@code @Inject} 直调裸 bean（无事务装饰器，损坏路径物理不可达）。
 * 事务提交后以裸 JDBC 断言持久层 {@code NOP_DATAV_SHARE.PASSWORD_HASH} 仍为原 BCrypt 哈希，
 * 随后 {@code getSharedDashboard} 错误密码仍被拒、正确密码放行。</p>
 *
 * <p>mutate-fail：若回退到「对 attached 实体 mask 改写」实现，事务 commit 的补发 UPDATE 会把
 * 哈希抹为 null → 首个断言（哈希仍为 BCrypt）确定性失败。</p>
 */
public class TestNopDatavShareToggleTransactionPath extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
        io.nop.api.core.context.IContext ctx = ContextProvider.currentContext();
        if (ctx != null) {
            ctx.setUserName(null);
        }
    }

    /**
     * 主断言：经真实事务装饰器路径 toggle 后，持久层 PASSWORD_HASH 不变（仍为 BCrypt），
     * 密码校验语义不变（错误密码拒绝、正确密码放行）。
     */
    @Test
    public void testToggleShareViaTransactionKeepsPasswordHash() {
        IServiceContext ownerCtx = newOwnerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-tx-toggle", "tx-toggle", "alice");
        seedPublishedSnapshot(dash.getDashboardId(), "alice");

        NopDatavDashboardShare share = shareBiz.createShare(
                dash.getDashboardId(), "secretPwd", null, ownerCtx);
        assertNotNull(share.getShareId());
        assertNull(share.getPasswordHash(), "createShare return view masks passwordHash");

        String hashBefore = readPasswordHashRaw(share.getShareId());
        assertNotNull(hashBefore, "DB row must have a password hash before toggle");
        assertTrue(hashBefore.startsWith("$2"), "hash is BCrypt composite output: " + hashBefore);

        // 经真实 GraphQL mutation 事务路径执行 toggleShare（关闭分享）
        setUserContext("alice");
        ApiResponse<?> revokeResp = executeToggleMutation(share.getShareId(), false);
        assertEquals(0, revokeResp.getStatus(), "toggleShare(revoke) mutation should succeed: " + revokeResp);
        // 再经同一事务路径重新启用
        ApiResponse<?> enableResp = executeToggleMutation(share.getShareId(), true);
        assertEquals(0, enableResp.getStatus(), "toggleShare(enable) mutation should succeed: " + enableResp);

        // 事务提交后，裸 JDBC 直读持久层：哈希必须仍是原 BCrypt 值（未被 commit 期补发 UPDATE 抹除）
        String hashAfter = readPasswordHashRaw(share.getShareId());
        assertNotNull(hashAfter, "PASSWORD_HASH must survive transactional toggle (AR-1)");
        assertEquals(hashBefore, hashAfter, "PASSWORD_HASH must be byte-identical after toggle");
        assertTrue(hashAfter.startsWith("$2"), "hash is still BCrypt after toggle");

        // toggle 后的 DB 行 enabled=1（启用）且密码语义仍生效
        NopDatavDashboardShare persisted = daoProvider.daoFor(NopDatavDashboardShare.class)
                .getEntityById(share.getShareId());
        assertEquals(Byte.valueOf((byte) 1), persisted.getEnabled(), "share re-enabled");

        // 密码校验语义：错误密码被拒（ERR_DATAV_SHARE_PASSWORD_MISMATCH）
        IServiceContext anonCtx = newOwnerContext(null);
        NopException wrong = assertThrows(NopException.class, () ->
                shareBiz.getSharedDashboard(share.getShareToken(), "WRONG-password", anonCtx));
        assertEquals("nop.err.datav.share-password-mismatch", wrong.getErrorCode(),
                "wrong password must still be rejected after toggle");

        // 正确密码放行（返回已发布快照）
        NopDatavDashboardSnapshot snapshot = shareBiz.getSharedDashboard(
                share.getShareToken(), "secretPwd", anonCtx);
        assertNotNull(snapshot, "correct password grants access after toggle");
        assertEquals(dash.getDashboardId(), snapshot.getDashboardId());
    }

    /**
     * createShare 出参 view：passwordHash=null（mask 语义保留），可暴露字段完整。
     */
    @Test
    public void testCreateShareViewFieldsCompleteAndMasked() {
        IServiceContext ownerCtx = newOwnerContext("alice");
        NopDatavDashboard dash = saveDashboardOwnedBy("dash-tx-view", "tx-view", "alice");

        NopDatavDashboardShare view = shareBiz.createShare(
                dash.getDashboardId(), "pwd-view", null, ownerCtx);

        assertNull(view.getPasswordHash(), "view must mask passwordHash");
        assertNotNull(view.getShareId());
        assertNotNull(view.getShareToken());
        assertEquals(dash.getDashboardId(), view.getDashboardId());
        assertEquals(Byte.valueOf((byte) 1), view.getEnabled());
        assertEquals("alice", view.getCreatedBy());

        // listShares 出参同样 mask 且不含 attached 实体
        setUserContext("alice");
        var shares = shareBiz.listShares(dash.getDashboardId(), ownerCtx);
        assertEquals(1, shares.size());
        assertNull(shares.get(0).getPasswordHash(), "listShares view must mask passwordHash");
        assertEquals(view.getShareId(), shares.get(0).getShareId());
    }

    // ==================== Helpers ====================

    private ApiResponse<?> executeToggleMutation(String shareId, boolean enabled) {
        Map<String, Object> data = new HashMap<>();
        data.put("shareId", shareId);
        data.put("enabled", enabled);
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavDashboardShare__toggleShare", request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    /**
     * 裸 JDBC 直读持久层（不经 ORM session，无 session 缓存/flush 干扰）。
     */
    private String readPasswordHashRaw(String shareId) {
        return jdbcTemplate.executeQuery(
                SQL.begin().name("readSharePasswordHash")
                        .sql("select PASSWORD_HASH from NOP_DATAV_SHARE where SHARE_ID=").param(shareId).end(),
                rs -> {
                    if (!rs.hasNext()) {
                        return null;
                    }
                    return (String) rs.next().getObject(0);
                });
    }

    private void setUserContext(String userName) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userName);
        userContext.setUserName(userName);
        userContext.setRoles(CollectionHelper.buildImmutableSet("admin"));
        IUserContext.set(userContext);
        ContextProvider.getOrCreateContext().setUserName(userName);
    }

    private IServiceContext newOwnerContext(String userName) {
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

    private void seedPublishedSnapshot(String dashboardId, String owner) {
        long now = System.currentTimeMillis();
        NopDatavDashboardSnapshot s = new NopDatavDashboardSnapshot();
        s.setSnapshotId("snap-" + dashboardId);
        s.setDashboardId(dashboardId);
        s.setSnapshotVersion(1L);
        s.setSnapshotContent("{}");
        s.setPublishedBy(owner);
        s.setPublishedTime(new Timestamp(now));
        s.setDelFlag((byte) 0);
        s.setVersion(0L);
        s.setCreatedBy(owner);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(owner);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(s);
    }
}
