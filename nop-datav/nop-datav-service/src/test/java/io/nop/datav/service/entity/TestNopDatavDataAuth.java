package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiErrors;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.CollectionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavFilterState;
import io.nop.datav.dao.entity.NopDatavPanel;
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
 * Row-level data permission (RLS) integration test.
 * <p>
 * Verifies the full chain: {@code nop-datav.data-auth.xml} rules →
 * {@code DefaultDataAuthChecker.getFilter} → {@code CrudBizModel.prepareFindPageQuery} →
 * {@code AuthHelper.appendFilter} → findPage result set shrunk by owner filter.
 * <p>
 * Wiring sanity: the first {@code enableDataAuth=TRUE} test asserts the checker is injected,
 * avoiding the hollow trap where a null checker means no filtering (admin sees all by default).
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE,
        enableDataAuth = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
public class TestNopDatavDataAuth extends AbstractNopDatavAuthTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDataAuthChecker dataAuthChecker;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
        IContext ctx = ContextProvider.currentContext();
        if (ctx != null) {
            ctx.setUserName(null);
        }
    }

    /**
     * Wiring sanity: dataAuthChecker is injected and the GraphQL context carries it.
     * This is the first enableDataAuth=TRUE test in the repo — it proves the checker
     * is truly wired, avoiding the "checker is null → no filter → admin sees all" trap.
     */
    @Test
    public void testWiringSanity_dataAuthCheckerInjected() {
        assertNotNull(dataAuthChecker, "IDataAuthChecker bean must be injected when enableDataAuth=true");

        setUserContext("wiring-admin", "admin");
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboard__findPage", new ApiRequest<>());
        assertNotNull(context.getDataAuthChecker(),
                "GraphQLExecutionContext must carry the data auth checker when enableDataAuth=true");
    }

    /**
     * Owner filter: user A creates a draft dashboard (publishStatus=0). User B cannot see it
     * via findPage because createdBy != user B and publishStatus != PUBLISHED.
     */
    @Test
    public void testOwnerFilter_nonOwnerCannotSeePrivateDashboard() {
        saveDashboard("dash-owner-a", "draft-a", "userA", 0);
        saveDashboard("dash-owner-b", "draft-b", "userB", 0);

        setUserContext("userB", "user");

        List<Map<String, Object>> items = findPageDashboards();
        // userB only sees their own dashboard (createdBy=userB), not userA's
        boolean seesOwn = items.stream().anyMatch(m -> "dash-owner-b".equals(m.get("dashboardId")));
        boolean seesOthers = items.stream().anyMatch(m -> "dash-owner-a".equals(m.get("dashboardId")));
        assertTrue(seesOwn, "user should see their own dashboard");
        assertFalse(seesOthers, "user should NOT see another user's private dashboard (RLS owner filter)");
    }

    /**
     * Published dashboard visible to all logged-in users (publishStatus=10).
     */
    @Test
    public void testPublishedDashboard_visibleToAll() {
        saveDashboard("dash-pub", "published", "userA", 10);

        setUserContext("userB", "user");

        List<Map<String, Object>> items = findPageDashboards();
        boolean seesPublished = items.stream().anyMatch(m -> "dash-pub".equals(m.get("dashboardId")));
        assertTrue(seesPublished, "published dashboard should be visible to all users (owner + published rule)");
    }

    /**
     * Admin sees all dashboards (no filter for admin role).
     */
    @Test
    public void testAdminSeesAll() {
        saveDashboard("dash-admin-1", "d1", "userA", 0);
        saveDashboard("dash-admin-2", "d2", "userB", 0);
        saveDashboard("dash-admin-3", "d3", "userC", 10);

        setUserContext("admin-user", "admin");

        List<Map<String, Object>> items = findPageDashboards();
        assertEquals(3, items.size(), "admin should see all dashboards regardless of owner/publishStatus");
    }

    /**
     * Layering note (plan 2026-08-15-2146-1): NopDatavDashboardSnapshot has admin-only rules in the
     * test data-auth.xml AND its query FNPT is admin-only after the D2 collapse. For the snapshot
     * entity the RBAC layer (action-auth) now rejects non-admin users FIRST with ERR_AUTH_NO_PERMISSION
     * (defense in depth — before data-auth even runs). The pure data-auth fail-closed behavior
     * (ERR_AUTH_NO_DATA_AUTH for a user passing action-auth but matching no data-auth rule) is
     * validated by {@link #testFailClosed_directCheckerThrows()} via the direct checker call;
     * after this plan's closure every user-accessible query FNPT has a matching user data-auth
     * rule, so the combined fail-closed path is intentionally unreachable for nop-datav entities.
     */
    @Test
    public void testFailClosed_nonAdminNoMatchingRule() {
        setUserContext("regular-user", "user");

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboardSnapshot__findPage", new ApiRequest<>());
        ApiResponse<?> response = io.nop.api.core.util.FutureHelper.syncGet(
                graphQLEngine.executeRpcAsync(context));

        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "non-admin user should be denied by RBAC for admin-only query FNPT (D2 collapse), got: " + response);
    }

    /**
     * Fail-closed via direct checker call: getFilter throws ERR_AUTH_NO_DATA_AUTH for
     * a bizObj with rules but no matching role-auth for the user.
     */
    @Test
    public void testFailClosed_directCheckerThrows() {
        setUserContext("regular-user", "viewer");

        NopException ex = null;
        try {
            io.nop.api.core.auth.ISecurityContext svcCtx =
                    (io.nop.api.core.auth.ISecurityContext) graphQLEngine.newRpcContext(
                            GraphQLOperationType.query, "NopDatavDashboardSnapshot__findPage", new ApiRequest<>())
                            .getServiceContext();
            dataAuthChecker.getFilter("NopDatavDashboardSnapshot", "findPage", svcCtx);
        } catch (NopException e) {
            ex = e;
        }
        assertNotNull(ex, "getFilter should throw for bizObj with rules but no matching role");
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), ex.getErrorCode(),
                "should be ERR_AUTH_NO_DATA_AUTH, got: " + ex);
    }

    // ==================== Panel 归属校验（P0-02，plan 2026-08-15-2146-1） ====================

    /**
     * P0-02 negative: user B calls all four panel data actions on a panel belonging to
     * user A's UNPUBLISHED dashboard → ERR_AUTH_NO_DATA_AUTH (panel→Dashboard ownership
     * check anchored on NopDatavDashboard RLS, mirroring requireSourceAccess precedent).
     */
    @Test
    public void testPanelActions_nonOwnerUnpublishedDashboardRejected() {
        saveDashboard("dash-panel-owner", "draft", "userA", 0);
        savePanel("panel-own-1", "dash-panel-owner");

        setUserContext("userB", "user");

        Map<String, Object> clickContext = Map.of("field", "region", "value", "north");
        ApiResponse<?> getPanelData = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__getPanelData", Map.of("id", "panel-own-1", "params", new HashMap<String, Object>()));
        assertNotNull(getPanelData);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), getPanelData.getCode(),
                "getPanelData should be rejected for non-owner on unpublished dashboard, got: " + getPanelData);

        ApiResponse<?> linkage = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__resolveLinkage", Map.of("id", "panel-own-1", "clickContext", clickContext));
        assertNotNull(linkage);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), linkage.getCode(),
                "resolveLinkage should be rejected for non-owner on unpublished dashboard, got: " + linkage);

        ApiResponse<?> jump = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__resolveJump", Map.of("id", "panel-own-1", "clickContext", clickContext));
        assertNotNull(jump);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), jump.getCode(),
                "resolveJump should be rejected for non-owner on unpublished dashboard, got: " + jump);

        ApiResponse<?> refresh = executeGraphQLOperation(GraphQLOperationType.mutation,
                "NopDatavPanel__refreshPanel", Map.of("id", "panel-own-1"));
        assertNotNull(refresh);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), refresh.getCode(),
                "NopDatavPanel__refreshPanel should be rejected for non-owner on unpublished dashboard, got: " + refresh);
    }

    /**
     * P0-02 positive: user B CAN access panel actions when the owning dashboard is PUBLISHED
     * (publishStatus=10 satisfies the Dashboard user rule createdBy OR publishStatus=10).
     */
    @Test
    public void testPanelActions_publishedDashboardAccessibleToNonOwner() {
        saveDashboard("dash-panel-pub", "published", "userA", 10);
        savePanel("panel-pub-1", "dash-panel-pub");

        setUserContext("userB", "user");

        ApiResponse<?> getPanelData = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__getPanelData", Map.of("id", "panel-pub-1", "params", new HashMap<String, Object>()));
        assertNotNull(getPanelData);
        assertEquals(0, getPanelData.getStatus(),
                "published dashboard's panel should be accessible to non-owner, got: " + getPanelData);

        ApiResponse<?> refresh = executeGraphQLOperation(GraphQLOperationType.mutation,
                "NopDatavPanel__refreshPanel", Map.of("id", "panel-pub-1"));
        assertNotNull(refresh);
        assertEquals(0, refresh.getStatus(),
                "published dashboard's panel refresh should be accessible to non-owner, got: " + refresh);

        Map<String, Object> clickContext = Map.of("field", "region", "value", "north");
        ApiResponse<?> linkage = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__resolveLinkage", Map.of("id", "panel-pub-1", "clickContext", clickContext));
        assertNotNull(linkage);
        assertEquals(0, linkage.getStatus(),
                "published dashboard's linkage should be accessible to non-owner, got: " + linkage);

        ApiResponse<?> jump = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__resolveJump", Map.of("id", "panel-pub-1", "clickContext", clickContext));
        assertNotNull(jump);
        assertEquals(0, jump.getStatus(),
                "published dashboard's jump should be accessible to non-owner, got: " + jump);
    }

    /**
     * P0-02 positive: the owner CAN access panel actions on their own UNPUBLISHED dashboard
     * (createdBy matches the Dashboard user rule) — no over-restriction for drafts.
     */
    @Test
    public void testPanelActions_ownerCanAccessOwnUnpublishedDashboard() {
        saveDashboard("dash-panel-self", "own-draft", "userA", 0);
        savePanel("panel-self-1", "dash-panel-self");

        setUserContext("userA", "user");

        ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavPanel__getPanelData", Map.of("id", "panel-self-1", "params", new HashMap<String, Object>()));
        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "owner should access own unpublished dashboard's panel, got: " + response);
    }

    // ==================== FilterState userName 隔离（P1-02 / D3，plan 2026-08-15-2146-1） ====================

    /**
     * P1-02: inherited FilterState__findPage is row-isolated by userName via the new data-auth rule
     * (design §69 "only query/update own records" now enforced at the inherited CRUD layer).
     */
    @Test
    public void testFilterState_findPageIsolatedByUserName() {
        saveFilterStateRow("fs-alice", "alice", "dash-fs-1");
        saveFilterStateRow("fs-bob", "bob", "dash-fs-1");

        setUserContext("alice", "user");
        List<Map<String, Object>> items = findPageFilterStates();
        assertTrue(items.stream().anyMatch(m -> "fs-alice".equals(m.get("stateId"))),
                "alice should see her own filter state");
        assertFalse(items.stream().anyMatch(m -> "fs-bob".equals(m.get("stateId"))),
                "alice should NOT see bob's filter state (userName RLS on inherited CRUD)");

        setUserContext("admin-user", "admin");
        List<Map<String, Object>> adminItems = findPageFilterStates();
        assertTrue(adminItems.stream().anyMatch(m -> "fs-alice".equals(m.get("stateId")))
                        && adminItems.stream().anyMatch(m -> "fs-bob".equals(m.get("stateId"))),
                "admin should see all filter states (no filter for admin role)");
    }

    /**
     * P1-02: inherited FilterState__get on ANOTHER user's row is rejected with ERR_AUTH_NO_DATA_AUTH
     * (the userName filter is evaluated against the loaded entity — not silently returning null).
     */
    @Test
    public void testFilterState_getOtherUsersRowRejected() {
        saveFilterStateRow("fs-bob-2", "bob", "dash-fs-2");

        setUserContext("alice", "user");

        ApiResponse<?> response = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavFilterState__get", Map.of("id", "fs-bob-2"));
        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), response.getCode(),
                "alice should be denied reading bob's filter state row, got: " + response);

        // owner still reads own row (regression: no over-restriction)
        saveFilterStateRow("fs-alice-2", "alice", "dash-fs-2");
        ApiResponse<?> own = executeGraphQLOperation(GraphQLOperationType.query,
                "NopDatavFilterState__get", Map.of("id", "fs-alice-2"));
        assertNotNull(own);
        assertEquals(0, own.getStatus(), "owner should read own filter state row, got: " + own);
    }

    // ==================== Helpers ====================

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
        // Also set userName on the IContext (ContextProvider), because the data-auth filter
        // uses $context.userName which resolves from ContextProvider.currentContext().getUserName().
        // In production this is populated by the HTTP auth filter from the JWT token.
        ContextProvider.getOrCreateContext().setUserName(userId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findPageDashboards() {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboard__findPage", new ApiRequest<>());
        ApiResponse<?> response = io.nop.api.core.util.FutureHelper.syncGet(
                graphQLEngine.executeRpcAsync(context));
        assertEquals(0, response.getStatus(), "findPage should succeed, got: " + response);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (List<Map<String, Object>>) data.get("items");
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

    private NopDatavFilterState saveFilterStateRow(String stateId, String userName, String dashboardId) {
        long now = System.currentTimeMillis();
        NopDatavFilterState s = new NopDatavFilterState();
        s.setStateId(stateId);
        s.setUserName(userName);
        s.setDashboardId(dashboardId);
        s.setStateContent("{\"globalFilters\":{}}");
        s.setDelFlag((byte) 0);
        s.setVersion(0L);
        s.setCreatedBy(userName);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(userName);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavFilterState.class).saveEntityDirectly(s);
        return s;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findPageFilterStates() {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavFilterState__findPage", new ApiRequest<>());
        ApiResponse<?> response = io.nop.api.core.util.FutureHelper.syncGet(
                graphQLEngine.executeRpcAsync(context));
        assertEquals(0, response.getStatus(), "FilterState findPage should succeed, got: " + response);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (List<Map<String, Object>>) data.get("items");
    }

    private ApiResponse<?> executeGraphQLOperation(GraphQLOperationType operationType, String operationName,
                                                   Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(operationType, operationName, request);
        return io.nop.api.core.util.FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }
}
