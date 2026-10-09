package io.nop.canvas2d;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * 图案 Paint（plan 2361 D10）：自定义 PaintContext 在用户空间采样平铺——
 * repeat 双向无限、repeat-x/repeat-y 单向、no-repeat 原点单次；
 * 用户空间锚定与 CTM 跟随由 createContext 传入的 xform（user→device）求逆采样实现。
 * TexturePaint 恒双向平铺不可复用（审查探针 P4）。
 */
class PatternPaint implements Paint {
    private final BufferedImage tile;
    private final String repetition;

    PatternPaint(BufferedImage tile, String repetition) {
        this.tile = tile;
        this.repetition = repetition;
    }

    @Override
    public PaintContext createContext(ColorModel cm, Rectangle deviceBounds, Rectangle2D userBounds,
                                      AffineTransform xform, RenderingHints hints) {
        return new PatternContext(tile, repetition, xform);
    }

    @Override
    public int getTransparency() {
        return java.awt.Transparency.TRANSLUCENT;
    }

    private record PatternContext(BufferedImage tile, String repetition, AffineTransform userToDevice)
            implements PaintContext {
        @Override
        public void dispose() {
        }

        @Override
        public ColorModel getColorModel() {
            return ColorModel.getRGBdefault();
        }

        @Override
        public Raster getRaster(int deviceX, int deviceY, int deviceW, int deviceH) {
            WritableRaster raster = getColorModel()
                    .createCompatibleWritableRaster(deviceW, deviceH);
            int[] pixel = new int[4];
            AffineTransform deviceToUser = null;
            try {
                deviceToUser = userToDevice.createInverse();
            } catch (java.awt.geom.NoninvertibleTransformException e) {
                return raster;
            }
            double[] device = new double[2];
            double[] user = new double[2];
            int tileW = tile.getWidth();
            int tileH = tile.getHeight();
            boolean repeatX = repetition.equals("repeat") || repetition.equals("repeat-x");
            boolean repeatY = repetition.equals("repeat") || repetition.equals("repeat-y");
            int[] tilePixels = tile.getRGB(0, 0, tileW, tileH, null, 0, tileW);
            for (int py = 0; py < deviceH; py++) {
                for (int px = 0; px < deviceW; px++) {
                    device[0] = deviceX + px + 0.5;
                    device[1] = deviceY + py + 0.5;
                    deviceToUser.transform(device, 0, user, 0, 1);
                    double ux = user[0];
                    double uy = user[1];
                    if (ux < 0 || uy < 0 || ux >= tileW || uy >= tileH) {
                        if (!repeatX && (ux < 0 || ux >= tileW)) {
                            continue;
                        }
                        if (!repeatY && (uy < 0 || uy >= tileH)) {
                            continue;
                        }
                        if (repetition.equals("no-repeat"))
                            continue;
                    }
                    int tx = sampleCoordinate(ux, tileW, repeatX);
                    int ty = sampleCoordinate(uy, tileH, repeatY);
                    if (tx < 0 || ty < 0)
                        continue;
                    int argb = tilePixels[ty * tileW + tx];
                    pixel[0] = (argb >> 16) & 0xFF;
                    pixel[1] = (argb >> 8) & 0xFF;
                    pixel[2] = argb & 0xFF;
                    pixel[3] = (argb >>> 24) & 0xFF;
                    raster.setPixel(px, py, pixel);
                }
            }
            return raster;
        }

        private int sampleCoordinate(double v, int size, boolean repeat) {
            if (repeat) {
                int t = (int) Math.floor(v) % size;
                return t < 0 ? t + size : t;
            }
            return v >= 0 && v < size ? (int) v : -1;
        }
    }
}
