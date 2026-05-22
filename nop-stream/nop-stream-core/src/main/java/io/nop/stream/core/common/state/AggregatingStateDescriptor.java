package io.nop.stream.core.common.state;

import io.nop.stream.core.common.functions.AggregateFunction;

public class AggregatingStateDescriptor<IN, ACC, OUT> extends StateDescriptor<ACC> {
    private final AggregateFunction<IN, ACC, OUT> aggregateFunction;

    public AggregatingStateDescriptor(String name, AggregateFunction<IN, ACC, OUT> aggregateFunction,
                                      Class<ACC> accumulatorType) {
        super(name, accumulatorType);
        this.aggregateFunction = aggregateFunction;
    }

    public AggregateFunction<IN, ACC, OUT> getAggregateFunction() {
        return aggregateFunction;
    }
}
