package io.nop.report.pdf.utils;

import io.nop.ooxml.xlsx.parse.ExcelHeaderFooterCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * 页眉页脚段文本 → 渲染 run 序列（WI4）：字体/字号代码切换当前 run 的样式属性，
 * 值占位符（&P/&N/&D/&T/&A/&F/&Z）保留文本供 {@link HeaderFooterEvaluator} 求值，
 * 位置/控制代码（&L/&C/&R/&B/&I/&G）剥离（&G 图片属 WI5）。
 */
public final class HeaderFooterRuns {

    /**
     * 渲染 run：displayText 已剥离控制代码、保留值占位符；fontName/fontSize 为段内代码切换结果（null/-1 表示继承样式默认）。
     */
    public static final class Run {
        private final String text;
        private final String fontName;
        private final int fontSize;

        Run(String text, String fontName, int fontSize) {
            this.text = text;
            this.fontName = fontName;
            this.fontSize = fontSize;
        }

        public String getText() {
            return text;
        }

        public String getFontName() {
            return fontName;
        }

        public int getFontSize() {
            return fontSize;
        }
    }

    private HeaderFooterRuns() {
    }

    public static List<Run> split(String sectionText) {
        List<Run> ret = new ArrayList<>();
        if (sectionText == null || sectionText.isEmpty())
            return ret;

        String fontName = null;
        int fontSize = -1;
        StringBuilder buf = new StringBuilder();

        int i = 0;
        while (i < sectionText.length()) {
            char ch = sectionText.charAt(i);
            if (ch == '&' && i + 1 < sectionText.length()) {
                char code = sectionText.charAt(i + 1);
                if (code == '"') {
                    int close = sectionText.indexOf('"', i + 2);
                    int end = close < 0 ? sectionText.length() : close + 1;
                    String spec = close < 0 ? sectionText.substring(i + 2)
                            : sectionText.substring(i + 2, close);
                    flush(ret, buf, fontName, fontSize);
                    fontName = firstToken(spec);
                    i = end;
                    continue;
                }
                if (Character.isDigit(code)) {
                    int j = i + 1;
                    int value = 0;
                    int digits = 0;
                    while (j < sectionText.length() && digits < 2 && Character.isDigit(sectionText.charAt(j))) {
                        value = value * 10 + (sectionText.charAt(j) - '0');
                        digits++;
                        j++;
                    }
                    flush(ret, buf, fontName, fontSize);
                    fontSize = value;
                    i = j;
                    continue;
                }
                switch (code) {
                    case 'B':
                    case 'I':
                    case 'G':
                    case 'L':
                    case 'C':
                    case 'R':
                        // 控制代码剥离（&B/&I 样式精修、&G 图片属 WI5；位置代码已由分段处理）
                        i += 2;
                        continue;
                    default:
                        // 值占位符（&P/&N/&D/&T/&A/&F/&Z）与转义（&&）进入文本
                        buf.append(ch);
                        i++;
                        continue;
                }
            }
            buf.append(ch);
            i++;
        }
        flush(ret, buf, fontName, fontSize);
        return ret;
    }

    private static void flush(List<Run> ret, StringBuilder buf, String fontName, int fontSize) {
        if (buf.length() > 0) {
            ret.add(new Run(buf.toString(), fontName, fontSize));
            buf.setLength(0);
        }
    }

    private static String firstToken(String spec) {
        int comma = spec.indexOf(',');
        return comma >= 0 ? spec.substring(0, comma) : spec;
    }
}
