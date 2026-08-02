/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.api.core.annotations.core.Internal;

/**
 * Stage 39 Phase 2: per-nodeId / per-jobId control-plane RPC topic naming convention.
 *
 * <p>Wiring topology (Phase 2 Decision 1):
 * <ul>
 *   <li>Task side: each TaskManager node exposes {@link IStreamTaskRpcService} on
 *       {@code nop-stream.rpc.task.{nodeId}}.</li>
 *   <li>Coordinator side: the coordinator exposes {@link IStreamCoordinatorRpcService}
 *       on {@code nop-stream.rpc.coordinator.{jobId}}.</li>
 * </ul>
 * Topic addressing (not socket ports) keeps the control plane on the same
 * {@code IMessageService} backend as the Stage 40 data plane.
 */
@Internal
public final class StreamControlRpcTopics {

    private static final String TASK_TOPIC_PREFIX = "nop-stream.rpc.task.";
    private static final String COORDINATOR_TOPIC_PREFIX = "nop-stream.rpc.coordinator.";

    private StreamControlRpcTopics() {
    }

    /** Topic on which a TaskManager node receives control calls. */
    public static String taskTopic(String nodeId) {
        return TASK_TOPIC_PREFIX + nodeId;
    }

    /** Topic on which the coordinator receives task→coordinator uplink calls. */
    public static String coordinatorTopic(String jobId) {
        return COORDINATOR_TOPIC_PREFIX + jobId;
    }
}
