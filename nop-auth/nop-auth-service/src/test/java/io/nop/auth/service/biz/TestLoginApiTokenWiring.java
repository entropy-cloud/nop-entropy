/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.biz;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.messages.AccessCodeRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.RefreshTokenRequest;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.core.context.IServiceContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 接线验证（Phase 1）：每个消费路径都调用正确的用途感知解析方法，而非通用的 parseAuthToken。
 * 通过记录型 ILoginService 代理验证，无需 IoC 容器或数据库。
 */
public class TestLoginApiTokenWiring {

    private static final String ACCESS = "access-token";
    private static final String REFRESH = "refresh-token";
    private static final String CODE = "access-code";

    @Test
    public void refreshTokenAsyncUsesParseRefreshToken() {
        RecordingLoginService svc = new RecordingLoginService();
        LoginApiBizModel api = newBizModel(svc);

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(REFRESH);

        LoginResult result = api.refreshTokenAsync(req, serviceContext()).toCompletableFuture().join();

        // refresh 消费路径必须走 parseRefreshToken，不能误用 access 解析
        assertEquals(REFRESH, svc.firstParseValue);
        assertEquals("parseRefreshToken", svc.firstParseMethod);
        assertNotNull(result);
    }

    @Test
    public void getLoginResultAsyncUsesParseAccessCode() {
        RecordingLoginService svc = new RecordingLoginService();
        LoginApiBizModel api = newBizModel(svc);

        AccessCodeRequest req = new AccessCodeRequest();
        req.setAccessCode(CODE);

        api.getLoginResultAsync(req, serviceContext()).toCompletableFuture().join();

        // accessCode 消费路径必须走 parseAccessCode
        assertEquals(CODE, svc.firstParseValue);
        assertEquals("parseAccessCode", svc.firstParseMethod);
    }

    private LoginApiBizModel newBizModel(ILoginService svc) {
        LoginApiBizModel api = new LoginApiBizModel();
        // LoginApiBizModel 的 loginService 字段通过 @Inject 注入，这里用反射注入测试桩
        try {
            java.lang.reflect.Field f = LoginApiBizModel.class.getDeclaredField("loginService");
            f.setAccessible(true);
            f.set(api, svc);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return api;
    }

    private IServiceContext serviceContext() {
        return (IServiceContext) Proxy.newProxyInstance(
                IServiceContext.class.getClassLoader(),
                new Class[]{IServiceContext.class},
                (proxy, method, args) -> {
                    if ("getRequestHeaders".equals(method.getName()))
                        return java.util.Collections.emptyMap();
                    return method.getDefaultValue();
                });
    }

    /**
     * 记录型 ILoginService：记录第一个被调用的 parse* 方法名与其入参，
     * 其余方法返回可让 LoginApiBizModel 正常走完流程的默认值。
     */
    private static class RecordingLoginService implements ILoginService {
        String firstParseMethod;
        String firstParseValue;

        private void record(String method, String value) {
            if (firstParseMethod == null) {
                firstParseMethod = method;
                firstParseValue = value;
            }
        }

        @Override
        public AuthToken parseAuthToken(String accessToken) {
            record("parseAuthToken", accessToken);
            return fakeToken();
        }

        @Override
        public AuthToken parseRefreshToken(String refreshToken) {
            record("parseRefreshToken", refreshToken);
            return fakeToken();
        }

        @Override
        public AuthToken parseAccessCode(String accessCode) {
            record("parseAccessCode", accessCode);
            return fakeToken();
        }

        @Override
        public CompletionStage<IUserContext> getUserContextAsync(AuthToken token, Map<String, Object> headers) {
            return CompletableFuture.completedFuture(fakeUser());
        }

        @Override
        public LoginUserInfo getUserInfo(IUserContext userContext) {
            return new LoginUserInfo();
        }

        private AuthToken fakeToken() {
            return new AuthToken(ACCESS, "a", "alice", "sid-1",
                    System.currentTimeMillis() + 60000, 60, java.util.Collections.emptyMap());
        }

        private IUserContext fakeUser() {
            UserContextImpl u = new UserContextImpl();
            u.setUserName("alice");
            u.setSessionId("sid-1");
            u.setAccessToken(ACCESS);
            u.setRefreshToken(REFRESH);
            return u;
        }

        // 以下方法本测试不使用，提供空实现以满足接口契约
        @Override
        public CompletionStage<IUserContext> loginAsync(io.nop.auth.api.messages.LoginRequest request, Map<String, Object> headers) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> logoutAsync(int logoutType, io.nop.auth.api.messages.LogoutRequest request) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> killLoginAsync(String userName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> flushUserContextAsync(IUserContext userContext) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<IUserContext> getLoginUserContextAsync(String userName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String generateVerifyCode(String verifySecret) {
            return "";
        }

        @Override
        public String refreshToken(IUserContext userContext, AuthToken authToken) {
            return ACCESS;
        }
    }
}
