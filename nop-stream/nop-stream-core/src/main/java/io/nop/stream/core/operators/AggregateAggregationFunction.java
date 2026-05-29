package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.windows.Window;

public class AggregateAggregationFunction<IN, ACC, OUT, K, W extends Window>
        implements WindowAggregationFunction<IN, ACC, OUT, K, W> {

    private static final long serialVersionUID = 1L;

    private final AggregateFunction<IN, ACC, OUT> aggregateFunction;

    public AggregateAggregationFunction(AggregateFunction<IN, ACC, OUT> aggregateFunction) {
        this.aggregateFunction = aggregateFunction;
    }

    @Override
    public ACC createAccumulator() {
        return aggregateFunction.createAccumulator();
    }

    @Override
    public ACC add(IN value, ACC accumulator) {
        return aggregateFunction.add(value, accumulator);
    }

    @Override
    public void emitResult(K key, W window, ACC accumulator, Collector<OUT> out) {
        out.collect(aggregateFunction.getResult(accumulator));
    }

    @Override
    public ACC merge(ACC acc1, ACC acc2) throws Exception {
        return aggregateFunction.merge(acc1, acc2);
    }
}
