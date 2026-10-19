package io.nop.canvas2d;

import java.util.List;

/**
 * CSS font 简写解析产物（plan 2360 D5）。
 *
 * @param style      Font.PLAIN / Font.ITALIC
 * @param weight     Font.PLAIN / Font.BOLD
 * @param sizePx     字号（px）
 * @param lineHeight 行高（px；未指定时 0，由消费方按宽字符规则推导）
 * @param families   家族列表（保持声明顺序，引号已剥离）
 */
public record CssFont(int style, int weight, float sizePx, float lineHeight, List<String> families) {
}
