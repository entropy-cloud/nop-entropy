/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

/**
 * The equi-join semantics declared by a {@code <joins>/<join>} registry entry (WI8d).
 * The runtime consumption of this enum belongs to WI13's buildJoin; this WI only
 * declares and validates it at build time.
 */
public enum JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL;

    /**
     * Whether the join type requires unmatched-record completion (left/right/full
     * outer semantics). Window join in the first delivery supports only INNER/LEFT —
     * FULL completion on a windowed join is a WI13 evaluation item.
     */
    public boolean isOuter() {
        return this == LEFT || this == RIGHT || this == FULL;
    }
}
