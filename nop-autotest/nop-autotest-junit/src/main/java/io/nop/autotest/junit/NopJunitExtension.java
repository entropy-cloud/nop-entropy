/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.junit;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static io.nop.core.unittest.BaseTestCase.setTestConfig;
import static io.nop.ioc.IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_MODE;

public class NopJunitExtension implements BeforeAllCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        BaseTestCase.resetAll();
        BaseTestCase.setTestRunning(true);
        processTestConfig(context);
        CoreInitialization.initialize();
    }

    void processTestConfig(ExtensionContext context) {
        // 单元测试总是采用lazy模式启动程序
        setTestConfig(CFG_IOC_APP_BEANS_CONTAINER_START_MODE, BeanContainerStartMode.ALL_LAZY.name());

        NopTestConfig config = context.getRequiredTestClass().getAnnotation(NopTestConfig.class);
        if (config != null) {
            new NopTestConfigProcessor().process(config);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        CoreInitialization.destroy();
        BaseTestCase.resetAll();
        BaseTestCase.setTestRunning(false);
    }
}