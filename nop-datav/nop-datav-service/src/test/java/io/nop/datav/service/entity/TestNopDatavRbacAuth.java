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
import io.nop.datav.dao.entity.NopDatavPanel;
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

    /**
     * Negative: 'user' role does NOT have NopDatavPanel:mutation permission
     * (P0-01 fix, plan 2026-08-15-2146-1: FNPT:NopDatavPanel:mutation collapsed to admin).
     * The standard CRUD mutation is rejected with ERR_AUTH_NO_PERMISSION.
     */
    @Test
    public void testUserWithoutPanelMutationPermissionIsRejected() {
        savePanel("panel-rbac-user-deny", saveDashboard("dash-panel-rbac-deny", "panel-rbac").getDashboardId());

        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.mutation,
                "NopDatavPanel__update", Map.of("data", Map.of(
                        "id", "panel-rbac-user-deny",
                        "panelName", "hijacked-name",
                        "displayName", "hijacked")));
        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user role should be denied NopDatavPanel__update (mutation collapsed to admin), got: " + response);
    }

    /**
     * Negative: 'user' role does NOT have NopDatavPanel:query permission either
     * (D1 adjudication: query collapsed together with mutation — Panel__findPage was the
     * enumeration vector reading other users' panelConfig without any filter).
     */
    @Test
    public void testUserWithoutPanelQueryPermissionIsRejected() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__findPage", new HashMap<>());
        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user role should be denied NopDatavPanel__findPage (query collapsed to admin), got: " + response);
    }

    /**
     * Positive: admin role keeps NopDatavPanel:mutation permission — the standard CRUD
     * mutation still works for admin after the collapse (regression: no over-restriction).
     */
    @Test
    public void testAdminCanUpdatePanel() {
        savePanel("panel-rbac-admin", saveDashboard("dash-panel-rbac-admin", "panel-rbac-admin").getDashboardId());

        setUserContext("admin-user", "admin");

        ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.mutation,
                "NopDatavPanel__update", Map.of("data", Map.of(
                        "id", "panel-rbac-admin",
                        "panelName", "renamed-by-admin",
                        "displayName", "renamed-by-admin")));
        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "admin should succeed NopDatavPanel__update after collapse, got: " + response);
    }

    /**
     * Negative: 'user' role does NOT have query permission for the 5 read-only sub-entities
     * (P1-01 fix, plan 2026-08-15-2146-1 / D2: query collapsed to admin — sub-entity rows have
     * no RLS, other users' draft content (tabConfig/DatasetRef/widgetConfig/snapshotContent)
     * must not be enumerable). All five inherited findPage ops are rejected with ERR_AUTH_NO_PERMISSION.
     */
    @Test
    public void testUserDeniedSubEntityQuery() {
        setUserContext("regular-user", "user");

        for (String operation : new String[]{
                "NopDatavDashboardSnapshot__findPage", "NopDatavDashboardTab__findPage",
                "NopDatavDatasetRef__findPage", "NopDatavScreenWidget__findPage",
                "NopDatavScreenSnapshot__findPage"}) {
            ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.query,
                    operation, new HashMap<>());
            assertNotNull(response);
            assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                    operation + " should be denied for user role (D2 collapse), got: " + response);
        }
    }

    /**
     * Positive: admin role keeps query permission for the 5 sub-entities after the D2 collapse
     * (regression: no over-restriction of the admin console read path).
     */
    @Test
    public void testAdminCanQuerySubEntities() {
        setUserContext("admin-user", "admin");

        for (String operation : new String[]{
                "NopDatavDashboardSnapshot__findPage", "NopDatavDashboardTab__findPage",
                "NopDatavDatasetRef__findPage", "NopDatavScreenWidget__findPage",
                "NopDatavScreenSnapshot__findPage"}) {
            ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.query,
                    operation, new HashMap<>());
            assertNotNull(response);
            assertEquals(0, response.getStatus(),
                    operation + " should succeed for admin after D2 collapse, got: " + response);
        }
    }

    /**
     * Negative: 'user' role does NOT have NopDatavFilterState:mutation permission
     * (P1-02 fix, plan 2026-08-15-2146-1 / D3: mutation collapsed to admin; the user write path
     * is the custom saveFilterState action which isolates by userName internally).
     */
    @Test
    public void testUserDeniedFilterStateMutation() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.mutation,
                "NopDatavFilterState__save", Map.of("data", Map.of(
                        "dashboardId", "dash-fs-rbac",
                        "userName", "regular-user",
                        "stateContent", "{}")));
        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user role should be denied NopDatavFilterState__save (mutation collapsed to admin), got: " + response);
    }

    // ==================== Helpers ====================

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private ApiResponse<?> executeGraphQLOperation(GraphQLOperationType operationType, String operationName,
                                                   Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(operationType, operationName, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
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

    private NopDatavPanel savePanel(String panelId, String dashboardId) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelId);
        p.setDisplayName(panelId);
        p.setPanelType(io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("test");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("test");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }
}
