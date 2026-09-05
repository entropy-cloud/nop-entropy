package io.nop.http.apache;

import com.sun.net.httpserver.HttpServer;
import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.support.DefaultHttpOutputFile;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApacheHttpClientFix {

    static SimpleHttpRequest build(ApacheHttpClient client, HttpRequest request) throws Exception {
        Method m = ApacheHttpClient.class.getDeclaredMethod("toSimpleRequest", HttpRequest.class);
        m.setAccessible(true);
        return (SimpleHttpRequest) m.invoke(client, request);
    }

    @Test
    public void testNullBodyNotSerializedAsStringNull() throws Exception {
        ApacheHttpClient client = new ApacheHttpClient(new HttpClientConfig());
        HttpRequest request = HttpRequest.get("http://localhost/test");

        SimpleHttpRequest req = build(client, request);
        // 无 body 的 GET 请求不应携带字符串 "null" 作为请求体
        assertNull(req.getBody());
    }

    @Test
    public void testExplicitContentTypeNotOverriddenByJson() throws Exception {
        ApacheHttpClient client = new ApacheHttpClient(new HttpClientConfig());
        HttpRequest request = HttpRequest.post("http://localhost/token")
                .header("content-type", "application/x-www-form-urlencoded")
                .body("grant_type=client_credentials");

        SimpleHttpRequest req = build(client, request);
        // 显式设置的 content-type 不应被默认 application/json 覆盖
        assertNotEquals(ContentType.APPLICATION_JSON, req.getBody().getContentType());
        assertEquals("grant_type=client_credentials", req.getBody().getBodyText());
    }

    @Test
    public void testCollectionHeaderValues() throws Exception {
        ApacheHttpClient client = new ApacheHttpClient(new HttpClientConfig());
        Set<String> values = new LinkedHashSet<>();
        values.add("a");
        values.add("b");
        HttpRequest request = HttpRequest.get("http://localhost/test").header("x-multi", values);

        SimpleHttpRequest req = build(client, request);
        Header[] headers = req.getHeaders("x-multi");
        assertEquals(2, headers.length);
    }

    @Test
    public void testUploadAsyncRejectsNullInputFile() {
        ApacheHttpClient client = new ApacheHttpClient(new HttpClientConfig());
        assertThrows(IllegalArgumentException.class,
                () -> client.uploadAsync(HttpRequest.post("http://localhost/x"), null, null, null));
    }

    @Test
    public void testDownloadErrorResponseNotWrittenToFile() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/not-found", exchange -> {
            byte[] body = "gateway-error-body".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ApacheHttpClient client = new ApacheHttpClient(new HttpClientConfig());
            client.start();
            try {
                File temp = File.createTempFile("nop-download-err", ".txt");
                temp.deleteOnExit();
                DefaultHttpOutputFile outputFile = DefaultHttpOutputFile.create(temp);

                client.downloadAsync(HttpRequest.get("http://127.0.0.1:" + server.getAddress().getPort() + "/not-found"),
                        outputFile, null, null).toCompletableFuture().get(10, TimeUnit.SECONDS);

                // 非 2xx 响应的错误体不应写入目标文件
                assertEquals(0, Files.size(temp.toPath()));
            } finally {
                client.stop();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testFetchAsyncCompletes() throws Exception {
        // fetchAsync 的 execute future 必须能完成（APACHE-01 的端到端表现之一）；
        // 此处用 204 无实体响应同时覆盖 APACHE-03
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/no-content", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            ApacheHttpClient client = new ApacheHttpClient(new HttpClientConfig());
            client.start();
            try {
                var response = client.fetchAsync(
                        HttpRequest.get("http://127.0.0.1:" + server.getAddress().getPort() + "/no-content"),
                        null).toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals(204, response.getHttpStatus());
            } finally {
                client.stop();
            }
        } finally {
            server.stop(0);
        }
    }
}
