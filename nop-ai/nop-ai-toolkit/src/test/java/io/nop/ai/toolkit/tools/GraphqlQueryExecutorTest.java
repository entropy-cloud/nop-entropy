package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.tools.ssrf.SsrfGuardDnsResolver;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import io.nop.http.api.IDnsResolver;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class GraphqlQueryExecutorTest {

    private GraphqlQueryExecutor executor;
    private MockHttpClient mockHttpClient;
    private MockDnsResolver mockResolver;

    @BeforeEach
    public void setUp() {
        executor = new GraphqlQueryExecutor();
        mockHttpClient = new MockHttpClient();
        executor.setHttpClient(mockHttpClient);
        // Inject the guarded resolver backed by a mock delegate so tests never
        // hit real DNS: the executor must consult SsrfGuardDnsResolver at
        // runtime before the transport is reached.
        mockResolver = new MockDnsResolver();
        executor.setDnsResolver(new SsrfGuardDnsResolver(mockResolver));
    }

    @Test
    public void testToolName() {
        assertEquals("graphql-query", executor.getToolName());
    }

    @Test
    public void testExecuteWithoutHttpClient() {
        executor.setHttpClient(null);
        AiToolCall call = createCall("{ __typename }");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("HTTP client not available"));
    }

    @Test
    public void testExecuteWithEmptyQuery() {
        AiToolCall call = createCall("");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("GraphQL query is required"));
    }

    @Test
    public void testExecuteSuccess() {
        mockHttpClient.setResponse(200, "{\"data\":{\"__typename\":\"Query\"}}");
        AiToolCall call = createCall("{ __typename }", "http://api.example.com/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("__typename"));
    }

    @Test
    public void testExecuteHttpError() {
        mockHttpClient.setResponse(500, "Internal Server Error");
        AiToolCall call = createCall("{ __typename }", "http://api.example.com/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("HTTP 500"));
    }

    @Test
    public void testExecuteWithCustomEndpoint() {
        mockHttpClient.setResponse(200, "{\"data\":{}}");
        XNode node = XNode.make("graphql-query");
        node.setAttr("endpoint", "http://api.example.com:9090/graphql");
        XNode input = node.makeChild("input");
        input.setContentValue("{ test }");
        AiToolCall call = AiToolCall.fromNode(node);
        executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("http://api.example.com:9090/graphql", mockHttpClient.getLastRequest().getUrl());
    }

    // ---- SSRF enforcement: denied targets never reach IHttpClient.fetchAsync ----

    @Test
    public void testSsrfInternalIpBlocked() {
        AiToolCall call = createCall("{ __typename }", "http://127.0.0.1/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("blocked"));
        assertNull(mockHttpClient.getLastRequest(), "internal IP must not reach transport");
    }

    @Test
    public void testSsrfCloudMetadataBlocked() {
        AiToolCall call = createCall("{ __typename }", "http://169.254.169.254/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "metadata endpoint must not reach transport");
    }

    @Test
    public void testSsrfEncodedDecimalIpBlocked() {
        AiToolCall call = createCall("{ __typename }", "http://2130706433/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "encoded 127.0.0.1 must not reach transport");
    }

    @Test
    public void testSsrfLocalhostBlocked() {
        AiToolCall call = createCall("{ __typename }", "http://localhost/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "localhost must not reach transport");
    }

    // ---- Resolution-time SSRF enforcement (SsrfGuardDnsResolver consumed at runtime) ----

    @Test
    public void testHostnameResolvingToInternalIpBlocked() {
        mockResolver.override("evil.internal", "127.0.0.1");
        AiToolCall call = createCall("{ __typename }", "http://evil.internal/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("blocked"),
                "resolution-time guard must report the blocked address, got: " + result.getError().getBody());
        assertNull(mockHttpClient.getLastRequest(),
                "hostname resolving to an internal address must not reach transport");
    }

    @Test
    public void testHostnameResolvingToPublicIpAllowed() {
        mockHttpClient.setResponse(200, "{\"data\":{}}");
        AiToolCall call = createCall("{ __typename }", "http://api.example.com/graphql");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertNotNull(mockHttpClient.getLastRequest(), "public-resolving hostname must reach transport");
    }

    @Test
    public void testSsrfGuardResolverConsumedAtRuntime() {
        mockHttpClient.setResponse(200, "{\"data\":{}}");
        AiToolCall call = createCall("{ __typename }", "http://api.example.com/graphql");
        executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertTrue(mockResolver.getResolveCount() > 0,
                "the guarded DNS resolver must be consulted by the executor before the transport");
    }

    private AiToolCall createCall(String query) {
        return createCall(query, "http://api.example.com/graphql");
    }

    private AiToolCall createCall(String query, String endpoint) {
        XNode node = XNode.make("graphql-query");
        node.setAttr("id", "1");
        node.setAttr("endpoint", endpoint);
        XNode input = node.makeChild("input");
        input.setContentValue(query);
        return AiToolCall.fromNode(node);
    }

    static class MockContext implements IToolExecuteContext {
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return null; }

        @Override
        public IThreadPoolExecutor getExecutor() {
            return SyncThreadPoolExecutor.INSTANCE;
        }
    }

    /**
     * Delegate resolver for {@link SsrfGuardDnsResolver}: resolves every host
     * to a public address by default, with per-host overrides (including null
     * = unresolvable).
     */
    static class MockDnsResolver implements IDnsResolver {
        private final Map<String, InetAddress[]> overrides = new HashMap<>();
        private final AtomicInteger resolveCount = new AtomicInteger();

        void override(String host, String ip) {
            try {
                overrides.put(host, new InetAddress[]{InetAddress.getByName(ip)});
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(e);
            }
        }

        int getResolveCount() {
            return resolveCount.get();
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            resolveCount.incrementAndGet();
            if (overrides.containsKey(host)) {
                return overrides.get(host);
            }
            return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }

    static class MockHttpClient implements IHttpClient {
        private int status = 200;
        private String body = "{}";
        private HttpRequest lastRequest;

        void setResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        HttpRequest getLastRequest() { return lastRequest; }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            this.lastRequest = request;
            return CompletableFuture.completedFuture(new MockHttpResponse(status, body));
        }

        @Override
        public IHttpResponse fetch(HttpRequest request, ICancelToken cancelToken) {
            this.lastRequest = request;
            return new MockHttpResponse(status, body);
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, 
                io.nop.http.api.client.IHttpOutputFile targetFile, 
                io.nop.http.api.client.DownloadOptions options, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new MockHttpResponse(status, body));
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, 
                io.nop.http.api.client.IHttpInputFile inputFile, 
                io.nop.http.api.client.UploadOptions options, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new MockHttpResponse(status, body));
        }
    }

    static class MockHttpResponse implements IHttpResponse {
        private final int status;
        private final String body;

        MockHttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override public int getHttpStatus() { return status; }
        @Override public String getBodyAsString() { return body; }
        @Override public byte[] getBodyAsBytes() { return body.getBytes(); }
        @Override public String getContentType() { return "application/json"; }
        @Override public String getCharset() { return "UTF-8"; }
        @Override public Map<String, String> getHeaders() { return Map.of(); }
        @Override public Object getBody() { return body; }
        @Override public <T> T getBodyAsBean(Class<T> beanClass) { return null; }
    }
}
