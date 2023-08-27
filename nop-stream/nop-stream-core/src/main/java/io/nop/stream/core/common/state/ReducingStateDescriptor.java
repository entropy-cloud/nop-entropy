package io.nop.stream.core.common.state;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;

public class ReducingStateDescriptor<T> extends StateDescriptor<T> {
    private final Class<? extends SimpleAccumulator<T>> accumulatorType;

    public ReducingStateDescriptor(String name, Class<T> valueType, Class<? extends SimpleAccumulator<T>> accumulatorType) {
        super(name, valueType);
        this.accumulatorType = accumulatorType;
    }

    public Class<? extends SimpleAccumulator<T>> getAccumulatorType() {
        return accumulatorType;
    }
}
