package io.nop.datav.biz;

import java.util.Collections;
import java.util.Map;

/**
 * 大屏主题解析结果（D4-3）。承载经主题解析器（{@code ScreenThemeParser}）解析后的结构化主题：
 * 命名色板（palette，缺省值已填充）+ 背景定义（background，type/value 缺省已应用）。
 *
 * <p>该类位于 nop-datav-dao 的 biz 包，仅使用基础 Java 类型（Map/String/Object），保持依赖方向正确。
 * 经 {@code ScreenLayoutConfig.theme} 字段暴露（additive，不破坏既有 {@code Canvas.backgroundConfig} 透传契约）。</p>
 *
 * <p>参见 {@code ai-dev/design/nop-datav/screen-design.md} §11 主题模型。</p>
 */
public class ScreenThemeConfig {

    /** 命名语义色集合（name → hex 色值字符串）。 */
    private Map<String, String> palette;
    /** 背景定义（type 缺省 color，value 缺省取 palette.background）。 */
    private Background background;

    public Map<String, String> getPalette() {
        return palette == null ? Collections.emptyMap() : palette;
    }

    public void setPalette(Map<String, String> palette) {
        this.palette = palette;
    }

    public Background getBackground() {
        return background;
    }

    public void setBackground(Background background) {
        this.background = background;
    }

    /** 背景定义：类型 + 值。 */
    public static class Background {
        /** 背景类型：{@code color}（缺省）| {@code image} | {@code gradient}。 */
        private String type;
        /**
         * 背景值，依 type 而定：
         * <ul>
         *   <li>{@code color}：hex 色值字符串（缺省取 palette.background）</li>
         *   <li>{@code image}：图片 URL 字符串</li>
         *   <li>{@code gradient}：渐变描述对象（Map）或 CSS 渐变字符串</li>
         * </ul>
         */
        private Object value;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }
}
