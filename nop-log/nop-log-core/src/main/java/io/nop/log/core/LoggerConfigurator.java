/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.log.core;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.log.core.LogErrors.ERR_LOG_CONFIGURATOR_NOT_INITIALIZED;

@GlobalInstance
public class LoggerConfigurator {
    static ILoggerConfigurator s_instance;

    static final Logger LOG = LoggerFactory.getLogger(LoggerConfigurator.class);

    public static boolean isInitialized() {
        return s_instance != null;
    }

    public static ILoggerConfigurator tryGetInstance(){
        return s_instance;
    }

    public static ILoggerConfigurator instance() {
        if (s_instance == null)
            throw new NopException(ERR_LOG_CONFIGURATOR_NOT_INITIALIZED);
        return s_instance;
    }

    public static void registerInstance(ILoggerConfigurator configurator) {
        if (configurator != null && s_instance != null && configurator != s_instance)
            LOG.info("nop.err.log.configurator-already-registered:old={},new={}", s_instance, configurator);
        s_instance = configurator;
    }
}
