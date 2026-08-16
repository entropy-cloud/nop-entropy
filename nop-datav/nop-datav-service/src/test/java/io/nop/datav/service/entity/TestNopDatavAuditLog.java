package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.commons.util.CollectionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLLogger;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.datav.dao.entity.NopDatavDashboard;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D3-4 operation audit log integration test + combined RBAC+RLS+audit E2E.
 * <p>
 * Verifies the full chain: {@code GraphQLAuditLogger.onRpcExecute} →
 * {@code IAuditService.saveAudit} (enqueue) → background batch flush →
 * {@code NopAuthOpLog} persisted.
 * <p>
 * Platform note: {@code graphQLEngine.executeRpcAsync} does NOT auto-invoke the logger
 * (only {@code GraphQLWebService} does). Tests manually call
 * {@code graphQLLogger.onRpcExecute(ctx, beginTime, result, exception)} — the same
 * pattern as {@code io.nop.auth.service.TestGraphQLLogger}.
 * <p>
 * Async flush is handled by polling {@code auditService.isAllProcessed()} with
 * {@code FutureHelper.waitUntil} (no Thread.sleep blind-wait, no skipped assertion).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE,
        enableDataAuth = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
@NopTestProperty(name = "nop.auth.graphql.enable-audit", value = "true")
@NopTestProperty(name = "nop.auth.graphql.audit-mutation-patterns",
        value = "NopDatavDashboard__*,NopDatavPanel__*,NopDatavFilterState__*,NopDatavDashboardShare__*")
@NopTestProperty(name = "nop.auth.graphql.audit-query-patterns",
        value = "NopDatavDashboard__getPublishedDashboard")
