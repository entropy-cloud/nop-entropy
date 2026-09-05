/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.health;

/**
 * Item 16 (P-REQ-7): logical job health state — seven states trimmed from the
 * Kafka Streams seven-state reference to nop-stream's continuous-stream +
 * global-recovery semantics (observability-design §3.6).
 *
 * <p>Semantic mapping: {@code RECOVERING} ≈ KS REBALANCING (topology being
 * rebuilt); {@code DEGRADED} ≈ KS PENDING_ERROR (still serving, but carrying
 * unresolved failure traces — restart count &gt; 0). KS NOT_RUNNING /
 * PENDING_SHUTDOWN have no corresponding lifecycle event in the
 * independent-process form and are deliberately NOT introduced (FINISHED
 * covers DRAIN/SUSPEND).
 */
public enum StreamJobHealth {
    CREATED,
    RUNNING,
    RECOVERING,
    DEGRADED,
    FAILED,
    CANCELED,
    FINISHED;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELED || this == FINISHED;
    }
}
