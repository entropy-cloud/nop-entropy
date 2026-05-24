/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

/**
 * Provides topic naming convention for cross-TaskManager data exchange.
 *
 * <p>Format: {@code nop-stream.{jobId}.{edgeId}.{sourceSubtask}.{targetSubtask}}
 */
public final class StreamTopicNaming {

    public static final String TOPIC_PREFIX = "nop-stream";

    private StreamTopicNaming() {
    }

    /**
     * Builds the topic name for a point-to-point data channel between two subtasks.
     *
     * @param jobId          the job identifier
     * @param edgeId         the edge identifier
     * @param sourceSubtask  the source subtask index
     * @param targetSubtask  the target subtask index
     * @return the topic name
     */
    public static String buildTopic(String jobId, String edgeId,
                                    int sourceSubtask, int targetSubtask) {
        return TOPIC_PREFIX + "." + jobId + "." + edgeId
                + "." + sourceSubtask + "." + targetSubtask;
    }
}
