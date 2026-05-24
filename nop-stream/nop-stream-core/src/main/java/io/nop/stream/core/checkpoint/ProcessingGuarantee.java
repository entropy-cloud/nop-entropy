/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

public enum ProcessingGuarantee {

    STRICT_EXACTLY_ONCE(true, true),
    AT_LEAST_ONCE(false, false),
    EFFECTIVELY_ONCE(false, true),
    BEST_EFFORT(false, false);

    private final boolean barrierAlignment;
    private final boolean requiresDurableCheckpoint;

    ProcessingGuarantee(boolean barrierAlignment, boolean requiresDurableCheckpoint) {
        this.barrierAlignment = barrierAlignment;
        this.requiresDurableCheckpoint = requiresDurableCheckpoint;
    }

    public boolean isBarrierAlignment() {
        return barrierAlignment;
    }

    public boolean requiresDurableCheckpoint() {
        return requiresDurableCheckpoint;
    }
}
