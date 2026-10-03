/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.auth.core.filter;

import io.nop.auth.core.login.AuthToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖 wi8#3（plan 2306 项 37）：@InjectValue 配置默认值为 true 的字段
 * （nop.auth.auto-refresh-token / nop.login.return-user-id）在 IoC 容器外的纯 JVM
 * 环境下必须与配置默认语义一致，不得因字段缺省 false 而反转。
 */
public class TestInjectValuePureJvmDefaults {

    @Test
    public void testAutoRefreshTokenDefaultsTrueOutsideContainer() {
        // 未经过 IoC 注入的 filter：autoRefreshToken 字段缺省必须为 true
        AuthHttpServerFilter filter = new AuthHttpServerFilter();
        // 构造已过半衰期的 token：timeToLive < halfLife 时 isNeedRefresh 为 true
        long now = System.currentTimeMillis();
        AuthToken token = new AuthToken("t", "s", "u", "sess", now + 1_000L, 100, null);
        assertTrue(filter.isNeedRefresh(token),
                "纯 JVM 环境下 auto-refresh-token 缺省必须为 true（与配置默认一致）");

        assertFalse(filter.isNeedRefresh(null), "null token 不触发刷新");
    }

    @Test
    public void testReturnUserIdDefaultsTrueOutsideContainer() {
        AbstractLoginServiceForTest service = new AbstractLoginServiceForTest();
        assertTrue(service.isReturnUserId(),
                "纯 JVM 环境下 return-user-id 缺省必须为 true（与配置默认一致）");
    }

    static class AbstractLoginServiceForTest extends io.nop.auth.core.login.AbstractLoginService {
        @Override
        public io.nop.api.core.auth.IUserContext extractFromHeaders(java.util.Map<String, Object> headers) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<io.nop.api.core.auth.IUserContext> loginAsync(
                io.nop.auth.api.messages.LoginRequest request, java.util.Map<String, Object> headers) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> logoutAsync(int logoutType,
                                                                      io.nop.auth.api.messages.LogoutRequest request) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> killLoginAsync(String userName) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> flushUserContextAsync(io.nop.api.core.auth.IUserContext userContext) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<io.nop.api.core.auth.IUserContext> getUserContextAsync(
                io.nop.auth.core.login.AuthToken accessToken, java.util.Map<String, Object> headers) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<io.nop.api.core.auth.IUserContext> getLoginUserContextAsync(String userName) {
            return null;
        }

        @Override
        public io.nop.auth.api.messages.LoginUserInfo getUserInfo(io.nop.api.core.auth.IUserContext userContext) {
            return null;
        }

        @Override
        public String generateVerifyCode(String verifySecret) {
            return null;
        }

        @Override
        public String refreshToken(io.nop.api.core.auth.IUserContext user,
                                   io.nop.auth.core.login.AuthToken token) {
            return null;
        }

        @Override
        public io.nop.auth.core.login.AuthToken parseAuthToken(String token) {
            return null;
        }
    }
}
