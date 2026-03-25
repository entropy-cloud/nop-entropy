package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(testBeansFile = "/nop/ai/beans/test-mock.beans.xml")
public class HttpRequestExecutorTest extends JunitBaseTestCase {

    private HttpRequestExecutor executor;
    private MockHttpClient mockHttpClient;

    @BeforeEach
    public void setUp() {
        executor = new HttpRequestExecutor();
        mockHttpClient = new MockHttpClient();
        executor.setHttpClient(mockHttpClient);
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
