/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.junit;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperties;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static io.nop.autotest.core.AutoTestConstants.CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP;
import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_MODE;

public class NopJunitExtension implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        // 其他单元测试有可能改变了配置项，这里重置为静态缺省值
        AppConfig.getConfigProvider().reset();

        BaseTestCase.beginTest();
        processTestConfig(context);

        // 仅在前序测试类动态修改过配置（assignConfigValue / setTestConfig / @NopTestProperty）
        // 时清空模型缓存。xlib/xdef 编译时会按当时的配置求值 feature:on 等条件；
        // 若不清缓存，后续类会复用前序类污染过的编译产物（典型症状：TenantSupport
        // 等条件节点被错误保留导致 ORM 模型 useTenant=true）。
        // 没有配置修改时跳过，避免无谓清空所有缓存。
        if (AppConfig.getConfigProvider().isDirty()) {
            ResourceComponentManager.instance().clearAllCache();
        }

        CoreInitialization.initialize();
    }

    void processTestConfig(ExtensionContext context) {
        // 单元测试总是采用lazy模式启动程序
        setTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_MODE, BeanContainerStartMode.ALL_LAZY.name());

        // 为了更精确的匹配JSON数据，这里保留Timestamp的毫秒部分
        setTestConfig(CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP, false);

        NopTestConfig config = context.getRequiredTestClass().getAnnotation(NopTestConfig.class);
        if (config != null) {
            new NopTestConfigProcessor().process(config);
        }

        NopTestProperties props = context.getRequiredTestClass().getAnnotation(NopTestProperties.class);
        if (props != null) {
            for (NopTestProperty prop : props.value()) {
                setTestConfig(prop.name(), prop.value());
            }
        } else {
            NopTestProperty prop = context.getRequiredTestClass().getAnnotation(NopTestProperty.class);
            if (prop != null) {
                setTestConfig(prop.name(), prop.value());
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        CoreInitialization.destroy();
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
        BaseTestCase.endTest();
    }
}