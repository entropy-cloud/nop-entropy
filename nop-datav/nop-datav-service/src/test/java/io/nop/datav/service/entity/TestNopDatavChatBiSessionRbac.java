package io.nop.datav.service.entity;

import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiErrors;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * ChatBI 会话管理 RBAC E2E 测试（plan 2026-08-15-0004-1 Phase 3，裁定 S5）。
 *
 * <p>验证在 {@code enableActionAuth=true} 下，{@code nop-datav.action-auth.xml} 新增的
 * 会话管理权限点（admin,user）与 ChatSession/ChatMessage 实体 CRUD admin-only 绑定
 * 在运行时经 {@code SiteCacheData.isPermitted} 真正生效（非 XML 文本断言）。</p>
 *
 * <p>测试矩阵：</p>
 * <ul>
 *   <li>正向：user 调用 4 个会话管理自定义 action 放行（不存在的会话 → 业务层 404，非
 *       ERR_AUTH_NO_PERMISSION，证明角色绑定生效）。</li>
 *   <li>反向：user 调用 NopDatavChatSession/NopDatavChatMessage 继承 CRUD {@code findPage}
 *       被拒（admin-only），闭合水平越权入口（镜像 Share/ExportTask D1 先例）。</li>
 *   <li>对照：admin 调用 {@code NopDatavChatSession__findPage} 放行。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
public class TestNopDatavChatBiSessionRbac extends AbstractNopDatavAuthTest {

    @Inject
    IGraphQLEngine graphQLEngine;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
    }

    // ==================== 正向：user 调用会话管理自定义 action 放行 ====================

    @Test
    public void testUserCanCallCreateChatSession() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation,
                "NopDatavChatBi__createChatSession", Map.of("sessionTitle", "rbac-session"));

        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "user should pass action-auth for createChatSession, got: " + response);
    }

    @Test
    public void testUserCanCallListChatSessions() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeRpc(GraphQLOperationType.query,
                "NopDatavChatBi__listChatSessions", new HashMap<>());

        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "user should pass action-auth for listChatSessions, got: " + response);
    }

    @Test
    public void testUserCanCallGetChatSessionHistory() {
        setUserContext("regular-user", "user");

        // 不存在的会话：action-auth 放行后业务层抛 ERR_DATAV_CHATBI_SESSION_NOT_FOUND
        ApiResponse<?> response = executeRpc(GraphQLOperationType.query,
                "NopDatavChatBi__getChatSessionHistory", Map.of("sessionId", "nonexistent-session"));

        assertNotNull(response);
        assertNotEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user should pass action-auth for getChatSessionHistory (business 404 is fine), got: " + response);
    }

    @Test
    public void testUserCanCallDeleteChatSession() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeRpc(GraphQLOperationType.mutation,
                "NopDatavChatBi__deleteChatSession", Map.of("sessionId", "nonexistent-session"));

        assertNotNull(response);
        assertNotEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user should pass action-auth for deleteChatSession (business 404 is fine), got: " + response);
    }

    // ==================== 反向：user 调用会话实体继承 CRUD 被拒（admin-only） ====================

    @Test
    public void testUserRejectedForChatSessionFindPage() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeFindPage("NopDatavChatSession__findPage");

        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user should be denied ChatSession findPage (admin-only CRUD), got: " + response);
    }

    @Test
    public void testUserRejectedForChatMessageFindPage() {
        setUserContext("regular-user", "user");

        ApiResponse<?> response = executeFindPage("NopDatavChatMessage__findPage");

        assertNotNull(response);
        assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), response.getCode(),
                "user should be denied ChatMessage findPage (admin-only CRUD), got: " + response);
    }

    // ==================== 对照：admin 放行 ====================

    @Test
    public void testAdminCanCallChatSessionFindPage() {
        setUserContext("admin-user", "admin");

        ApiResponse<?> response = executeFindPage("NopDatavChatSession__findPage");

        assertNotNull(response);
        assertEquals(0, response.getStatus(),
                "admin should succeed calling ChatSession findPage, got: " + response);
    }

    // ==================== Helpers ====================

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private ApiResponse<?> executeRpc(GraphQLOperationType operationType, String operationName,
                                      Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(new HashMap<>(data));

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(operationType, operationName, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private ApiResponse<?> executeFindPage(String operationName) {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, operationName, new ApiRequest<>());
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }
}
