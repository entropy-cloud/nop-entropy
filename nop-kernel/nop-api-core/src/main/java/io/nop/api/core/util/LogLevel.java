/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import org.slf4j.Logger;

/**
 * 与JVM的logLevel设置一致
 */
public enum LogLevel {
    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5),
    OFF(Integer.MAX_VALUE);
    final int level;

    LogLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    @StaticFactoryMethod
    public static LogLevel fromText(String text) {
        if (text == null || text.length() <= 0)
            return null;

        for (LogLevel level : values()) {
            if (level.name().equalsIgnoreCase(text))
                return level;
        }
        return null;
    }

    public static void log(Logger logger, LogLevel level, String message) {
        switch (level) {
            case TRACE: {
                logger.trace(message);
                break;
            }
            case DEBUG: {
                logger.debug(message);
                break;
            }
            case INFO: {
                logger.info(message);
                break;
            }
            case WARN: {
                logger.warn(message);
                break;
            }
            case ERROR: {
                logger.error(message);
                break;
            }
        }
    }

    public static void log(Logger logger, LogLevel level, String message, Object... args) {
        switch (level) {
            case TRACE: {
                logger.trace(message, args);
                break;
            }
            case DEBUG: {
                logger.debug(message, args);
                break;
            }
            case INFO: {
                logger.info(message, args);
                break;
            }
            case WARN: {
                logger.warn(message, args);
                break;
            }
            case ERROR: {
                logger.error(message, args);
                break;
            }
        }
    }
}