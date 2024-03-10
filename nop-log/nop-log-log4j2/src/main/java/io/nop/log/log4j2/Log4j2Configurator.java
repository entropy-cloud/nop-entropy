/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.log.log4j2;

import io.nop.api.core.util.LogLevel;
import io.nop.log.core.ILoggerConfigurator;
import io.nop.log.core.LogConstants;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Log4j2Configurator implements ILoggerConfigurator {
    static final Logger LOG = LoggerFactory.getLogger(Log4j2Configurator.class);

    @Override
    public LogLevel getLogLevel(String loggerName) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();

        if (loggerName == null || loggerName.length() <= 0)
            loggerName = LogConstants.ROOT_LOGGER_NAME;

        org.slf4j.Logger slfLogger = loggerFactory.getLogger(loggerName);

        return getLoggerLevel(slfLogger);
    }

    @Override
    public void changeLogLevel(String loggerName, LogLevel logLevel) {
        if (getLogLevel(loggerName) == logLevel)
            return;

        LoggerContext loggerContext = getLoggerContext();

        if (loggerName == null || loggerName.length() <= 0)
            loggerName = LogConstants.ROOT_LOGGER_NAME;

        LOG.info("nop.log.change-log-level:loggerName={},logLevel={}", loggerName, logLevel);
        
        Level level = toLog4jLevel(logLevel);
        LoggerConfig logger = loggerContext.getConfiguration().getLoggerConfig(loggerName);
        if (logger == null) {
            if (loggerName.equalsIgnoreCase(LogConstants.ROOT_LOGGER_NAME)) {
                logger = loggerContext.getConfiguration().getRootLogger();
            }
        }
        if (logger != null) {
            logger.setLevel(level); //NOSONAR
        } else {
            loggerContext.getConfiguration().addLogger(loggerName,
                    new NopLoggerConfig(loggerName, level, true));
        }

        loggerContext.updateLoggers();
    }

    static Level toLog4jLevel(LogLevel logLevel) {
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
        if (level.isMoreSpecificThan(Level.OFF))
            return LogLevel.OFF;
        if (level.isMoreSpecificThan(Level.ERROR))
            return LogLevel.ERROR;
        if (level.isMoreSpecificThan(Level.WARN))
            return LogLevel.WARN;
        if (level.isMoreSpecificThan(Level.INFO))
            return LogLevel.INFO;
        if (level.isMoreSpecificThan(Level.DEBUG))
            return LogLevel.DEBUG;
        return LogLevel.TRACE;
    }

    static LoggerContext getLoggerContext() {
        return (LoggerContext) LogManager.getContext(false);
    }

    static class NopLoggerConfig extends LoggerConfig {
        public NopLoggerConfig(String name, Level level, boolean additive) {
            super(name, level, additive);
        }
    }
}
