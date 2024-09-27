package io.nop.commons.aggregator;

public class CountAggregator implements IAggregator {
    private int result = 0;

    @Override
    public void aggregate(Object value) {
        this.result++;
    }

    @Override
    public Integer getResult() {
        return result;
    }

    @Override
    public void reset() {
        result = 0;
    }
}