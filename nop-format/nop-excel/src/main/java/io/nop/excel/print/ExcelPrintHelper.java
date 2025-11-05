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
}