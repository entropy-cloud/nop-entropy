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
