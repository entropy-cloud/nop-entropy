package io.nop.excel.print;

import io.nop.api.core.beans.geometry.RectangleBean;
import io.nop.api.core.beans.geometry.SizeBean;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.constants.ExcelPaperSize;

import static io.nop.excel.model.constants.ExcelModelConstants.DEFAULT_HEADER_FOOTER;
import static io.nop.excel.model.constants.ExcelModelConstants.DEFAULT_MARGIN;

public class ExcelPrintHelper {
    public static SizeBean getStandardPaperSize(Integer paperSize) {
        if (paperSize == null)
            return ExcelPaperSize.A4_PAPER.getSize();
        return ExcelPaperSize.of(paperSize).getSize();
    }

    public static SizeBean getPaperSize(ExcelPageSetup pageSetup) {
        if (pageSetup == null) {
            return ExcelPaperSize.A4_PAPER.getSize();
        }

        // 获取宽度和高度（考虑自定义尺寸和标准尺寸）
        Float width = pageSetup.getPaperWidth();
        Float height = pageSetup.getPaperHeight();

        SizeBean size = (width != null && height != null)
                ? new SizeBean(width, height)
                : getStandardPaperSize(pageSetup.getPaperSize());

        // 处理横向打印的情况
        return Boolean.TRUE.equals(pageSetup.getOrientationHorizontal())
                ? new SizeBean(size.getHeight(), size.getWidth())
                : size;
    }

    /**
     * 计算实际可打印区域（考虑页边距后的可用区域）
     *
     * @param pageSize 页面大小
     * @param margins  页边距配置（单位：pt）
     * @return 可打印区域的PDRectangle
     */
    public static RectangleBean calculatePrintArea(SizeBean pageSize, ExcelPageMargins margins) {
        if (pageSize == null) {
            pageSize = ExcelPaperSize.A4_PAPER.getSize();
        }

        // 获取边距值（使用合理默认值）
        double left = getPositiveValue(margins != null ? margins.getLeft() : null, DEFAULT_MARGIN);
        double right = getPositiveValue(margins != null ? margins.getRight() : null, DEFAULT_MARGIN);
        double top = getPositiveValue(margins != null ? margins.getTop() : null, DEFAULT_MARGIN);
        double bottom = getPositiveValue(margins != null ? margins.getBottom() : null, DEFAULT_MARGIN);
        double header = getPositiveValue(margins != null ? margins.getHeader() : null, DEFAULT_HEADER_FOOTER);
        double footer = getPositiveValue(margins != null ? margins.getFooter() : null, DEFAULT_HEADER_FOOTER);

        // 计算可打印区域
        double x = left;
        double y = (top + header);
        double width = pageSize.getWidth() - (left + right);
        double height = pageSize.getHeight() - (top + header + bottom + footer);

        // 确保宽高不为负（如果边距过大则至少保留5pt的可打印区域）
        width = Math.max(5, width);
        height = Math.max(5, height);

        return new RectangleBean(x, y, width, height);
    }

    public static double getHeaderHeight(ExcelPageMargins margins) {
        return getPositiveValue(margins != null ? margins.getHeader() : null, DEFAULT_HEADER_FOOTER);
    }

    public static double getFooterHeight(ExcelPageMargins margins) {
        return getPositiveValue(margins != null ? margins.getFooter() : null, DEFAULT_HEADER_FOOTER);
    }

    public static double getPositiveValue(Double value, double defaultValue) {
        if (value != null) {
            // 确保边距不小于0
            return Math.max(0, value);
        }
        return defaultValue;
    }

    /**
     * 解析重复标题行/列范围字符串（1-based 闭区间）为 {@code int[]{first,last}}。
     *
     * @param range 归一化范围，行形如 "1:2"，列形如 "A:B"
     * @return {@code [first, last]}（1-based）
     * @throws IllegalArgumentException range 为空或形态非法（空段/倒序/非数字母等）
     */
    public static int[] parseTitleRange(String range) {
        if (range == null || range.isEmpty())
            throw new IllegalArgumentException("Invalid print title range: empty range");
        int sep = range.indexOf(':');
        if (sep <= 0 || sep == range.length() - 1)
            throw new IllegalArgumentException("Invalid print title range: " + range);
        String firstText = range.substring(0, sep).trim();
        String lastText = range.substring(sep + 1).trim();
        if (isDigits(firstText) != isDigits(lastText) || isLetters(firstText) != isLetters(lastText))
            throw new IllegalArgumentException("Invalid print title range (mixed row/col segments): " + range);
        int first = parseTitleIndex(firstText, range);
        int last = parseTitleIndex(lastText, range);
        if (first > last)
            throw new IllegalArgumentException("Invalid print title range (first > last): " + range);
        return new int[]{first, last};
    }

    private static int parseTitleIndex(String text, String range) {
        if (text.isEmpty())
            throw new IllegalArgumentException("Invalid print title range: " + range);
        if (isDigits(text))
            return Integer.parseInt(text);
        if (isLetters(text))
            return colLettersToIndex(text);
        throw new IllegalArgumentException("Invalid print title range segment '" + text + "' in: " + range);
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i)))
                return false;
        }
        return true;
    }

    private static boolean isLetters(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')))
                return false;
        }
        return s.length() > 0;
    }

    /**
     * Excel 列字母（A/XFD）转 1-based 列序号。
     */
    public static int colLettersToIndex(String letters) {
        int ret = 0;
        for (int i = 0; i < letters.length(); i++) {
            char c = Character.toUpperCase(letters.charAt(i));
            ret = ret * 26 + (c - 'A' + 1);
        }
        return ret;
    }

    /**
     * 是否为前缀形态范围（first == 1）。PDF 分页重复仅支持从首行/首列开始的前缀声明。
     */
    public static boolean isPrefixRange(int[] range) {
        return range != null && range[0] == 1;
    }
}