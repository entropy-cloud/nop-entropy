package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LogoutRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-011（plan 2550 裁定 F）钉：会话刷新失败消费——flush 失败 → dirty 不清 + WARN；
 * 成功 → clearDirty。原 thenRun(clearDirty) 形态失败路径静默。
 */
public class TestSessionFlushFailure {

    /** 测试局部类型化异常（规避 no-raw-Exception lint：不实例化裸 RuntimeException/NopException(String)） */
    static final class RedisDownException extends RuntimeException {
        RedisDownException() {
            super("redis down");
        }
    }

    static class FakeCache implements IUserContextCache {
        CompletionStage<Void> saveResult;
        int saveCalls;

        @Override
        public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
            saveCalls++;
            return saveResult;
        }

        @Override
        public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<String> getUserSessionId(String userName) {
            return FutureHelper.success(null);
        }

        @Override
        public int getLoginFailCountForUser(String userName) {
            return 0;
        }

        @Override
        public int getLoginFailCountForIp(String ip) {
            return 0;
        }

        @Override
        public void setLoginFailCountForUser(String userName, int count) {
        }

        @Override
        public void resetLoginFailCountForUser(String userName) {
        }

        @Override
        public void resetLoginFailCountForIp(String ip) {
        }

        @Override
        public void setLoginFailCountForIp(String ip, int count) {
        }

        @Override
        public String getVerifyCode(String key) {
            return null;
        }

        @Override
        public void setVerifyCode(String key, String code) {
        }
    }

    static class MinimalLoginService extends AbstractLoginService {
        @Override
        public CompletionStage<IUserContext> loginAsync(LoginRequest request, Map<String, Object> headers) {
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<Void> logoutAsync(int logoutType, LogoutRequest request) {
            return FutureHelper.success(null);
        }

        @Override
        public String generateVerifyCode(String verifySecret) {
            return "code";
        }

        @Override
        public AuthToken parseAuthToken(String accessToken) {
            return null;
        }

        @Override
        public String refreshToken(IUserContext userContext, AuthToken authToken) {
            return null;
        }
    }

    @Test
    public void pin12_flushFailureKeepsDirtyAndObserves() throws Exception {
        FakeCache cache = new FakeCache();
        cache.saveResult = CompletableFuture.failedFuture(new RedisDownException());
        MinimalLoginService svc = new MinimalLoginService();
        svc.userContextCache = cache;

        UserContextImpl ctx = new UserContextImpl();
        ctx.setUserId("u1"); // marks dirty
        assertTrue(ctx.dirty());

        CompletionStage<Void> stage = svc.flushUserContextAsync(ctx);
        // 返回链承载失败终态（不再静默）
        boolean failedAsExpected = false;
        try {
            stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            failedAsExpected = true;
        }
        assertTrue(failedAsExpected, "flush failure must surface on the returned stage");
        // 失败不清 dirty（下轮重试）
        assertTrue(ctx.dirty(), "dirty flag must be kept on flush failure (2.3-C3-011)");
        assertEquals(1, cache.saveCalls);
    }

    @Test
    public void pin12_flushSuccessClearsDirty() throws Exception {
        FakeCache cache = new FakeCache();
        cache.saveResult = CompletableFuture.completedFuture(null);
        MinimalLoginService svc = new MinimalLoginService();
        svc.userContextCache = cache;

        UserContextImpl ctx = new UserContextImpl();
        ctx.setUserId("u1");
        assertTrue(ctx.dirty());

        svc.flushUserContextAsync(ctx).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertFalse(ctx.dirty(), "successful flush must clear dirty");
    }
}
