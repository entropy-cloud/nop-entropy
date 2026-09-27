/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

/**
 * Whether a restored state is exposed under the public state interface
 * (e.g. {@code AggregatingState}) or under the internal appending contract
 * (e.g. {@code InternalAggregatingState}). Only the instantiated/registered
 * state class differs; the persisted payload and restore procedure are identical.
 */
public enum RestoredStateFlavor {
    /**
     * Restored under the public state interface (e.g. {@code AggregatingState}).
     */
    PUBLIC,
    /**
     * Restored under the internal state contract (e.g. {@code InternalAggregatingState}).
     */
    INTERNAL
}
