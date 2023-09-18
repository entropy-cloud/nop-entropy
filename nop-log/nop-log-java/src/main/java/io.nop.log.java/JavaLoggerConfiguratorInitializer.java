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
