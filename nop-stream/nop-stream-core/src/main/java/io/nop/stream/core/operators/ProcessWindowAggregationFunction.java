package io.nop.stream.core.operators;

import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.windows.Window;

/**
 * @deprecated Use {@link io.nop.stream.core.operators.IWindowOperatorFactory} with
 * {@code WindowOperator} from the runtime module instead.
 */
@Deprecated
public class ProcessWindowAggregationFunction<IN, OUT, K, W extends Window>
        implements WindowAggregationFunction<IN, List<IN>, OUT, K, W> {

    private static final long serialVersionUID = 1L;

    private final ProcessWindowFunction<IN, OUT, K, W> processWindowFunction;

    public ProcessWindowAggregationFunction(ProcessWindowFunction<IN, OUT, K, W> processWindowFunction) {
        this.processWindowFunction = processWindowFunction;
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
        processWindowFunction.process(key, window, accumulator, null, out);
    }
}
