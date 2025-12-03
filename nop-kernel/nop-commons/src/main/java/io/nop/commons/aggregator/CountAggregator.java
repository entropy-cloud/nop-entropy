package io.nop.commons.aggregator;

public class CountAggregator implements IAggregator {
    private int result = 0;

    @Override
    public void update(Object value) {
        this.result++;
    }

    @Override
    public Integer getValue() {
        return result;
    }

    @Override
    public void reset() {
        result = 0;
    }
}