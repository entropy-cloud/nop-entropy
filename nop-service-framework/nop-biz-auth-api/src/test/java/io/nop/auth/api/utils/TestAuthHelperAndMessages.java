package io.nop.auth.api.utils;

import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.AuthApiErrors;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * nop-biz-auth-api 结构性用例：{@link AuthHelper} 数据权限工具语义（过滤条件追加、
 * 实体级数据权限拒绝及错误参数），以及消息 Bean 的访问器契约。
 * 纯单元测试，仅依赖 nop-api-core。
 */
public class TestAuthHelperAndMessages {

    // ==================== AuthHelper.appendFilter ====================

    /**
     * checker 为 null（未启用数据权限）时查询条件原样返回，不新增过滤。
     */
    @Test
    public void testAppendFilterSkipsWhenCheckerMissing() {
        QueryBean query = new QueryBean();
        QueryBean result = AuthHelper.appendFilter(null, query, "TestObj", "findPage",
                securityContext(userContext("alice")));

        assertSame(query, result, "missing checker must keep the query untouched");
        assertNull(result.getFilter(), "no filter may be appended without a checker");
    }

    /**
     * 用户上下文缺失（未登录）时不追加行级过滤（公开语义由上游 RBAC 把关）。
     */
    @Test
    public void testAppendFilterSkipsWhenNoUserContext() {
        QueryBean query = new QueryBean();
        RecordingChecker checker = new RecordingChecker(filter("deleted", "eq", 0));

        QueryBean result = AuthHelper.appendFilter(checker, query, "TestObj", "findPage",
                securityContext(null));

        assertSame(query, result);
        assertEquals(0, checker.getFilterCalls, "checker must not be consulted without user context");
    }

    /**
     * checker 返回的行级过滤条件被追加到查询上。
     */
    @Test
    public void testAppendFilterAddsCheckerFilterToQuery() {
        QueryBean query = new QueryBean();
        RecordingChecker checker = new RecordingChecker(filter("createdBy", "eq", "alice"));

        AuthHelper.appendFilter(checker, query, "TestObj", "findPage", securityContext(userContext("alice")));

        assertEquals(1, checker.getFilterCalls, "checker must be consulted once");
        assertTrue(query.getFilter() != null, "checker filter must be appended to the query");
    }

    /**
     * checker 返回 null 过滤（该实体无行级约束）时查询保持不变。
     */
    @Test
    public void testAppendFilterKeepsQueryWhenCheckerReturnsNull() {
        QueryBean query = new QueryBean();
        RecordingChecker checker = new RecordingChecker(null);

        AuthHelper.appendFilter(checker, query, "TestObj", "findPage", securityContext(userContext("alice")));

        assertNull(query.getFilter(), "null checker filter must not touch the query");
    }

    // ==================== AuthHelper.checkDataAuth ====================

