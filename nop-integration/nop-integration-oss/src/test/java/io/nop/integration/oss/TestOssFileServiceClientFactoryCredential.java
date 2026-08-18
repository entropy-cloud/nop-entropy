/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.oss;

import com.amazonaws.auth.AWSCredentials;
import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_FIELD_REQUIRED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_RESOLVE_FAILED;
import static io.nop.integration.api.IntegrationErrors.ERR_CREDENTIAL_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W16-impl-ext：OssFileServiceClientFactory 构造期 credentialId 解析测试——优先级链三态
 * （空=OssConfig 静态值直用/有效=整组覆盖/失效=fail-closed 中止 bean 初始化不回退）、
 * {@code BasicAWSCredentials} 构造点接线（解析值真实到达 seam）、registerUsage 幂等 +
 * catch-all WARN。
 */
public class TestOssFileServiceClientFactoryCredential {

    static final String CONSUMER_REF = "integration:oss-s3";

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

    /** 测试用工厂子类：替换 AWS 凭证构造点，捕获解析后的凭证组。 */
    static class TestableFactory extends OssFileServiceClientFactory {
        String lastAccessKey;
        String lastSecretKey;

        @Override
        protected AWSCredentials createAwsCredentials(String accessKey, String secretKey) {
            this.lastAccessKey = accessKey;
            this.lastSecretKey = secretKey;
            return super.createAwsCredentials(accessKey, secretKey);
        }
    }

    private static OssConfig config(String accessKey, String secretKey) {
        OssConfig config = new OssConfig();
        config.setEndpoint("http://127.0.0.1:19000");
        config.setRegion("us-east-1");
        config.setAccessKey(accessKey);
        config.setSecretKey(secretKey);
        return config;
    }

    private static CredentialData ossCredential(Object accessKey, Object secretKey) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("accessKey", accessKey);
        fields.put("secretKey", secretKey);
        return new CredentialData("oss-s3", fields);
    }

    // ==================== 优先级链三态：init() 驱动（构造期一次消费） ====================

    @Test
    public void blankCredentialIdUsesStaticValuesWithoutProviderCall() {
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED); // 若被调用即失败
        factory.setCredentialProvider(provider);

        factory.init();
        assertEquals("static-ak", factory.lastAccessKey);
        assertEquals("static-sk", factory.lastSecretKey);
        assertEquals(0, provider.getCredentialCalls);

        cfg.setCredentialId("   "); // 空白视同缺失
        TestableFactory factory2 = new TestableFactory();
        factory2.setOssConfig(cfg);
        factory2.setCredentialProvider(provider);
        factory2.init();
        assertEquals("static-ak", factory2.lastAccessKey);
        assertEquals(0, provider.getCredentialCalls);
    }

    @Test
    public void validCredentialOverridesStaticValuesAsGroupAtConstructTime() {
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.data = ossCredential("cred-ak", "cred-sk");
        factory.setCredentialProvider(provider);
        cfg.setCredentialId("cred-1");

        factory.init();
        assertEquals("cred-ak", factory.lastAccessKey);
        assertEquals("cred-sk", factory.lastSecretKey);
        assertEquals(1, provider.getCredentialCalls, "construct-period single resolution");
    }

    @Test
    public void failedResolutionFailsClosedWithoutFallback() {
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.error = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED,
                new RuntimeException("provider says credential deleted"));
        factory.setCredentialProvider(provider);
        cfg.setCredentialId("cred-broken");

        NopException ex = assertThrows(NopException.class, factory::init);
        assertEquals(ERR_CREDENTIAL_RESOLVE_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void providerMissingWithCredentialIdFailsClosedAsDeployInconsistency() {
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        cfg.setCredentialId("cred-1"); // provider 未装配（null）

        NopException ex = assertThrows(NopException.class, factory::init);
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void wrongCredentialTypeRejected() {
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.data = new CredentialData("feishu-app", Map.of("appId", "x"));
        factory.setCredentialProvider(provider);
        cfg.setCredentialId("cred-wrong-type");

        NopException ex = assertThrows(NopException.class, factory::init);
        assertEquals(ERR_CREDENTIAL_TYPE_MISMATCH.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void requiredFieldMissingFailsClosed() {
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.data = ossCredential("  ", "cred-sk"); // accessKey 必填空白
        factory.setCredentialProvider(provider);
        cfg.setCredentialId("cred-1");

        NopException ex = assertThrows(NopException.class, factory::init);
        assertEquals(ERR_CREDENTIAL_FIELD_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("accessKey", ex.getParam("fieldName"));
    }

    // ==================== registerUsage：幂等登记 + catch-all WARN ====================

    @Test
    public void registerUsageOnInitWhenCredentialConfigured() {
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.data = ossCredential("cred-ak", "cred-sk");
        factory.setCredentialProvider(provider);
        cfg.setCredentialId("cred-1");

        factory.init();
        factory.init(); // 重复初始化幂等（无异常；provider 侧幂等由唯一约束承载）
        assertEquals(2, provider.registerUsageCalls);
        assertEquals("cred-1", provider.lastRegisterCredentialId);
        assertEquals(CONSUMER_REF, provider.lastRegisterConsumerRef);
    }

    @Test
    public void registerUsageSkippedWhenCredentialIdBlank() {
        TestableFactory factory = new TestableFactory();
        factory.setOssConfig(config("static-ak", "static-sk"));
        FakeProvider provider = new FakeProvider();
        factory.setCredentialProvider(provider);
        factory.init(); // credentialId 空 → 不登记
        assertEquals(0, provider.registerUsageCalls);
    }

    @Test
    public void providerMissingWithCredentialIdFailsClosedBeforeRegistration() {
        // provider null + credentialId 非空：构造期解析 fail-closed（先于登记——无登记发生）
        TestableFactory factory2 = new TestableFactory();
        OssConfig cfg2 = config("static-ak", "static-sk");
        factory2.setOssConfig(cfg2);
        cfg2.setCredentialId("cred-1");
        NopException ex = assertThrows(NopException.class, factory2::init);
        assertEquals(ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void registerUsageProviderValidationErrorDoesNotBlockInit() {
        // D6-03 后 registerUsage 前置校验凭证存在且未软删（fail-closed 抛错）——
        // 配错 credentialId 的 bean 初始化必须存活（catch-all WARN，不阻断启动）
        TestableFactory factory = new TestableFactory();
        OssConfig cfg = config("static-ak", "static-sk");
        factory.setOssConfig(cfg);
        FakeProvider provider = new FakeProvider();
        provider.data = ossCredential("cred-ak", "cred-sk"); // 解析面正常（本用例仅验证登记异常路径）
        provider.registerUsageError = new NopException(ERR_CREDENTIAL_RESOLVE_FAILED);
        factory.setCredentialProvider(provider);
        cfg.setCredentialId("cred-misconfigured");

        factory.init(); // 不抛错（登记 catch-all WARN；安全边界 = 解析 fail-closed）
        assertEquals(1, provider.registerUsageCalls);
    }
}
