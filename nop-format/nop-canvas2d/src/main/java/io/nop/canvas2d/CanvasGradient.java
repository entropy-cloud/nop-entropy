package io.nop.canvas2d;

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 渐变实现（plan 2361 D9）：
 * stops 按 offset 稳定排序；越界 offset 抛 {@link NopCanvas2DException}；
 * 退化渐变（linear 两点重合 / radial 同心同半径）物化为 null Paint——消费方绘制为空（D2 规范对齐例外）；
 * CycleMethod 固定 NO_CYCLE（canvas 越端取端点色 = pad）；
 * 径向渐变 r0=0（zrender 唯一形态，dist:27228）时焦点模型与 canvas 双圆模型精确等价。
 */
public class CanvasGradient implements ICanvasGradient {
    private final boolean radial;
    private final double x0;
    private final double y0;
    private final double r0;
    private final double x1;
    private final double y1;
    private final double r1;
    private final List<Stop> stops = new ArrayList<>();
    private Paint cachedPaint;
    private boolean degenerate;

    private record Stop(double offset, Color color) {
    }

    CanvasGradient(boolean radial, double x0, double y0, double r0, double x1, double y1, double r1) {
        this.radial = radial;
        this.x0 = x0;
        this.y0 = y0;
        this.r0 = r0;
        this.x1 = x1;
        this.y1 = y1;
        this.r1 = r1;
        this.degenerate = radial ? (x0 == x1 && y0 == y1 && r0 == r1) : (x0 == x1 && y0 == y1);
    }

    @Override
    public void addColorStop(double offset, String cssColor) {
        if (!Double.isFinite(offset) || offset < 0 || offset > 1)
            throw new NopCanvas2DException("Gradient color stop offset out of range [0,1], got: " + offset);
        stops.add(new Stop(offset, CssColors.parse(cssColor)));
        cachedPaint = null;
    }

    /**
     * 物化为 Java2D Paint；无 stop 或退化渐变返回 null（消费方绘制为空）。
     */
    Paint toPaint() {
        if (stops.isEmpty() || degenerate)
            return null;
        if (cachedPaint != null)
            return cachedPaint;
        List<Stop> sorted = new ArrayList<>(stops);
        sorted.sort((a, b) -> Double.compare(a.offset(), b.offset()));
        float[] fractions = new float[sorted.size()];
        Color[] colors = new Color[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            fractions[i] = (float) sorted.get(i).offset();
            colors[i] = sorted.get(i).color();
        }
        MultipleGradientPaint.CycleMethod cycle = MultipleGradientPaint.CycleMethod.NO_CYCLE;
        if (radial) {
            Point2D.Float center = new Point2D.Float((float) x1, (float) y1);
            Point2D.Float focus = new Point2D.Float((float) x0, (float) y0);
            cachedPaint = new RadialGradientPaint(center, (float) r1, focus, fractions, colors, cycle);
        } else {
            cachedPaint = new LinearGradientPaint((float) x0, (float) y0, (float) x1, (float) y1,
                    fractions, colors, cycle);
        }
        return cachedPaint;
    }
}
