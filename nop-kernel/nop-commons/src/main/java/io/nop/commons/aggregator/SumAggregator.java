package io.nop.commons.aggregator;

import io.nop.commons.util.MathHelper;

public class SumAggregator implements IAggregator {
    private Number result = 0;

    @Override
    public void update(Object value) {
        if(value != null)
            this.result = MathHelper.add(this.result, value);
    }

    @Override
    public Number getValue() {
        return result;
    }

    @Override
    public void reset() {
        result = 0;
    }
}