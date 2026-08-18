/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sftp;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static io.nop.integration.sftp.SftpErrors.ERR_SFTP_CONNECT_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl-ext：SFTP 家族 credentialId 接线测试——优先级链三态（空=SftpConfig 静态值直用/
 * 有效=整组覆盖/失效=fail-closed 且 NopException 独立失败<b>不被 ERR_SFTP_CONNECT_FAIL 包装</b>）、
 * 公钥无口令场景（password/passphrase 均空合法）、jsch 消费点接线（解析值真实到达
 * addIdentity/getSession/setPassword 消费面）、registerUsage 幂等 + catch-all WARN。
 *
 * <p>live 形态：构造函数内即 {@code connect()}（super 构造期实例字段未初始化）——捕获经
 * static 录制器（类载入期可用）。
 */
public class TestSftpClientCredential {

    static final String CONSUMER_REF = "integration:sftp-ssh";

    /** 手写 fake provider：返回预设数据/抛错，记录 getCredential 与 registerUsage 调用。 */
    static class FakeProvider implements ICredentialProvider {
        CredentialData data;
        NopException error;
        NopException registerUsageError;
        int getCredentialCalls;
        int registerUsageCalls;
        String lastRegisterCredentialId;
        String lastRegisterConsumerRef;

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
            registerUsageCalls++;
            lastRegisterCredentialId = credentialId;
            lastRegisterConsumerRef = consumerRef;
            if (registerUsageError != null) {
                throw registerUsageError;
            }
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }
    }

    /** openConnection 捕获录制器（static——super 构造期可用）。 */
    static final List<SftpClient.ResolvedCredential> RECORDED_GROUPS = new ArrayList<>();

    /** jsch 捕获录制器（static——super 构造期可用）。 */
    static RecordingJsch LAST_JSCH;

    /** 记录型 jsch：捕获 addIdentity/getSession 真实消费参数（getSession 返回真实离线 Session）。 */
    static class RecordingJsch extends JSch {
        String identityKeyPath;
        String identityPassphrase;
        String sessionUsername;
        String sessionHost;
        int sessionPort;

        @Override
        public void addIdentity(String prvkey, String passphrase) {
            this.identityKeyPath = prvkey;
            this.identityPassphrase = passphrase;
        }

        @Override
        public Session getSession(String username, String host, int port) {
            this.sessionUsername = username;
            this.sessionHost = host;
            this.sessionPort = port;
            try {
                return super.getSession(username, host, port);
            } catch (com.jcraft.jsch.JSchException e) {
                throw new IllegalStateException("unexpected: real jsch getSession is offline-safe", e);
            }
        }
    }

    static class RecordingSftpClient extends SftpClient {
        RecordingSftpClient(SftpConfig config, ICredentialProvider provider) {
            super(config, provider);
        }

        @Override
        protected void openConnection(SftpClient.ResolvedCredential credential) {
            RECORDED_GROUPS.add(credential); // 不真正连接（离线捕获消费面输入）
        }
    }

    /** 真实 jsch 消费路径：RecordingJsch + 真实 openConnection（session.connect 离线拒绝→包装码）。 */
    static class JschLevelSftpClient extends SftpClient {
        JschLevelSftpClient(SftpConfig config, ICredentialProvider provider) {
            super(config, provider);
        }

        @Override
        protected JSch newJsch() {
            LAST_JSCH = new RecordingJsch();
            return LAST_JSCH;
        }
    }

    @BeforeEach
    void reset() {
        RECORDED_GROUPS.clear();
        LAST_JSCH = null;
    }

    private static SftpConfig config(String host, int port, String username, String password,
                                     String keyPath, String passphrase) {
        SftpConfig config = new SftpConfig();
        config.setHost(host);
        config.setPort(port);
        config.setUsername(username);
        config.setPassword(password);
        config.setKeyPath(keyPath);
        config.setPassphrase(passphrase);
        return config;
    }

    private static CredentialData sftpCredential(Object username, Object password, Object passphrase) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (username != null) {
            fields.put("username", username);
        }
        if (password != null) {
            fields.put("password", password);
        }
        if (passphrase != null) {
            fields.put("passphrase", passphrase);
        }
        return new CredentialData("sftp-ssh", fields);
    }

    // ==================== 优先级链三态：构造驱动（逐次操作期时序） ====================

    @Test
    public void blankCredentialIdUsesStaticValuesWithoutProviderCall() {
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED); // 若被调用即失败
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId(null);

        new RecordingSftpClient(cfg, provider);
        assertEquals(1, RECORDED_GROUPS.size());
        assertEquals("static-user", RECORDED_GROUPS.get(0).username);
        assertEquals("static-pass", RECORDED_GROUPS.get(0).password);
        assertEquals(0, provider.getCredentialCalls);

        RECORDED_GROUPS.clear();
        cfg.setCredentialId("   "); // 空白视同缺失
        new RecordingSftpClient(cfg, provider);
        assertEquals("static-user", RECORDED_GROUPS.get(0).username);
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    public void validCredentialOverridesStaticValuesAsGroup() {
        FakeProvider provider = new FakeProvider();
        provider.data = sftpCredential("cred-user", "cred-pass", "cred-pp");
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", "/static/key", "static-pp");
        cfg.setCredentialId("cred-1");

        new RecordingSftpClient(cfg, provider);
        assertEquals(1, RECORDED_GROUPS.size());
        assertEquals("cred-user", RECORDED_GROUPS.get(0).username);
        assertEquals("cred-pass", RECORDED_GROUPS.get(0).password);
        assertEquals("cred-pp", RECORDED_GROUPS.get(0).passphrase);
        assertEquals(1, provider.getCredentialCalls);
    }

    @Test
    public void publicKeyWithoutPassphraseScenarioStaysLegal() {
        // 公钥无口令场景（password/passphrase 均空）合法语义保持——sftp-ssh 无跨字段约束
        FakeProvider provider = new FakeProvider();
        provider.data = sftpCredential("cred-user", null, null);
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", "/static/key", "static-pp");
        cfg.setCredentialId("cred-1");

        new RecordingSftpClient(cfg, provider);
        assertEquals("cred-user", RECORDED_GROUPS.get(0).username);
        assertNull(RECORDED_GROUPS.get(0).password);
        assertNull(RECORDED_GROUPS.get(0).passphrase);
    }

    @Test
    public void failedResolutionFailsClosedWithoutFallbackAndKeepsErrorCode() {
        // 落点约束：解析失败必须独立失败（IntegrationErrors 码），不被 ERR_SFTP_CONNECT_FAIL 包装
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED,
                new RuntimeException("provider says credential deleted"));
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class,
                () -> new RecordingSftpClient(cfg, provider));
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode(),
                "resolution failure must surface its own IntegrationErrors code, not the connect-wrap code");
        assertEquals(0, RECORDED_GROUPS.size(), "no connection attempt after fail-closed resolution");
    }

    @Test
    public void providerMissingWithCredentialIdFailsClosedAsDeployInconsistency() {
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId("cred-1"); // provider 未装配（null，直构路径）

        NopException ex = assertThrows(NopException.class, () -> new RecordingSftpClient(cfg, null));
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void wrongCredentialTypeRejected() {
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("smtp-email", Map.of("username", "u"));
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId("cred-wrong-type");

        NopException ex = assertThrows(NopException.class, () -> new RecordingSftpClient(cfg, provider));
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    // ==================== 接线：解析值真实到达 jsch 消费点 ====================

    @Test
    public void resolvedGroupReachesOpenConnectionConsumptionFace() {
        // openConnection 消费面（setPassword 的输入值经此面断言——Session 不可覆写，
        // password 消费由本组值 + jsch 级用例共同钉死）
        FakeProvider provider = new FakeProvider();
        provider.data = sftpCredential("cred-user", "cred-pass", null);
        SftpConfig cfg = config("127.0.0.1", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId("cred-1");

        new RecordingSftpClient(cfg, provider);
        assertEquals(1, RECORDED_GROUPS.size());
        assertEquals("cred-user", RECORDED_GROUPS.get(0).username);
        assertEquals("cred-pass", RECORDED_GROUPS.get(0).password);
        assertNull(RECORDED_GROUPS.get(0).passphrase);
    }

    @Test
    public void resolvedValuesReachRealJschIdentityAndSessionConsumptionPoints() {
        // 真实 jsch 级消费：addIdentity 收到 keyPath + 解析 passphrase、getSession 收到解析 username
        // （host/port 为拓扑）。session.connect() 对 127.0.0.1:1 离线拒绝 → 包装 ERR_SFTP_CONNECT_FAIL
        // （传输层错误码，区别于解析错误码——两者分界即本用例的落点验证）。
        FakeProvider provider = new FakeProvider();
        provider.data = sftpCredential("cred-user", "cred-pass", "cred-pp");
        SftpConfig cfg = config("127.0.0.1", 1, "static-user", "static-pass", "/id_rsa", "static-pp");
        cfg.setCredentialId("cred-1");

        NopException wrap = assertThrows(NopException.class,
                () -> new JschLevelSftpClient(cfg, provider));
        assertEquals(ERR_SFTP_CONNECT_FAIL.getErrorCode(), wrap.getErrorCode(),
                "offline connect refusal surfaces as the transport-level wrap code");

        // 消费点断言（发生在 connect 失败之前，值已记录）
        assertEquals("/id_rsa", LAST_JSCH.identityKeyPath);
        assertEquals("cred-pp", LAST_JSCH.identityPassphrase, "addIdentity must consume the resolved passphrase");
        assertEquals("cred-user", LAST_JSCH.sessionUsername, "getSession must consume the resolved username");
        assertEquals("127.0.0.1", LAST_JSCH.sessionHost);
        assertEquals(1, LAST_JSCH.sessionPort);
    }

    // ==================== registerUsage：幂等登记 + catch-all WARN ====================

    @Test
    public void registerUsageOnInitWhenCredentialConfigured() {
        SftpClientFactory factory = new SftpClientFactory();
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId("cred-1");
        factory.setConfig(cfg);
        FakeProvider provider = new FakeProvider();
        factory.setCredentialProvider(provider);

        factory.init();
        factory.init(); // 重复初始化幂等（无异常；provider 侧幂等由唯一约束承载）
        assertEquals(2, provider.registerUsageCalls);
        assertEquals("cred-1", provider.lastRegisterCredentialId);
        assertEquals(CONSUMER_REF, provider.lastRegisterConsumerRef);
    }

    @Test
    public void registerUsageSkippedWhenCredentialIdBlank() {
        SftpClientFactory factory = new SftpClientFactory();
        factory.setConfig(config("h", 22, "static-user", "static-pass", null, null));
        FakeProvider provider = new FakeProvider();
        factory.setCredentialProvider(provider);
        factory.init(); // credentialId 空 → 不登记
        assertEquals(0, provider.registerUsageCalls);
    }

    @Test
    public void registerUsageProviderValidationErrorDoesNotBlockInit() {
        // D6-03 后 registerUsage 前置校验凭证存在且未软删（fail-closed 抛错）——
        // 配错 credentialId 的 bean 初始化必须存活（catch-all WARN，不阻断启动）
        SftpClientFactory factory = new SftpClientFactory();
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId("cred-misconfigured");
        factory.setConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.registerUsageError = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        factory.setCredentialProvider(provider);

        factory.init(); // 不抛错（catch-all WARN）
        assertEquals(1, provider.registerUsageCalls);
    }

    @Test
    public void factoryPassesProviderToNewClientForPerOperationResolution() {
        // 工厂 → client 的 provider 传递链：newClient() 构造的 client 在 credentialId 配置下
        // 走 provider 解析（逐次操作期时序）
        SftpClientFactory factory = new SftpClientFactory();
        SftpConfig cfg = config("h", 22, "static-user", "static-pass", null, null);
        cfg.setCredentialId("cred-1");
        factory.setConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        factory.setCredentialProvider(provider);

        NopException ex = assertThrows(NopException.class, factory::newClient,
                "newClient must resolve through the provider passed by the factory (fail-closed)");
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
        assertTrue(provider.getCredentialCalls >= 1);
    }
}
