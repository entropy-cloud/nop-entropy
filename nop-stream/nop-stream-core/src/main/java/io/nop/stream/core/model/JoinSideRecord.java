/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import java.io.Serializable;

/**
 * WI13: one side-tagged record entering the equi-join (the DSL builder tags each
 * join input before the union so the single keyed join operator can tell the two
 * sides apart). Carries the pre-computed equi-key (the side's key expressions
 * evaluated once, in the per-side tag map — the join operator itself evaluates
 * nothing) plus the payload.
 *
 * <p>The {@code matched} flag is a per-buffer-entry bookkeeping bit: it flips to
 * {@code true} when a cross-side pair involving this record has been emitted, so
 * the watermark pass only completes records that never matched (double emission
 * of a matched record's outer completion would violate the single-emit contract).
 * The event timestamp is NOT stored here — it rides the enclosing StreamRecord
 * envelope, mirroring the engine's timestamp propagation.
 *
 * <p>Serializable and in the {@code io.nop.stream.} package prefix so tagged
 * records survive the transport layer's class-name whitelist.
 */
public class JoinSideRecord<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean left;
    private final Object equiKey;
    private final T payload;
    private boolean matched;

    public JoinSideRecord(boolean left, Object equiKey, T payload) {
        this.left = left;
        this.equiKey = equiKey;
        this.payload = payload;
    }

    /** true for the left join input, false for the right. */
    public boolean isLeft() {
        return left;
    }

    /** the side-specific key expressions' composite evaluation (routing + grouping key). */
    public Object getEquiKey() {
        return equiKey;
    }

    public T getPayload() {
        return payload;
    }

    /** whether some cross-side pair involving this record has already been emitted. */
    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    @Override
    public String toString() {
        return (left ? "L|" : "R|") + equiKey + "|" + payload;
    }
}
