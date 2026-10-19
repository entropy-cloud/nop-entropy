package io.nop.canvas2d;

/**
 * 文本度量结果（canvas TextMetrics 的 zrender 消费子集 + 模块内布局所需）。
 */
public interface ITextMetrics {
    double getWidth();

    float getAscent();

    float getDescent();
}
