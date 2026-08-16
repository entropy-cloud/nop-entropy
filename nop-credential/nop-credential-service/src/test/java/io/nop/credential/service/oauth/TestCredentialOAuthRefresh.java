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
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.service.CredentialProviderImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.http.api.client.IHttpClient;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * W9 Phase 3：惰性刷新 + 跨副本互斥（确定性判据：stub 刷新端点调用计数）+ 分组写语义 +
 * oauth2 disabled 全路径拒绝 + 非 OAuth 类型一期语义零回归。
 *
 * <p>刷新路径经手搭 {@link CredentialProviderImpl}（动态 stub registry 端点）；
 * saveCredential 分组写经 GraphQL 引擎驱动容器 BizModel（type=generic-oauth2）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestCredentialOAuthRefresh extends JunitBaseTestCase {

    private static final String OAUTH_TYPE = "generic-oauth2"; // 生产 registry 中已注册的示例类型
    private static final String NON_OAUTH_TYPE = "generic-secret";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    CredentialCipher credentialCipher;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IHttpClient httpClient;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate txnTemplate;

    private HttpServer stubServer;
    private final AtomicInteger refreshCallCount = new AtomicInteger();
    private volatile String refreshResponseBody =
            "{\"access_token\":\"at-refreshed\",\"refresh_token\":\"rt-rotated\",\"expires_in\":7200,"
                    + "\"token_type\":\"Bearer\",\"scope\":\"read write\"}";
    private volatile int refreshResponseStatus = 200;

    private CredentialProviderImpl provider;

    @BeforeEach
    public void setUpStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/token", exchange -> {
            refreshCallCount.incrementAndGet();
            respond(exchange, refreshResponseStatus, refreshResponseBody);
        });
        stubServer.start();

        provider = new CredentialProviderImpl();
        provider.setDaoProvider(daoProvider);
        provider.setCredentialCipher(credentialCipher);
        provider.setCredentialTypeRegistry(newDynamicRegistry());
        provider.setOrmTemplate(ormTemplate);
        provider.setTxnTemplate(txnTemplate);
        OAuthTokenClient tokenClient = new OAuthTokenClient();
        tokenClient.setHttpClient(httpClient);
        provider.setOauthTokenClient(tokenClient);
    }

    @AfterEach
    public void tearDownStub() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    // ==================== 测试基建 ====================

    /** 动态 registry：generic-oauth2 的令牌端点指向 stub（refreshWindow 覆盖为 300s）。 */
    private ICredentialTypeRegistry newDynamicRegistry() {
        CredentialType type = new CredentialType();
        type.setName(OAUTH_TYPE);
        type.setDisplayName("Generic OAuth2 (stub)");
        type.setAuthType(CredentialType.AUTH_TYPE_OAUTH2);
        type.setFields(new ArrayList<>(List.of(
                new CredentialType.CredentialField("clientId", "Client ID", "string", false, null, true),
                new CredentialType.CredentialField("clientSecret", "Client Secret", "password", true, null, true))));
        CredentialType.OAuth2Metadata metadata = new CredentialType.OAuth2Metadata();
        metadata.setAuthorizationEndpoint("http://127.0.0.1/auth");
        metadata.setTokenEndpoint("http://127.0.0.1:" + stubServer.getAddress().getPort() + "/token");
        metadata.setScopes("read write");
        metadata.setRefreshWindowSeconds(300);
        type.setOauth2(metadata);
        return new ICredentialTypeRegistry() {
            @Override
            public CredentialType getType(String typeName) {
                if (OAUTH_TYPE.equals(typeName)) {
                    return type;
                }
                // 非 oauth2 类型走生产 registry 语义（此处仅需 none 类型存在）
                if (NON_OAUTH_TYPE.equals(typeName)) {
                    CredentialType plain = new CredentialType();
                    plain.setName(NON_OAUTH_TYPE);
                    plain.setAuthType(CredentialType.AUTH_TYPE_NONE);
                    return plain;
                }
                throw new NopException(io.nop.credential.crypto.CredentialErrors.ERR_CREDENTIAL_UNKNOWN_TYPE)
                        .param(io.nop.credential.crypto.CredentialErrors.ARG_TYPE_NAME, typeName);
            }

            @Override
            public List<CredentialType> listTypes() {
                return List.of(type);
            }
        };
    }

    private String saveOauth2Credential(String id, long expiresAtEpochMillis, String refreshToken) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("clientId", "refresh-client-id");
        fields.put("clientSecret", "refresh-client-secret");
        fields.put("accessToken", "at-old");
        if (refreshToken != null) {
            fields.put("refreshToken", refreshToken);
        }
        fields.put("expiresAt", expiresAtEpochMillis);
        fields.put("tokenType", "Bearer");
        fields.put("scope", "read write");
        return saveCredentialRow(id, OAUTH_TYPE, fields, "enabled");
    }

    private String saveCredentialRow(String id, String typeName, Map<String, Object> fields, String status) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.newEntity();
        entity.setCredentialId(id);
        entity.setName(id);
        entity.setTypeName(typeName);
        entity.setStatus(status != null ? status : "enabled");
        entity.setDelFlag((byte) 0);
        entity.setVersion(1);
        entity.setData(credentialCipher.encrypt(JsonTool.stringify(fields)));
        dao.saveEntityDirectly(entity);
        return id;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Map<String, Object> decryptData(String id) {
        NopCredential entity = daoProvider.daoFor(NopCredential.class).getEntityById(id);
        return JsonTool.parseMap(credentialCipher.decrypt(entity.getData()));
    }

    private GraphQLResponseBean executeGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    // ==================== 惰性刷新 ====================

    @Test
    public void nearingExpiryTriggersRefreshOnceAndWritesBack() {
        long expiresIn60s = System.currentTimeMillis() + 60_000; // < 300s 窗口 → 临期
        String id = saveOauth2Credential("cred-refresh-nearing", expiresIn60s, "rt-old");

        CredentialData data = provider.getCredential(id);
        assertEquals("at-refreshed", data.getField("accessToken"), "nearing expiry must refresh and return new token");
        assertEquals("rt-rotated", data.getField("refreshToken"), "rotated refresh token must be written back");
        assertEquals(1, refreshCallCount.get(), "refresh endpoint must be called exactly once");

        // DB 回写验证（只写保留字段；人工字段保留）
        Map<String, Object> stored = decryptData(id);
        assertEquals("at-refreshed", stored.get("accessToken"));
        assertEquals("rt-rotated", stored.get("refreshToken"));
        assertEquals("refresh-client-id", stored.get("clientId"));
        assertEquals("refresh-client-secret", stored.get("clientSecret"));
        assertTrue(((Number) stored.get("expiresAt")).longValue() > System.currentTimeMillis() + 3_600_000,
                "new expiresAt must be ~now+7200s");

        // 窗口内（新 token 远未临期）再次取用不刷新——确定性判据：计数仍为 1
        CredentialData again = provider.getCredential(id);
        assertEquals("at-refreshed", again.getField("accessToken"));
        assertEquals(1, refreshCallCount.get(), "second getCredential within window must NOT refresh");
    }

    @Test
    public void notNearingReturnsDirectlyWithoutRefresh() {
        long expiresIn1h = System.currentTimeMillis() + 3_600_000; // > 300s 窗口 → 非临期
        String id = saveOauth2Credential("cred-refresh-far", expiresIn1h, "rt-old");

        CredentialData data = provider.getCredential(id);
        assertEquals("at-old", data.getField("accessToken"));
        assertEquals(0, refreshCallCount.get(), "non-nearing credential must not trigger refresh");
    }

    @Test
    public void refreshFailureFailsClosed() {
        long expiresIn60s = System.currentTimeMillis() + 60_000;
        String id = saveOauth2Credential("cred-refresh-fail", expiresIn60s, "rt-old");
        refreshResponseBody = "{\"error\":\"invalid_grant\"}";
        refreshResponseStatus = 400;

        NopException ex = assertThrows(NopException.class, () -> provider.getCredential(id));
        assertEquals("nop.err.credential.oauth-refresh-failed", ex.getErrorCode(),
                "refresh failure must fail-closed (no silent stale token)");

        // data 不变（旧 token 未被破坏）
        Map<String, Object> stored = decryptData(id);
        assertEquals("at-old", stored.get("accessToken"));
    }

    @Test
    public void expiredWithoutRefreshTokenFailsClosedWithReauthHint() {
        long expired = System.currentTimeMillis() - 60_000;
        String id = saveOauth2Credential("cred-refresh-reauth", expired, null);

        NopException ex = assertThrows(NopException.class, () -> provider.getCredential(id));
        assertEquals("nop.err.credential.oauth-reauth-required", ex.getErrorCode());
        assertEquals(0, refreshCallCount.get(), "no refresh endpoint call without refreshToken");
    }

    @Test
    public void nearingWithoutRefreshTokenButNotExpiredReturnsCurrent() {
        long expiresIn60s = System.currentTimeMillis() + 60_000;
        String id = saveOauth2Credential("cred-refresh-nort", expiresIn60s, null);

        // 尚未过期、无 refreshToken：直接返回现值（到过期点才 fail-closed）
        CredentialData data = provider.getCredential(id);
        assertEquals("at-old", data.getField("accessToken"));
        assertEquals(0, refreshCallCount.get());
    }

    // ==================== 并发互斥（确定性判据：刷新端点调用计数） ====================

    @Test
    public void concurrentGetCredentialRefreshesExactlyOnce() throws Exception {
        long expiresIn60s = System.currentTimeMillis() + 60_000;
        String id = saveOauth2Credential("cred-refresh-concurrent", expiresIn60s, "rt-old");

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return provider.getCredential(id).getField("accessToken").toString();
                }));
            }
            start.countDown();
            String first = null;
            for (Future<String> f : futures) {
                String token = f.get(60, TimeUnit.SECONDS);
                if (first == null) {
                    first = token;
                }
                assertEquals(first, token, "all threads must observe the same refreshed token");
            }
            assertEquals("at-refreshed", first);
            assertEquals(1, refreshCallCount.get(),
                    "row-lock mutex must converge concurrent refresh to exactly one endpoint call");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void concurrentRefreshVsSaveCredentialNoLostUpdate() throws Exception {
        long expiresIn60s = System.currentTimeMillis() + 60_000;
        String id = saveOauth2Credential("cred-refresh-vs-save", expiresIn60s, "rt-old");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> refreshFuture = pool.submit(() -> {
                start.await();
                return provider.getCredential(id).getField("accessToken").toString();
            });
            Future<?> saveFuture = pool.submit(() -> {
                start.await();
                GraphQLResponseBean response = executeGraphQL(String.format(
                        "mutation { NopCredential__saveCredential(typeName: \"%s\", name: \"renamed-%s\", id: \"%s\", "
                                + "fields: {clientId:\"new-client-id\",clientSecret:\"new-client-secret\"}) { credentialId } }",
                        OAUTH_TYPE, id, id));
                assertFalse(response.hasError(), "saveCredential must succeed: " + JSON.stringify(response));
                return "saved";
            });
            start.countDown();
            Object refreshResult = refreshFuture.get(60, TimeUnit.SECONDS);
            Object saveResult = saveFuture.get(60, TimeUnit.SECONDS);
            assertNotNull(refreshResult);
            assertEquals("saved", saveResult);

            // 最终 data 一致性：刷新效果（新 token）与人工保存效果（新人工字段值）都存在，无丢失
            Map<String, Object> stored = decryptData(id);
            assertEquals("at-refreshed", stored.get("accessToken"), "refresh effect must survive");
            assertEquals("rt-rotated", stored.get("refreshToken"));
            assertEquals("new-client-id", stored.get("clientId"), "manual replace effect must survive");
            assertEquals("new-client-secret", stored.get("clientSecret"));
        } finally {
            pool.shutdownNow();
        }
    }

    // ==================== disabled 全路径拒绝 / 非 OAuth 零回归 ====================

    @Test
    public void disabledOauth2CredentialRejectedOnGetCredentialAndMask() {
        long expiresIn1h = System.currentTimeMillis() + 3_600_000;
        String id = saveOauth2Credential("cred-disabled-oauth", expiresIn1h, "rt-old");
        setStatus(id, "disabled");

        NopException getCredentialEx = assertThrows(NopException.class, () -> provider.getCredential(id));
        assertEquals("nop.err.credential.disabled", getCredentialEx.getErrorCode(),
                "oauth2 disabled must be rejected on getCredential");

        NopException maskEx = assertThrows(NopException.class, () -> provider.mask(id));
        assertEquals("nop.err.credential.disabled", maskEx.getErrorCode(),
                "oauth2 disabled must be rejected on mask");
    }

    @Test
    public void disabledNonOauthCredentialStillAccessible() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("secret", "plain-secret-value");
        String id = saveCredentialRow("cred-disabled-secret", NON_OAUTH_TYPE, fields, "disabled");

        // 一期语义：非 OAuth 类型取用仅校验 delFlag——disabled 仍可取
        CredentialData data = provider.getCredential(id);
        assertEquals("plain-secret-value", data.getField("secret"));
    }

    private void setStatus(String id, String status) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        NopCredential entity = dao.getEntityById(id);
        entity.setStatus(status);
        dao.updateEntityDirectly(entity);
    }

    // ==================== saveCredential 分组写 + 保留字段拒绝 + typeList 裁剪 ====================

    @Test
    public void saveCredentialRejectsReservedFieldNames() {
        GraphQLResponseBean response = executeGraphQL(String.format(
                "mutation { NopCredential__saveCredential(typeName: \"%s\", name: \"reserved-reject\", "
                        + "fields: {clientId:\"c1\",clientSecret:\"s1\",accessToken:\"forged\"}) { credentialId } }",
                OAUTH_TYPE));
        assertTrue(response.hasError(), "reserved field names in input must be rejected");
        assertEquals("nop.err.credential.reserved-field-input", response.getErrorCode(),
                "must fail with ERR_CREDENTIAL_RESERVED_FIELD_INPUT, errors=" + response.getErrors());
    }

    @Test
    public void saveCredentialManualReplacePreservesTokenSet() {
        long expiresIn1h = System.currentTimeMillis() + 3_600_000;
        String id = saveOauth2Credential("cred-grouped-write", expiresIn1h, "rt-grouped");

        // 人工字段整包替换（新增 extraField、未传的旧人工字段删除），token 集不动
        GraphQLResponseBean response = executeGraphQL(String.format(
                "mutation { NopCredential__saveCredential(typeName: \"%s\", name: \"grouped\", id: \"%s\", "
                        + "fields: {clientId:\"replaced-id\",extraField:\"extra\"}) { credentialId name } }",
                OAUTH_TYPE, id));
        assertFalse(response.hasError(), "grouped saveCredential must succeed: " + JSON.stringify(response));

        Map<String, Object> stored = decryptData(id);
        assertEquals("at-old", stored.get("accessToken"), "token set must survive manual replace");
        assertEquals("rt-grouped", stored.get("refreshToken"));
        assertEquals("replaced-id", stored.get("clientId"), "manual field must be replaced");
        assertEquals("extra", stored.get("extraField"), "new manual field must be added");
        assertNull(stored.get("clientSecret"), "unpassed manual field must be deleted (整包替换)");
        assertEquals("grouped", daoProvider.daoFor(NopCredential.class).getEntityById(id).getName(),
                "metadata (name) must be updated");
    }

    @Test
    public void typeListPrunesReservedFieldsForOauth2Types() {
        GraphQLResponseBean response = executeGraphQL(
                "query { NopCredential__typeList { name authType fields { name } } }");
        assertFalse(response.hasError(), "typeList must succeed: " + response.getErrors());

        Map<String, Object> data = (Map<String, Object>) response.getData();
        List<Map<String, Object>> types = (List<Map<String, Object>>) data.get("NopCredential__typeList");
        assertNotNull(types);
        Map<String, Object> oauth2Type = types.stream()
                .filter(t -> OAUTH_TYPE.equals(t.get("name"))).findFirst().orElse(null);
        assertNotNull(oauth2Type, "generic-oauth2 must be listed");
        assertEquals("oauth2", oauth2Type.get("authType"));

        List<Map<String, Object>> fields = (List<Map<String, Object>>) oauth2Type.get("fields");
        for (Map<String, Object> field : fields) {
            assertFalse(CredentialType.OAUTH_RESERVED_FIELD_NAMES.contains(field.get("name")),
                    "typeList must prune reserved fields for oauth2 types, got: " + field.get("name"));
        }
        assertTrue(fields.stream().anyMatch(f -> "clientId".equals(f.get("name"))),
                "manual fields must remain");
    }
}
