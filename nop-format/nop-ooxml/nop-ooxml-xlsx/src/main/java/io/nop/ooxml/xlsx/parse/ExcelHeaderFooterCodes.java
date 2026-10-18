package io.nop.ooxml.xlsx.parse;

import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 页眉页脚 &-语法解析（WI4）：位置分段（&L/&C/&R）+ 字体/字号段识别 + 值占位符保留。
 *
 * <p>存储裁定（plan 2467）：模型 left/center/right String <b>保留原始 &-代码不剥离</b>（保真无损、
 * 模型不膨胀）；渲染端复用本解析器做段级字体/字号切换与占位符求值。同段多字体代码取首个。</p>
 *
 * <p>代码集：位置 {@code &L/&C/&R}；值占位符 {@code &P/&N/&D/&T/&A/&F/&Z}；转义 {@code &&}；
 * 字体 {@code &"name[,style]"}；字号 {@code &nn}（两位数字）；粗体/斜体 {@code &B/&I}；
 * 图片 {@code &G}（WI5 渲染）。</p>
 */
public final class ExcelHeaderFooterCodes {

    /** 位置分段：缺省段归属 center（无位置代码的文本归 center） */
    public static final String SECTION_DEFAULT = "C";

    private ExcelHeaderFooterCodes() {
    }

    /**
     * 段定义：位置（L/C/R）与该段的原始文本（保留 &-代码）。
     */
    public static final class Section {
        private final String position;
        private final String text;

        Section(String position, String text) {
            this.position = position;
            this.text = text;
        }

        public String getPosition() {
            return position;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 把 Excel 页眉页脚原始串拆为 L/C/R 三段（保留 &-代码）。
     *
     * @param raw 原始串，如 {@code &C&"SimSun,Plain"&9 第1页/共3页}；null/空返回空列表
     */
    public static List<Section> parseSections(String raw) {
        List<Section> ret = new ArrayList<>();
        if (StringHelper.isEmpty(raw))
            return ret;

        String current = SECTION_DEFAULT;
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (ch == '&' && i + 1 < raw.length()) {
                char code = raw.charAt(i + 1);
                if (code == 'L' || code == 'C' || code == 'R') {
                    // 切换位置段：非首段（或已有文本）时冲刷当前段；跳过开头无内容的缺省段
                    if (buf.length() > 0 || !ret.isEmpty()) {
                        ret.add(new Section(current, buf.toString()));
                        buf.setLength(0);
                    }
                    current = String.valueOf(code);
                    i += 2;
                    continue;
                }
                // 转义 && 与其他代码（含字体 &"..." 需要整体消费）保持原样进入段文本
                if (code == '"') {
                    int close = raw.indexOf('"', i + 2);
                    int end = close < 0 ? raw.length() : close + 1;
                    buf.append(raw, i, end);
                    i = end;
                    continue;
                }
            }
            buf.append(ch);
            i++;
        }
        if (buf.length() > 0 || ret.isEmpty())
            ret.add(new Section(current, buf.toString()));
        return ret;
    }

    /**
     * 段内首个字体名（{@code &"name[,style]"}），无则 null。
     */
    public static String firstFontName(String sectionText) {
        if (StringHelper.isEmpty(sectionText))
            return null;
        int i = sectionText.indexOf("&\"");
        if (i < 0)
            return null;
        int close = sectionText.indexOf('"', i + 2);
        if (close < 0)
            return null;
        String name = sectionText.substring(i + 2, close);
        int comma = name.indexOf(',');
        return comma >= 0 ? name.substring(0, comma) : name;
    }

    /**
     * 段内首个字号（{@code &nn}，1-2 位数字代码，如 &9），无则 -1。
     */
    public static int firstFontSize(String sectionText) {
        if (StringHelper.isEmpty(sectionText))
            return -1;
        for (int i = 0; i < sectionText.length() - 1; i++) {
            if (sectionText.charAt(i) != '&')
                continue;
            int j = i + 1;
            int value = 0;
            int digits = 0;
            while (j < sectionText.length() && digits < 2 && Character.isDigit(sectionText.charAt(j))) {
                value = value * 10 + (sectionText.charAt(j) - '0');
                digits++;
                j++;
            }
            if (digits > 0)
                return value;
        }
        return -1;
    }

    /**
     * 是否含值占位符（&P/&N/&D/&T/&A/&F/&Z）——渲染期需延迟求值的判定依据。
     */
    public static boolean hasValuePlaceholder(String text) {
        if (StringHelper.isEmpty(text))
            return false;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '&')
                continue;
            char code = text.charAt(i + 1);
            switch (code) {
                case 'P':
                case 'N':
                case 'D':
                case 'T':
                case 'A':
                case 'F':
                case 'Z':
                    return true;
                case '&':
                    i++; // 转义跳过
                    break;
                default:
            }
        }
        return false;
    }

    /**
     * 是否含任何 &-代码（含仅字体/字号代码）——延迟渲染分流判定：含代码的文本走求值渲染，
     * 避免 & 代码被字面打印。
     */
    public static boolean hasAnyCode(String text) {
        if (StringHelper.isEmpty(text))
            return false;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '&') {
                char next = text.charAt(i + 1);
                if (next == '&') {
                    i++;
                    continue;
                }
                return true;
            }
        }
        return false;
    }
}
