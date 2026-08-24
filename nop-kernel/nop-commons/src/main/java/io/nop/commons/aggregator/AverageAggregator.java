package io.nop.commons.aggregator;

import io.nop.commons.util.MathHelper;

public class AverageAggregator implements IAggregator {
    private Number sum = 0;
    private int count = 0;

    /**
     * 与SQL语义一致：忽略null值，null既不计入分子也不计入分母
     */
    @Override
    public void update(Object value) {
        if (value == null)
            return;

        this.sum = MathHelper.add(this.sum, value);
        this.count++;
    }

    @Override
    public Number getValue() {
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
