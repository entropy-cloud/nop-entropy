package io.nop.metadata.service.connection;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl：MetaDataSourceConnectionProcessor 单点凭证解析测试（plain JUnit5 直构，沿
 * TestMetaDataSourceConnectionSecurity 形态）。兼容矩阵三行（存量明文/迁移/过渡并存）+
 * fail-closed 分支 + 空串视同缺失 + testConnect 结构化失败不泄漏细节 + AR-02 不吞。
 *
 * <p>成功路径经真实 H2 建连（h2 为模块 test 依赖；{@code jdbc:h2:mem:} 无 host——AR-02 主机
 * 白名单不适用）。过渡并存行的"凭证侧生效、JSON 明文忽略"以<b>预建 DB 用户</b>判别：先用
 * sa 建库，JSON 明文里的错误用户连接会失败、凭证侧 sa 成功——连接结果即可区分取值来源。
 */
public class TestMetaDataSourceConnectionProcessorCredential {

    private final MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();

    /** 手写 fake provider：返回预设数据/抛错，记录 getCredential 调用。 */
    static class FakeProvider implements ICredentialProvider {
        CredentialData data;
        NopException error;
        int getCredentialCalls;

        @Override
        public CredentialData getCredential(String credentialId) {
            getCredentialCalls++;
            if (error != null) {
                throw error;
            }
            return data;
        }

        @Override
        public Object getCredentialData(String credentialId, String field) {
            return getCredential(credentialId).getField(field);
        }

        @Override
        public TestResult testCredential(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public MaskedCredential mask(String credentialId) {
            throw new UnsupportedOperationException("not used in test");
        }

        @Override
        public void registerUsage(String credentialId, String consumerRef) {
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }
    }

    private static CredentialData jdbcCredential(String username, String password) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", username);
        fields.put("password", password);
        return new CredentialData("jdbc-datasource", fields);
    }

