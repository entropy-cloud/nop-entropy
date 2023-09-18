package io.nop.log.java;

import io.nop.api.core.util.LogLevel;
import io.nop.log.core.ILoggerConfigurator;
import io.nop.log.core.LogConstants;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.logging.Level;

public class JavaLoggerConfigurator implements ILoggerConfigurator {
    static final Logger LOG = LoggerFactory.getLogger(JavaLoggerConfigurator.class);

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

        if (loggerName == null || loggerName.length() <= 0)
            loggerName = LogConstants.ROOT_LOGGER_NAME;

        LOG.info("nop.log.change-log-level:loggerName={},logLevel={}", loggerName, logLevel);

        if(LogConstants.ROOT_LOGGER_NAME.equalsIgnoreCase(loggerName))
            loggerName = "";

        Level level = toLog4jLevel(logLevel);
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(loggerName);
        if (logger != null) {
            logger.setLevel(level);
        }
    }

    static Level toLog4jLevel(LogLevel logLevel) {
        switch (logLevel) {
            case OFF:
                return Level.OFF;
            case ERROR:
                return Level.SEVERE;
            case WARN:
                return Level.WARNING;
            case INFO:
                return Level.INFO;
            case DEBUG:
                return Level.FINE;
            case TRACE:
                return Level.FINEST;
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
        if (level.intValue() >= Level.OFF.intValue())
            return LogLevel.OFF;
        if (level.intValue() >= Level.SEVERE.intValue())
            return LogLevel.ERROR;
        if (level.intValue() >= Level.WARNING.intValue())
            return LogLevel.WARN;
        if (level.intValue() >= Level.INFO.intValue())
            return LogLevel.INFO;
        if (level.intValue() >= Level.FINE.intValue())
            return LogLevel.DEBUG;
        return LogLevel.TRACE;
    }

}
