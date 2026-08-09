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
     * Fail-closed: NopDatavDashboardSnapshot has admin-only rules in the test data-auth.xml.
     * A non-admin user has no matching rule → ERR_AUTH_NO_DATA_AUTH (not silent allow).
     */
    @Test
    public void testFailClosed_nonAdminNoMatchingRule() {
        setUserContext("regular-user", "user");

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavDashboardSnapshot__findPage", new ApiRequest<>());
        ApiResponse<?> response = io.nop.api.core.util.FutureHelper.syncGet(
                graphQLEngine.executeRpcAsync(context));

        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), response.getCode(),
                "non-admin user should be denied for admin-only bizObj (fail-closed), got: " + response);
    }

    /**
     * Fail-closed via direct checker call: getFilter throws ERR_AUTH_NO_DATA_AUTH for
     * a bizObj with rules but no matching role-auth for the user.
     */
    @Test
    public void testFailClosed_directCheckerThrows() {
        setUserContext("regular-user", "user");

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
}
