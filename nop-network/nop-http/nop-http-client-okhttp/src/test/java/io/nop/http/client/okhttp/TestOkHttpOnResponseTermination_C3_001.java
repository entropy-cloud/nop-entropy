package io.nop.http.client.okhttp;

import com.sun.net.httpserver.HttpServer;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3-001 (plan 2522, R21 future-never-completes family #4): an exception thrown inside
 * the onResponse callback body (e.g. body().string() failing on a truncated connection)
 * must terminate the returned future — the defect swallows it (the stage returned by
 * OkHttp's enqueue callback is discarded) and the caller hangs forever.
 * Failure mode in the defect state is a HANG — bounded get() timeout, annotated.
 */
@Timeout(120)
public class TestOkHttpOnResponseTermination_C3_001 {

    private HttpServer server;
    private OkHttpClientImpl client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new OkHttpClientImpl(new okhttp3.OkHttpClient.Builder().build(), new HttpClientConfig());
        // respond with a declared body length but deliver a truncated body, then close
        // abruptly — body().string() throws IOException inside onResponse
        server.createContext("/truncated", exchange -> {
            exchange.getResponseHeaders().set("Content-Length", "1000");
            exchange.sendResponseHeaders(200, 1000);
            OutputStream os = exchange.getResponseBody();
            os.write("short".getBytes(StandardCharsets.UTF_8));
            os.flush();
            exchange.close(); // abrupt close — body incomplete
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        client.stop();
        server.stop(0);
    }

    private HttpRequest truncatedRequest() {
        return HttpRequest.get("http://127.0.0.1:" + server.getAddress().getPort() + "/truncated");
    }

    @Test
    public void fetchAsyncOnResponseThrowTerminatesFuture() throws Exception {
        CompletionStage<IHttpResponse> stage = client.fetchAsync(truncatedRequest(), null);
        CompletableFuture<IHttpResponse> future = stage.toCompletableFuture();

        // 失败模式 = 超时（defect: the future never completes when onResponse throws)
        try {
            Object ignored = future.get(10, TimeUnit.SECONDS);
            // a successful response would mean the truncation was not observed —
            // fail explicitly so the pin cannot silently pass
            throw new AssertionError("expected the truncated body to fail the response read");
        } catch (java.util.concurrent.ExecutionException e) {
            // post-fix path: the exception reached the caller — C3-001 closed
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError(
                    "C3-001: onResponse body read failure must completeExceptionally the returned"
                            + " future (defect: swallowed by the discarded callback stage — caller hangs)",
                    e);
        }
        assertTrue(future.isCompletedExceptionally(), "future must reach a terminal state");
    }
}
