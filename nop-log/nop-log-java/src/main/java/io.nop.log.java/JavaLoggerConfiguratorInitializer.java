/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.log.java;

import io.nop.core.initialize.ICoreInitializer;
import io.nop.log.core.LoggerConfigurator;

import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_INTERNAL;

public class JavaLoggerConfiguratorInitializer implements ICoreInitializer {
    static JavaLoggerConfigurator INSTANCE = new JavaLoggerConfigurator();

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
