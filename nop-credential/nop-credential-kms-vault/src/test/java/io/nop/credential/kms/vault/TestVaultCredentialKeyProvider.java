/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.kms.vault;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.crypto.ITextCipher;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W10 Phase 2 {@link VaultCredentialKeyProvider} 启动期 fail-closed 校验组单测
 * （stub IHttpClient 按分支返回状态/响应体/传输异常——plan 工程注记允许的 IHttpClient
 * mock 路径；真实 HTTP 栈的协议级验证在 {@link TestVaultKeyProviderWiring}）。
 *
 * <p><b>无本地降级断言</b>：全部故障分支均为 init 抛错（应用拒绝启动），不存在
 * "回退 DefaultCredentialKeyProvider / 跳过该 key 继续启动"路径——本类逐分支断言
 * init 抛错即为其测试形态；代码侧无任何 DefaultCredentialKeyProvider 引用与
 * catch-continue 分支（结构事实）。
 */
public class TestVaultCredentialKeyProvider {

    @org.junit.jupiter.api.BeforeAll
    static void initJsonProvider() {
        // plain JUnit 下无 CoreInitialization：手工注册 JSON provider（运行期由
        // ReflectionHelperMethodInitializer 注册，见该类 JSON.registerProvider 调用）
        if (io.nop.api.core.json.JSON.getProvider() == null) {
            io.nop.api.core.json.JSON.registerProvider(io.nop.core.lang.json.JsonTool.instance());
        }
    }

    // ==================== stub IHttpClient / IHttpResponse ====================

    static final class StubResponse implements IHttpResponse {
        private final int status;
        private final String body;

        StubResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public int getHttpStatus() {
            return status;
        }

        @Override
        public String getBodyAsString() {
            return body;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getCharset() {
            return "UTF-8";
        }

        @Override
        public byte[] getBodyAsBytes() {
            return body == null ? new byte[0] : body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            throw new UnsupportedOperationException("not needed in stub");
        }

        @Override
        public Object getBody() {
            return body;
        }
    }

    static final class StubHttpClient implements IHttpClient {
        final Function<HttpRequest, IHttpResponse> handler;

        StubHttpClient(Function<HttpRequest, IHttpResponse> handler) {
            this.handler = handler;
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            IHttpResponse response = handler.apply(request);
            if (response instanceof ThrowableResponse) {
                CompletableFuture<IHttpResponse> future = new CompletableFuture<>();
                future.completeExceptionally(((ThrowableResponse) response).error);
                return future;
            }
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, io.nop.http.api.client.IHttpOutputFile targetFile,
                                                             io.nop.http.api.client.DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("not needed in stub");
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, io.nop.http.api.client.IHttpInputFile inputFile,
                                                          io.nop.http.api.client.UploadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("not needed in stub");
        }
    }

    static final class ThrowableResponse implements IHttpResponse {
        final RuntimeException error;

        ThrowableResponse(RuntimeException error) {
            this.error = error;
        }

        @Override
        public int getHttpStatus() {
            throw error;
        }

        @Override
        public String getBodyAsString() {
            throw error;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public String getCharset() {
            return null;
        }

        @Override
        public byte[] getBodyAsBytes() {
            throw error;
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            throw error;
        }

        @Override
        public Object getBody() {
            throw error;
        }
    }

    // ==================== helpers ====================

    private static String kvV2Body(String passphrase) {
        // 测试材料均为普通字母数字，无需 JSON 转义（plain JUnit 下 JSON provider 未注册）
        return "{\"data\":{\"data\":{\"passphrase\":\"" + passphrase + "\"},\"metadata\":{\"version\":1}}}";
    }

    private static VaultCredentialKeyProvider newProvider(IHttpClient client, List<String> keys) {
        VaultCredentialKeyProvider p = new VaultCredentialKeyProvider();
        p.setHttpClient(client);
        p.setAddress("http://127.0.0.1:8200");
        p.setToken("test-token");
        p.setKeys(keys);
        return p;
    }

    private static VaultCredentialKeyProvider happyPathProvider() {
        return newProvider(new StubHttpClient(req -> new StubResponse(200, kvV2Body("stub-material"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
    }

    private static String initError(VaultCredentialKeyProvider p) {
        NopException ex = assertThrows(NopException.class, p::init);
        return ex.getErrorCode();
    }

    // ==================== 启动校验组：fail-closed 逐分支 ====================

    @Test
    public void initThrowsWhenAddressMissing() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))), Collections.singletonList("keyA:p"));
        p.setAddress(null);
        assertEquals("nop.err.credential.vault.config-missing", initError(p));
    }

