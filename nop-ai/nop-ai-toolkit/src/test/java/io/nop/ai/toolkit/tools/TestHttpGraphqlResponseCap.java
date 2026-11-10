package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.mock.MockHttpResponse;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.2-C8-007（plan 2557 裁定 G，行为钉）：http + graphql 双路径 ≥帽×2 body →
 * 截断 + truncated 标记（截断-续排空，响应已完整读取，仅呈现面截断）；
 * 配置键 {@code nop.ai.tool.http-max-response-chars}，缺省 100_000。
 */
public class TestHttpGraphqlResponseCap {

    private static final int CAP = 200;

    static class StubClient implements IHttpClient {
        final MockHttpResponse response;
        StubClient(MockHttpResponse response) {
            this.response = response;
        }

        @Override
        public CompletableFuture<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<IHttpResponse> downloadAsync(HttpRequest request,
                io.nop.http.api.client.IHttpOutputFile targetFile, io.nop.http.api.client.DownloadOptions options,
                ICancelToken cancelToken) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
        }

        @Override
        public CompletableFuture<IHttpResponse> uploadAsync(HttpRequest request,
                io.nop.http.api.client.IHttpInputFile inputFile, io.nop.http.api.client.UploadOptions options,
                ICancelToken cancelToken) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("not used"));
        }
    }

    static class Ctx implements IToolExecuteContext {
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public io.nop.ai.toolkit.fs.IToolFileSystem getFileSystem() { return null; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
    }

    private static AiToolCall httpCall(String url) {
        XNode node = XNode.make("http-request");
        node.setAttr("id", "1");
        node.setAttr("url", url);
        return AiToolCall.fromNode(node);
    }

    private static AiToolCall graphqlCall() {
        XNode node = XNode.make("graphql-query");
        node.setAttr("id", "1");
        node.setAttr("endpoint", "https://example.com/graphql");
        node.makeChild("input").setContentValue("{ viewer { id } }");
        return AiToolCall.fromNode(node);
    }

    /** 直连 stub resolver：公网 host 解析成功，绕过真实 DNS（单元测试面）。 */
    private static io.nop.http.api.IDnsResolver publicHostResolver() {
        return new io.nop.http.api.IDnsResolver() {
            @Override
            public InetAddress[] resolve(String host) {
                return new InetAddress[]{ inet("93.184.216.34") };
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host + ".example.com";
            }
        };
    }

    private static InetAddress inet(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (java.net.UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String bigBody(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }

    @Test
    void defaultCapIsReadFileCapFamily() {
        assertEquals(100_000, HttpRequestExecutor.DEFAULT_MAX_RESPONSE_CHARS);
        assertEquals(100_000, GraphqlQueryExecutor.DEFAULT_MAX_RESPONSE_CHARS);
    }

    @Test
    void httpBodyOverCapIsTruncatedWithMarker() {
        HttpRequestExecutor executor = new HttpRequestExecutor();
        executor.setHttpClient(new StubClient(new MockHttpResponse(200, bigBody(CAP * 2))));
        executor.setDnsResolver(publicHostResolver());
        executor.setMaxResponseChars(CAP);

        AiToolCallResult result = executor.executeAsync(httpCall("http://example.com/api"), new Ctx())
                .toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        String body = result.getOutput().getBody();
        assertTrue(body.contains("TRUNCATED"), "truncated marker present");
        assertTrue(body.contains("\"truncated\": true"), "structured truncated flag present");
        // 呈现面有界：帽 + wrapper + marker（远小于 2×帽 body 本体）
        assertTrue(body.length() < CAP * 2, "body is capped, got length " + body.length());
    }

    @Test
    void httpBodyUnderCapIsNotTruncated() {
        String small = bigBody(CAP / 2);
        HttpRequestExecutor executor = new HttpRequestExecutor();
        executor.setHttpClient(new StubClient(new MockHttpResponse(200, small)));
        executor.setDnsResolver(publicHostResolver());
        executor.setMaxResponseChars(CAP);

        AiToolCallResult result = executor.executeAsync(httpCall("http://example.com/api"), new Ctx())
                .toCompletableFuture().join();
        assertTrue(result.getOutput().getBody().contains(small),
                "under-cap body passes through unmodified");
        assertFalse(result.getOutput().getBody().contains("TRUNCATED"));
    }

    @Test
    void graphqlSuccessPathOverCapIsTruncated() {
        GraphqlQueryExecutor executor = new GraphqlQueryExecutor();
        executor.setHttpClient(new StubClient(new MockHttpResponse(200, bigBody(CAP * 2))));
        executor.setDnsResolver(publicHostResolver());
        executor.setMaxResponseChars(CAP);

        AiToolCallResult result = executor.executeAsync(graphqlCall(), new Ctx())
                .toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        String body = result.getOutput().getBody();
        assertTrue(body.contains("TRUNCATED"), "success path truncated marker");
        assertTrue(body.length() < CAP * 2);
    }

    @Test
    void graphqlErrorPathOverCapIsTruncated() {
        GraphqlQueryExecutor executor = new GraphqlQueryExecutor();
        executor.setHttpClient(new StubClient(new MockHttpResponse(500, bigBody(CAP * 2))));
        executor.setDnsResolver(publicHostResolver());
        executor.setMaxResponseChars(CAP);

        AiToolCallResult result = executor.executeAsync(graphqlCall(), new Ctx())
                .toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        String body = result.getError().getBody();
        assertTrue(body.contains("TRUNCATED"), "error path truncated marker");
        assertTrue(body.length() < CAP * 3, "error body capped");
    }
}
