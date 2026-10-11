package io.nop.http.client.jdk;

import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.nop.http.api.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1-check-netrpc-24（plan 2391）：toHttpResponse 不再把 503 硬编码映射为
 * NopConnectException——503 按正常响应送达（status/body/headers 完整），与
 * Apache/OkHttp 实现（3.9 audit :846 行为对照）对齐。真实 503（网关宕机等常见态）
 * 此前在 JDK 路径全部被误判为连接失败且丢失 body。
 */
public class TestToHttpResponseStatus {

    /** 最小 HttpResponse stub：只覆盖 toHttpResponse 触达的 statusCode/headers/body。 */
    static HttpResponse<byte[]> responseOf(int status, byte[] body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("content-type", List.of("text/plain")), (k, v) -> true);
            }

            @Override
            public byte[] body() {
                return body;
            }

            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public java.net.http.HttpRequest request() {
                return null; // toHttpResponse 不触达（JDK 26 起 HttpRequest 为抽象类，null 即可）
            }

            @Override
            public URI uri() {
                return URI.create("http://test/service");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }

            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
                return java.util.Optional.empty(); // toHttpResponse 不触达
            }
        };
    }

    @Test
    public void test503DeliveredAsNormalResponse() {
        HttpClientConfig config = new HttpClientConfig();
        JdkHttpClient client = new JdkHttpClient(config);
        byte[] body = "gateway-unavailable".getBytes(StandardCharsets.UTF_8);

        IHttpResponse ret = assertDoesNotThrow(
                () -> client.toHttpResponse(responseOf(SC_SERVICE_UNAVAILABLE, body)),
                "503 must not be misclassified as a connect failure (P1-check-netrpc-24)");

        assertEquals(SC_SERVICE_UNAVAILABLE, ret.getHttpStatus());
        assertEquals("gateway-unavailable", ret.getBodyAsString());
    }

    @Test
    public void test200Unchanged() {
        HttpClientConfig config = new HttpClientConfig();
        JdkHttpClient client = new JdkHttpClient(config);
        IHttpResponse ret = client.toHttpResponse(responseOf(200, "ok".getBytes(StandardCharsets.UTF_8)));
        assertEquals(200, ret.getHttpStatus());
        assertEquals("ok", ret.getBodyAsString());
    }
}
