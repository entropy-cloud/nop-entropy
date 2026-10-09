package io.nop.canvas2d;

import java.awt.image.BufferedImage;

/**
 * Canvas2D 画布元素（对应浏览器的 HTMLCanvasElement 中 zrender 实际消费的最小面）。
 * 画布尺寸固定于构造，不提供 resize（静态单帧渲染场景）。
 */
public interface ICanvas2D {
    int getWidth();

    int getHeight();

    /**
     * 返回本画布绑定的 2D 上下文（每次调用返回同一实例，与浏览器语义一致）。
     */
    ICanvas2DContext getContext2D();

    /**
     * 返回当前渲染结果（ARGB，含 alpha 通道）。返回图像与内部渲染缓冲的后续独立性由实现保证。
     */
    BufferedImage getImage();

    /**
     * 像素级直写复制（WI3）：源画布内容替换本画布（要求同尺寸，不受 transform/clip/alpha 影响）。
     */
    void copyFrom(ICanvas2D source);
}
