package io.nop.canvas2d;

/**
 * ITextMetrics 默认实现。
 */
public record TextMetrics(double width, float ascent, float descent) implements ITextMetrics {
    @Override
    public double getWidth() {
        return width;
    }

    @Override
    public float getAscent() {
        return ascent;
    }

    @Override
    public float getDescent() {
        return descent;
    }
}
