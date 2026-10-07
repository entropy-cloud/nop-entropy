/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.flow.builder.functions.XplFunctionSupport;

import java.util.Collections;

/**
 * WI8 Phase 2 (plan 2332): the execution operator behind an XDSL
 * {@code <sideOutput tag condition>} node. Copy semantics: every input record passes
 * through to the main output unchanged, and records matching the declared condition are
 * ADDITIONALLY emitted into the tag channel via {@code output.collect(outputTag, record)}
 * — the runtime output protocol routes them to the tag edge(s) declared with
 * {@code <edge from="<sideOutput node>" ... outputTag="<tag>">}.
 *
 * <p>Condition binding: the expr is evaluated per record with {@code event} bound to the
 * record value (the same binding as {@code <keyBy keyExpr>}); the result is converted
 * truthy-style (null/0/empty = false). A sideOutput node without a condition copies
 * EVERY record into the tag channel (pure tap) — that is the declared semantics of
 * omitting the attribute, not a silent fallback.
 */
public class SideOutputTapOperator<T> extends AbstractStreamOperator<T>
        implements OneInputStreamOperator<T, T> {

    private static final long serialVersionUID = 1L;

    private final OutputTag<T> outputTag;

    /**
     * Nullable: {@code null} = unconditional copy-all tap.
     */
    private final IEvalAction condition;

    public SideOutputTapOperator(OutputTag<T> outputTag, IEvalAction condition) {
        if (outputTag == null) {
            throw new IllegalArgumentException("outputTag must not be null");
        }
        this.outputTag = outputTag;
        this.condition = condition;
    }

    /**
     * Stateless tap: the tag and the compiled condition expression are immutable and
     * shareable across subtasks — a fresh instance per subtask preserves the
     * serialization-safe contract (same pattern as {@code StreamMap}).
     */
    @Override
    public SideOutputTapOperator<T> copyForSubtask() {
        return new SideOutputTapOperator<>(outputTag, condition);
    }

    @Override
    public void processElement(StreamRecord<T> element) throws Exception {
        if (condition == null || matches(element.getValue())) {
            // Tag-channel copy first: the tagged emission observes the record value
            // before any downstream mutation of a mutable value.
            output.collect(outputTag,
                    new StreamRecord<>(element.getValue(), element.getTimestamp()));
        }
        // Main output passes through unchanged — the tap never filters the main path.
        output.collect(new StreamRecord<>(element.getValue(), element.getTimestamp()));
    }

    private boolean matches(T value) {
        Object result = condition.invoke(XplFunctionSupport.newCallScope()
                .newChildScope(Collections.singletonMap("event", value)));
        return XplFunctionSupport.toBoolean(result);
    }
}
