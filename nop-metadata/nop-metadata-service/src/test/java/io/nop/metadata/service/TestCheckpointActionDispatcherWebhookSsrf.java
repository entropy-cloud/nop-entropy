/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.mock.MockHttpClient;
import io.nop.metadata.service.quality.CheckpointActionDispatcher;
import io.nop.http.api.client.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 维度13-04 回归测试：webhook SSRF 防护 + timeout + method 白名单。
 *
 * <p><b>测试方式</b>：mock {@link io.nop.http.api.client.IHttpClient}（{@link MockHttpClient}），
 * 不依赖真实网络（CI 沙箱可能禁出站）。断言：
 * <ul>
 *   <li>URL 协议白名单：拒绝 file:/ftp:/data: 等。</li>
 *   <li>主机白名单：默认 fail-closed 拒绝内网（RFC1918 + 169.254 + localhost）。</li>
 *   <li>method 白名单：仅 POST/PUT 通过；GET/DELETE/TRACE 等被拒。</li>
 *   <li>显式 timeout：{@code HttpRequest.timeout} 被设置（毫秒）。</li>
 * </ul>
 */
public class TestCheckpointActionDispatcherWebhookSsrf {

    private CheckpointActionDispatcher dispatcher;
    private MockHttpClient mockHttpClient;
    private NopMetaQualityCheckpoint cp;

    @BeforeEach
    public void setUp() {
        mockHttpClient = new MockHttpClient();
        dispatcher = new CheckpointActionDispatcher(mockHttpClient, null);
        cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-ssrf");
    }

    @AfterEach
    public void tearDown() {
        mockHttpClient.reset();
    }

    private Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checkpointId", "cp-ssrf");
        summary.put("errors", new ArrayList<>());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> errorsOf(Map<String, Object> summary) {
        return (List<Map<String, Object>>) summary.get("errors");
    }

