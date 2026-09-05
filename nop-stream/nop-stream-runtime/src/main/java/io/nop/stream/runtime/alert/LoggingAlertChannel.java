/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item 16 (P-REQ-12): structured single-line log channel (built-in, default
 * enabled). The {@code nop-stream alert:} prefix is the grep anchor used by
 * log pipelines and by the multi-JVM e2e assertions.
 */
public class LoggingAlertChannel implements IAlertChannel {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingAlertChannel.class);

    public static final String LOG_PREFIX = "nop-stream alert:";

    @Override
    public String getName() {
        return "logging";
    }

    @Override
    public void send(AlertEvent event) {
        switch (event.getSeverity()) {
            case ERROR:
                LOG.error("{} job={} severity={} type={} message={}", LOG_PREFIX,
                        event.getJobId(), event.getSeverity(), event.getEventType(), event.getMessage());
                break;
            case WARN:
                LOG.warn("{} job={} severity={} type={} message={}", LOG_PREFIX,
                        event.getJobId(), event.getSeverity(), event.getEventType(), event.getMessage());
                break;
            default:
                LOG.info("{} job={} severity={} type={} message={}", LOG_PREFIX,
                        event.getJobId(), event.getSeverity(), event.getEventType(), event.getMessage());
        }
    }
}
