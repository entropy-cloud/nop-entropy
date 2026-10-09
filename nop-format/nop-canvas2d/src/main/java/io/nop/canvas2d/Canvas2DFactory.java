package io.nop.canvas2d;

/**
 * 离屏画布工厂（canvas createCanvas 的平台面）。
 */
public final class Canvas2DFactory {

    private Canvas2DFactory() {
    }

    public static Java2DCanvas create(int width, int height) {
        return new Java2DCanvas(width, height);
    }

    public static Java2DCanvas create(int width, int height, TextMeasurer textMeasurer) {
        return new Java2DCanvas(width, height, textMeasurer);
    }
}
