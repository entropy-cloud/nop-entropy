package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.CollectionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.service.screen.ScreenAdaptorMode;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 大屏权限集成测试（D4-1，复用 D3-1 鉴权测试基建）。
 *
 * <p>验证 {@code nop-datav.data-auth.xml} 中 {@code NopDatavScreen} 的 owner 行级 RLS：
 * admin 无 filter；user owner（createdBy）+ 已发布（publishStatus=10）。与 Dashboard 同语义。</p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE,
        enableDataAuth = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
public class TestNopDatavScreenDataAuth extends AbstractNopDatavAuthTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
        IContext ctx = ContextProvider.currentContext();
        if (ctx != null) {
            ctx.setUserName(null);
        }
    }

    /**
     * Owner filter: user A creates a draft screen. User B cannot see it via findPage
     * because createdBy != user B and publishStatus != PUBLISHED.
     */
    @Test
    public void testOwnerFilter_nonOwnerCannotSeePrivateScreen() {
        saveScreen("screen-owner-a", "draft-a", "userA", 0);
        saveScreen("screen-owner-b", "draft-b", "userB", 0);

        setUserContext("userB", "user");

        List<Map<String, Object>> items = findPageScreens();
        boolean seesOwn = items.stream().anyMatch(m -> "screen-owner-b".equals(m.get("screenId")));
        boolean seesOthers = items.stream().anyMatch(m -> "screen-owner-a".equals(m.get("screenId")));
        assertTrue(seesOwn, "user should see their own screen");
        assertFalse(seesOthers, "user should NOT see another user's private screen (RLS owner filter)");
    }

    /**
     * Published screen visible to all logged-in users (publishStatus=10).
     */
    @Test
    public void testPublishedScreen_visibleToAll() {
        saveScreen("screen-pub", "published", "userA", 10);

        setUserContext("userB", "user");

        List<Map<String, Object>> items = findPageScreens();
        boolean seesPublished = items.stream().anyMatch(m -> "screen-pub".equals(m.get("screenId")));
        assertTrue(seesPublished, "published screen should be visible to all users (owner + published rule)");
    }

    /**
     * Admin sees all screens (no filter for admin role).
     */
    @Test
    public void testAdminSeesAll() {
        saveScreen("screen-admin-1", "s1", "userA", 0);
        saveScreen("screen-admin-2", "s2", "userB", 0);
        saveScreen("screen-admin-3", "s3", "userC", 10);

        setUserContext("admin-user", "admin");

        List<Map<String, Object>> items = findPageScreens();
        assertEquals(3, items.size(), "admin should see all screens regardless of owner/publishStatus");
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findPageScreens() {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "NopDatavScreen__findPage", new ApiRequest<>());
        ApiResponse<?> response = io.nop.api.core.util.FutureHelper.syncGet(
                graphQLEngine.executeRpcAsync(context));
        assertEquals(0, response.getStatus(), "findPage should succeed, got: " + response);
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (List<Map<String, Object>>) data.get("items");
    }

    private NopDatavScreen saveScreen(String id, String name, String owner, int publishStatus) {
        long now = System.currentTimeMillis();
        NopDatavScreen s = new NopDatavScreen();
        s.setScreenId(id);
        s.setScreenName(name);
        s.setDisplayName(name);
        s.setScreenWidth(1920);
        s.setScreenHeight(1080);
        s.setAdaptorMode(ScreenAdaptorMode.FULL);
        s.setPublishStatus(publishStatus);
        s.setVersion(0L);
        s.setCreatedBy(owner);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(owner);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavScreen.class).saveEntityDirectly(s);
        return s;
    }
}
