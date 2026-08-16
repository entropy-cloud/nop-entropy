package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 快照/告警状态标准 mutation 面裁剪测试（P1-10，plan 2026-08-15-2146-1，裁定 D5 方案 c）。
 *
 * <p>三个实体（DashboardSnapshot/ScreenSnapshot/AlertState）的标准 CRUD mutation 入口经
 * {@code NopDatavSingleWriterCrudBizModel} 显式拒绝：admin（有 mutation 角色权限）调用被裁剪的
 * mutation action → 收到结构化拒绝错误码（非静默成功、非权限错误——证明拒绝来自 BizModel 层
 * 单写点守卫，且 GraphQL operation 面真实可达该守卫）。领域写入点（publish/rollback/AlertEvaluator）
 * 经 DAO 直写不受影响的回归由既有测试覆盖（TestNopDatavDashboardBizModel/TestNopDatavScreenBizModel/
 * TestNopDatavAlertE2E/TestNopDatavShareE2E 等）。</p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
public class TestNopDatavSingleWriterMutationPruning extends AbstractNopDatavAuthTest {

    private static final String ERR_SNAPSHOT_MUTATION =
            "nop.err.datav.snapshot-std-mutation-not-allowed";
    private static final String ERR_ALERT_STATE_MUTATION =
            "nop.err.datav.alert-state-std-mutation-not-allowed";

    @Inject
    IGraphQLEngine graphQLEngine;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    /**
     * admin 调 NopDatavDashboardSnapshot__save（伪造快照）→ 结构化拒绝
     * （ERR_DATAV_SNAPSHOT_STD_MUTATION_NOT_ALLOWED；写入点仅 publishDashboard/rollbackDashboard）。
     */
    @Test
    public void testAdminDashboardSnapshotSaveRejected() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = execute(GraphQLOperationType.mutation, "NopDatavDashboardSnapshot__save",
                Map.of("data", Map.of("dashboardId", "dash-x", "snapshotVersion", 99, "snapshotContent", "{}")));
        assertNotNull(response);
        assertEquals(ERR_SNAPSHOT_MUTATION, response.getCode(),
                "forged snapshot save must be explicitly rejected, got: " + response);
    }

    /**
     * admin 调 NopDatavDashboardSnapshot__update（篡改发布历史审计链）→ 结构化拒绝。
     */
    @Test
    public void testAdminDashboardSnapshotUpdateRejected() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = execute(GraphQLOperationType.mutation, "NopDatavDashboardSnapshot__update",
                Map.of("data", Map.of("id", "snap-x", "publishedBy", "attacker")));
        assertNotNull(response);
        assertEquals(ERR_SNAPSHOT_MUTATION, response.getCode(),
                "snapshot history tamper must be explicitly rejected, got: " + response);
    }

    /**
     * admin 调 NopDatavScreenSnapshot__delete（删除大屏发布快照）→ 结构化拒绝
     * （快照 append-only；领域删除走大屏删除级联）。
     */
    @Test
    public void testAdminScreenSnapshotDeleteRejected() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = execute(GraphQLOperationType.mutation, "NopDatavScreenSnapshot__delete",
                Map.of("id", "screen-snap-x"));
        assertNotNull(response);
        assertEquals(ERR_SNAPSHOT_MUTATION, response.getCode(),
                "screen snapshot delete must be explicitly rejected, got: " + response);
    }

    /**
     * admin 调 NopDatavAlertState__update（越过状态机改写告警状态）→ 结构化拒绝
     * （唯一写入点 AlertEvaluator）。
     */
    @Test
    public void testAdminAlertStateUpdateRejected() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = execute(GraphQLOperationType.mutation, "NopDatavAlertState__update",
                Map.of("data", Map.of("id", "state-x", "alertStatus", "firing")));
        assertNotNull(response);
        assertEquals(ERR_ALERT_STATE_MUTATION, response.getCode(),
                "alert state update must be explicitly rejected, got: " + response);
    }

    /**
     * admin 调 NopDatavAlertState__batchDelete → 结构化拒绝（13 个 mutation 入口全量裁剪的抽样）。
     */
    @Test
    public void testAdminAlertStateBatchDeleteRejected() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = execute(GraphQLOperationType.mutation, "NopDatavAlertState__batchDelete",
                Map.of("ids", java.util.List.of("state-1", "state-2")));
        assertNotNull(response);
        assertEquals(ERR_ALERT_STATE_MUTATION, response.getCode(),
                "alert state batch delete must be explicitly rejected, got: " + response);
    }

    /**
     * 查询面不受裁剪影响（回归）：admin 的 Snapshot findPage 正常返回（0 状态）。
     */
    @Test
    public void testAdminSnapshotQueryStillWorks() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = execute(GraphQLOperationType.query, "NopDatavDashboardSnapshot__findPage",
                new HashMap<>());
        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "snapshot query surface must remain functional for admin, got: " + response);
    }

    // ==================== Helpers ====================

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private ApiResponse<?> execute(GraphQLOperationType operationType, String operationName,
                                   Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(operationType, operationName, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }
}
