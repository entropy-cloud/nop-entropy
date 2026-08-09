package io.nop.datav.service.screen;

/**
 * 大屏屏幕适配模式常量（dict {@code datav/screen-adaptor}）。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/screen-design.md} §3 屏幕适配语义。</p>
 */
public final class ScreenAdaptorMode {
    /** 高度优先：按高度等比缩放，宽度可滚动。 */
    public static final int HEIGHT_FIRST = 0;
    /** 整体铺满：等比缩放铺满视口。 */
    public static final int FULL = 10;
    /** 保持原始：不缩放。 */
    public static final int KEEP = 20;

    private ScreenAdaptorMode() {
    }

    public static boolean isValid(int mode) {
        return mode == HEIGHT_FIRST || mode == FULL || mode == KEEP;
    }
}
