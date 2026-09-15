package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.tools.ssrf.SsrfGuardDnsResolver;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
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

@NopTestConfig(testBeansFile = "/nop/ai/beans/test-mock.beans.xml")
public class HttpRequestExecutorTest extends JunitBaseTestCase {

    private HttpRequestExecutor executor;
    private MockHttpClient mockHttpClient;
    private MockDnsResolver mockResolver;

    @BeforeEach
    public void setUp() {
        executor = new HttpRequestExecutor();
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
        assertEquals("http-request", executor.getToolName());
    }

    @Test
    public void testExecuteWithoutHttpClient() {
        executor.setHttpClient(null);
        AiToolCall call = createCall("http://example.com");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("HTTP client not available"));
    }

    @Test
    public void testExecuteWithEmptyUrl() {
        AiToolCall call = createCall("");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("URL is required"));
    }

    @Test
    public void testExecuteGetSuccess() {
        mockHttpClient.setResponse(200, "{\"data\":\"test\"}");
        AiToolCall call = createCall("http://example.com/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("\"status\": 200"));
        assertTrue(result.getOutput().getBody().contains("\"data\":\"test\""));
        assertEquals("GET", mockHttpClient.getLastRequest().getMethod());
    }

    @Test
    public void testExecutePostWithBody() {
        mockHttpClient.setResponse(201, "{\"created\":true}");
        XNode node = XNode.make("http-request");
        node.setAttr("id", "1");
        node.setAttr("url", "http://example.com/api");
        node.setAttr("method", "POST");
        XNode body = node.makeChild("body");
        body.setContentValue("{\"name\":\"test\"}");
        
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertEquals("POST", mockHttpClient.getLastRequest().getMethod());
        assertEquals("{\"name\":\"test\"}", mockHttpClient.getLastRequest().getBody());
    }

    @Test
    public void testExecuteWithHeaders() {
        mockHttpClient.setResponse(200, "OK");
        XNode node = XNode.make("http-request");
        node.setAttr("id", "1");
        node.setAttr("url", "http://example.com/api");
        XNode headers = node.makeChild("headers");
        XNode h1 = XNode.make("header");
        h1.setAttr("name", "Content-Type");
        h1.setAttr("value", "application/json");
        headers.appendChild(h1);
        XNode h2 = XNode.make("header");
        h2.setAttr("name", "X-Custom");
        h2.setAttr("value", "custom-value");
        headers.appendChild(h2);
        
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        Map<String, Object> reqHeaders = mockHttpClient.getLastRequest().getHeaders();
        assertNotNull(reqHeaders);
        assertEquals("application/json", reqHeaders.get("Content-Type"));
        assertEquals("custom-value", reqHeaders.get("X-Custom"));
    }

    @Test
    public void testExecuteWithParams() {
        mockHttpClient.setResponse(200, "OK");
        XNode node = XNode.make("http-request");
        node.setAttr("id", "1");
        node.setAttr("url", "http://example.com/api");
        XNode params = node.makeChild("params");
        XNode p1 = XNode.make("param");
        p1.setAttr("name", "key");
        p1.setAttr("value", "value1");
        params.appendChild(p1);
        
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        Map<String, Object> reqParams = mockHttpClient.getLastRequest().getParams();
        assertNotNull(reqParams);
        assertEquals("value1", reqParams.get("key"));
    }

    @Test
    public void testExecuteHttpError() {
        mockHttpClient.setResponse(404, "Not Found");
        AiToolCall call = createCall("http://example.com/notfound");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("\"status\": 404"));
    }

    @Test
    public void testExecuteException() {
        mockHttpClient.setException(new RuntimeException("Connection refused"));
        AiToolCall call = createCall("http://example.com/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Connection refused"));
    }

    // ---- SSRF enforcement: denied targets never reach IHttpClient.fetch ----

    @Test
    public void testSsrfInternalIpBlocked() {
        AiToolCall call = createCall("http://127.0.0.1/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("blocked"));
        assertNull(mockHttpClient.getLastRequest(), "no request should reach the transport for internal IP");
    }

    @Test
    public void testSsrfPrivateIpBlocked() {
        for (String host : new String[]{"10.0.0.1", "192.168.1.1", "172.16.0.1"}) {
            mockHttpClient.clearLastRequest();
            AiToolCall call = createCall("http://" + host + "/api");
            AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
            assertEquals("failure", result.getStatus(), "expected block for " + host);
            assertNull(mockHttpClient.getLastRequest(), "no request for " + host);
        }
    }

    @Test
    public void testSsrfCloudMetadataBlocked() {
        AiToolCall call = createCall("http://169.254.169.254/latest/meta-data/");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "cloud-metadata target must not reach transport");
    }

    @Test
    public void testSsrfEncodedDecimalIpBlocked() {
        AiToolCall call = createCall("http://2130706433/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("blocked"));
        assertNull(mockHttpClient.getLastRequest(), "encoded 127.0.0.1 must not reach transport");
    }

    @Test
    public void testSsrfEncodedHexIpBlocked() {
        AiToolCall call = createCall("http://0x7f000001/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "hex-encoded 127.0.0.1 must not reach transport");
    }

    @Test
    public void testSsrfIpv6LoopbackBlocked() {
        AiToolCall call = createCall("http://[::1]/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "IPv6 loopback must not reach transport");
    }

    @Test
    public void testSsrfIpv6MappedIpv4Blocked() {
        AiToolCall call = createCall("http://[::ffff:127.0.0.1]/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "IPv6-mapped 127.0.0.1 must not reach transport");
    }

    @Test
    public void testSsrfLocalhostBlocked() {
        AiToolCall call = createCall("http://localhost/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "localhost must not reach transport");
    }

    @Test
    public void testSsrfPublicHostReachesTransport() {
        mockHttpClient.setResponse(200, "OK");
        AiToolCall call = createCall("http://example.com/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertNotNull(mockHttpClient.getLastRequest(), "public host must reach transport");
    }

    // ---- Resolution-time SSRF enforcement (SsrfGuardDnsResolver consumed at runtime) ----

    @Test
    public void testHostnameResolvingToInternalIpBlocked() {
        mockResolver.override("evil.internal", "127.0.0.1");
        AiToolCall call = createCall("http://evil.internal/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("blocked"),
                "resolution-time guard must report the blocked address, got: " + result.getError().getBody());
        assertNull(mockHttpClient.getLastRequest(),
                "hostname resolving to an internal address must not reach transport");
    }

    @Test
    public void testHostnameResolvingToPrivateIpBlocked() {
        for (String internal : new String[]{"10.0.0.5", "192.168.1.20", "169.254.169.254"}) {
            mockResolver.override("rebinding.test", internal);
            mockHttpClient.clearLastRequest();
            AiToolCall call = createCall("http://rebinding.test/api");
            AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
            assertEquals("failure", result.getStatus(), "expected block for resolution to " + internal);
            assertNull(mockHttpClient.getLastRequest(), "no request for " + internal);
        }
    }

    @Test
    public void testHostnameResolvingToPublicIpAllowed() {
        mockHttpClient.setResponse(200, "OK");
        AiToolCall call = createCall("http://example.com/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertNotNull(mockHttpClient.getLastRequest(), "public-resolving hostname must reach transport");
    }

    @Test
    public void testSsrfGuardResolverConsumedAtRuntime() {
        mockHttpClient.setResponse(200, "OK");
        AiToolCall call = createCall("http://example.com/api");
        executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertTrue(mockResolver.getResolveCount() > 0,
                "the guarded DNS resolver must be consulted by the executor before the transport");
    }

    @Test
    public void testUnresolvableHostFailsClosed() {
        mockResolver.override("nonexistent.example", (InetAddress[]) null);
        AiToolCall call = createCall("http://nonexistent.example/api");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertNull(mockHttpClient.getLastRequest(), "unresolvable host must not reach transport");
    }

    private AiToolCall createCall(String url) {
        XNode node = XNode.make("http-request");
        node.setAttr("id", "1");
        node.setAttr("url", url);
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

        void override(String host, InetAddress[] addresses) {
            overrides.put(host, addresses);
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
        private RuntimeException exception;

        void setResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        void setException(RuntimeException e) {
            this.exception = e;
        }

        HttpRequest getLastRequest() { return lastRequest; }

        void clearLastRequest() { this.lastRequest = null; }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            this.lastRequest = request;
            if (exception != null) {
                return CompletableFuture.failedFuture(exception);
            }
            return CompletableFuture.completedFuture(new MockHttpResponse(status, body));
        }

        @Override
        public IHttpResponse fetch(HttpRequest request, ICancelToken cancelToken) {
            this.lastRequest = request;
            if (exception != null) throw exception;
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
        @Override public <T> T getBodyAsBean(Class<T> beanClass) { return null; }
        @Override public Object getBody() { return body; }
        @Override public Map<String, String> getHeaders() { return Map.of("Content-Type", "application/json"); }
    }
}
