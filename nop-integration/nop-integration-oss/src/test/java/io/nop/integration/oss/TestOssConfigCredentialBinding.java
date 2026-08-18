/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.oss;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W16-impl-ext：{@code nop.integration.oss.credentialId} 配置键<b>真实绑定</b>测试——经真实
 * NopIoC 容器 + 生产 {@code ioc:config-prefix="nop.integration.oss"} 绑定机制证明键生效
 * （{@code OssConfig.credentialId} 经 {@code @ConfigField(name="credentialId")} 钉住 camelCase
 * 键名——缺省归一为 kebab-case {@code credential-id}，与设计 §4.1 结论 1 键名不符，故显式指定；
 * 实测结论回写设计）。同时覆盖：构造期消费真实发生（工厂 init 经 provider 解析 + 登记）、
 * enabled 门控零介入（真实 oss-defaults.beans.xml 装载，enabled 缺省 → 门控 bean 不创建、
 * provider 零调用）。
 */
public class TestOssConfigCredentialBinding {

    private static IConfigProvider configProvider;

    /** 静态计数 fake provider（容器 bean）：绑定/门控断言的探针。 */
    public static class FakeProvider implements ICredentialProvider {
        public static int getCredentialCalls;
        public static int registerUsageCalls;
        public static String lastRegisterCredentialId;
        public static String lastRegisterConsumerRef;

        @Override
        public CredentialData getCredential(String credentialId) {
            getCredentialCalls++;
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("accessKey", "cred-ak");
            fields.put("secretKey", "cred-sk");
            return new CredentialData("oss-s3", fields);
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
        }

        @Override
        public void unregisterUsage(String credentialId, String consumerRef) {
        }
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
        configProvider = AppConfig.getConfigProvider();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainerImplementor newContainer(String resourcePath) {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                new ClassPathResource(resourcePath));
        container.start();
        return container;
    }

    @Test
    public void credentialIdKeyBindsViaConfigPrefixAndFactoryConsumesAtConstructTime() {
        String origCredentialId = configProvider.getConfigValue("nop.integration.oss.credentialId", null);
        String origEndpoint = configProvider.getConfigValue("nop.integration.oss.endpoint", null);
        String origRegion = configProvider.getConfigValue("nop.integration.oss.region", null);
        configProvider.assignConfigValue("nop.integration.oss.credentialId", "cred-oss-1");
        configProvider.assignConfigValue("nop.integration.oss.endpoint", "http://127.0.0.1:19001");
        configProvider.assignConfigValue("nop.integration.oss.region", "us-east-1");
        FakeProvider.getCredentialCalls = 0;
        FakeProvider.registerUsageCalls = 0;
        try {
            IBeanContainerImplementor container = newContainer(
                    "classpath:_vfs/test/oss/test-oss-credential-binding.beans.xml");
            try {
                // 配置键真实绑定：config-prefix 归一化后 credentialId 属性收到值（非 kebab 键）
                OssConfig config = (OssConfig) container.getBean("nopOssConfig");
                assertEquals("cred-oss-1", config.getCredentialId(),
                        "nop.integration.oss.credentialId must bind through the real config-prefix mechanism");

                // 构造期消费真实发生：工厂 init() 经 provider 解析整组 + 幂等登记
                OssFileServiceClientFactory factory =
                        (OssFileServiceClientFactory) container.getBean("nopOssFileServiceClientFactory");
                assertTrue(factory != null, "factory bean must initialize");
                assertEquals(1, FakeProvider.getCredentialCalls,
                        "construct-period single resolution must run through the provider");
                assertEquals(1, FakeProvider.registerUsageCalls);
                assertEquals("cred-oss-1", FakeProvider.lastRegisterCredentialId);
                assertEquals("integration:oss-s3", FakeProvider.lastRegisterConsumerRef);
            } finally {
                container.stop();
            }
        } finally {
            configProvider.assignConfigValue("nop.integration.oss.credentialId", origCredentialId);
            configProvider.assignConfigValue("nop.integration.oss.endpoint", origEndpoint);
            configProvider.assignConfigValue("nop.integration.oss.region", origRegion);
        }
    }

