package io.nop.http.client.oauth.enhancer;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import io.nop.http.client.oauth.HttpClientAuthConfig;
import io.nop.http.client.oauth.HttpClientAuthConfigs;
import io.nop.http.client.oauth.OauthProviderConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAddAccessTokenEnhancerFix {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    static class StubHttpClient implements IHttpClient {
        volatile String lastTokenJson;

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            DefaultHttpResponse response = new DefaultHttpResponse();
            response.setHttpStatus(200);
            response.setBodyAsText(lastTokenJson);
            return CompletableFuture.completedFuture((IHttpResponse) response);
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            return null;
        }
    }

    static String tokenJson(String token, long expiresIn) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("access_token", token);
        map.put("token_type", "Bearer");
        map.put("expires_in", expiresIn);
        return JSON.serialize(map, false);
    }

    static AddAccessTokenHttpClientEnhancer.EnhancedClient newClient(StubHttpClient stub, long expireGap) {
        AddAccessTokenHttpClientEnhancer enhancer = new AddAccessTokenHttpClientEnhancer();
        HttpClientAuthConfigs configs = new HttpClientAuthConfigs();
        OauthProviderConfig provider = new OauthProviderConfig();
        provider.setTokenUri("http://localhost/oauth/token");
        provider.setExpireGap(expireGap);
        provider.setClientId("id");
        provider.setClientSecret("secret");
        Map<String, OauthProviderConfig> providers = new LinkedHashMap<>();
        providers.put("p1", provider);
        configs.setOauthProviders(providers);

        HttpClientAuthConfig clientConfig = new HttpClientAuthConfig();
        clientConfig.setUrlPattern("http://localhost/api/.*");
        clientConfig.setOauthProvider("p1");
        Map<String, HttpClientAuthConfig> clients = new LinkedHashMap<>();
        clients.put("c1", clientConfig);
        configs.setHttpClients(clients);

        enhancer.setAuthConfigs(configs);
        return enhancer.new EnhancedClient(stub);
    }

    @Test
    public void testFirstTokenFetchNoNpe() {
        StubHttpClient stub = new StubHttpClient();
        stub.lastTokenJson = tokenJson("token-1", 3600);
        AddAccessTokenHttpClientEnhancer.EnhancedClient client = newClient(stub, 0);

        HttpRequest request = HttpRequest.get("http://localhost/api/users");
        assertDoesNotThrow(() -> client.onFetchBegin(request, null));

        assertEquals("Bearer token-1", request.getHeader("authorization"));
    }

    @Test
    public void testExpiredTokenRefreshed() {
        StubHttpClient stub = new StubHttpClient();
        AddAccessTokenHttpClientEnhancer.EnhancedClient client = newClient(stub, 0);

        stub.lastTokenJson = tokenJson("old-token", 0);
        HttpRequest first = HttpRequest.get("http://localhost/api/a");
        assertDoesNotThrow(() -> client.onFetchBegin(first, null));
        assertEquals("Bearer old-token", first.getHeader("authorization"));

        // 旧 token 已过期（expiresIn=0），下次请求必须取到新 token 而不是复用旧值
        stub.lastTokenJson = tokenJson("new-token", 3600);
        HttpRequest second = HttpRequest.get("http://localhost/api/b");
        assertDoesNotThrow(() -> client.onFetchBegin(second, null));
        assertEquals("Bearer new-token", second.getHeader("authorization"));
    }
}
