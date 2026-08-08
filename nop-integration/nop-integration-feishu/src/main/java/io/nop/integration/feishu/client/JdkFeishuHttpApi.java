package io.nop.integration.feishu.client;

import io.nop.integration.feishu.NopFeishuException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Production {@link IFeishuHttpApi} backed by the JDK
 * {@link java.net.http.HttpClient} (Java 11+ stdlib — no external dependency,
 * per the W5-0 SDK-selection decision). Calls the Feishu/Lark Open API:
 * stream-gateway endpoint resolution, {@code tenant_access_token} acquisition
 * and {@code im/v1/messages} send. Real API behaviour is verified at W6 E2E.
 *
 * <p>Package-private: internal HTTP implementation, exercised only via the
 * {@link IFeishuHttpApi} seam.
 */
class JdkFeishuHttpApi implements IFeishuHttpApi {

    static final String DEFAULT_BASE_URL = "https://open.feishu.cn";

    private final HttpClient httpClient;
    private String baseUrl = DEFAULT_BASE_URL;

    JdkFeishuHttpApi() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public StreamEndpoint getStreamEndpoint(String appId, String appSecret) {
        String body = "{\"app_id\":\"" + appId + "\",\"app_secret\":\"" + appSecret + "\"}";
        String resp = postJson(baseUrl + "/open-apis/event/v1/connect", body);
        // Feishu returns {"data":{"URL":"wss://...","expire":...,"ticket":"..."}} — note "URL" key
        String uri = FeishuJsons.extractString(resp, "URL");
        String ticket = FeishuJsons.extractString(resp, "ticket");
        if (uri == null || ticket == null) {
            throw new NopFeishuException("JdkFeishuHttpApi.getStreamEndpoint: missing URL/ticket in response: " + resp);
        }
        return new StreamEndpoint(uri, ticket);
    }

    @Override
    public AccessTokenResult getTenantAccessToken(String appId, String appSecret) {
        String body = "{\"app_id\":\"" + appId + "\",\"app_secret\":\"" + appSecret + "\"}";
        String resp = postJson(baseUrl + "/open-apis/auth/v3/tenant_access_token/internal", body);
        String token = FeishuJsons.extractString(resp, "tenant_access_token");
        long expire = FeishuJsons.extractLong(resp, "expire", 7200L);
        if (token == null) {
            throw new NopFeishuException("JdkFeishuHttpApi.getTenantAccessToken: missing token in response: " + resp);
        }
        return new AccessTokenResult(token, expire);
    }

    @Override
    public int sendMessage(String accessToken, String receiveIdType, String receiveId,
                           String msgType, String content) {
        String body = "{\"receive_id_type\":\"" + receiveIdType + "\","
                + "\"receive_id\":\"" + receiveId + "\","
                + "\"msg_type\":\"" + msgType + "\","
                + "\"content\":" + content + "}";
        String resp = postJsonWithAuth(baseUrl + "/open-apis/im/v1/messages?receive_id_type=" + receiveIdType,
                body, accessToken);
        // success indicated by HTTP 2xx (HttpResponse already validated) + code:0 in body
        long code = FeishuJsons.extractLong(resp, "code", -1);
        if (code != 0) {
            throw new NopFeishuException("JdkFeishuHttpApi.sendMessage failed, code=" + code + ", resp=" + resp);
        }
        return 200;
    }

    private String postJson(String url, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new NopFeishuException("Feishu API " + url + " returned status " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body();
        } catch (NopFeishuException e) {
            throw e;
        } catch (Exception e) {
            throw new NopFeishuException("Feishu API call failed: " + url, e);
        }
    }

    private String postJsonWithAuth(String url, String body, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new NopFeishuException("Feishu API " + url + " returned status " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body();
        } catch (NopFeishuException e) {
            throw e;
        } catch (Exception e) {
            throw new NopFeishuException("Feishu API call failed: " + url, e);
        }
    }
}
