/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service.oauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IDirtyFlagSupport;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.config.CredentialConfigs;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialOauthState;
import io.nop.credential.service.CredentialProviderImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W9 Phase 2 授权码闭环端到端测试（stub 授权/令牌服务器 = JDK 内置 HttpServer）。
 *
 * <p>覆盖：发起（URL 组装 + state 持久化 + 校验链拒绝分支）→ 回调（token 加密回写保留字段 +
 * 人工字段保留 + state 一次性/过期/重放拒绝 + token endpoint error 显式抛错）→ 回调响应无
 * token 明文 → 并发双回调同 state 恰一个成功 → 发起 → 授权 URL 302 → 回调 → getCredential
 * 取用明文的全链路 → state 过期行惰性清理。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestOAuthFlowService extends JunitBaseTestCase {

    private static final String TYPE_NAME = "test-oauth2";
    private static final String NON_OAUTH_TYPE = "test-secret";
    private static final String TEST_USER_ID = "oauth-test-user";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    CredentialCipher credentialCipher;

    @Inject
    ICredentialProvider credentialProvider;

    @Inject
    IHttpClient httpClient;

    /** 容器装配的 API 面 BizModel（credential-defaults.beans.xml 显式注册——NopIoC 无注解扫描） */
    @Inject
    CredentialOAuthApiBizModel containerApiBizModel;

    /**
     * stub 授权服务器：/authorize 返回 302（Location=回调端点+code+state）；
     * /token 记录调用次数与表单参数，可注入响应（正常 token / error）。
     */
    private HttpServer stubServer;
    private String stubServerBase;
    private final AtomicInteger tokenCallCount = new AtomicInteger();
    private final List<Map<String, String>> tokenRequests = Collections.synchronizedList(new ArrayList<>());
    private volatile String tokenResponseBody =
            "{\"access_token\":\"at-001\",\"refresh_token\":\"rt-001\",\"expires_in\":3600,"
                    + "\"token_type\":\"Bearer\",\"scope\":\"read write\"}";
    private volatile int tokenResponseStatus = 200;

    private OAuthFlowService flowService;
    private NopCredentialOauthStateStore stateStore;
    private CredentialProviderImpl providerImpl;

    @BeforeEach
    public void setUpStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServerBase = "http://127.0.0.1:" + stubServer.getAddress().getPort();

        stubServer.createContext("/authorize", exchange -> {
            // 模拟第三方授权服务器：302 回跳回调端点（携带 code + 原 state）
            String query = exchange.getRequestURI().getQuery();
            String state = extractParam(query, "state");
            String location = "https://platform.example.test/r/CredentialOAuthApi__oauthCallback"
                    + "?code=auth-code-abc&state=" + state;
            respond(exchange, 302, "", "Location", location);
        });
        stubServer.createContext("/token", exchange -> {
            tokenCallCount.incrementAndGet();
            Map<String, String> form = new LinkedHashMap<>();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            for (String pair : body.split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    form.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
                }
            }
            tokenRequests.add(form);
            respond(exchange, tokenResponseStatus, tokenResponseBody);
        });
        stubServer.start();

        // 测试专用 registry：oauth2 类型端点指向动态端口的 stub 服务器 + 一个非 oauth2 类型
        ICredentialTypeRegistry stubRegistry = newStubRegistry();
        providerImpl = (CredentialProviderImpl) credentialProvider;

        OAuthTokenClient tokenClient = new OAuthTokenClient();
        tokenClient.setHttpClient(httpClient);

        stateStore = new NopCredentialOauthStateStore();
        stateStore.setDaoProvider(daoProvider);
        stateStore.setJdbcTemplate(jdbcTemplate);

        flowService = new OAuthFlowService();
        flowService.setDaoProvider(daoProvider);
        flowService.setCredentialTypeRegistry(stubRegistry);
        flowService.setCredentialProvider(providerImpl);
        flowService.setStateStore(stateStore);
        flowService.setTokenClient(tokenClient);

        // 回调基础地址/结果页：测试内以配置覆盖（回调 action 由测试直接调用模拟浏览器 GET）
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_OAUTH_CALLBACK_BASE_URL, "https://platform.example.test");
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_OAUTH_RESULT_PAGE_URL, "https://platform.example.test/oauth-result");

        IUserContext.set(new TestUserContext(TEST_USER_ID));
    }

    @AfterEach
    public void tearDownStub() {
        IUserContext.set(null);
        if (stubServer != null) {
            stubServer.stop(0);
        }
        // 恢复配置缺省，避免污染其他测试
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_OAUTH_CALLBACK_BASE_URL, null);
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_OAUTH_RESULT_PAGE_URL, null);
    }

    // ==================== 测试基建 ====================

    private static CredentialType newOauth2StubType(String base) {
        CredentialType type = new CredentialType();
        type.setName(TYPE_NAME);
        type.setDisplayName("Test OAuth2");
        type.setAuthType(CredentialType.AUTH_TYPE_OAUTH2);
        CredentialType.CredentialField clientId = new CredentialType.CredentialField(
                "clientId", "Client ID", "string", false, null, true);
        CredentialType.CredentialField clientSecret = new CredentialType.CredentialField(
                "clientSecret", "Client Secret", "password", true, null, true);
        type.setFields(new ArrayList<>(List.of(clientId, clientSecret)));
        CredentialType.OAuth2Metadata metadata = new CredentialType.OAuth2Metadata();
        metadata.setAuthorizationEndpoint(base + "/authorize");
        metadata.setTokenEndpoint(base + "/token");
        metadata.setScopes("read write");
        metadata.setRefreshWindowSeconds(300);
        type.setOauth2(metadata);
        return type;
    }

    private static CredentialType newNonOauthStubType() {
        CredentialType type = new CredentialType();
        type.setName(NON_OAUTH_TYPE);
        type.setDisplayName("Test Secret");
        type.setAuthType(CredentialType.AUTH_TYPE_NONE);
        type.setFields(new ArrayList<>(List.of(new CredentialType.CredentialField(
                "secret", "Secret", "password", true, null, true))));
        return type;
    }

    private ICredentialTypeRegistry newStubRegistry() {
        CredentialType oauth2Type = newOauth2StubType(stubServerBase);
        CredentialType nonOauthType = newNonOauthStubType();
        return new ICredentialTypeRegistry() {
            @Override
            public CredentialType getType(String typeName) {
                if (TYPE_NAME.equals(typeName)) {
                    return oauth2Type;
                }
                if (NON_OAUTH_TYPE.equals(typeName)) {
                    return nonOauthType;
                }
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_UNKNOWN_TYPE)
                        .param(CredentialErrors.ARG_TYPE_NAME, typeName);
            }

            @Override
            public List<CredentialType> listTypes() {
                return List.of(oauth2Type, nonOauthType);
            }
        };
    }

    /** 最小 IUserContext 实现（nop-credential 不依赖 nop-auth，无法用 UserContextImpl）。
     *  W11 回补后发起动作对 system 级凭证要求管理员——默认测试用户带 admin 角色。 */
    private static final class TestUserContext implements IUserContext, IDirtyFlagSupport {
        private final String userId;
        private final Set<String> roles;

        private TestUserContext(String userId) {
            this(userId, Set.of("admin"));
        }

        private TestUserContext(String userId, Set<String> roles) {
            this.userId = userId;
            this.roles = roles;
        }

        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public String getUserName() {
            return userId;
        }

        @Override
        public boolean isUserInRole(String roleId) {
            return roles.contains(roleId);
        }

        @Override
        public boolean isUserInAnyRole(Collection<String> roleIds) {
            for (String role : roleIds) {
                if (roles.contains(role)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Set<String> getRoles() {
            return roles;
        }

        @Override
        public void addRole(String roleId) {
        }

        @Override
        public void removeRole(String roleId) {
        }

        @Override
        public String getAccessToken() {
            return null;
        }

        @Override
        public String getRefreshToken() {
            return null;
        }

        @Override
        public void setAccessToken(String accessToken) {
        }

        @Override
        public void setRefreshToken(String refreshToken) {
        }

        @Override
        public long getLastAccessTime() {
            return 0;
        }

        @Override
        public void setLastAccessTime(long lastAccessTime) {
        }

        @Override
        public boolean dirty() {
            return false;
        }

        @Override
        public void markDirty() {
        }

        @Override
        public void clearDirty() {
        }

        @Override
        public String getSessionId() {
            return null;
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public String getLocale() {
            return null;
        }

        @Override
        public String getTimeZone() {
            return null;
        }

        @Override
        public String getDeptId() {
            return null;
        }

        @Override
        public String getDeptName() {
            return null;
        }

        @Override
        public String getOpenId() {
            return null;
        }

        @Override
        public String getNickName() {
            return null;
        }

        @Override
        public String getPrimaryRole() {
            return null;
        }

        @Override
        public Map<String, Object> getAttrs() {
            return Collections.emptyMap();
        }

        @Override
        public Object getAttr(String name) {
            return null;
        }

        @Override
        public void setAttr(String name, Object value) {
        }
    }

    private String saveCredentialRow(String id, String typeName, Map<String, Object> fields, String status) {
        return saveCredentialRow(id, typeName, fields, status, null, null);
    }

    private String saveCredentialRow(String id, String typeName, Map<String, Object> fields, String status,
                                     String scope, String ownerId) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName(typeName);
        entity.setStatus(status != null ? status : "enabled");
        entity.setDelFlag((byte) 0);
        entity.setVersion(1);
        entity.setScope(scope);
        entity.setOwnerId(ownerId);
        entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        dao.saveEntityDirectly(entity);
        return id;
    }

    private Map<String, Object> oauth2ManualFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("clientId", "test-client-id");
        fields.put("clientSecret", "test-client-secret");
        return fields;
    }

    private static void respond(HttpExchange exchange, int status, String body, String... headers) throws IOException {
        for (int i = 0; i + 1 < headers.length; i += 2) {
            exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
        }
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    private static String extractParam(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).equals(name)) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    private static String findHeaderIgnoreCase(IHttpResponse response, String name) {
        for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String beginAndExtractState(String credentialId) {
        String authUrl = flowService.beginOAuthFlow(credentialId);
        return extractParam(authUrl.substring(authUrl.indexOf('?') + 1), "state");
    }

    private NopCredential reload(String id) {
        return daoProvider.daoFor(NopCredential.class).getEntityById(id);
    }

    // ==================== 发起（beginOAuthFlow） ====================

    @Test
    public void beginReturnsAuthorizationUrlWithStateAndPersistBinding() {
        String id = saveCredentialRow("cred-oauth-begin", TYPE_NAME, oauth2ManualFields(), "enabled");
        String authUrl = flowService.beginOAuthFlow(id);

        assertNotNull(authUrl);
        assertTrue(authUrl.startsWith(stubServerBase + "/authorize?"),
                "auth url must target stub authorization endpoint: " + authUrl);
        assertTrue(authUrl.contains("client_id=test-client-id"), authUrl);
        assertTrue(authUrl.contains("response_type=code"), authUrl);
        assertTrue(authUrl.contains("redirect_uri="), authUrl);

        String state = extractParam(authUrl.substring(authUrl.indexOf('?') + 1), "state");
        assertEquals(32, state.length(), "state must be 128-bit hex token");

        NopCredentialOauthState row = stateStore.peek(state);
        assertNotNull(row, "state binding row must be persisted");
        assertEquals(id, row.getCredentialId());
        assertEquals(TEST_USER_ID, row.getUserId());
        assertTrue(row.getExpireAt() > System.currentTimeMillis(), "binding must not be expired");
    }

    @Test
    public void beginWithoutLoginThrowsFailClosed() {
        String id = saveCredentialRow("cred-oauth-nologin", TYPE_NAME, oauth2ManualFields(), "enabled");
        IUserContext.set(null);
        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.credential.oauth-user-context-required", ex.getErrorCode());
        IUserContext.set(new TestUserContext(TEST_USER_ID));
    }

    @Test
    public void beginOnNonOauth2TypeThrows() {
        String id = saveCredentialRow("cred-oauth-notype", NON_OAUTH_TYPE,
                new LinkedHashMap<>(Map.of("secret", "s1")), "enabled");
        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.credential.oauth-not-oauth2-type", ex.getErrorCode());
    }

    @Test
    public void beginOnDisabledCredentialThrows() {
        String id = saveCredentialRow("cred-oauth-disabled", TYPE_NAME, oauth2ManualFields(), "disabled");
        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.credential.disabled", ex.getErrorCode());
    }

    @Test
    public void beginOnDeletedCredentialThrows() {
        String id = saveCredentialRow("cred-oauth-deleted", TYPE_NAME, oauth2ManualFields(), "enabled");
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.getEntityById(id);
        entity.setDelFlag((byte) 1);
        dao.updateEntityDirectly(entity);

        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.credential.deleted", ex.getErrorCode());
    }

    @Test
    public void beginWithoutClientSecretThrows() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("clientId", "test-client-id"); // 缺 clientSecret
        String id = saveCredentialRow("cred-oauth-nosecret", TYPE_NAME, fields, "enabled");

        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.credential.oauth-client-credentials-missing", ex.getErrorCode());
    }

    /**
     * D2-02（A1-audit successor，2026-08-17）：发起侧补 clientId 非空校验（对齐回调侧
     * 字段集）——缺 clientId 与缺 clientSecret 同样拒绝，param 标注缺失字段集。
     */
    @Test
    public void beginWithoutClientIdThrows() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("clientSecret", "test-client-secret"); // 缺 clientId
        String id = saveCredentialRow("cred-oauth-noclientid", TYPE_NAME, fields, "enabled");

        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.credential.oauth-client-credentials-missing", ex.getErrorCode(),
                "missing clientId must be rejected at begin (D2-02, aligned with callback-side field set)");
        assertEquals("clientId", ex.getParam("fieldNames"),
                "param must name the missing field set (D2-02)");

        // 两者皆缺 → 缺失字段集同时标注两者
        Map<String, Object> bothMissing = new LinkedHashMap<>();
        bothMissing.put("extraField", "x");
        String id2 = saveCredentialRow("cred-oauth-noboth", TYPE_NAME, bothMissing, "enabled");
        NopException both = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id2));
        assertEquals("nop.err.credential.oauth-client-credentials-missing", both.getErrorCode());
        assertEquals("clientId,clientSecret", both.getParam("fieldNames"),
                "both missing fields must be named in the param (D2-02)");
    }

    @Test
    public void beginWithoutCallbackBaseUrlThrows() {
        String id = saveCredentialRow("cred-oauth-nobase", TYPE_NAME, oauth2ManualFields(), "enabled");
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_OAUTH_CALLBACK_BASE_URL, null);
        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.credential.oauth-callback-not-configured", ex.getErrorCode());
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_OAUTH_CALLBACK_BASE_URL, "https://platform.example.test");
    }

    // ==================== 回调（handleOAuthCallback）====================

    @Test
    public void callbackWritesTokenSetToReservedFieldsAndKeepsManualFields() {
        String id = saveCredentialRow("cred-oauth-callback", TYPE_NAME, oauth2ManualFields(), "enabled");
        String state = beginAndExtractState(id);

        WebContentBean page = flowService.handleOAuthCallback("auth-code-abc", state);
        assertNotNull(page);
        assertEquals(WebContentBean.CONTENT_TYPE_HTML, page.getContentType());

        // DB 中 data 已更新：解密验证保留字段 + 人工字段保留
        NopCredential entity = reload(id);
        Map<String, Object> fields = JsonTool.parseMap(credentialCipher.decrypt(entity.getData()));
        assertEquals("at-001", fields.get("accessToken"));
        assertEquals("rt-001", fields.get("refreshToken"));
        assertEquals("Bearer", fields.get("tokenType"));
        assertEquals("read write", fields.get("scope"));
        Object expiresAt = fields.get("expiresAt");
        assertTrue(expiresAt instanceof Number, "expiresAt must be numeric epoch millis");
        assertTrue(((Number) expiresAt).longValue() > System.currentTimeMillis(),
                "expiresAt must be future (now + expires_in)");
        assertEquals("test-client-id", fields.get("clientId"), "manual field clientId must be preserved");
        assertEquals("test-client-secret", fields.get("clientSecret"), "manual field clientSecret must be preserved");

        // 令牌端点收到的表单（机密客户端授权码换取协议形态；接线验证：IHttpClient 被实际使用）
        assertEquals(1, tokenRequests.size());
        Map<String, String> form = tokenRequests.get(0);
        assertEquals("authorization_code", form.get("grant_type"));
        assertEquals("auth-code-abc", form.get("code"));
        assertEquals("test-client-id", form.get("client_id"));
        assertEquals("test-client-secret", form.get("client_secret"));
        assertEquals("https://platform.example.test/r/CredentialOAuthApi__oauthCallback", form.get("redirect_uri"));
    }

    @Test
    public void callbackResponsePageContainsNoTokenPlaintext() {
        String id = saveCredentialRow("cred-oauth-noplain", TYPE_NAME, oauth2ManualFields(), "enabled");
        String state = beginAndExtractState(id);

        WebContentBean page = flowService.handleOAuthCallback("auth-code-abc", state);
        String html = (String) page.getContent();
        assertNotNull(html);
        assertFalse(html.contains("at-001"), "page must not contain accessToken plaintext");
        assertFalse(html.contains("rt-001"), "page must not contain refreshToken plaintext");
        assertTrue(html.contains("https://platform.example.test/oauth-result"),
                "page must redirect to configured result page");
    }

    /**
     * D2-05（A1-audit successor，2026-08-17）：结果页 URL 含引号/反斜杠时按语境分别编码——
     * JS 字符串上下文 JSON 编码（引号转义为 {@code \"}，无 HTML 实体残留），meta-refresh
     * 属性上下文保持 HTML 转义（{@code &quot;}）。
     */
    @Test
    public void resultPageEncodesUrlPerContextJsonForJsAndHtmlForMeta() {
        String hostileUrl = "https://x.test/a\"b\\c'?q=1";
        AppConfig.getConfigProvider().updateConfigValue(
                CredentialConfigs.CFG_CREDENTIAL_OAUTH_RESULT_PAGE_URL, hostileUrl);
        try {
            String id = saveCredentialRow("cred-oauth-jsenc", TYPE_NAME, oauth2ManualFields(), "enabled");
            String state = beginAndExtractState(id);
            WebContentBean page = flowService.handleOAuthCallback("auth-code-abc", state);
            String html = (String) page.getContent();
            assertNotNull(html);

            // JS 上下文：JSON 编码生效——引号被 JS 语法转义为 \"（escapeHtml 的 &quot; 残留可检测为失败）
            int jsStart = html.indexOf("window.location.replace(");
            assertTrue(jsStart > 0, "page must contain the JS redirect");
            String jsSegment = html.substring(jsStart, Math.min(html.length(), jsStart + 160));
            assertTrue(jsSegment.contains("\\\""),
                    "JS string context must JSON-encode quotes as \\\" (D2-05), segment: " + jsSegment);
            assertFalse(jsSegment.contains("&quot;"),
                    "escapeHtml residue must not appear in the JS string context (D2-05), segment: " + jsSegment);
            assertFalse(jsSegment.contains("&#39;"),
                    "HTML entity residue must not appear in the JS string context (D2-05)");

            // meta-refresh 属性上下文：HTML 转义保持（引号实体形式，不破坏属性定界）
            int metaStart = html.indexOf("http-equiv=\"refresh\"");
            assertTrue(metaStart > 0, "page must contain the meta-refresh tag");
            String metaSegment = html.substring(metaStart, Math.min(html.length(), metaStart + 160));
            assertTrue(metaSegment.contains("&quot;"),
                    "meta-refresh attribute context must keep HTML escaping (D2-05), segment: " + metaSegment);
            assertFalse(metaSegment.contains(";url=https://x.test/a\""),
                    "raw unescaped quote must not appear inside the meta url attribute (D2-05), segment: " + metaSegment);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(
                    CredentialConfigs.CFG_CREDENTIAL_OAUTH_RESULT_PAGE_URL, "https://platform.example.test/oauth-result");
        }
    }

    @Test
    public void callbackStateReplayRejected() {
        String id = saveCredentialRow("cred-oauth-replay", TYPE_NAME, oauth2ManualFields(), "enabled");
        String state = beginAndExtractState(id);

        assertNotNull(flowService.handleOAuthCallback("auth-code-abc", state));
        // 重放同一 state → 拒绝（一次性消费）
        NopException ex = assertThrows(NopException.class,
                () -> flowService.handleOAuthCallback("auth-code-abc", state));
        assertEquals("nop.err.credential.oauth-state-invalid", ex.getErrorCode());
    }

    @Test
    public void callbackUnknownStateRejected() {
        saveCredentialRow("cred-oauth-unknown", TYPE_NAME, oauth2ManualFields(), "enabled");
        NopException ex = assertThrows(NopException.class,
                () -> flowService.handleOAuthCallback("auth-code-abc", "deadbeefdeadbeefdeadbeefdeadbeef"));
        assertEquals("nop.err.credential.oauth-state-invalid", ex.getErrorCode());
    }

    @Test
    public void callbackExpiredStateRejected() {
        String id = saveCredentialRow("cred-oauth-expired", TYPE_NAME, oauth2ManualFields(), "enabled");
        String state = beginAndExtractState(id);

        // 将绑定行改为已过期
        IEntityDao<NopCredentialOauthState> dao = daoProvider.daoFor(NopCredentialOauthState.class);
        NopCredentialOauthState row = stateStore.peek(state);
        row.setExpireAt(System.currentTimeMillis() - 1000);
        dao.updateEntityDirectly(row);

        NopException ex = assertThrows(NopException.class,
                () -> flowService.handleOAuthCallback("auth-code-abc", state));
        assertEquals("nop.err.credential.oauth-state-invalid", ex.getErrorCode());
    }

    @Test
    public void callbackMissingParamsRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> flowService.handleOAuthCallback(null, "some-state"));
        assertEquals("nop.err.credential.oauth-callback-param-missing", ex.getErrorCode());
    }

    @Test
    public void callbackTokenEndpointErrorThrowsExplicitly() {
        String id = saveCredentialRow("cred-oauth-terror", TYPE_NAME, oauth2ManualFields(), "enabled");
        String state = beginAndExtractState(id);
        tokenResponseBody = "{\"error\":\"invalid_grant\",\"error_description\":\"code expired\"}";
        tokenResponseStatus = 400;

        NopException ex = assertThrows(NopException.class,
                () -> flowService.handleOAuthCallback("auth-code-abc", state));
        assertEquals("nop.err.credential.oauth-token-exchange-failed", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("invalid_grant"),
                "error detail must surface provider error: " + ex.getMessage());

        // 失败后 data 不变（token 未写入）
        NopCredential entity = reload(id);
        Map<String, Object> fields = JsonTool.parseMap(credentialCipher.decrypt(entity.getData()));
        assertNull(fields.get("accessToken"), "failed exchange must not write tokens");
    }

    @Test
    public void callbackOnDisabledCredentialRejected() {
        String id = saveCredentialRow("cred-oauth-cbdisabled", TYPE_NAME, oauth2ManualFields(), "enabled");
        String state = beginAndExtractState(id);

        // 发起后禁用该凭证 → 回调拒绝
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.getEntityById(id);
        entity.setStatus("disabled");
        dao.updateEntityDirectly(entity);

        NopException ex = assertThrows(NopException.class,
                () -> flowService.handleOAuthCallback("auth-code-abc", state));
        assertEquals("nop.err.credential.disabled", ex.getErrorCode());
    }

    @Test
    public void concurrentDoubleCallbackSameStateExactlyOneSucceeds() throws Exception {
        String id = saveCredentialRow("cred-oauth-concurrent", TYPE_NAME, oauth2ManualFields(), "enabled");
        String state = beginAndExtractState(id);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        flowService.handleOAuthCallback("auth-code-abc", state);
                        return true;
                    } catch (NopException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(60, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes, "concurrent double callback on same state must allow exactly one success");
            assertEquals(1, tokenCallCount.get(), "exactly one token exchange must have happened");
        } finally {
            pool.shutdownNow();
        }
    }

    // ==================== 端到端（begin → 授权 URL 302 → 回调 → 取用明文） ====================

    @Test
    public void endToEndBeginToGetCredential() {
        String id = saveCredentialRow("cred-oauth-e2e", TYPE_NAME, oauth2ManualFields(), "enabled");

        // 1. 发起：得到授权 URL
        String authUrl = flowService.beginOAuthFlow(id);

        // 2. 模拟浏览器请求授权 URL：stub 授权服务器 302 → 回调 URL（code + state）
        HttpRequest request = new HttpRequest();
        request.setMethod("GET");
        request.url(authUrl);
        IHttpResponse response = httpClient.fetch(request, null);
        assertEquals(302, response.getHttpStatus(), "stub authorization server must 302 to callback");
        String location = findHeaderIgnoreCase(response, "Location");
        assertNotNull(location);
        assertTrue(location.startsWith("https://platform.example.test/r/CredentialOAuthApi__oauthCallback"));

        String query = location.substring(location.indexOf('?') + 1);
        String code = extractParam(query, "code");
        String state = extractParam(query, "state");
        assertEquals("auth-code-abc", code);

        // 3. 模拟浏览器 GET 回调端点（测试中直接调 action；REST /r/ 层由平台映射 query 参数为方法参数）
        CredentialOAuthApiBizModel bizModel = new CredentialOAuthApiBizModel();
        bizModel.setOauthFlowService(flowService);
        WebContentBean page = bizModel.oauthCallback(code, state, null);
        assertNotNull(page);
        assertFalse(((String) page.getContent()).contains("at-001"));

        // 4. 取用明文：getCredential 出口拿到闭环写入的 accessToken
        CredentialData data = credentialProvider.getCredential(id);
        assertEquals("at-001", data.getField("accessToken"));
        assertEquals("rt-001", data.getField("refreshToken"));
        assertEquals("test-client-secret", data.getField("clientSecret"));
    }

    // ==================== state 存储语义（惰性清理） ====================

    @Test
    public void stateLazyCleanupOnCreate() {
        String id = saveCredentialRow("cred-oauth-lazy", TYPE_NAME, oauth2ManualFields(), "enabled");

        // 造一行已过期的绑定
        IEntityDao<NopCredentialOauthState> dao = daoProvider.daoFor(NopCredentialOauthState.class);
        NopCredentialOauthState expired = dao.newEntity();
        expired.setState("expiredstateexpiredstateexpired1234");
        expired.setCredentialId(id);
        expired.setUserId(TEST_USER_ID);
        expired.setExpireAt(System.currentTimeMillis() - 60000);
        expired.setConsumed((byte) 0);
        dao.saveEntityDirectly(expired);
        assertNotNull(dao.getEntityById("expiredstateexpiredstateexpired1234"));

        // 再次发起 → 惰性清理删除过期行
        flowService.beginOAuthFlow(id);
        assertNull(dao.getEntityById("expiredstateexpiredstateexpired1234"),
                "expired state rows must be lazily cleaned on begin");
    }

    // ==================== 容器装配（bean 注册接线，closure audit 回补） ====================

    /**
     * NopIoC 无注解扫描：API 面 BizModel 必须在 beans 文件显式注册（LoginApiBizModel 先例）。
     * 本用例经 @NopTestConfig 容器注入（非手工 new）解析 CredentialOAuthApiBizModel，
     * 并以发起不存在凭证的 fail-closed 调用证明容器装配的依赖链（BizModel → OAuthFlowService →
     * provider/dao）在运行时接通。
     */
    @Test
    public void containerRegisteredApiBizModelWiredThroughIoc() {
        assertNotNull(containerApiBizModel, "CredentialOAuthApiBizModel must be resolvable from the IoC container");

        // D1-03/D4-07：发起不存在凭证统一 UnknownEntityException（原 ERR_CREDENTIAL_NOT_FOUND 归一）
        NopException e = assertThrows(NopException.class,
                () -> containerApiBizModel.beginOAuthFlow("no-such-credential-id", null));
        assertEquals("nop.err.dao.unknown-entity", e.getErrorCode(),
                "container-wired begin must fail closed on unknown credential (normalized, D1-03/D4-07)");
    }

    // ==================== W11 回补：发起动作归属校验（§5.3 写类矩阵） ====================

    @Test
    public void beginOnSystemScopeByNonAdminRejected() {
        String id = saveCredentialRow("cred-oauth-sys-nonadmin", TYPE_NAME, oauth2ManualFields(), "enabled",
                "system", null);
        IUserContext.set(new TestUserContext(TEST_USER_ID, Collections.emptySet()));

        // D1-03/D4-07：越权发起与"不存在"统一 UnknownEntityException（三态不可区分）
        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.dao.unknown-entity", ex.getErrorCode(),
                "unauthorized begin must be normalized as not-found (D1-03/D4-07)");
        assertEquals(0, stateStoreAllCount(), "no state binding must be persisted on denial");
    }

    @Test
    public void beginOnSystemScopeByAdminAllowed() {
        String id = saveCredentialRow("cred-oauth-sys-admin", TYPE_NAME, oauth2ManualFields(), "enabled",
                "system", null);
        IUserContext.set(new TestUserContext("admin-user", Set.of("admin")));

        String authUrl = flowService.beginOAuthFlow(id);
        assertNotNull(authUrl, "admin must be able to begin oauth flow for system-scope credential");
    }

    @Test
    public void beginOnUserScopeByOwnerAllowed() {
        String id = saveCredentialRow("cred-oauth-user-owner", TYPE_NAME, oauth2ManualFields(), "enabled",
                "user", TEST_USER_ID);
        IUserContext.set(new TestUserContext(TEST_USER_ID, Collections.emptySet()));

        String authUrl = flowService.beginOAuthFlow(id);
        assertNotNull(authUrl, "owner must be able to begin oauth flow for own user-scope credential");
    }

    @Test
    public void beginOnUserScopeByOtherRejected() {
        String id = saveCredentialRow("cred-oauth-user-other", TYPE_NAME, oauth2ManualFields(), "enabled",
                "user", "someone-else");
        IUserContext.set(new TestUserContext(TEST_USER_ID, Collections.emptySet()));

        // D1-03/D4-07：非 owner 非 admin 发起他人 user 级 → 与"不存在"不可区分
        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.dao.unknown-entity", ex.getErrorCode(),
                "unauthorized begin must be normalized as not-found (D1-03/D4-07)");
    }

    @Test
    public void beginOnUserScopeByAdminAllowed() {
        String id = saveCredentialRow("cred-oauth-user-admin", TYPE_NAME, oauth2ManualFields(), "enabled",
                "user", "someone-else");
        IUserContext.set(new TestUserContext("admin-user", Set.of("admin")));

        String authUrl = flowService.beginOAuthFlow(id);
        assertNotNull(authUrl, "admin must be able to begin oauth flow for user-scope credential (§5.3 write matrix)");
    }

    @Test
    public void beginOnNullScopeLegacyRowByNonAdminRejected() {
        // 存量 NULL scope 行视同 system：非管理员发起同样拒绝（NULL 视同 system 的写面语义）
        String id = saveCredentialRow("cred-oauth-null-nonadmin", TYPE_NAME, oauth2ManualFields(), "enabled");
        IUserContext.set(new TestUserContext(TEST_USER_ID, Collections.emptySet()));

        // D1-03/D4-07：NULL scope（视同 system）非管理员发起 → 与"不存在"不可区分
        NopException ex = assertThrows(NopException.class, () -> flowService.beginOAuthFlow(id));
        assertEquals("nop.err.dao.unknown-entity", ex.getErrorCode(),
                "unauthorized begin on NULL-scope (system) row must be normalized as not-found (D1-03/D4-07)");
    }

    /**
     * D1-03/D4-07 专项：发起不存在凭证与越权发起不可区分（统一 unknown-entity）——
     * credentialId 枚举探测无法经发起路径区分"存在但无权"与"不存在"。
     */
    @Test
    public void beginOnNonExistentCredentialNormalizedAsUnknown() {
        NopException ex = assertThrows(NopException.class,
                () -> flowService.beginOAuthFlow("no-such-credential-id"));
        assertEquals("nop.err.dao.unknown-entity", ex.getErrorCode(),
                "begin on non-existent credential must throw unknown-entity (D1-03/D4-07)");
    }

    private long stateStoreAllCount() {
        IEntityDao<NopCredentialOauthState> dao = daoProvider.daoFor(NopCredentialOauthState.class);
        return dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()).size();
    }
}
