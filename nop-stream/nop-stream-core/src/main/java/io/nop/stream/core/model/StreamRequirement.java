/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

public enum StreamRequirement {

    STATEFUL_PROCESSING,
    KEYED_STATE_PROCESSING,

    SPLITTABLE_SOURCE,
    BUNDLE_FINALIZATION,
    STABLE_INPUT,
    TIME_SORTED_INPUT,

    STRICT_EXACTLY_ONCE,
    EFFECTIVELY_ONCE,
    AT_LEAST_ONCE,

    DURABLE_CHECKPOINT,
    INCREMENTAL_CHECKPOINT,
    UNALIGNED_CHECKPOINT,

    TWO_PHASE_COMMIT_SINK,
    STAGED_ATOMIC_COMMIT_SINK,
    OUTBOX_EPOCH_LOG_SINK,

    DISTRIBUTED_EXECUTION,
    REMOTE_STATE_SERVICE,
    RESCALABLE_STATE,

    PROTOCOL_LEVEL_METRICS,
    SAMPLED_DATA_TRACING
}
