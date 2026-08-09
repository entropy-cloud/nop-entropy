package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC (action-level) auth integration test.
 * <p>
 * Verifies the full chain: {@code @Auth(permissions=...)} on impl methods →
 * {@link io.nop.graphql.core.engine.GraphQLActionAuthChecker} →
 * {@code IActionAuthChecker.isPermissionSetSatisfied} →
 * {@code SiteMapProvider.isPermitted} (permission→role resolution from action-auth.xml).
 * <p>
 * Positive test: admin role has permission → mutation succeeds.
 * Negative test: role without permission → response contains ERR_AUTH_NO_PERMISSION.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
public class TestNopDatavRbacAuth extends AbstractNopDatavAuthTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    /**
     * Positive: admin role has NopDatavDashboard:publishDashboard permission.
     * The mutation executes and returns a successful response.
     */
    @Test
    public void testAdminCanPublishDashboard() {
        NopDatavDashboard dashboard = saveDashboard("dash-rbac-admin", "admin-test");

        setUserContext("admin-user", "admin");

        ApiResponse<?> response = executePublishDashboard(dashboard.getDashboardId());

        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "admin should succeed, got: " + response);
    }

    /**
     * Negative: 'user' role does NOT have NopDatavDashboard:publishDashboard permission
     * (only admin has it in action-auth.xml). The mutation is rejected with ERR_AUTH_NO_PERMISSION.
     */
    @Test
    public void testUserWithoutPublishPermissionIsRejected() {
        NopDatavDashboard dashboard = saveDashboard("dash-rbac-user-deny", "user-deny-test");

        setUserContext("regular-user", "user");

        ApiResponse<?> response = executePublishDashboard(dashboard.getDashboardId());
        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user role should be denied publishDashboard, got: " + response);
    }

    /**
     * Positive: 'user' role HAS NopDatavDashboard:getPublishedDashboard permission
     * (roles="admin,user" in action-auth.xml). The custom query action succeeds.
     */
    @Test
    public void testUserCanGetPublishedDashboard() {
        NopDatavDashboard dashboard = saveDashboard("dash-rbac-query", "query-test");
        // Publish first as admin so a snapshot exists
        setUserContext("admin-user", "admin");
        executePublishDashboard(dashboard.getDashboardId());

        // Then user (who has getPublishedDashboard permission) can view it
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeGetPublishedDashboard(dashboard.getDashboardId());
        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "user should be able to get published dashboard, got: " + response);
    }

    /**
     * Negative deny-by-default: 'rollbackDashboard' permission is bound to roles="admin" only
     * in action-auth.xml. A non-admin authenticated user (role 'user') is rejected.
     * <p>
     * Platform note: {@code UserContextImpl.isUserInRole} always returns true for the 'user' role
     * (any authenticated user is considered to have 'user'). So to test genuine deny-by-default,
     * we use an admin-only permission and a non-admin user. {@code SiteCacheData.isPermitted}
     * returns false when the user's roles don't intersect the permission's allowed roles.
     */
    @Test
    public void testNonAdminDeniedForAdminOnlyAction() {
        NopDatavDashboard dashboard = saveDashboard("dash-rbac-rollback", "rollback-test");
        setUserContext("admin-user", "admin");
        ApiResponse<?> publishResp = executePublishDashboard(dashboard.getDashboardId());
        assertEquals(0, publishResp.getStatus(), "admin publish should succeed: " + publishResp);

        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeRollbackDashboard(dashboard.getDashboardId(), 1L);
        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user role should be denied rollbackDashboard (admin-only), got: " + response);
    }

    // ==================== Helpers ====================

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private ApiResponse<?> executePublishDashboard(String dashboardId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", dashboardId);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation,
                "NopDatavDashboard__publishDashboard",
                request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeGetPublishedDashboard(String dashboardId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", dashboardId);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query,
                "NopDatavDashboard__getPublishedDashboard",
                request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeRollbackDashboard(String dashboardId, long snapshotVersion) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", dashboardId);
        data.put("snapshotVersion", snapshotVersion);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation,
                "NopDatavDashboard__rollbackDashboard",
                request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private NopDatavDashboard saveDashboard(String id, String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }
}
