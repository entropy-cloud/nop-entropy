package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiErrors;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.CollectionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.datav.dao.entity.NopDatavDashboard;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Share/Export RBAC E2E test (plan {1} Phase 2).
 *
 * <p>验证在 {@code enableActionAuth=true} 下，{@code nop-datav.action-auth.xml} 新增的
 * {@code NopDatavDashboardShare-main} 与 {@code NopDatavExportTask-main} resource 块的角色绑定
 * 在运行时经 {@code SiteCacheData.isPermitted} 真正生效。</p>
 *
 * <p>测试矩阵：</p>
 * <ul>
 *   <li>正向：admin/user 调用 Share 自定义 action（{@code listShares}）放行。</li>
 *   <li>正向：admin/user 调用 ExportTask 自定义 action（{@code getExportTask}）放行
 *       （非 owner 不重要——只要不被 ERR_AUTH_NO_PERMISSION 拒绝，说明 action-auth 角色绑定生效）。</li>
 *   <li>反向：user 调用 Share/ExportTask 继承 CRUD {@code findPage} 被拒（admin-only），
 *       闭合 Dim13-01 水平越权入口。</li>
 *   <li>回归：{@code getSharedDashboard}（publicAccess=true）在启用 action-auth 后仍可匿名放行
 *       （角色绑定未误伤公共访问路径）。</li>
 * </ul>
 *
 * <p>接线证明：所有断言均经 graphQLEngine.newRpcContext → executeRpcAsync → SiteCacheData.isPermitted
 * 完整链路，非 XML 文本断言。</p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
public class TestNopDatavShareExportRbac extends AbstractNopDatavAuthTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    // ==================== Share 自定义 action：admin/user 放行 ====================

    /**
     * 正向：admin 角色调用 Share 自定义 action {@code listShares} 放行。
     * admin 经 requireDashboardOwnership 的 admin 旁路。
     */
    @Test
    public void testAdminCanCallListShares() {
        NopDatavDashboard dash = saveDashboard("dash-share-admin", "share-admin", "admin-test");
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = executeListShares(dash.getDashboardId());

        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "admin should succeed calling listShares, got: " + response);
    }

    /**
     * 正向：user 角色调用 Share 自定义 action {@code listShares} 放行（自己是 dashboard owner）。
     * 证明 action-auth.xml 的 roles="admin,user" 绑定对 user 生效。
     */
    @Test
    public void testUserOwnerCanCallListShares() {
        // dashboard.createdBy matches the user's userName → owner check passes
        NopDatavDashboard dash = saveDashboard("dash-share-user", "share-user", "regular-user");
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeListShares(dash.getDashboardId());

        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "user (owner) should succeed calling listShares, got: " + response);
    }

    // ==================== ExportTask 自定义 action：admin/user 放行 ====================

    /**
     * 正向：admin 角色调用 ExportTask 自定义 action {@code getExportTask} 放行。
     *
     * <p>使用不存在的 taskId：action-auth 角色绑定通过后，业务层抛 ERR_DATAV_EXPORT_TASK_NOT_FOUND
     * （非 ERR_AUTH_NO_PERMISSION），证明角色绑定生效（未被 deny-by-default 拒绝）。</p>
     */
    @Test
    public void testAdminCanCallGetExportTask() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = executeGetExportTask("nonexistent-task-id");

        assertNotNull(response);
        // 关键断言：NOT ERR_AUTH_NO_PERMISSION（说明角色绑定生效，业务层 404 是另一回事）
        assertNotEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "admin should pass action-auth for getExportTask (not ERR_AUTH_NO_PERMISSION), got: " + response);
    }

    /**
     * 正向：user 角色调用 ExportTask 自定义 action {@code getExportTask} 放行。
     */
    @Test
    public void testUserCanCallGetExportTask() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeGetExportTask("nonexistent-task-id");

        assertNotNull(response);
        assertNotEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user should pass action-auth for getExportTask (not ERR_AUTH_NO_PERMISSION), got: " + response);
    }

    // ==================== 反向：继承 CRUD findPage admin-only ====================

    /**
     * 反向：user 调用 Share 继承 {@code findPage} 被拒（admin-only binding）。
     * 闭合 Dim13-01：user 无法经继承 CRUD 触达 share 行（水平越权入口）。
     */
    @Test
    public void testUserRejectedForShareFindPage() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeFindPage("NopDatavDashboardShare__findPage");

        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user should be denied Share findPage (admin-only CRUD), got: " + response);
    }

    /**
     * 反向：user 调用 ExportTask 继承 {@code findPage} 被拒（admin-only binding）。
     */
    @Test
    public void testUserRejectedForExportTaskFindPage() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeFindPage("NopDatavExportTask__findPage");

        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user should be denied ExportTask findPage (admin-only CRUD), got: " + response);
    }

    /**
     * 反向对照：admin 调用 Share 继承 {@code findPage} 放行（admin-only 但 admin 有权）。
     * 证明 admin-only 绑定不影响 admin。
     */
    @Test
    public void testAdminCanCallShareFindPage() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = executeFindPage("NopDatavDashboardShare__findPage");

        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "admin should succeed calling Share findPage, got: " + response);
    }

    // ==================== 回归：getSharedDashboard publicAccess 匿名放行 ====================

    /**
     * 回归：{@code getSharedDashboard}（publicAccess=true）在启用 action-auth 后仍可匿名放行。
     * 角色绑定未误伤公共访问路径。
     *
     * <p>使用不存在的 token：action-auth 放行后业务层抛 ERR_DATAV_SHARE_TOKEN_NOT_FOUND，
     * 而非 ERR_AUTH_NO_PERMISSION。证明 publicAccess=true 优先于角色判定。</p>
     */
    @Test
    public void testGetSharedDashboardStillAllowAnonymousAccess() {
        // 不设置任何 userContext（匿名）
        IUserContext.set(null);

        ApiResponse<?> response = executeGetSharedDashboard("nonexistent-token", null);

        assertNotNull(response);
        assertNotEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "anonymous getSharedDashboard should NOT be rejected by action-auth (publicAccess=true), got: "
                        + response);
    }

    // ==================== Helpers ====================

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private ApiResponse<?> executeListShares(String dashboardId) {
        Map<String, Object> data = new HashMap<>();
        data.put("dashboardId", dashboardId);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query,
                "NopDatavDashboardShare__listShares",
                request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeGetExportTask(String taskId) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query,
                "NopDatavExportTask__getExportTask",
                request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeFindPage(String operationName) {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operationName, new ApiRequest<>());
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeGetSharedDashboard(String shareToken, String password) {
        Map<String, Object> data = new HashMap<>();
        data.put("shareToken", shareToken);
        if (password != null) {
            data.put("password", password);
        }

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query,
                "NopDatavDashboardShare__getSharedDashboard",
                request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private NopDatavDashboard saveDashboard(String id, String name, String owner) {
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
