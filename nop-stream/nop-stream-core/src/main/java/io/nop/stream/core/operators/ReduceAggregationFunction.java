package io.nop.stream.core.operators;

import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.windows.Window;

public class ReduceAggregationFunction<T, K, W extends Window>
        implements WindowAggregationFunction<T, T, T, K, W> {

    private static final long serialVersionUID = 1L;

    private final ReduceFunction<T> reduceFunction;

    public ReduceAggregationFunction(ReduceFunction<T> reduceFunction) {
        this.reduceFunction = reduceFunction;
    }

    @Override
    public T createAccumulator() {
        return null;
    }

    @Override
    public T add(T value, T accumulator) throws Exception {
        if (accumulator == null) {
            return value;
        }
        return reduceFunction.reduce(accumulator, value);
    }

    @Override
    public void emitResult(K key, W window, T accumulator, Collector<T> out) {
        if (accumulator != null) {
            out.collect(accumulator);
        }
    }
}
