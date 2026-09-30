package io.nop.graphql.core.engine;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G5-13-02：CancelTokenManager 归属绑定与reqId撞车语义（plan 2283 裁定(c)）：
 * <ul>
 *   <li>同一用户携同reqId重试在途请求：幂等重绑定，先到请求不被取消且仍可被归属者取消</li>
 *   <li>不同用户复用同一reqId：响亮拒绝</li>
 *   <li>带归属的cancel：未登录调用、取消他人或未登录方注册的请求均显式失败</li>
 *   <li>无userId的cancel保留为系统级通道，不受归属约束</li>
 * </ul>
 */
public class TestCancelTokenManagerOwnership {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 同一用户携同reqId重试：先到请求不被取消（修复前register会oldToken.cancel("replace")），
     * 且重试请求完成时不移除先到请求的登记，归属者随后仍可取消先到请求。
     */
    @Test
    public void testSameUserRetryKeepsFirstRequestCancellable() throws Exception {
        CancelTokenManager manager = new CancelTokenManager();

        ServiceContextImpl first = serviceContext("uA", "req-1");
        manager.buildInvoker(first);

        ServiceContextImpl retry = serviceContext("uA", "req-1");
        IAsyncFunctionInvoker retryInvoker = manager.buildInvoker(retry);
        assertFalse(first.isCancelled(), "same-user retry must not cancel the first in-flight request");

        // 重试请求正常完成：其清理不得移除先到请求的登记
        retryInvoker.invokeAsync(req -> CompletableFuture.completedFuture(req), "ping")
                .toCompletableFuture().get();

        assertTrue(manager.cancel("req-1", "uA"), "owner must still be able to cancel the first request");
        assertTrue(first.isCancelled());
        assertFalse(retry.isCancelled(), "retry context was never registered, must not be cancelled");
    }

    /**
     * register直接返回语义：同用户同reqId重试返回既有token（幂等重绑定）。
     */
    @Test
    public void testRegisterReturnsExistingTokenForSameUser() {
        CancelTokenManager manager = new CancelTokenManager();
        Cancellable first = new Cancellable();
        Cancellable retry = new Cancellable();

        assertSame(first, manager.register("req-2", first, "uA"));
        assertSame(first, manager.register("req-2", retry, "uA"), "same-user retry must rebind to existing token");
        assertFalse(first.isCancelled());

        assertTrue(manager.cancel("req-2"), "system cancel must still reach the registered token");
        assertTrue(first.isCancelled());
    }

    /**
     * 不同用户复用同一reqId：register响亮拒绝，先到请求不受影响、仍可被归属者取消。
     */
    @Test
    public void testDifferentUserSameReqIdRejected() {
        CancelTokenManager manager = new CancelTokenManager();
        ServiceContextImpl first = serviceContext("uA", "req-3");
        manager.buildInvoker(first);

        ServiceContextImpl attacker = serviceContext("uB", "req-3");
        assertThrows(NopException.class, () -> manager.buildInvoker(attacker));

        assertFalse(first.isCancelled(), "rejected registration must not touch the first request");
        assertTrue(manager.cancel("req-3", "uA"));
        assertTrue(first.isCancelled());
    }

    /**
     * 带归属的cancel：不能取消他人注册的请求；未登录调用显式失败。
     */
    @Test
    public void testOwnershipCheckedCancelRejectsForeignAndAnonymous() {
        CancelTokenManager manager = new CancelTokenManager();
        Cancellable token = new Cancellable();
        manager.register("req-4", token, "uA");

        assertThrows(NopException.class, () -> manager.cancel("req-4", "uB"));
        assertFalse(token.isCancelled(), "foreign cancel must not cancel the request");

        assertThrows(NopException.class, () -> manager.cancel("req-4", null));
        assertFalse(token.isCancelled());

        assertTrue(manager.cancel("req-4", "uA"));
        assertTrue(token.isCancelled());
    }

    /**
     * 未登录注册的请求不开放带归属校验的公开取消通道；系统级cancel（无userId）仍可达。
     */
    @Test
    public void testAnonymousRegisteredRequestNotCancellableViaCheckedPath() {
        CancelTokenManager manager = new CancelTokenManager();
        Cancellable token = new Cancellable();
        manager.register("req-5", token);

        assertThrows(NopException.class, () -> manager.cancel("req-5", "uA"));
        assertFalse(token.isCancelled());

        assertTrue(manager.cancel("req-5"), "system-level cancel must remain available");
        assertTrue(token.isCancelled());
    }

    /**
     * 未知reqId：带归属cancel幂等返回false，不构成权限事件。
     */
    @Test
    public void testUnknownReqIdReturnsFalse() {
        CancelTokenManager manager = new CancelTokenManager();
        assertFalse(manager.cancel("no-such-req", "uA"));
        assertFalse(manager.cancel("no-such-req"));
    }

    private static ServiceContextImpl serviceContext(String userId, String reqId) {
        ServiceContextImpl ctx = new ServiceContextImpl();
        ctx.setUserContext(new StubUserContext(userId));
        Map<String, Object> headers = new HashMap<>();
        headers.put("nop-id", reqId);
        ctx.setRequestHeaders(headers);
        return ctx;
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