    /**
     * checker 拒绝时抛 ERR_AUTH_NO_DATA_AUTH，并携带 bizObjName/actionName/userName 参数。
     */
    @Test
    public void testCheckDataAuthThrowsWithParamsWhenNotPermitted() {
        RecordingChecker checker = new RecordingChecker(null);
        checker.permitted = false;

        NopException ex = assertThrows(NopException.class,
                () -> AuthHelper.checkDataAuth(checker, "NopAuthUser", "get", entity("u-1"),
                        securityContext(userContext("alice"))));

        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), ex.getErrorCode(),
                "denied entity access must fail with no-data-auth");
        assertEquals("NopAuthUser", ex.getParams().get("bizObjName"));
        assertEquals("get", ex.getParams().get("actionName"));
        assertEquals("alice", ex.getParams().get("userName"));
        assertEquals("u-1", ex.getParams().get("id"));
    }

    /**
     * checker 放行或实体为 null 时不抛异常（null 实体交由上游 404 语义处理）。
     */
    @Test
    public void testCheckDataAuthPassesWhenPermittedOrNullEntity() {
        RecordingChecker checker = new RecordingChecker(null);
        checker.permitted = true;

        AuthHelper.checkDataAuth(checker, "NopAuthUser", "get", entity("u-1"),
                securityContext(userContext("alice")));
        AuthHelper.checkDataAuth(checker, "NopAuthUser", "get", null,
                securityContext(userContext("alice")));
        AuthHelper.checkDataAuth(null, "NopAuthUser", "get", entity("u-1"),
                securityContext(userContext("alice")));
    }

    /**
     * checkDataAuthForList 对集合中每个实体逐一校验，任一实体被拒即抛错。
     */
    @Test
    public void testCheckDataAuthForListChecksEveryEntity() {
        RecordingChecker checker = new RecordingChecker(null);
        checker.permitted = true;

        AuthHelper.checkDataAuthForList(checker, "NopAuthUser", "get",
                List.of(entity("u-1"), entity("u-2")), securityContext(userContext("alice")));
        assertEquals(2, checker.isPermittedCalls, "every entity in the list must be checked");

        checker.permitted = false;
        checker.isPermittedCalls = 0;
        NopException ex = assertThrows(NopException.class,
                () -> AuthHelper.checkDataAuthForList(checker, "NopAuthUser", "get",
                        List.of(entity("u-1"), entity("u-2")), securityContext(userContext("alice"))));
        assertEquals(AuthApiErrors.ERR_AUTH_NO_DATA_AUTH.getErrorCode(), ex.getErrorCode());
    }

    // ==================== 消息 Bean 访问器契约 ====================

    /**
     * LoginResult 默认 tokenType=bearer，且 accessCode/token 字段访问器成对可用
     * （OAuth 风格响应结构契约）。
     */
    @Test
    public void testLoginResultAccessors() {
        io.nop.auth.api.messages.LoginResult result = new io.nop.auth.api.messages.LoginResult();
        assertEquals("bearer", result.getTokenType(), "tokenType must default to bearer");

        result.setAccessToken("at");
        result.setRefreshToken("rt");
        result.setExpiresIn(3600L);
        result.setAccessCode("ac");
        assertEquals("at", result.getAccessToken());
        assertEquals("rt", result.getRefreshToken());
        assertEquals(3600L, result.getExpiresIn());
        assertEquals("ac", result.getAccessCode());
    }

    /**
     * LoginRequest 携带登录类型与主体凭证字段（多通道登录契约）。
     */
    @Test
    public void testLoginRequestAccessors() {
        io.nop.auth.api.messages.LoginRequest request = new io.nop.auth.api.messages.LoginRequest();
        request.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        request.setPrincipalId("alice");
        request.setPrincipalSecret("secret");
        request.setRememberMe(true);

        assertEquals(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD, request.getLoginType());
        assertEquals("alice", request.getPrincipalId());
        assertTrue(request.isRememberMe());
    }

    // ==================== fixtures ====================

    private static TreeBean filter(String name, String op, Object value) {
        return io.nop.api.core.beans.FilterBeans.compareOp(op, name, value);
    }

    private static Object entity(String id) {
        return (io.nop.api.core.util.IWithIdentifier) () -> id;
    }

    private static IUserContext userContext(String userName) {
        return (IUserContext) Proxy.newProxyInstance(
                TestAuthHelperAndMessages.class.getClassLoader(),
                new Class[]{IUserContext.class},
                (proxy, method, args) -> {
                    if ("getUserName".equals(method.getName()))
                        return userName;
                    return defaultValue(method.getReturnType());
                });
    }

    private static ISecurityContext securityContext(IUserContext userContext) {
        return (ISecurityContext) Proxy.newProxyInstance(
                TestAuthHelperAndMessages.class.getClassLoader(),
                new Class[]{ISecurityContext.class},
                (proxy, method, args) -> {
                    if ("getUserContext".equals(method.getName()))
                        return userContext;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }

    static class RecordingChecker implements IDataAuthChecker {
        final TreeBean filterToReturn;
        boolean permitted = true;
        int getFilterCalls;
        int isPermittedCalls;

        RecordingChecker(TreeBean filterToReturn) {
            this.filterToReturn = filterToReturn;
        }

        @Override
        public boolean isPermitted(String bizObj, String action, Object entity, ISecurityContext context) {
            isPermittedCalls++;
            return permitted;
        }

        @Override
        public TreeBean getFilter(String bizObj, String action, ISecurityContext context) {
            getFilterCalls++;
            return filterToReturn;
        }
    }
}
