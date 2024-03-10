/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.log.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.nop.api.core.util.LogLevel;
import io.nop.log.core.ILoggerConfigurator;
import io.nop.log.core.LogConstants;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

public class LogbackConfigurator implements ILoggerConfigurator {
    static final org.slf4j.Logger LOG = LoggerFactory.getLogger(LogbackConfigurator.class);

    @Override
    public LogLevel getLogLevel(String loggerName) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();

        if (loggerName == null || loggerName.length() <= 0)
            loggerName = "root";

        org.slf4j.Logger slfLogger = loggerFactory.getLogger(loggerName);

        return getLoggerLevel(slfLogger);
    }

    @Override
    public void changeLogLevel(String loggerName, LogLevel logLevel) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();

        if (loggerName == null || loggerName.length() <= 0)
            loggerName = LogConstants.ROOT_LOGGER_NAME;

        if (loggerFactory instanceof LoggerContext) {
            LoggerContext context = (LoggerContext) loggerFactory;
            LOG.info("nop.log.change-log-level:loggerName={},logLevel={}", loggerName, logLevel);
            Logger logger = context.getLogger(loggerName);
            logger.setLevel(toLogbackLevel(logLevel)); //NOSONAR
        }
    }

    static Level toLogbackLevel(LogLevel logLevel) {
        switch (logLevel) {
            case OFF:
                return Level.OFF;
            case ERROR:
                return Level.ERROR;
            case WARN:
                return Level.WARN;
            case INFO:
                return Level.INFO;
            case DEBUG:
                return Level.DEBUG;
            case TRACE:
                return Level.TRACE;
        }
        return Level.INFO;
    }

    static LogLevel getLoggerLevel(org.slf4j.Logger logger) {
        if (logger.isTraceEnabled())
            return LogLevel.TRACE;
        if (logger.isDebugEnabled())
            return LogLevel.DEBUG;
        if (logger.isInfoEnabled())
            return LogLevel.INFO;
        if (logger.isWarnEnabled())
            return LogLevel.WARN;
        if (logger.isErrorEnabled())
            return LogLevel.ERROR;
        return LogLevel.OFF;
    }

    static LogLevel toLogLevel(Level level) {
        if (level.isGreaterOrEqual(Level.OFF))
            return LogLevel.OFF;
        if (level.isGreaterOrEqual(Level.ERROR))
            return LogLevel.ERROR;
        if (level.isGreaterOrEqual(Level.WARN))
            return LogLevel.WARN;
        if (level.isGreaterOrEqual(Level.INFO))
            return LogLevel.INFO;
        if (level.isGreaterOrEqual(Level.DEBUG))
            return LogLevel.DEBUG;
        return LogLevel.TRACE;
    }
}
