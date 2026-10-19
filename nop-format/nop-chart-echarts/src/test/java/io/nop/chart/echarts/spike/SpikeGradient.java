package io.nop.chart.echarts.spike;

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * WI0 spike 的 CanvasGradient 简化实现，坐标在创建时的用户空间解释（与 W3C 「使用时用户空间」
 * 语义在 zrender 先设变换后建渐变的调用顺序下等价）。径向渐变以内圆为焦点做近似。
 * 生产级实现归 WI3 的 nop-canvas2d。
 */
public class SpikeGradient {
    private final boolean radial;
    private final double x0;
    private final double y0;
    private final double r0;
    private final double x1;
    private final double y1;
    private final double r1;
    private final List<Stop> stops = new ArrayList<>();

    private record Stop(double offset, Color color) {
    }

    private SpikeGradient(boolean radial, double x0, double y0, double r0, double x1, double y1, double r1) {
        this.radial = radial;
        this.x0 = x0;
        this.y0 = y0;
        this.r0 = r0;
        this.x1 = x1;
        this.y1 = y1;
        this.r1 = r1;
    }

    public static SpikeGradient linear(double x0, double y0, double x1, double y1) {
        return new SpikeGradient(false, x0, y0, 0, x1, y1, 0);
    }

    public static SpikeGradient radial(double x0, double y0, double r0, double x1, double y1, double r1) {
        return new SpikeGradient(true, x0, y0, r0, x1, y1, r1);
    }

    public void addColorStop(double offset, String cssColor) {
        if (offset < 0 || offset > 1)
            throw new IllegalArgumentException("Gradient color stop offset out of range [0,1]: " + offset);
        stops.add(new Stop(offset, SpikeCanvasBackend.parseColor(cssColor)));
    }

    public Paint toPaint() {
        if (stops.isEmpty())
            throw new IllegalStateException("Gradient has no color stops");
        stops.sort((a, b) -> Double.compare(a.offset(), b.offset()));
        float[] fractions = new float[stops.size()];
        Color[] colors = new Color[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            fractions[i] = (float) stops.get(i).offset();
            colors[i] = stops.get(i).color();
        }
        if (radial) {
            Point2D.Float center = new Point2D.Float((float) x1, (float) y1);
            Point2D.Float focus = new Point2D.Float((float) x0, (float) y0);
            return new RadialGradientPaint(center, (float) Math.max(r1, 0.001), focus, fractions, colors,
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
        }
        return new LinearGradientPaint((float) x0, (float) y0, (float) x1, (float) y1, fractions, colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }
}
