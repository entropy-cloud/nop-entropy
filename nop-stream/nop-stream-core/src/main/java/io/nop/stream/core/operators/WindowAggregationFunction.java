package io.nop.stream.core.operators;

import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.windows.Window;

import java.io.Serializable;

public interface WindowAggregationFunction<IN, ACC, OUT, KEY, W extends Window> extends Serializable {
    ACC createAccumulator();

    ACC add(IN value, ACC accumulator) throws Exception;

    void emitResult(KEY key, W window, ACC accumulator, Collector<OUT> out) throws Exception;
}
