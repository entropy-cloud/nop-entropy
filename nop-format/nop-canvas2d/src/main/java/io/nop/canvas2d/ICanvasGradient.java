package io.nop.canvas2d;

/**
 * 渐变（canvas CanvasGradient 的 zrender 消费子集）。
 */
public interface ICanvasGradient {
    void addColorStop(double offset, String cssColor);
}
