package io.nop.table.validator.validate;

import java.util.HashSet;
import java.util.Set;

public class ColumnStats {
    private long count;
    private long nullCount;
    private double sum;
    private double sumOfSquares;
    private double min = Double.MAX_VALUE;
    private double max = -Double.MAX_VALUE;
    private Set<Object> distinctSet = new HashSet<>();

    public void accumulate(Object value) {
        if (value == null) {
            nullCount++;
            return;
        }

        count++;
        distinctSet.add(value);

        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            sum += v;
            sumOfSquares += v * v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
    }

    public long getCount() {
        return count;
    }

    public long getNullCount() {
        return nullCount;
    }

    public Double getSum() {
        return count > 0 ? sum : null;
    }

    public Double getSumOfSquares() {
        return count > 0 ? sumOfSquares : null;
    }

    public Double getMean() {
        return count > 0 ? sum / count : null;
    }

    public Double getStdDev() {
        if (count < 2) return null;
        double mean = sum / count;
        return Math.sqrt((sumOfSquares - 2 * mean * sum + count * mean * mean) / (count - 1));
    }

    public Double getMin() {
        return count > 0 ? min : null;
    }

    public Double getMax() {
        return count > 0 ? max : null;
    }

    public int getDistinctCount() {
        return distinctSet.size();
    }

    public Set<Object> getDistinctSet() {
        return distinctSet;
    }
}
