/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item 16 (P-REQ-2): built-in logging listener. Emits one stable-marker log
 * line per event so job lifecycle progress is observable in process logs
 * (including spawned multi-JVM cluster processes whose log files can be
 * asserted by e2e tests).
 */
public class LoggingJobEventListener implements StreamJobEventListener {

    public static final String LOG_MARKER = "nop-stream job event:";

    private static final Logger LOG = LoggerFactory.getLogger(LoggingJobEventListener.class);

    @Override
    public void onEvent(StreamJobEvent event) {
        LOG.info("{} type={} job={} checkpointId={} durationMs={} sizeBytes={} cause={}",
                LOG_MARKER, event.getType(), event.getJobId(), event.getCheckpointId(),
                event.getDurationMs(), event.getSizeBytes(), event.getCause());
    }
}
