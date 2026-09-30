package io.nop.graphql.core.web;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.graphql.CancelRequestBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.engine.GraphQLEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G5-13-02：Sys__cancel 归属校验。取消操作的用户可见行为变更：
 * <ul>
 *   <li>要求登录：未登录调用显式失败（NopException），不再放行</li>
 *   <li>仅允许取消本人发起的在途请求：跨用户取消显式失败，且目标请求不被取消</li>
 *   <li>拒绝路径显式失败（异常），不返回空成功</li>
 * </ul>
 * 修复前：cancel是无归属校验的公开查询，任何用户可凭reqId取消他人在途请求——
 * 跨用户与未登录两例在修复前失败（先红后绿）。
 */
public class TestSysCancelOwnership {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 用户A注册在途请求req-1后，用户B以同一reqId调用Sys__cancel必须被显式拒绝，
     * 且用户A的请求不被取消。修复前：cancel直接执行engine.cancel(reqId)，返回true并取消A的请求。
     */
    @Test
    public void testCrossUserCancelRejected() {
        GraphQLEngine engine = new GraphQLEngine();
        SysBizModel model = new SysBizModel();
        model.graphQLEngine = engine;

        ServiceContextImpl ctxA = loggedInContext("uA");
        ctxA.setRequestHeaders(headers("req-1"));
        engine.getCancelTokenManager().buildInvoker(ctxA);

        ServiceContextImpl ctxB = loggedInContext("uB");
        assertThrows(NopException.class, () -> model.cancel(cancelBean("req-1"), ctxB));
        assertFalse(ctxA.isCancelled(), "user B must not be able to cancel user A's in-flight request");
    }

    /**
     * 未登录调用方不允许通过公开cancel API取消任何请求（plan 2283 语义裁定(b)）。
     * 修复前：未登录调用直接放行并取消目标请求。
     */
    @Test
    public void testAnonymousCallerRejected() {
        GraphQLEngine engine = new GraphQLEngine();
        SysBizModel model = new SysBizModel();
        model.graphQLEngine = engine;

        ServiceContextImpl ctxA = loggedInContext("uA");
        ctxA.setRequestHeaders(headers("req-2"));
        engine.getCancelTokenManager().buildInvoker(ctxA);

        ServiceContextImpl anon = anonymousContext();
        assertThrows(NopException.class, () -> model.cancel(cancelBean("req-2"), anon));
        assertFalse(ctxA.isCancelled(), "anonymous caller must not be able to cancel any request");
    }

    /**
     * 归属者本人取消自己的请求仍然可用（控制组：不因加固而破坏正常取消语义）。
     */
    @Test
    public void testOwnerCanCancelOwnRequest() {
        GraphQLEngine engine = new GraphQLEngine();
        SysBizModel model = new SysBizModel();
        model.graphQLEngine = engine;

        ServiceContextImpl ctxA = loggedInContext("uA");
        ctxA.setRequestHeaders(headers("req-3"));
        engine.getCancelTokenManager().buildInvoker(ctxA);

        assertTrue(model.cancel(cancelBean("req-3"), ctxA), "owner cancel must succeed");
        assertTrue(ctxA.isCancelled(), "owner's own request must actually be cancelled");
    }

    /**
     * 未知reqId返回false（不存在的请求无可取消对象，不构成权限事件）。
     */
    @Test
    public void testUnknownReqIdReturnsFalse() {
        GraphQLEngine engine = new GraphQLEngine();
        SysBizModel model = new SysBizModel();
        model.graphQLEngine = engine;

        ServiceContextImpl ctxA = loggedInContext("uA");
        assertFalse(model.cancel(cancelBean("no-such-req"), ctxA));
    }

    private static ServiceContextImpl loggedInContext(String userId) {
        ServiceContextImpl ctx = new ServiceContextImpl();
        ctx.setUserContext(new StubUserContext(userId));
        return ctx;
    }

    private static ServiceContextImpl anonymousContext() {
        return new ServiceContextImpl();
    }

    private static Map<String, Object> headers(String reqId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("nop-id", reqId);
        return headers;
    }

    private static CancelRequestBean cancelBean(String reqId) {
        CancelRequestBean bean = new CancelRequestBean();
        bean.setReqId(reqId);
        return bean;
    }

    private static final class StubUserContext implements IUserContext {
        private final String userId;

        StubUserContext(String userId) {
            this.userId = userId;
        }

        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public String getUserName() {
            return userId;
        }

        @Override
        public String getSessionId() {
            return "sess-" + userId;
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public String getLocale() {
            return null;
        }

        @Override
        public String getTimeZone() {
            return null;
        }

        @Override
        public String getDeptId() {
            return null;
        }

        @Override
        public String getDeptName() {
            return null;
        }

        @Override
        public String getOpenId() {
            return null;
        }

        @Override
        public String getNickName() {
            return null;
        }

        @Override
        public String getPrimaryRole() {
            return null;
        }

        @Override
        public boolean isUserInRole(String roleId) {
            return false;
        }

        @Override
        public boolean isUserInAnyRole(Collection<String> roleIds) {
            return false;
        }

        @Override
        public Set<String> getRoles() {
            return Set.of();
        }

        @Override
        public void addRole(String roleId) {
            // test stub: roles not supported
        }

        @Override
        public void removeRole(String roleId) {
            // test stub: roles not supported
        }

        @Override
        public String getAccessToken() {
            return null;
        }

        @Override
        public String getRefreshToken() {
            return null;
        }

        @Override
        public void setAccessToken(String accessToken) {
            // test stub: tokens not supported
        }

        @Override
        public void setRefreshToken(String refreshToken) {
            // test stub: tokens not supported
        }

        @Override
        public long getLastAccessTime() {
            return 0L;
        }

        @Override
        public void setLastAccessTime(long lastAccessTime) {
            // test stub: not tracked
        }

        @Override
        public Map<String, Object> getAttrs() {
            return null;
        }

        @Override
        public Object getAttr(String name) {
            return null;
        }

        @Override
        public void setAttr(String name, Object value) {
            // test stub: attrs not supported
        }

        @Override
        public boolean dirty() {
            return false;
        }

        @Override
        public void clearDirty() {
            // test stub: not tracked
        }

        @Override
        public void markDirty() {
            // test stub: not tracked
        }
    }
}
