/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.windowing.windows.Window;

/**
 * WI8 (plan 2420): single-contract window operator factory. The former
 * 17-method surface (4 abstract methods + 13 default shims grown by four
 * parameter-creep generations: elementType → lateDataOutputTag →
 * maxDelay/emitWindowEvents/windowEventsOutputTag → accumulationMode) is
 * replaced by the immutable {@link WindowSpec} value object — every
 * configuration axis travels in the spec; the operation function and (for
 * aggregate only) the accumulator type are the sole companion parameters, so
 * a new configuration axis can never fork another interface method or revive
 * a parameter-dropping default shim.
 *
 * <p>Replacement adjudication (2026-10-11, plan 2420): direct replacement
 * without a {@code @Deprecated(forRemoval=true)} bridge — the factory has
 * zero code consumers outside nop-stream (repo-wide grep), the roadmap M2
 * milestone requires the 17-method surface to disappear, and a deprecated
 * shim layer would preserve exactly the silent parameter-dropping failure
 * mode this contract removes.
 */
public interface IWindowOperatorFactory extends java.io.Serializable {

    <IN, ACC, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createAggregateOperator(
            WindowSpec<IN, K, W> spec,
            AggregateFunction<IN, ACC, OUT> aggregateFunction,
            Class<ACC> accumulatorType);

    <IN, K, W extends Window>
    OneInputStreamOperator<IN, IN> createReduceOperator(
            WindowSpec<IN, K, W> spec,
            ReduceFunction<IN> reduceFunction);

    <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createApplyOperator(
            WindowSpec<IN, K, W> spec,
            WindowFunction<IN, OUT, K, W> windowFunction);

    <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createProcessOperator(
            WindowSpec<IN, K, W> spec,
            ProcessWindowFunction<IN, OUT, K, W> processWindowFunction);
}
