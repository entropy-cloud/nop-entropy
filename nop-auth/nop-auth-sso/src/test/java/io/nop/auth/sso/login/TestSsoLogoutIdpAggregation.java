package io.nop.auth.sso.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.util.ICancelToken;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.sso.SsoConfig;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.1-I-06（plan 2569）回归钉：SSO 登出半成功——doLogout 派生的 IdP 登出外呼 future
 * 曾被丢弃（waitAll 只等本地 promise），IdP 侧登出可能未发出/失败不可见。
 *
 * <p>新契约：IdP 外呼纳入 waitAll 聚合（失败传播，FutureHelper.waitAll=allOf）+ ERROR 日志；
 * 客户端重试登出幂等无害（已吊销 token parseAuthToken 返回 null → success）。
 */
class TestSsoLogoutIdpAggregation {

    private static final String SESSION_ID = "logout-e2e-1";

    /**
     * IdP 外呼必失败的 fake client（I-06 红面：外呼失败曾被静默丢弃）。
     */
    static class FailingHttpClient implements IHttpClient {
        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            return CompletableFuture.failedFuture(new RuntimeException("simulated idp logout failure"));
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile outputFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void idpLogoutFailureMustPropagateThroughLogoutFuture() throws Exception {
        SsoConfig config = new SsoConfig();
        config.setAuthServerUrl("https://idp.example.com");
        config.setRealm("master");
        config.setClientId("test-client");
        config.setTokenUrl("/realms/{realm}/protocol/openid-connect/token");
        TestSsoRefreshAsyncChain.StubUserContextCache cache = new TestSsoRefreshAsyncChain.StubUserContextCache();
        UserContextImpl ctx = new UserContextImpl();
        ctx.setSessionId(SESSION_ID);
        ctx.setUserName("alice");
        ctx.setUserId("alice");
        ctx.setAccessToken("at-old");
        ctx.setRefreshToken("rt-old");
        cache.sessions.put(SESSION_ID, ctx);

        TestSsoRefreshAsyncChain.E2EOAuthLoginService service =
                new TestSsoRefreshAsyncChain.E2EOAuthLoginService(new FailingHttpClient(), cache, config);

        CompletableFuture<Void> done = new CompletableFuture<>();
        service.doLogout(0, new SessionInfo("alice", SESSION_ID)).whenComplete((v, e) -> {
            if (e != null)
                done.completeExceptionally(e);
            else
                done.complete(null);
        });

        Throwable failure = assertDoesNotThrow(() -> {
            try {
                done.get(2, TimeUnit.SECONDS);
                return null;
            } catch (Exception e) {
                return e.getCause() != null ? e.getCause() : e;
            }
        });
        assertNotNull(failure, "IdP logout failure must be observable through the aggregated logout future (I-06)");
        assertTrue(failure.getMessage() != null && failure.getMessage().contains("simulated idp logout failure"),
                "failure should carry the IdP error: " + failure);
    }

    @Test
    void idpLogoutSuccessKeepsLogoutNormal() throws Exception {
        // 对照腿：外呼成功（context 缺失分支——promise 返回 null 不外呼）登出正常完成
        SsoConfig config = new SsoConfig();
        TestSsoRefreshAsyncChain.StubUserContextCache empty = new TestSsoRefreshAsyncChain.StubUserContextCache();
        TestSsoRefreshAsyncChain.E2EOAuthLoginService service =
                new TestSsoRefreshAsyncChain.E2EOAuthLoginService(new FailingHttpClient(), empty, config);

        // SSO_CALLBACK 分支不经修改路径；context 缺失时 promise 空值分支不外呼
        service.doLogout(0, new SessionInfo("nobody", "sess-none")).toCompletableFuture().get(2, TimeUnit.SECONDS);
    }
}
