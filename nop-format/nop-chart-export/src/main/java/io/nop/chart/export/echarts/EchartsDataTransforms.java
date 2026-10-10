package io.nop.chart.export.echarts;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared data transforms for cartesian series builders (plan 2364 D36).
 */
public final class EchartsDataTransforms {

    private EchartsDataTransforms() {
    }

    /**
     * Percent-stacked transform: within each category, every series value becomes
     * value / categorySum * 100 (zeros when the category sum is zero). Null values
     * stay null.
     */
    public static List<List<Double>> percentStacked(List<List<Number>> seriesValues) {
        int categoryCount = 0;
        for (List<Number> values : seriesValues)
            categoryCount = Math.max(categoryCount, values == null ? 0 : values.size());

        double[] sums = new double[categoryCount];
        for (List<Number> values : seriesValues) {
            if (values == null)
                continue;
            for (int c = 0; c < values.size(); c++) {
                Number v = values.get(c);
                if (v != null)
                    sums[c] += v.doubleValue();
            }
        }

        List<List<Double>> result = new ArrayList<>(seriesValues.size());
        for (List<Number> values : seriesValues) {
            List<Double> transformed = new ArrayList<>(categoryCount);
            for (int c = 0; c < categoryCount; c++) {
                Number v = values == null || c >= values.size() ? null : values.get(c);
                if (v == null)
                    transformed.add(null);
                else if (sums[c] == 0)
                    transformed.add(0.0);
                else
                    transformed.add(v.doubleValue() / sums[c] * 100.0);
            }
            result.add(transformed);
        }
        return result;
    }

    /**
     * Apply the dispBlanksAs=ZERO semantic (D32): null values become 0.0; otherwise
     * nulls pass through (GAP/SPAN keep holes in the line). Callers pass
     * {@code zeroFill = chart.getDispBlanksAs() == ChartDispBlankAs.ZERO}.
     */
    public static List<Object> dataValues(List<? extends Number> values, boolean zeroFill) {
        List<Object> data = new ArrayList<>(values.size());
        for (Number v : values)
            data.add(v == null && zeroFill ? 0.0 : v);
        return data;
    }
}
