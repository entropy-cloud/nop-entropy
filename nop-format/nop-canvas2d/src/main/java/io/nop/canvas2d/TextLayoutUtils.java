package io.nop.canvas2d;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本布局工具（plan 2360，语义对齐 zrender truncateText2 dist:9632-9703 与 wrapText dist:10033+）。
 * <p>
 * 消费者裁定：zrender JS 自带 truncate/wrap（只消费 platformApi.measureText），WI4 引擎链路
 * 不会调用本工具——定位为 Java 侧基础设施，消费者为后续 ChartModel 适配层的 Java 侧文本处理。
 */
public final class TextLayoutUtils {
    private static final String DEFAULT_ELLIPSIS = "...";

    private TextLayoutUtils() {
    }

    /**
     * 超宽截断 + 省略号。省略号宽度计入 maxWidth；省略号自身超宽返回空串（zrender 语义）；
     * 文本本就放得下则原样返回。
     */
    public static String truncateText(String text, String cssFont, double maxWidth, TextMeasurer measurer) {
        return truncateText(text, cssFont, maxWidth, DEFAULT_ELLIPSIS, measurer);
    }

    public static String truncateText(String text, String cssFont, double maxWidth, String ellipsis,
                                      TextMeasurer measurer) {
        String normalized = text == null ? "null" : text;
        if (measurer.measure(normalized, cssFont).getWidth() <= maxWidth)
            return normalized;
        double ellipsisWidth = measurer.measure(ellipsis, cssFont).getWidth();
        if (ellipsisWidth > maxWidth)
            return "";
        double available = maxWidth - ellipsisWidth;
        // 按字符宽度累加找前缀（近似），再以 stringWidth 精确校验收缩（修正 kerning 误差）
        int limit = normalized.length();
        while (limit > 0 && measurer.measure(normalized.substring(0, limit), cssFont).getWidth() > available)
            limit--;
        return normalized.substring(0, limit) + ellipsis;
    }

    /**
     * 分行：先按 \n 切分，再对超宽行自动换行。断点语义对齐 zrender wrapText：
     * 非字母字符后即可断（CJK 逐字、标点/空格后可断），英文单词保持完整，
     * 单词自身超宽时硬断。
     */
    public static List<String> splitLines(String text, String cssFont, double maxWidth, TextMeasurer measurer) {
        String normalized = text == null ? "null" : text;
        List<String> lines = new ArrayList<>();
        for (String raw : normalized.split("\n", -1)) {
            if (maxWidth <= 0 || measurer.measure(raw, cssFont).getWidth() <= maxWidth) {
                lines.add(raw);
                continue;
            }
            wrapLine(raw, cssFont, maxWidth, measurer, lines);
        }
        return lines;
    }

    private static void wrapLine(String line, String cssFont, double maxWidth, TextMeasurer measurer,
                                 List<String> out) {
        int start = 0;
        int lastBreak = -1;
        int end = start + 1;
        while (end <= line.length()) {
            // 断行空格不计宽：剥离窗口尾部空白后再测（zrender 断点空格免费语义）
            String window = line.substring(start, end);
            String stripped = stripTrailingWhitespace(window);
            boolean fits = measurer.measure(stripped.isEmpty() ? window : stripped, cssFont).getWidth() <= maxWidth;
            if (!fits) {
                int cut = lastBreak > start ? lastBreak : Math.max(start + 1, end - 1);
                String emitted = stripTrailingWhitespace(line.substring(start, cut));
                out.add(emitted);
                start = cut;
                while (start < line.length() && Character.isWhitespace(line.charAt(start)))
                    start++;
                lastBreak = -1;
                end = start;
            } else if (isBreakAfter(line.charAt(end - 1))) {
                // 仅记录「当前仍放得下」的断点——溢出前缀的断点不可用
                lastBreak = end;
            }
            end++;
        }
        if (start < line.length())
            out.add(line.substring(start));
    }

    private static String stripTrailingWhitespace(String s) {
        int e = s.length();
        while (e > 0 && Character.isWhitespace(s.charAt(e - 1)))
            e--;
        return s.substring(0, e);
    }

    private static boolean isBreakAfter(char ch) {
        // 非字母即断点（CJK/标点/空格）；字母连续序列保持为词
        return !Character.isLetter(ch) || ch >= 0x2E80;
    }

}
