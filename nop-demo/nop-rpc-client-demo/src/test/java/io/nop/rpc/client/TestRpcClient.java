package io.nop.rpc.client;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.client.oauth.HttpClientAuthConfig;
import io.nop.http.client.oauth.HttpClientAuthConfigs;
import io.nop.http.client.oauth.enhancer.AddAccessTokenHttpClientEnhancer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(testBeansFile = "classpath:test.beans.xml", localDb = true)
public class TestRpcClient extends JunitBaseTestCase {
    @Inject
    IHttpClient httpClient;

    @Test
    public void testClient() {
        assertTrue(httpClient instanceof AddAccessTokenHttpClientEnhancer.EnhancedClient);

        AddAccessTokenHttpClientEnhancer.EnhancedClient client = (AddAccessTokenHttpClientEnhancer.EnhancedClient) httpClient;
        HttpClientAuthConfigs configs = client.getAuthConfigs();
        HttpClientAuthConfig config = configs.getHttpClientConfigForUrl("http://localhost:9090").getValue();
        assertEquals("auth1", config.getOauthProvider());
    }
}
