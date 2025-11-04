package io.nop.commons.aggregator;

import io.nop.commons.util.MathHelper;

public class AverageAggregator implements IAggregator {
    private Number sum = 0;
    private int count = 0;

    @Override
    public void aggregate(Object value) {
        this.sum = MathHelper.add(this.sum, value);
        this.count++;
    }

    @Override
    public Number getResult() {
        if (this.count == 0)
            return null;
        return MathHelper.divide(sum, count);
    }

    @Override
    public void reset() {
        sum = 0;
        count = 0;
    }
}
