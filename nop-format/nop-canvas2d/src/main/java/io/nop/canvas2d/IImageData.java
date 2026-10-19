package io.nop.canvas2d;

/**
 * 图像数据（canvas ImageData 的服务端子集）。D12 契约：data 为
 * <b>non-premultiplied ARGB</b>（TYPE_INT_ARGB getRGB 直读，往返无损）；
 * WI4 桥接 JS ImageData 的 byte[] RGBA 转换在桥接层做。
 */
public interface IImageData {
    int getWidth();

    int getHeight();

    int[] getData();
}
