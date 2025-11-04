/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogHelper {
    static final Logger LOG = LoggerFactory.getLogger(LogHelper.class);

    public static void trace(String message, Object[] args) {
        LOG.trace(message, args);
    }

    public static void debug(String message, Object[] args) {
        LOG.debug(message, args);
    }

    public static void info(String message, Object[] args) {
        LOG.info(message, args);
    }

    public static void warn(String message, Object[] args) {
        LOG.warn(message, args);
    }

    public static void error(String message, Object[] args) {
        LOG.error(message, args);
    }
}
