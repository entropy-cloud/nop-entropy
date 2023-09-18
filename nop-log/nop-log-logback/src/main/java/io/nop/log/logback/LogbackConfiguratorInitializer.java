/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.log.logback;

import io.nop.core.initialize.ICoreInitializer;
import io.nop.log.core.LoggerConfigurator;

import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_INTERNAL;

public class LogbackConfiguratorInitializer implements ICoreInitializer {
    static LogbackConfigurator INSTANCE = new LogbackConfigurator();

    @Override
    public int order() {
        return INITIALIZER_PRIORITY_INTERNAL;
    }

    @Override
    public void initialize() {
        LoggerConfigurator.registerInstance(INSTANCE);
    }

    @Override
    public void destroy() {
        if (LoggerConfigurator.tryGetInstance() == INSTANCE)
            LoggerConfigurator.registerInstance(null);
    }
}