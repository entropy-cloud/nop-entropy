package io.nop.canvas2d;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 像素断言助手（plan 2359 D4 纪律）：布尔覆盖断言、采样点由调用方保证距几何边界 ≥2px、
 * 禁止整图计数断言。画布为 ARGB 透明底，前景判定以 alpha 通道为主。
 */
final class PixelAssert {
    private PixelAssert() {
    }

    /** 近不透明（前景核心区） */
    static void assertOpaque(BufferedImage img, int x, int y) {
        int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
        assertTrue(alpha >= 200, "expected opaque pixel at (" + x + "," + y + "), alpha=" + alpha);
    }

    /** 近透明（背景区） */
    static void assertTransparent(BufferedImage img, int x, int y) {
        int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
        assertTrue(alpha <= 50, "expected transparent pixel at (" + x + "," + y + "), alpha=" + alpha);
    }

    static void assertAlphaNear(BufferedImage img, int x, int y, int expectedAlpha, int tolerance) {
        int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
        assertTrue(Math.abs(alpha - expectedAlpha) <= tolerance,
                "expected alpha≈" + expectedAlpha + " at (" + x + "," + y + "), got " + alpha);
    }

    /** 不透明像素的 RGB 颜色近似断言 */
    static void assertRgbNear(BufferedImage img, int x, int y, int r, int g, int b, int tolerance) {
        int rgb = img.getRGB(x, y);
        int pr = (rgb >> 16) & 0xFF;
        int pg = (rgb >> 8) & 0xFF;
        int pb = rgb & 0xFF;
        assertTrue(Math.abs(pr - r) <= tolerance && Math.abs(pg - g) <= tolerance && Math.abs(pb - b) <= tolerance,
                "expected rgb(" + r + "," + g + "," + b + ")±" + tolerance + " at (" + x + "," + y
                        + "), got rgb(" + pr + "," + pg + "," + pb + ")");
    }
}
