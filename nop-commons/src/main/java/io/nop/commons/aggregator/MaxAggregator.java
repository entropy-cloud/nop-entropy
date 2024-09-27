package io.nop.commons.aggregator;

import io.nop.commons.util.MathHelper;

public class MaxAggregator implements IAggregator {
    private Object result = null;

    @Override
    public void aggregate(Object value) {
        if (result == null) {
            result = value;
        } else if (value != null) {
            result = MathHelper.max(result, value);
        }
    }

    @Override
    public Object getResult() {
        return result;
    }

    @Override
    public void reset() {
        result = null;
    }
}