public class TestNopDatavAuditLog extends AbstractNopDatavAuthTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IAuditService auditService;

    @Inject
    IGraphQLLogger graphQLLogger;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
        IContext ctx = ContextProvider.currentContext();
        if (ctx != null) {
            ctx.setUserName(null);
        }
    }

    /**
     * Audit success: admin publishes a dashboard → onRpcExecute → flush → NopAuthOpLog
     * contains a record with resultStatus=0, userName=admin-user, operation containing
     * NopDatavDashboard__publishDashboard.
     */
    @Test
    public void testAuditLog_successMutation() {
        NopDatavDashboard dashboard = saveDashboard("dash-audit-ok", "audit-success");
        setUserContext("admin-user", "admin");

        long beginTime = CoreMetrics.currentTimeMillis();
        ApiResponse<?> result = executePublishDashboard(dashboard.getDashboardId());
        assertEquals(0, result.getStatus(), "publish should succeed: " + result);

        // Manually invoke the logger (same as GraphQLWebService does in production)
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavDashboard__publishDashboard", new ApiRequest<>());
        graphQLLogger.onRpcExecute(ctx, beginTime, result, null);

        flushAudit();

        NopAuthOpLog log = findLogByOperation("NopDatavDashboard__publishDashboard");
        assertNotNull(log, "audit log should exist for successful publishDashboard");
        assertEquals(0, log.getResultStatus(), "resultStatus should be 0 for success");
        assertEquals("admin-user", log.getUserName(), "userName should be admin-user");
    }

    /**
     * Audit failure: publishDashboard on a non-existent dashboard throws → onRpcExecute
     * with exception → flush → NopAuthOpLog contains a record with errorCode non-null.
     */
    @Test
    public void testAuditLog_failedMutation() {
        setUserContext("admin-user", "admin");

        long beginTime = CoreMetrics.currentTimeMillis();
        ApiResponse<?> result = executePublishDashboard("non-existent-id");

        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavDashboard__publishDashboard", new ApiRequest<>());
        // Pass exception=null since the error is captured in the response (status=-1, code=errorCode).
        // In production, GraphQLWebService passes both response and exception.
        graphQLLogger.onRpcExecute(ctx, beginTime, result, null);

        flushAudit();

        NopAuthOpLog log = findLogByOperation("NopDatavDashboard__publishDashboard");
        assertNotNull(log, "audit log should exist even for failed publishDashboard");
        assertTrue(log.getErrorCode() != null && !log.getErrorCode().isEmpty(),
                "errorCode should be non-empty for failed operation, got: " + log.getErrorCode());
    }

    /**
     * Pattern not matched: findPage (query, not in audit-query-patterns) → shouldAudit
     * returns false → no audit record created.
     */
    @Test
    public void testAuditLog_patternNotMatched() {
        setUserContext("admin-user", "admin");

        long beginTime = CoreMetrics.currentTimeMillis();
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboard__findPage", new ApiRequest<>());
        ApiResponse<?> result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        // The logger checks shouldAudit → findPage is not in patterns → no save
        graphQLLogger.onRpcExecute(ctx, beginTime, result, null);

        flushAudit();

        NopAuthOpLog log = findLogByOperation("NopDatavDashboard__findPage");
        // No record should exist because findPage doesn't match audit patterns
        assertTrue(log == null || !containsOperation(log, "NopDatavDashboard__findPage"),
                "findPage should NOT be audited (not in audit patterns)");
    }

    /**
     * Share management mutation audit (plan 2026-08-14-2020-1 Phase 4): {@code NopDatavDashboardShare__*}
     * in audit-mutation-patterns → revokeShare (owner-guarded management mutation) 真实产生审计记录。
     * 防 pattern 拼写/顺序错误静默失效（application.yaml 与本测试 patterns 保持一致）。
     */
    @Test
    public void testAuditLog_shareManagementMutation() {
        NopDatavDashboard dashboard = saveDashboard("dash-audit-share", "audit-share");
        io.nop.datav.dao.entity.NopDatavDashboardShare share = seedShare("share-audit-1", dashboard.getDashboardId());
        setUserContext("admin-user", "admin");

        long beginTime = CoreMetrics.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("shareId", share.getShareId());
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavDashboardShare__revokeShare", request);
        ApiResponse<?> result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        assertEquals(0, result.getStatus(), "revokeShare should succeed: " + result);

        graphQLLogger.onRpcExecute(ctx, beginTime, result, null);
        flushAudit();

        NopAuthOpLog log = findLogByOperation("NopDatavDashboardShare__revokeShare");
        assertNotNull(log, "share management mutation should be audited (NopDatavDashboardShare__* pattern)");
        assertEquals(0, log.getResultStatus(), "resultStatus should be 0 for successful revokeShare");
    }

    /**
     * Combined E2E: RBAC deny + RLS filter + audit recording in the same auth-enabled context.
     * <ul>
     *   <li>RBAC: user role denied rollbackDashboard (admin-only permission)</li>
     *   <li>RLS: user A's draft dashboard not visible to user B via findPage</li>
     *   <li>Audit: the denied rollback attempt is recorded in NopAuthOpLog</li>
     * </ul>
     */
    @Test
    public void testCombinedE2E_rbacRlsAudit() {
        // Setup: userA owns a published dashboard; userB is a regular user
        saveDashboard("dash-e2e", "e2e-dashboard", "userA", 10);

        // --- RBAC deny: user denied rollbackDashboard (admin-only) ---
        setUserContext("regular-user", "user");

        long beginTime = CoreMetrics.currentTimeMillis();
        ApiResponse<?> rollbackResp = executeRollbackDashboard("dash-e2e", 1L);
        assertEquals(io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(),
                rollbackResp.getCode(),
                "user should be denied rollbackDashboard by RBAC: " + rollbackResp);

        IGraphQLExecutionContext rbacCtx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavDashboard__rollbackDashboard", new ApiRequest<>());
        graphQLLogger.onRpcExecute(rbacCtx, beginTime, rollbackResp, null);

        flushAudit();
        NopAuthOpLog denyLog = findLogByOperation("NopDatavDashboard__rollbackDashboard");
        assertNotNull(denyLog, "denied rollback should still be audited");
        assertTrue(denyLog.getErrorCode() != null && !denyLog.getErrorCode().isEmpty(),
                "denied operation should have errorCode in audit log");

        // --- RLS filter: userB cannot see userA's unpublished dashboard ---
        saveDashboard("dash-e2e-private", "private", "userA", 0);
        setUserContext("userB", "user");
        List<Map<String, Object>> items = findPageDashboards();
        boolean seesPrivate = items.stream().anyMatch(m -> "dash-e2e-private".equals(m.get("dashboardId")));
        assertFalse(seesPrivate, "RLS should filter out userA's private dashboard from userB");

        // --- Audit: successful query also recorded if pattern matches ---
        // getPublishedDashboard is in audit-query-patterns
        long beginTime2 = CoreMetrics.currentTimeMillis();
        ApiResponse<?> pubResp = executeGetPublishedDashboard("dash-e2e");
        IGraphQLExecutionContext pubCtx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboard__getPublishedDashboard", new ApiRequest<>());
        graphQLLogger.onRpcExecute(pubCtx, beginTime2, pubResp, null);
        flushAudit();
        NopAuthOpLog pubLog = findLogByOperation("NopDatavDashboard__getPublishedDashboard");
        assertNotNull(pubLog, "getPublishedDashboard should be audited (in query patterns)");
    }

    // ==================== Helpers ====================

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
        ContextProvider.getOrCreateContext().setUserName(userId);
    }

    private void flushAudit() {
        assertTrue(FutureHelper.waitUntil(() -> auditService.isAllProcessed(), 10000),
                "audit service should flush all records within timeout");
    }

    private NopAuthOpLog findLogByOperation(String operationFragment) {
        List<NopAuthOpLog> logs = daoProvider.daoFor(NopAuthOpLog.class).findAll();
        for (NopAuthOpLog log : logs) {
            if (containsOperation(log, operationFragment))
                return log;
        }
        return null;
    }

    private boolean containsOperation(NopAuthOpLog log, String operationFragment) {
        String op = log.getOperation();
        return op != null && op.contains(operationFragment);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findPageDashboards() {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboard__findPage", new ApiRequest<>());
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        assertEquals(0, response.getStatus(), "findPage should succeed: " + response);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (List<Map<String, Object>>) data.get("items");
    }

    private ApiResponse<?> executePublishDashboard(String dashboardId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", dashboardId);
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavDashboard__publishDashboard", request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeGetPublishedDashboard(String dashboardId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", dashboardId);
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboard__getPublishedDashboard", request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeRollbackDashboard(String dashboardId, long snapshotVersion) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", dashboardId);
        data.put("snapshotVersion", snapshotVersion);
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavDashboard__rollbackDashboard", request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private NopDatavDashboard saveDashboard(String id, String name) {
        return saveDashboard(id, name, "admin-user", 0);
    }

    private NopDatavDashboard saveDashboard(String id, String name, String owner, int publishStatus) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(publishStatus);
        d.setVersion(0L);
        d.setCreatedBy(owner);
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy(owner);
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }

    private io.nop.datav.dao.entity.NopDatavDashboardShare seedShare(String shareId, String dashboardId) {
        long now = System.currentTimeMillis();
        io.nop.datav.dao.entity.NopDatavDashboardShare share =
                new io.nop.datav.dao.entity.NopDatavDashboardShare();
        share.setShareId(shareId);
        share.setShareToken("token-" + shareId);
        share.setDashboardId(dashboardId);
        share.setEnabled((byte) 1);
        share.setVersion(0L);
        share.setCreatedBy("admin-user");
        share.setCreateTime(new Timestamp(now));
        share.setUpdatedBy("admin-user");
        share.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavDashboardShare.class).saveEntityDirectly(share);
        return share;
    }
}
