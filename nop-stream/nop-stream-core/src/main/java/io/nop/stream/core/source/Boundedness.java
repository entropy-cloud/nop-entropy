/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

/**
 * Defines whether a source is bounded (finite input) or unbounded (continuous input).
 *
 * <p>Stage 49 FLIP-27 style Source contract. v1 reference source ({@code FileSource}) is
 * {@link #BOUNDED}; unbounded successor sources (e.g. Kafka partition-as-split) declare
 * {@link #CONTINUOUS_UNBOUNDED}.
 *
 * @see Source#getBoundedness()
 */
public enum Boundedness {

    /**
     * Finite input: the source will eventually emit a final marker and stop.
     * The job can terminate after all bounded sources finish.
     */
    BOUNDED,

    /**
     * Continuous input: the source never naturally finishes; the job runs until
     * explicitly cancelled or drained.
     */
    CONTINUOUS_UNBOUNDED
}
