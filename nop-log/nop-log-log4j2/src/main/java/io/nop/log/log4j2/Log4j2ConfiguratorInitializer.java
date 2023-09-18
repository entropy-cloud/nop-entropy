package io.nop.log.log4j2;

import io.nop.core.initialize.ICoreInitializer;
import io.nop.log.core.LoggerConfigurator;

import static io.nop.core.CoreConstants.INITIALIZER_PRIORITY_INTERNAL;

public class Log4j2ConfiguratorInitializer implements ICoreInitializer {
    static Log4j2Configurator INSTANCE = new Log4j2Configurator();

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
