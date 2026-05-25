package io.nop.stream.core.operators;

import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.windows.Window;

public class ApplyAggregationFunction<IN, OUT, K, W extends Window>
        implements WindowAggregationFunction<IN, List<IN>, OUT, K, W> {

    private static final long serialVersionUID = 1L;

    private final WindowFunction<IN, OUT, K, W> windowFunction;

    public ApplyAggregationFunction(WindowFunction<IN, OUT, K, W> windowFunction) {
        this.windowFunction = windowFunction;
    }

    @Override
    public List<IN> createAccumulator() {
        return new ArrayList<>();
    }

    @Override
    public List<IN> add(IN value, List<IN> accumulator) {
        accumulator.add(value);
        return accumulator;
    }

    @Override
    public void emitResult(K key, W window, List<IN> accumulator, Collector<OUT> out) throws Exception {
        windowFunction.apply(key, window, accumulator, out);
    }
}