    @Test
    public void blankCredentialIdLeavesStaticPathUntouched() {
        String origCredentialId = configProvider.getConfigValue("nop.integration.oss.credentialId", null);
        configProvider.assignConfigValue("nop.integration.oss.credentialId", "");
        String origAccessKey = configProvider.getConfigValue("nop.integration.oss.access-key", null);
        String origSecretKey = configProvider.getConfigValue("nop.integration.oss.secret-key", null);
        String origEndpoint = configProvider.getConfigValue("nop.integration.oss.endpoint", null);
        String origRegion = configProvider.getConfigValue("nop.integration.oss.region", null);
        configProvider.assignConfigValue("nop.integration.oss.access-key", "static-ak");
        configProvider.assignConfigValue("nop.integration.oss.secret-key", "static-sk");
        configProvider.assignConfigValue("nop.integration.oss.endpoint", "http://127.0.0.1:19002");
        configProvider.assignConfigValue("nop.integration.oss.region", "us-east-1");
        FakeProvider.getCredentialCalls = 0;
        try {
            IBeanContainerImplementor container = newContainer(
                    "classpath:_vfs/test/oss/test-oss-credential-binding.beans.xml");
            try {
                OssConfig config = (OssConfig) container.getBean("nopOssConfig");
                assertTrue(config.getCredentialId() == null || config.getCredentialId().isBlank(),
                        "empty/null credentialId both bind as missing (static path)");
                assertEquals("static-ak", config.getAccessKey(), "existing kebab-case key keeps binding");
                container.getBean("nopOssFileServiceClientFactory");
                assertEquals(0, FakeProvider.getCredentialCalls,
                        "blank credentialId must keep the static path (zero provider calls)");
            } finally {
                container.stop();
            }
        } finally {
            configProvider.assignConfigValue("nop.integration.oss.credentialId", origCredentialId);
            configProvider.assignConfigValue("nop.integration.oss.access-key", origAccessKey);
            configProvider.assignConfigValue("nop.integration.oss.secret-key", origSecretKey);
            configProvider.assignConfigValue("nop.integration.oss.endpoint", origEndpoint);
            configProvider.assignConfigValue("nop.integration.oss.region", origRegion);
        }
    }

    @Test
    public void enabledGateSkipsFactoryBeanWithZeroCredentialIntervention() {
        // 真实 oss-defaults.beans.xml 装载：enabled 缺省（enableIfMissing=false）→ 门控 bean 不创建，
        // credentialId 即使配置也无登记无解析（enabled 门控下零介入）
        String origCredentialId = configProvider.getConfigValue("nop.integration.oss.credentialId", null);
        configProvider.assignConfigValue("nop.integration.oss.credentialId", "cred-oss-1");
        FakeProvider.getCredentialCalls = 0;
        FakeProvider.registerUsageCalls = 0;
        try {
            IBeanContainerImplementor container = newContainer(
                    "classpath:_vfs/nop/integration/beans/oss-defaults.beans.xml");
            try {
                // 未启用（enabled 未配置，enableIfMissing=false）→ 工厂 bean 缺席
                assertFalse(container.containsBean("nopOssFileServiceClientFactory"),
                        "gated factory bean must be absent when nop.integration.oss.enabled is unset");
                assertEquals(0, FakeProvider.getCredentialCalls,
                        "disabled gate must mean zero credential intervention");
                assertEquals(0, FakeProvider.registerUsageCalls);
            } finally {
                container.stop();
            }
        } finally {
            configProvider.assignConfigValue("nop.integration.oss.credentialId", origCredentialId);
        }
    }

