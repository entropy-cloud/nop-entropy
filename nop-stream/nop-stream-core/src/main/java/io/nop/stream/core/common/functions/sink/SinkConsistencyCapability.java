/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.sink;

public enum SinkConsistencyCapability {

    TWO_PHASE_COMMIT,
    STAGED_ATOMIC_COMMIT,
    OUTBOX_EPOCH_LOG,
    IDEMPOTENT,
    UPSERT_BY_KEY,
    AT_LEAST_ONCE,
    BEST_EFFORT
}
