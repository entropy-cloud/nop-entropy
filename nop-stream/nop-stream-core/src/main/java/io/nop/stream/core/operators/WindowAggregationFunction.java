package io.nop.stream.core.operators;

import java.io.Serializable;

import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.windows.Window;

/**
 * @deprecated Use {@link io.nop.stream.core.operators.IWindowOperatorFactory} with
 * {@code WindowOperator} from the runtime module instead.
 */
@Deprecated
public interface WindowAggregationFunction<IN, ACC, OUT, KEY, W extends Window> extends Serializable {
    ACC createAccumulator();

    ACC add(IN value, ACC accumulator) throws Exception;

    void emitResult(KEY key, W window, ACC accumulator, Collector<OUT> out) throws Exception;

    default ACC merge(ACC acc1, ACC acc2) throws Exception {
        throw new UnsupportedOperationException("merge not implemented");
    }
}