    @Test
    public void enabledGateWithMissingCredentialKeepsStaticPath() {
        // 真实 oss-defaults.beans.xml + enabled=true：credentialId 未配置 → 静态 accessKey/secretKey
        // 路径（现状行为零回归——工厂 bean 创建成功，无 provider 干预）
        Boolean origEnabled = configProvider.getConfigValue("nop.integration.oss.enabled", Boolean.FALSE);
        String origCredentialId = configProvider.getConfigValue("nop.integration.oss.credentialId", null);
        String origAccessKey = configProvider.getConfigValue("nop.integration.oss.access-key", null);
        String origSecretKey = configProvider.getConfigValue("nop.integration.oss.secret-key", null);
        String origEndpoint = configProvider.getConfigValue("nop.integration.oss.endpoint", null);
        String origRegion = configProvider.getConfigValue("nop.integration.oss.region", null);
        configProvider.assignConfigValue("nop.integration.oss.enabled", true);
        configProvider.assignConfigValue("nop.integration.oss.credentialId", "");
        configProvider.assignConfigValue("nop.integration.oss.access-key", "static-ak");
        configProvider.assignConfigValue("nop.integration.oss.secret-key", "static-sk");
        configProvider.assignConfigValue("nop.integration.oss.endpoint", "http://127.0.0.1:19003");
        configProvider.assignConfigValue("nop.integration.oss.region", "us-east-1");
        FakeProvider.getCredentialCalls = 0;
        FakeProvider.registerUsageCalls = 0;
        try {
            IBeanContainerImplementor container = newContainer(
                    "classpath:_vfs/nop/integration/beans/oss-defaults.beans.xml");
            try {
                assertTrue(container.containsBean("nopOssFileServiceClientFactory"),
                        "enabled=true must create the factory bean");
                assertEquals(0, FakeProvider.getCredentialCalls,
                        "blank credentialId must keep the static path even when enabled");
                assertEquals(0, FakeProvider.registerUsageCalls);
            } finally {
                container.stop();
            }
        } finally {
            configProvider.assignConfigValue("nop.integration.oss.enabled",
                    origEnabled != null ? origEnabled : Boolean.FALSE);
            configProvider.assignConfigValue("nop.integration.oss.credentialId", origCredentialId);
            configProvider.assignConfigValue("nop.integration.oss.access-key", origAccessKey);
            configProvider.assignConfigValue("nop.integration.oss.secret-key", origSecretKey);
            configProvider.assignConfigValue("nop.integration.oss.endpoint", origEndpoint);
            configProvider.assignConfigValue("nop.integration.oss.region", origRegion);
        }
    }

    @Test
    public void enabledGateWithBrokenCredentialFailsClosedAtBeanInit() {
        // enabled=true + credentialId 配错（provider 未装配该凭证）→ fail-closed：工厂 bean 初始化
        // 抛 NopException（不静默回退静态值）。本测试容器不装配 provider（真实 defaults 文件无
        // provider bean）→ 部署不一致错误。
        Boolean origEnabled = configProvider.getConfigValue("nop.integration.oss.enabled", Boolean.FALSE);
        String origCredentialId = configProvider.getConfigValue("nop.integration.oss.credentialId", null);
        configProvider.assignConfigValue("nop.integration.oss.enabled", true);
        configProvider.assignConfigValue("nop.integration.oss.credentialId", "cred-oss-broken");
        try {
            NopException ex = assertThrows(NopException.class, () -> {
                IBeanContainerImplementor container = newContainer(
                        "classpath:_vfs/nop/integration/beans/oss-defaults.beans.xml");
                try {
                    container.stop();
                } catch (Exception ignore) {
                    // best-effort
                }
            }, "container start must fail closed when credentialId is configured without a provider");
            assertTrue(ex.getMessage() != null);
        } finally {
            configProvider.assignConfigValue("nop.integration.oss.enabled",
                    origEnabled != null ? origEnabled : Boolean.FALSE);
            configProvider.assignConfigValue("nop.integration.oss.credentialId", origCredentialId);
        }
    }
}
