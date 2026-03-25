package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
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

public class GraphqlQueryExecutorTest {

    private GraphqlQueryExecutor executor;
    private MockHttpClient mockHttpClient;

    @BeforeEach
    public void setUp() {
        executor = new GraphqlQueryExecutor();
        mockHttpClient = new MockHttpClient();
        executor.setHttpClient(mockHttpClient);
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
        AiToolCall call = createCall("{ __typename }");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("__typename"));
    }

    @Test
    public void testExecuteHttpError() {
        mockHttpClient.setResponse(500, "Internal Server Error");
        AiToolCall call = createCall("{ __typename }");
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("HTTP 500"));
    }

    @Test
    public void testExecuteWithCustomEndpoint() {
        mockHttpClient.setResponse(200, "{\"data\":{}}");
        XNode node = XNode.make("graphql-query");
        node.setAttr("endpoint", "http://custom:9090/graphql");
        XNode input = node.makeChild("input");
        input.setContentValue("{ test }");
        AiToolCall call = AiToolCall.fromNode(node);
        executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("http://custom:9090/graphql", mockHttpClient.getLastRequest().getUrl());
    }

    private AiToolCall createCall(String query) {
        XNode node = XNode.make("graphql-query");
        node.setAttr("id", "1");
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