    @Test
    public void initThrowsWhenTokenMissing() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))), Collections.singletonList("keyA:p"));
        p.setToken("");
        assertEquals("nop.err.credential.vault.config-missing", initError(p));
    }

    @Test
    public void initThrowsWhenNoKeysConfigured() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))), Collections.emptyList());
        assertEquals("nop.err.credential.vault.no-key-configured", initError(p));
    }

    @Test
    public void initThrowsForInvalidKeyMappings() {
        IHttpClient ok = new StubHttpClient(req -> new StubResponse(200, kvV2Body("m")));
        assertEquals("nop.err.credential.vault.key-mapping-invalid",
                initError(newProvider(ok, Collections.singletonList("no-colon-at-all"))));
        assertEquals("nop.err.credential.vault.key-mapping-invalid",
                initError(newProvider(ok, Collections.singletonList("key.with.dot:p"))));
        assertEquals("nop.err.credential.vault.key-mapping-invalid",
                initError(newProvider(ok, Collections.singletonList("keyA:"))));
        assertEquals("nop.err.credential.vault.key-mapping-invalid",
                initError(newProvider(ok, Arrays.asList("keyA:p1", "keyA:p2"))));
        assertEquals("nop.err.credential.vault.key-mapping-invalid",
                initError(newProvider(ok, Arrays.asList("keyA:p1", null))));
    }

    @Test
    public void initThrowsWhenVaultUnreachable() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new ThrowableResponse(new RuntimeException("connect refused"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.unreachable", initError(p));
    }

    @Test
    public void initThrowsOn401Unauthorized() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(401, "{\"errors\":[\"permission denied\"]}")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.auth-failed", initError(p));
    }

    @Test
    public void initThrowsOn403Forbidden() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(403, "{\"errors\":[\"bad token\"]}")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.auth-failed", initError(p));
    }

    @Test
    public void initThrowsOn404KeyMissing() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(404, "{\"errors\":[\"not found\"]}")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.key-not-found", initError(p));
    }

    @Test
    public void initThrowsOn500ReadFailed() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(500, "internal error")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.read-failed", initError(p));
    }

    @Test
    public void initThrowsOnEmptyBody() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, "")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.material-invalid", initError(p));
    }

    @Test
    public void initThrowsOnMissingNestedData() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, "{\"data\":{\"metadata\":{}}}")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.material-invalid", initError(p));
    }

    @Test
    public void initThrowsOnNonStringMaterial() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, "{\"data\":{\"data\":{\"passphrase\":123}}}")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.material-invalid", initError(p));
    }

    @Test
    public void initThrowsOnEmptyMaterial() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, "{\"data\":{\"data\":{\"passphrase\":\"\"}}}")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.material-invalid", initError(p));
    }

    @Test
    public void initThrowsOnMalformedJson() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, "not-json")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.material-invalid", initError(p));
    }

    // ==================== 密钥来源混合 / 迁移残余列表 ====================

    @Test
    public void initThrowsWhenMasterKeysResidual() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        p.setMasterKeys(Collections.singletonList("old:local-pass-leftover"));
        assertEquals("nop.err.credential.vault.master-keys-residual", initError(p));
    }

    @Test
    public void initThrowsForInvalidMigrationKeyEntry() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        p.setMigrationKeys(Collections.singletonList("no-colon"));
        assertEquals("nop.err.credential.vault.migration-key-invalid", initError(p));
    }

    @Test
    public void initThrowsWhenMigrationKeyOverlapsVaultKey() {
        // keyB 为非 active 的 Vault 密钥：残余列表与其冲突 → keyId 冲突错误
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
        p.setMigrationKeys(Collections.singletonList("keyB:legacy-pass"));
        assertEquals("nop.err.credential.vault.migration-key-invalid", initError(p));
    }

    @Test
    public void initThrowsWhenMigrationKeyContainsActiveKey() {
        // active=keyA 且残余列表包含 keyA → "残余含 active key"错误（残余 key 只解不加密）
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
        p.setActiveKeyId("keyA");
        p.setMigrationKeys(Collections.singletonList("keyA:legacy-pass"));
        assertEquals("nop.err.credential.vault.migration-key-active", initError(p));
    }

    @Test
    public void initThrowsWhenActiveKeyPointsToMigrationKeyOnly() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        p.setActiveKeyId("legacyKey");
        p.setMigrationKeys(Collections.singletonList("legacyKey:legacy-pass"));
        assertEquals("nop.err.credential.vault.unknown-active-key", initError(p));
    }

    // ==================== WARN 审计（迁移残余列表） ====================

    private ListAppender<ILoggingEvent> appender;
    private Logger providerLogger;

    @BeforeEach
    public void attachAppender() {
        providerLogger = (Logger) LoggerFactory.getLogger(VaultCredentialKeyProvider.class);
        appender = new ListAppender<>();
        appender.start();
        providerLogger.addAppender(appender);
    }

    @AfterEach
    public void detachAppender() {
        providerLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    public void migrationKeysEmitWarnAuditWithKeyId() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("vault-mat"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        p.setMigrationKeys(Collections.singletonList("legacyKey:legacy-pass-1"));
        assertDoesNotThrow(p::init);

        assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("legacyKey")),
                "每次启动必须对残余 keyId 输出 WARN 审计, got: " + appender.list);
    }

    @Test
    public void noWarnWhenMigrationListEmpty() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("vault-mat"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertDoesNotThrow(p::init);
        assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.WARN),
                "无残余 key 时不得输出 WARN, got: " + appender.list);
    }

    // ==================== 材料读取 happy path + 迁移残余语义 ====================

    @Test
    public void happyPathMaterialsRoundTripWithCipherSemantics() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("stub-material"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
        p.init();

        assertEquals("keyA", p.getActiveKeyId(), "active key 缺省取映射首项");
        assertTrue(p.getKeyIds().contains("keyA"));
        assertTrue(p.getKeyIds().contains("keyB"));

        // 与一期 CredentialCipher 语义一致：getKey 返回 AESTextCipher（v1: 密文），
        // 同材料直接构造的 AESTextCipher 可互解（逐字等价证明）
        ITextCipher cipher = p.getKey("keyB");
        String v1 = cipher.encrypt("round-trip");
        assertTrue(v1.startsWith("v1:"));
        assertEquals("round-trip", cipher.decrypt(v1));
        assertEquals("round-trip",
                new io.nop.commons.crypto.impl.AESTextCipher().encKey("stub-material").decrypt(v1));
    }

    @Test
    public void migrationKeysJoinDecryptMapButNotActiveCandidates() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("vault-mat"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
        p.setActiveKeyId("keyB");
        p.setMigrationKeys(Arrays.asList("legacy1:legacy-pass-1", "legacy2:legacy-pass-2"));
        p.init();

        // 残余 key 只并入解密 keyMap（keyIds 可见、getKey 可解），active 仍为 vault key
        assertEquals("keyB", p.getActiveKeyId());
        assertTrue(p.getKeyIds().containsAll(Arrays.asList("keyA", "keyB", "legacy1", "legacy2")));
        assertEquals("v", p.getKey("legacy1").decrypt(p.getKey("legacy1").encrypt("v")));
        assertEquals("v", p.getKey("legacy2").decrypt(p.getKey("legacy2").encrypt("v")));
    }

    @Test
    public void runtimeGetKeyFailsClosedForUnknownKeyId() {
        VaultCredentialKeyProvider p = happyPathProvider();
        p.init();
        assertThrows(NopException.class, () -> p.getKey("not-registered"));
    }

    // ==================== D3-01：active-key-id 配置归一（回退 + 冲突 fail-closed） ====================

    /**
     * D3-01（A1-audit successor，2026-08-17）：vault 专用 {@code nop.credential.vault.active-key-id}
     * 未设时回退共享 {@code nop.credential.active-key-id}——local→vault 迁移期共享配置先行
     * 调整时 vault 不再静默沿用映射首项（消除静默改变 active key 的迁移陷阱）。
     */
    @Test
    public void activeKeyFallsBackToSharedConfigWhenVaultUnset() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
        p.setSharedActiveKeyId("keyB"); // 仅设共享配置
        p.init();
        assertEquals("keyB", p.getActiveKeyId(),
                "unset vault active-key-id must fall back to shared nop.credential.active-key-id (D3-01)");
    }

    /**
     * D3-01：两处同设且一致 → 放行（等值不构成冲突）。
     */
    @Test
    public void sameVaultAndSharedActiveKeyAllowed() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
        p.setActiveKeyId("keyA");
        p.setSharedActiveKeyId("keyA");
        p.init();
        assertEquals("keyA", p.getActiveKeyId());
    }

    /**
     * D3-01：两处同设且不一致 → 启动失败（fail-closed，防迁移期静默改变 active key）。
     */
    @Test
    public void initThrowsWhenVaultAndSharedActiveKeyConflict() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Arrays.asList("keyA:secret/data/cred/a", "keyB:secret/data/cred/b"));
        p.setActiveKeyId("keyA");
        p.setSharedActiveKeyId("keyB");
        assertEquals("nop.err.credential.vault.active-key-id-conflict", initError(p));
    }

    /**
     * D3-01 回退目标仍受既有校验约束：共享配置回退值必须命中 Vault 密钥映射
     * （不得绕过 unknown-active-key 检查指向迁移残余/未知 key）。
     */
    @Test
    public void sharedFallbackStillValidatedAgainstVaultKeys() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        p.setSharedActiveKeyId("not-in-vault");
        assertEquals("nop.err.credential.vault.unknown-active-key", initError(p));
    }

    // ==================== D3-05：启动期材料读取请求级超时 ====================

    /**
     * D3-05（A1-audit successor，2026-08-17）：fetchMaterial 的 HTTP 请求携带请求级超时
     * （缺省 10s）——stub 捕获请求断言 {@code timeout > 0}；配置覆盖与非法回退同样验证。
     */
    @Test
    public void materialFetchCarriesRequestLevelTimeout() {
        java.util.concurrent.atomic.AtomicLong capturedTimeout = new java.util.concurrent.atomic.AtomicLong(-1);
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(req -> {
            capturedTimeout.set(req.getTimeout());
            return new StubResponse(200, kvV2Body("m"));
        }), Collections.singletonList("keyA:secret/data/cred/a"));
        p.init();

        assertTrue(capturedTimeout.get() > 0, "fetch request must carry a positive timeout (D3-05), got: "
                + capturedTimeout.get());
        assertEquals(VaultCredentialKeyProvider.DEFAULT_REQUEST_TIMEOUT_MS, capturedTimeout.get(),
                "unset config must fall back to the 10s default (no infinite blocking)");

        // 配置覆盖生效
        java.util.concurrent.atomic.AtomicLong overridden = new java.util.concurrent.atomic.AtomicLong(-1);
        VaultCredentialKeyProvider p2 = newProvider(new StubHttpClient(req -> {
            overridden.set(req.getTimeout());
            return new StubResponse(200, kvV2Body("m"));
        }), Collections.singletonList("keyA:secret/data/cred/a"));
        p2.setRequestTimeout(2500L);
        p2.init();
        assertEquals(2500L, overridden.get(), "configured request-timeout must be applied (D3-05)");

        // 非法取值（<=0 / 显式置空）回退缺省，不再无限阻塞
        VaultCredentialKeyProvider p3 = newProvider(new StubHttpClient(req -> {
            overridden.set(req.getTimeout());
            return new StubResponse(200, kvV2Body("m"));
        }), Collections.singletonList("keyA:secret/data/cred/a"));
        p3.setRequestTimeout(0L);
        p3.init();
        assertEquals(VaultCredentialKeyProvider.DEFAULT_REQUEST_TIMEOUT_MS, overridden.get(),
                "explicitly-invalid timeout (<=0) must fall back to default, not block forever");
    }

    // ==================== D5-06：passphrase 纯空白收紧（材料 + 迁移残余） ====================

    /**
     * D5-06：Vault 返回材料为纯空白（空格/制表符）→ 材料非法 fail-closed
     * （原实现仅拦截空串；空白材料形同弱密钥）。
     */
    @Test
    public void initThrowsOnWhitespaceOnlyMaterial() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, "{\"data\":{\"data\":{\"passphrase\":\"   \\t \"}}}")),
                Collections.singletonList("keyA:secret/data/cred/a"));
        assertEquals("nop.err.credential.vault.material-invalid", initError(p));
    }

    /**
     * D5-06：迁移残余条目 passphrase 纯空白 → 条目非法 fail-closed（与 master-keys 同口径）。
     */
    @Test
    public void initThrowsForWhitespaceOnlyMigrationKeyPassphrase() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("m"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        p.setMigrationKeys(Collections.singletonList("legacyKey:   "));
        assertEquals("nop.err.credential.vault.migration-key-invalid", initError(p));
    }

    /**
     * D5-06 边界：含空格的非空白 passphrase（材料与残余条目）保持合法。
     */
    @Test
    public void whitespaceContainingPassphraseRemainsLegal() {
        VaultCredentialKeyProvider p = newProvider(new StubHttpClient(
                req -> new StubResponse(200, kvV2Body("vault mat with spaces"))),
                Collections.singletonList("keyA:secret/data/cred/a"));
        p.setMigrationKeys(Collections.singletonList("legacyKey:legacy pass with spaces"));
        assertDoesNotThrow(p::init);
        assertEquals("v", p.getKey("legacyKey").decrypt(p.getKey("legacyKey").encrypt("v")),
                "non-blank passphrase with spaces must still work (material + migration key)");
    }
}
