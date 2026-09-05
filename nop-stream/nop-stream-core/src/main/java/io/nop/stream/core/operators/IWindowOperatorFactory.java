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
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.Window;

public interface IWindowOperatorFactory extends java.io.Serializable {
    <IN, ACC, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createAggregateOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            AggregateFunction<IN, ACC, OUT> aggregateFunction,
            Class<ACC> accumulatorType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass);

    /**
     * F-05 (plan 1326-2 Phase 1): aggregate with an explicit IN element type. The
     * evictor branch of the aggregate path stores raw IN elements in a
     * {@code ListStateDescriptor} — the element type must be inferable or bean
     * elements break on the RocksDB backend / Memory-JSON restore. Default
     * delegates to the legacy overload (elementType treated as unknown) so
     * existing implementations stay source-compatible.
     */
    default <IN, ACC, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createAggregateOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            AggregateFunction<IN, ACC, OUT> aggregateFunction,
            Class<ACC> accumulatorType,
            Class<IN> elementType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass) {
        return createAggregateOperator(windowAssigner, trigger, evictor, allowedLateness,
                aggregateFunction, accumulatorType, keySelector, keyClass);
    }

    <IN, K, W extends Window>
    OneInputStreamOperator<IN, IN> createReduceOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            ReduceFunction<IN> reduceFunction,
            Class<IN> valueType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass);

    <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createApplyOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            WindowFunction<IN, OUT, K, W> windowFunction,
            Class<IN> elementType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass);

    <IN, OUT, K, W extends Window>
    OneInputStreamOperator<IN, OUT> createProcessOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            Evictor<? super IN, W> evictor,
            long allowedLateness,
            ProcessWindowFunction<IN, OUT, K, W> processWindowFunction,
            Class<IN> elementType,
            KeySelector<IN, K> keySelector,
            Class<K> keyClass);
}