    private static void preCreateH2(String dbName) throws Exception {
        // DB_CLOSE_DELAY=-1：连接关闭后库保活——后续以错误用户连入将失败（H2 拒绝未知用户）
        DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "").close();
    }

    // ==================== 兼容矩阵行 1：存量明文行（无键）零回归 ====================

    @Test
    public void legacyPlaintextRowWithoutCredentialIdUsesStaticPath() {
        // provider 未装配（null）——存量行无 credentialId 键，必须完全不受影响
        Map<String, Object> result = service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:legacy_row\",\"username\":\"sa\",\"password\":\"\"}");
        assertEquals(Boolean.TRUE, result.get("connected"), "legacy plaintext row must keep working: " + result);
    }

    @Test
    public void blankCredentialIdTreatedAsMissing() {
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ErrorCode.define("test.err.must-not-be-called", "must not be called"));
        service.setCredentialProvider(provider);

        // 空串/空白 credentialId → 静态路径（provider 不得被调用）
        Map<String, Object> result = service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:blank_cid\",\"username\":\"sa\",\"password\":\"\","
                        + "\"credentialId\":\"  \"}");
        assertEquals(Boolean.TRUE, result.get("connected"));
        assertEquals(0, provider.getCredentialCalls);
    }

    // ==================== 兼容矩阵行 2：迁移行（明文已清）整组取凭证 ====================

    @Test
    public void migratedRowResolvesCredentialEndToEnd() throws Exception {
        preCreateH2("migrated_row");
        FakeProvider provider = new FakeProvider();
        provider.data = jdbcCredential("sa", "");
        service.setCredentialProvider(provider);

        Map<String, Object> result = service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:migrated_row;DB_CLOSE_DELAY=-1\",\"credentialId\":\"cred-1\"}");
        assertEquals(Boolean.TRUE, result.get("connected"),
                "migrated row must connect via credential-resolved username/password: " + result);
        assertEquals(1, provider.getCredentialCalls);
    }

    // ==================== 兼容矩阵行 3：过渡并存行——凭证侧生效，JSON 明文忽略 ====================

    @Test
    public void transitionRowCredentialWinsOverJsonPlaintext() throws Exception {
        // 预建库（sa）——若代码回退 JSON 明文（wronguser），H2 拒绝未知用户 → connected=false
        preCreateH2("transition_row");
        FakeProvider provider = new FakeProvider();
        provider.data = jdbcCredential("sa", "");
        service.setCredentialProvider(provider);

        Map<String, Object> result = service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:transition_row;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"wronguser\",\"password\":\"wrongpass\",\"credentialId\":\"cred-1\"}");
        assertEquals(Boolean.TRUE, result.get("connected"),
                "credential side must win over JSON plaintext (no fallback): " + result);
        assertEquals(1, provider.getCredentialCalls);
    }

    @Test
    public void transitionRowFailClosedWithoutFallbackToPlaintext() throws Exception {
        // 预建库 + JSON 明文是 wronguser：若代码回退 JSON 明文会得到 "Connection failed"
        // （H2 拒绝未知用户的连接失败路径）；fail-closed 语义要求固定描述
        // "credential resolution failed"——以 error 描述判别无回退。
        preCreateH2("transition_fail");
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ErrorCode.define("test.err.credential.not-found", "not found"));
        service.setCredentialProvider(provider);

        Map<String, Object> result = service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:transition_fail;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"wronguser\",\"password\":\"wrongpass\",\"credentialId\":\"cred-x\"}");
        assertEquals(Boolean.FALSE, result.get("connected"));
        assertEquals("credential resolution failed", result.get("error"),
                "must fail closed with fixed description, not fall back to JSON plaintext "
                        + "(which would yield 'Connection failed')");
    }

    // ==================== fail-closed 分支（withConnection 路径异常上抛） ====================

    @Test
    public void providerFailureFailsClosedViaWithConnection() {
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ErrorCode.define("test.err.credential.deleted", "deleted"));
        service.setCredentialProvider(provider);

        NopException ex = assertThrows(NopException.class, () -> service.withConnection("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:fc_1\",\"credentialId\":\"cred-dead\"}",
                (conn, meta) -> {
                }));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void providerMissingWithCredentialIdFailsClosedAsDeployInconsistency() {
        // provider null + credentialId 非空 → 部署不一致（异常上抛，不回退明文）
        NopException ex = assertThrows(NopException.class, () -> service.withConnection("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:fc_2\",\"username\":\"sa\",\"password\":\"\","
                        + "\"credentialId\":\"cred-1\"}",
                (conn, meta) -> {
                }));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_PROVIDER_NOT_AVAILABLE.getErrorCode(),
                ex.getErrorCode());
    }

    @Test
    public void typeMismatchFailsClosed() {
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("tencent-sms", Map.of("appId", 1, "appKey", "k"));
        service.setCredentialProvider(provider);

        NopException ex = assertThrows(NopException.class, () -> service.withConnection("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:fc_3\",\"credentialId\":\"cred-wrong-type\"}",
                (conn, meta) -> {
                }));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void requiredUsernameMissingFailsClosed() {
        FakeProvider provider = new FakeProvider();
        provider.data = jdbcCredential("  ", ""); // username 空白 → 必填校验拒绝
        service.setCredentialProvider(provider);

        NopException ex = assertThrows(NopException.class, () -> service.withConnection("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:fc_4\",\"credentialId\":\"cred-no-user\"}",
                (conn, meta) -> {
                }));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex.getErrorCode());
    }

    // ==================== testConnect 结构化失败（仅凭证解析失败映射，不泄漏细节） ====================

    @Test
    public void testConnectMapsCredentialFailureToStructuredResultWithoutDetailLeak() {
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ErrorCode.define("test.err.credential.not-found",
                "SECRET-DETAIL must not leak: credential cred-leak deleted at 2026-01-01"));
        service.setCredentialProvider(provider);

        Map<String, Object> result = service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:tc_struct\",\"credentialId\":\"cred-leak\"}");
        assertEquals(Boolean.FALSE, result.get("connected"));
        assertEquals("credential resolution failed", result.get("error"),
                "fixed description only — no plaintext/ciphertext detail leak");
        assertFalse(String.valueOf(result.get("error")).contains("SECRET-DETAIL"));
    }

    @Test
    public void testConnectStillThrowsAr02Violations() {
        // AR-02 异常不在 try 域内：不吞成结构化 false（防校验失败被静默降级）
        FakeProvider provider = new FakeProvider();
        provider.data = jdbcCredential("sa", "");
        service.setCredentialProvider(provider);

        NopException ex = assertThrows(NopException.class, () -> service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:mysql://host:3306/db?allowLoadLocalInfile=true\","
                        + "\"credentialId\":\"cred-1\"}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testConnectStillThrowsConfigInvalid() {
        // config-invalid（必填 jdbcUrl 缺失）同样维持上抛
        FakeProvider provider = new FakeProvider();
        provider.data = jdbcCredential("sa", "");
        service.setCredentialProvider(provider);

        NopException ex = assertThrows(NopException.class, () -> service.testConnect("jdbc",
                "{\"credentialId\":\"cred-1\"}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testConnectThrowsProviderNotAvailableInsteadOfStructuredFalse() {
        // 部署不一致（provider null）非 provider 侧异常——不映射结构化 false，上抛显式暴露
        NopException ex = assertThrows(NopException.class, () -> service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:tc_na\",\"credentialId\":\"cred-1\"}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CREDENTIAL_PROVIDER_NOT_AVAILABLE.getErrorCode(),
                ex.getErrorCode());
    }
}
