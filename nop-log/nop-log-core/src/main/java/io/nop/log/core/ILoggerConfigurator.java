/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.log.core;

import io.nop.api.core.util.LogLevel;

public interface ILoggerConfigurator {
    LogLevel getLogLevel(String loggerName);

    void changeLogLevel(String loggerName, LogLevel logLevel);

    default LogLevel getRootLogLevel() {
        return getLogLevel(LogConstants.ROOT_LOGGER_NAME);
    }

    default void changeRootLogLevel(LogLevel logLevel) {
        changeLogLevel(LogConstants.ROOT_LOGGER_NAME, logLevel);
    }
}