    private void dispatchWebhook(String url) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", url);
        // dispatch 内部 per-action try/catch 会把异常转成 errors 条目
        String actions = "[{\"actionType\":\"webhook\",\"enabled\":true,\"config\":"
                + configToJson(config) + "}]";
        cp.setActions(actions);
        dispatcher.dispatch(cp, summary());
    }

    private void dispatchWebhookWithMethod(String url, String method) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", url);
        config.put("method", method);
        String actions = "[{\"actionType\":\"webhook\",\"enabled\":true,\"config\":"
                + configToJson(config) + "}]";
        cp.setActions(actions);
        Map<String, Object> s = summary();
        dispatcher.dispatch(cp, s);
    }

    /** 简易 Map→JSON（测试输入不需要完整 JSON 序列化器）。 */
    @SuppressWarnings("unchecked")
    private static String configToJson(Map<String, Object> config) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : config.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String) {
                sb.append("\"").append(v).append("\"");
            } else {
                sb.append(v);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    // ===== URL 协议白名单 =====

    /** 非 http/https 协议必须被拒绝。 */
    @Test
    public void testNonHttpProtocolsBlocked() {
        String[] badUrls = {
                "file:///etc/passwd",
                "ftp://evil.example.com/leak",
                "data:text/html,<script>...</script>",
                "gopher://evil.example.com/x",
                "jar:http://evil.example.com/evil.jar!/",
                "netdoc:///etc/passwd"
        };
        for (String url : badUrls) {
            assertWebhookBlocked(url, "protocol not in whitelist",
                    "non-http protocol must be blocked: " + url);
        }
    }

    // ===== 主机白名单（fail-closed）=====

    /** 内网主机（RFC1918 / link-local / loopback）默认被拒。 */
    @Test
    public void testInternalHostsBlockedByDefault() {
        String[] internalUrls = {
                "http://127.0.0.1:8080/leak",
                "http://localhost/admin",
                "http://10.0.0.1/internal",
                "http://192.168.1.1/router",
                "http://172.16.0.1/corp",
                "http://172.31.255.255/corp",
                "http://169.254.169.254/latest/meta-data/iam/security-credentials/",  // AWS metadata
                "http://[::1]/ipv6-loopback"
        };
        for (String url : internalUrls) {
            assertWebhookBlocked(url, "internal/link-local/loopback host not in allowed-hosts",
                    "internal host must be blocked by default: " + url);
        }
    }

    /** 配置 allowlist 后，对应的内网主机允许（运维显式放行场景）。 */
    @Test
    public void testInternalHostAllowedWhenInAllowlist() {
        dispatcher.configureWebhookSsrf("intranet.example.com,internal.svc", 30);
        // allowlist 内的 host 应通过（不抛异常，到达 IHttpClient.fetch）
        mockHttpClient.responseStatus = 200;
        assertDoesNotThrow(() -> dispatchWebhook("http://intranet.example.com/hook"));
        assertEquals(1, mockHttpClient.fetchCallCount,
                "internal host in allowlist must reach IHttpClient.fetch");
        assertEquals("http://intranet.example.com/hook", mockHttpClient.lastRequest.getUrl(),
                "request URL passed through correctly");
    }

    /** 外网主机（不在 RFC1918/loopback/link-local）默认允许（无需 allowlist）。 */
    @Test
    public void testExternalHostAllowedByDefault() {
        // example.com 是 IANA 保留的文档演示域名，不会真实命中内网
        assertDoesNotThrow(() -> dispatchWebhook("https://example.com/webhook"));
        assertEquals(1, mockHttpClient.fetchCallCount,
                "external host must reach IHttpClient.fetch");
        HttpRequest lastReq = mockHttpClient.lastRequest;
        assertNotNull(lastReq, "request must be passed to client");
        assertEquals("https://example.com/webhook", lastReq.getUrl());
    }

    // ===== Method 白名单 =====

    /** GET 触发副作用/CSRF，必须被拒。 */
    @Test
    public void testGetMethodBlocked() {
        assertMethodBlocked("https://example.com/hook", "GET", "GET not in POST/PUT whitelist");
    }

    /** DELETE/TRACE 等危险 method 必须被拒。 */
    @Test
    public void testDangerousMethodsBlocked() {
        assertMethodBlocked("https://example.com/hook", "DELETE", null);
        assertMethodBlocked("https://example.com/hook", "TRACE", null);
        assertMethodBlocked("https://example.com/hook", "CONNECT", null);
        assertMethodBlocked("https://example.com/hook", "PATCH", null);
    }

    /** POST（默认）+ PUT 允许。 */
    @Test
    public void testPostAndPutAllowed() {
        // 默认 method（未配置）→ POST
        assertDoesNotThrow(() -> dispatchWebhook("https://example.com/hook"));
        assertEquals("POST", mockHttpClient.lastRequest.getMethod(),
                "default method is POST");
        // 显式 PUT
        assertDoesNotThrow(() -> dispatchWebhookWithMethod("https://example.com/hook", "PUT"));
        assertEquals("PUT", mockHttpClient.lastRequest.getMethod(),
                "explicit PUT allowed");
    }

    // ===== 显式 timeout =====

    /** 默认 timeout=30s（30_000 ms），且 HttpRequest.timeout 被设置。 */
    @Test
    public void testDefaultTimeoutSet() {
        assertDoesNotThrow(() -> dispatchWebhook("https://example.com/hook"));
        assertNotNull(mockHttpClient.lastRequest, "request must be recorded");
        assertEquals(30_000L, mockHttpClient.lastRequest.getTimeout(),
                "default webhook timeout is 30_000 ms");
    }

    /** 配置 timeout=5s 后，HttpRequest.timeout=5_000 ms。 */
    @Test
    public void testConfiguredTimeoutApplied() {
        dispatcher.configureWebhookSsrf("", 5);
        assertDoesNotThrow(() -> dispatchWebhook("https://example.com/hook"));
        assertEquals(5_000L, mockHttpClient.lastRequest.getTimeout(),
                "configured timeout (5s) must be applied to HttpRequest");
    }

    /** 配置 timeout=0 或负数 → 回退默认 30s。 */
    @Test
    public void testInvalidTimeoutFallsBackToDefault() {
        dispatcher.configureWebhookSsrf("", 0);
        assertDoesNotThrow(() -> dispatchWebhook("https://example.com/hook"));
        assertEquals(30_000L, mockHttpClient.lastRequest.getTimeout(),
                "timeout=0 → fall back to default 30s");
    }

    /** Content-Type + body 被正确设置（确保 SSRF 防护未破坏正常 webhook 调用）。 */
    @Test
    public void testContentTypeAndBodyPreserved() {
        assertDoesNotThrow(() -> dispatchWebhook("https://example.com/hook"));
        HttpRequest req = mockHttpClient.lastRequest;
        assertEquals("application/json", req.getHeaders().get("Content-Type"),
                "Content-Type header preserved");
        assertNotNull(req.getBody(), "body preserved");
        String bodyStr = String.valueOf(req.getBody());
        assertTrue(bodyStr.contains("cp-ssrf"),
                "body contains summary checkpointId: " + bodyStr);
    }

    // ===== helpers =====

    private void assertWebhookBlocked(String url, String expectedReasonFragment, String msg) {
        Map<String, Object> s = summary();
        String actions = "[{\"actionType\":\"webhook\",\"enabled\":true,\"config\":{\"url\":\""
                + url + "\"}}]";
        cp.setActions(actions);
        // dispatch 内部 try/catch，异常被收集到 errors 不外抛
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, s),
                "dispatch should not throw (per-action isolation), error collected: " + msg);
        List<Map<String, Object>> errors = errorsOf(s);
        assertEquals(1, errors.size(), "exactly one webhook error: " + msg);
        Map<String, Object> err = errors.get(0);
        assertEquals("webhook", err.get("actionType"), "error is webhook: " + msg);
        String errorMsg = String.valueOf(err.get("error"));
        assertTrue(errorMsg.contains("checkpoint-webhook") || errorMsg.contains("ssrf") || errorMsg.contains("blocked"),
                "error must reference SSRF/URL block: " + errorMsg + " (" + msg + ")");
        if (expectedReasonFragment != null) {
            assertTrue(errorMsg.contains(expectedReasonFragment),
                    "error reason must contain '" + expectedReasonFragment + "': " + errorMsg);
        }
        // 关键：内网主机未到达 IHttpClient.fetch（不会发起真实网络请求）
        assertEquals(0, mockHttpClient.fetchCallCount,
                "blocked URL must NOT reach IHttpClient.fetch (no real network call): " + msg);
    }

    private void assertMethodBlocked(String url, String method, String unused) {
        Map<String, Object> s = summary();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", url);
        config.put("method", method);
        String actions = "[{\"actionType\":\"webhook\",\"enabled\":true,\"config\":"
                + configToJson(config) + "}]";
        cp.setActions(actions);
        assertDoesNotThrow(() -> dispatcher.dispatch(cp, s),
                "method-block is recorded as error (per-action isolation)");
        List<Map<String, Object>> errors = errorsOf(s);
        assertEquals(1, errors.size(), "exactly one method error for " + method);
        String errorMsg = String.valueOf(errors.get(0).get("error"));
        assertTrue(errorMsg.contains("checkpoint-webhook-method-blocked"),
                "must reference method-blocked ErrorCode: " + errorMsg);
        assertEquals(0, mockHttpClient.fetchCallCount,
                "blocked method must NOT reach IHttpClient.fetch");
    }
}
