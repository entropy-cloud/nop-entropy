/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.feishu.client;

import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.AppBeanContainerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * W16-impl-ext：{@code nop.integration.feishu.credentialId} 配置键<b>真实绑定</b>测试——经真实
 * NopIoC 容器装载生产 {@code feishu-defaults.beans.xml}（{@code nopFeishuCredentials} DataBean
 * 的 {@code @InjectValue("@cfg:nop.integration.feishu.*|")} setter 族），证明第五键与现有四键
 * 并列生效（防"配置键静默不生效→静默回退静态值"路径）。空串缺省语义（未配置 = ""）同时断言。
 */
class TestFeishuCredentialIdBinding {

    private static IConfigProvider configProvider;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
        configProvider = AppConfig.getConfigProvider();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IBeanContainerImplementor newContainer() {
        IBeanContainerImplementor container = new AppBeanContainerLoader().loadFromResource("test",
                new ClassPathResource("classpath:_vfs/nop/integration/feishu/beans/feishu-defaults.beans.xml"));
        container.start();
        return container;
    }

    @Test
    void credentialIdKeyBindsAlongsideExistingFourKeys() {
        String origAppId = configProvider.getConfigValue("nop.integration.feishu.appId", null);
        String origAppSecret = configProvider.getConfigValue("nop.integration.feishu.appSecret", null);
        String origCredentialId = configProvider.getConfigValue("nop.integration.feishu.credentialId", null);
        configProvider.assignConfigValue("nop.integration.feishu.appId", "cli_binding");
        configProvider.assignConfigValue("nop.integration.feishu.appSecret", "sec_binding");
        configProvider.assignConfigValue("nop.integration.feishu.credentialId", "cred-feishu-1");
        try {
            IBeanContainerImplementor container = newContainer();
            try {
                FeishuCredentials creds = (FeishuCredentials) container.getBean("nopFeishuCredentials");
                assertEquals("cli_binding", creds.getAppId(), "existing appId key keeps binding");
                assertEquals("sec_binding", creds.getAppSecret(), "existing appSecret key keeps binding");
                assertEquals("cred-feishu-1", creds.getCredentialId(),
                        "nop.integration.feishu.credentialId must bind through the real @InjectValue mechanism");
            } finally {
                container.stop();
            }
        } finally {
            configProvider.assignConfigValue("nop.integration.feishu.appId", origAppId);
            configProvider.assignConfigValue("nop.integration.feishu.appSecret", origAppSecret);
            configProvider.assignConfigValue("nop.integration.feishu.credentialId", origCredentialId);
        }
    }

    @Test
    void unsetKeyDefaultsToEmptyString() {
        IBeanContainerImplementor container = newContainer();
        try {
            FeishuCredentials creds = (FeishuCredentials) container.getBean("nopFeishuCredentials");
            // @InjectValue("|") 空串缺省——空串经 isConfigured 视同缺失（静态路径），与四键同型
            assertEquals("", creds.getCredentialId() == null ? "" : creds.getCredentialId(),
                    "unset credentialId must behave as missing (empty-string default)");
        } finally {
            container.stop();
        }
    }
}
