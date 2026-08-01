package io.nop.http.apache;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.http.api.HttpApiErrors.ARG_BODY;
import static io.nop.http.api.HttpApiErrors.ARG_HTTP_STATUS;
import static io.nop.http.api.HttpApiErrors.ARG_RESPONSE_HEADERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan W2e-0 (Phase 1): verify the Apache {@link ServerEventPublisher} attaches
 * the response headers (incl. {@code Retry-After}) to the non-2xx exception,
 * so the LLM error-normalization layer can read Retry-After downstream. Uses an
 * in-process {@link HttpServer} (no external network).
 */
public class TestServerEventPublisherErrorResponse {

    private HttpServer server;
    private ApacheHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new ApacheHttpClient(new HttpClientConfig());
        client.start();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessResponseExposesHeadersOnException() throws Exception {
        server.createContext("/err", new NonSuccessHandler(429, "5",
                "{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"rate limited\"}}"));
        server.start();

        HttpRequest request = new HttpRequest();
        request.setMethod("POST");
        request.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/err");
        request.setHeader("accept", "text/event-stream");

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        client.fetchServerEventFlow(request, null).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(IServerEventResponse item) {
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "onError/onComplete must be reached");
        Throwable error = errorRef.get();
        assertNotNull(error, "non-2xx must surface as onError");
        assertTrue(error instanceof NopException, "error must be a NopException: " + error);

        NopException ex = (NopException) error;
        assertEquals(429, ex.getParam(ARG_HTTP_STATUS));
        Object body = ex.getParam(ARG_BODY);
        assertNotNull(body, "response body must be attached");
        assertTrue(body.toString().contains("rate_limit_exceeded"), "body content must be preserved");

        Object headersObj = ex.getParam(ARG_RESPONSE_HEADERS);
        assertNotNull(headersObj, "response headers Map must be attached to the exception");
        assertTrue(headersObj instanceof Map, "headers must be a Map");
        String retryAfter = findHeaderIgnoreCase((Map<String, String>) headersObj, "retry-after");
        assertNotNull(retryAfter, "Retry-After header must be present");
        assertEquals("5", retryAfter);
    }

    private static String findHeaderIgnoreCase(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static class NonSuccessHandler implements HttpHandler {
        private final int status;
        private final String retryAfter;
        private final String body;

        NonSuccessHandler(int status, String retryAfter, String body) {
            this.status = status;
            this.retryAfter = retryAfter;
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] payload = body.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After", retryAfter);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }
}
