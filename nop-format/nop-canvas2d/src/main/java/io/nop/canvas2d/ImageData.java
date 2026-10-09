package io.nop.canvas2d;

/**
 * IImageData 默认实现（数据以行优先 int[] non-premultiplied ARGB 存储）。
 */
public record ImageData(int width, int height, int[] data) implements IImageData {
    public ImageData {
        if (width <= 0 || height <= 0)
            throw new NopCanvas2DException("ImageData size must be positive, got: " + width + "x" + height);
        if (data.length != width * height)
            throw new NopCanvas2DException("ImageData data length mismatch: " + data.length
                    + " != " + width + "*" + height);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int[] getData() {
        return data;
    }
}
