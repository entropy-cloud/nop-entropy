package io.nop.integration.feishu.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.nop.integration.feishu.NopFeishuException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G12-13-03 unit tests for the two new {@link IFeishuHttpApi} OAuth methods on
 * {@link JdkFeishuHttpApi}, driven against a local stub HTTP server (JDK
 * built-in {@code com.sun.net.httpserver} — no external dependency, mirrors
 * the TestFileTransfer stub pattern; no real Feishu connectivity needed):
 *
 * <ul>
 *   <li>{@code exchangeUserAccessToken}: POST {@code /open-apis/authen/v1/accessToken}
 *       with app_id/app_secret/code/grant_type; extracts {@code data.access_token};</li>
 *   <li>{@code fetchOpenId}: GET {@code /open-apis/authen/v1/user_info} with
 *       {@code Authorization: Bearer <user_access_token>}; extracts {@code data.open_id};</li>
 *   <li>fail-loud: missing expected field or non-2xx throws {@link NopFeishuException}.</li>
 * </ul>
 */
class TestJdkFeishuHttpApiOAuth {

    static final class RecordedRequest {
        String method;
        String path;
        String body;
        String authorization;
    }

    HttpServer server;
    JdkFeishuHttpApi api;
    final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    volatile String nextResponse = "{}";
    volatile int nextStatus = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            RecordedRequest rec = new RecordedRequest();
            rec.method = exchange.getRequestMethod();
            rec.path = exchange.getRequestURI().getPath();
            rec.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            rec.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requests.add(rec);
            byte[] resp = nextResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(nextStatus, resp.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(resp);
            }
        });
        server.start();

        api = new JdkFeishuHttpApi();
        api.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void exchangeUserAccessTokenPostsCodeAndExtractsToken() {
        nextResponse = "{\"code\":0,\"msg\":\"ok\",\"data\":{\"access_token\":\"u-at-123\","
                + "\"refresh_token\":\"u-rt\",\"token_type\":\"Bearer\",\"expires_in\":6900}}";

        String token = api.exchangeUserAccessToken("cli_app", "app_secret_v", "auth-code-9");

        assertEquals("u-at-123", token, "the user_access_token must be extracted from the response");
        assertEquals(1, requests.size());
        RecordedRequest req = requests.get(0);
        assertEquals("POST", req.method);
        assertEquals("/open-apis/authen/v1/accessToken", req.path,
                "must call the official Feishu v1 authen token endpoint");
        assertTrue(req.body.contains("\"app_id\":\"cli_app\""), "body must carry app_id: " + req.body);
        assertTrue(req.body.contains("\"app_secret\":\"app_secret_v\""),
                "body must carry app_secret: " + req.body);
        assertTrue(req.body.contains("\"code\":\"auth-code-9\""), "body must carry the code: " + req.body);
        assertTrue(req.body.contains("\"grant_type\":\"authorization_code\""),
                "body must carry grant_type: " + req.body);
    }

    @Test
    void fetchOpenIdGetsUserInfoWithBearerToken() {
        nextResponse = "{\"code\":0,\"msg\":\"ok\",\"data\":{\"open_id\":\"ou_server_1\","
                + "\"union_id\":\"un_1\",\"name\":\"Alice\"}}";

        String openId = api.fetchOpenId("u-at-123");

        assertEquals("ou_server_1", openId, "the open_id must be extracted from the user info");
        assertEquals(1, requests.size());
        RecordedRequest req = requests.get(0);
        assertEquals("GET", req.method);
        assertEquals("/open-apis/authen/v1/user_info", req.path,
                "must call the official Feishu v1 user_info endpoint");
        assertNotNull(req.authorization, "the user_access_token must be sent");
        assertEquals("Bearer u-at-123", req.authorization,
                "the user_access_token must be sent as a Bearer token");
    }

    @Test
    void missingAccessTokenInTokenResponseFailsLoudly() {
        nextResponse = "{\"code\":99991668,\"msg\":\"invalid code\",\"data\":{}}";

        NopFeishuException ex = assertThrows(NopFeishuException.class,
                () -> api.exchangeUserAccessToken("cli_app", "s", "bad-code"));
        assertTrue(ex.getMessage().contains("missing access_token"),
                "a token response without access_token must fail loudly: " + ex.getMessage());
    }

    @Test
    void missingOpenIdInUserInfoResponseFailsLoudly() {
        nextResponse = "{\"code\":99991663,\"msg\":\"token expired\",\"data\":{}}";

        NopFeishuException ex = assertThrows(NopFeishuException.class,
                () -> api.fetchOpenId("expired-token"));
        assertTrue(ex.getMessage().contains("missing open_id"),
                "a user_info response without open_id must fail loudly: " + ex.getMessage());
    }

    @Test
    void non2xxStatusFailsLoudly() {
        nextStatus = 500;
        nextResponse = "{\"error\":\"boom\"}";

        assertThrows(NopFeishuException.class,
                () -> api.exchangeUserAccessToken("cli_app", "s", "c"),
                "a non-2xx token response must fail loudly");
        assertThrows(NopFeishuException.class,
                () -> api.fetchOpenId("t"),
                "a non-2xx user_info response must fail loudly");
    }
}
