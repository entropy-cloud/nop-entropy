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

        // 每个测试类开始前清空模型缓存。xlib/xdef 编译时会按当时的配置求值
        // feature:on 等条件；缓存若跨类复用，会复用前序类配置下编译的产物
        //（典型症状：TenantSupport 等条件节点被错误保留导致 ORM 模型
        // useTenant=true）。
        // 注：此处不能用 isDirty() 门控——下方 processTestConfig 会无条件 assign
        // 两个内置测试配置（container startMode / graphql ignoreMillis），dirty
        // 恒为 true，门控从未生效过；每类清缓存是配置条件编译产物的安全网。
        ResourceComponentManager.instance().clearAllCache();

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

        // 恢复配置环境：本测试类通过 @NopTestProperty / setTestConfig / assignConfigValue
        // 注入的值不应泄漏到同 fork 中后续不经过本 Extension 的代码
        // （plain JUnit 测试、静态初始化器等）。下一个受管类 beforeAll 的 reset()
        // 仍然保留，作为兜底。
        AppConfig.getConfigProvider().reset();
    }
}