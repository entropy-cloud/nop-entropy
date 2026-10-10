package io.nop.chart.export.echarts;

import io.nop.api.core.exceptions.NopException;
import io.nop.chart.export.ChartExportErrors;
import io.nop.excel.chart.constants.ChartTrendLineType;
import io.nop.excel.chart.model.ChartTrendLineModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes trend line values for ECharts overlay rendering (plan 2364 D29).
 *
 * <p>Regression x values are category indices. Degenerate inputs fail fast with
 * ERR_DATA_RESOLUTION_FAILED: no data, fewer than 2 points (except moving average
 * which handles its own window), zero x-variance, non-positive values for
 * log/power/exp fits. A constant y series is a valid horizontal line.</p>
 */
public final class TrendLineComputer {

    private TrendLineComputer() {
    }

    /**
     * Compute overlay values (one per category index) for the given trend line.
     * Returns null when the trend line type maps to a straight markLine (LINEAR).
     */
    public static List<Double> computeOverlay(ChartTrendLineModel trend, int pointCount, List<Number> values) {
        switch (trend.getType() == null ? ChartTrendLineType.LINEAR : trend.getType()) {
            case MOVING_AVG:
                return movingAverage(values, trend.getPeriod() == null ? 2 : trend.getPeriod());
            case LOGARITHMIC:
                return logFit(values);
            case POWER:
                return powerFit(values);
            case EXPONENTIAL:
                return expFit(values);
            case POLYNOMIAL:
                return polyFit(values, 2);
            case LINEAR:
            default:
                return null; // rendered as markLine
        }
    }

    /**
     * Least-squares linear fit y = a + b*x over category indices. Returns the two
     * endpoints {y0, y1}. Degenerate inputs throw ERR_DATA_RESOLUTION_FAILED.
     */
    public static double[] linearFit(List<Number> values) {
        int n = values.size();
        require(n >= 2, "linear trendline needs at least 2 data points, got " + n);
        double xm = (n - 1) / 2.0;
        double ym = 0;
        for (Number v : values)
            ym += v.doubleValue();
        ym /= n;
        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < n; i++) {
            double dx = i - xm;
            sxx += dx * dx;
            sxy += dx * (values.get(i).doubleValue() - ym);
        }
        if (sxx == 0)
            throw resolutionFailed("linear trendline has zero x-variance");
        double b = sxy / sxx;
        double a = ym - b * xm;
        return new double[]{a, a + b * (n - 1)};
    }

    private static List<Double> movingAverage(List<Number> values, int period) {
        require(period >= 1, "moving average period must be >= 1, got " + period);
        require(!values.isEmpty(), "moving average trendline has no data");
        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (i + 1 < period) {
                result.add(null);
                continue;
            }
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++)
                sum += values.get(j).doubleValue();
            result.add(sum / period);
        }
        return result;
    }

    private static List<Double> logFit(List<Number> values) {
        double[] xs = indices(values.size());
        double[] ys = positive(values, "logarithmic trendline requires positive values");
        double[] fit = fit(xs, ln(ys));
        double a = Math.exp(fit[0]);
        double b = fit[1];
        List<Double> result = new ArrayList<>(xs.length);
        for (double x : xs)
            result.add(a + b * Math.log(x));
        return result;
    }

    private static List<Double> powerFit(List<Number> values) {
        int n = values.size();
        double[] xs = indices(n);
        double[] ys = positive(values, "power trendline requires positive values");
        double[] fit = fit(ln(xs), ln(ys));
        double a = Math.exp(fit[0]);
        double b = fit[1];
        List<Double> result = new ArrayList<>(n);
        for (double x : xs)
            result.add(a * Math.pow(x, b));
        return result;
    }

    private static List<Double> expFit(List<Number> values) {
        int n = values.size();
        double[] xs = indices(n);
        double[] ys = positive(values, "exponential trendline requires positive values");
        double[] fit = fit(xs, ln(ys));
        double a = Math.exp(fit[0]);
        double b = fit[1];
        List<Double> result = new ArrayList<>(n);
        for (double x : xs)
            result.add(a * Math.exp(b * x));
        return result;
    }

    /**
     * Quadratic (order-2) polynomial fit, matching the Excel default polynomial order.
     */
    private static List<Double> polyFit(List<Number> values, int order) {
        int n = values.size();
        require(n > order, "polynomial trendline needs more than " + order + " data points, got " + n);
        // Normal equations for y = a + b*x + c*x^2
        double s0 = n, s1 = 0, s2 = 0, s3 = 0, s4 = 0, t0 = 0, t1 = 0, t2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i).doubleValue();
            s1 += x;
            s2 += x * x;
            s3 += x * x * x;
            s4 += x * x * x * x;
            t0 += y;
            t1 += x * y;
            t2 += x * x * y;
        }
        double det = det3(s0, s1, s2, s1, s2, s3, s2, s3, s4);
        if (det == 0)
            throw resolutionFailed("polynomial trendline is degenerate for " + n + " points");
        double a = det3(t0, s1, s2, t1, s2, s3, t2, s3, s4) / det;
        double b = det3(s0, t0, s2, s1, t1, s3, s2, t2, s4) / det;
        double c = det3(s0, s1, t0, s1, s2, t1, s2, s3, t2) / det;
        List<Double> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double x = i;
            result.add(a + b * x + c * x * x);
        }
        return result;
    }

    private static double det3(double a, double b, double c, double d, double e, double f,
                               double g, double h, double i) {
        return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    }

    private static double[] fit(double[] xs, double[] ys) {
        int n = xs.length;
        double xm = 0, ym = 0;
        for (int i = 0; i < n; i++) {
            xm += xs[i];
            ym += ys[i];
        }
        xm /= n;
        ym /= n;
        double sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (xs[i] - xm) * (xs[i] - xm);
            sxy += (xs[i] - xm) * (ys[i] - ym);
        }
        if (sxx == 0)
            throw resolutionFailed("trendline has zero x-variance");
        double b = sxy / sxx;
        return new double[]{ym - b * xm, b};
    }

    private static double[] positive(List<Number> values, String message) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i).doubleValue();
            require(v > 0, message);
            result[i] = v;
        }
        return result;
    }

    private static double[] ln(double[] vs) {
        double[] result = new double[vs.length];
        for (int i = 0; i < vs.length; i++)
            result[i] = Math.log(vs[i]);
        return result;
    }

    private static double[] indices(int n) {
        double[] result = new double[n];
        for (int i = 0; i < n; i++)
            result[i] = i;
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw resolutionFailed(message);
    }

    private static NopException resolutionFailed(String reason) {
        return new NopException(ChartExportErrors.ERR_DATA_RESOLUTION_FAILED)
                .param(ChartExportErrors.ARG_REASON, reason);
    }
}
