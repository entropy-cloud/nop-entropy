/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.renderer;

import io.nop.api.core.beans.geometry.RectangleBean;
import io.nop.api.core.beans.geometry.SizeBean;
import io.nop.commons.util.StringHelper;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.print.ExcelPrintHelper;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML 打印分页装箱（见 ai-dev/design/nop-report/html-pagination.md）：
 * 按 `@page` 内容区高度把 tbody 行装箱为分页节；断点避让 mergeDown 跨界合并块（整块移入下一页）；
 * 页脚占位符 &P/&N 语义对齐 PDF 页脚（&P=页码、&N=总页数），多 sheet 按 sheet 作用域各自编号。
 * fitToWidth/fitToHeight/scale 缩放被忽略（登记已知限制）。
 */
public final class HtmlPagePagination {

    public static final String VAR_PAGE = "&P";
    public static final String VAR_PAGES = "&N";

    private HtmlPagePagination() {
    }

    public static boolean isPaginated(IExcelSheet sheet) {
        return sheet != null && sheet.getPageSetup() != null && sheet.getTable() != null;
    }

    /**
     * @page CSS 规则（纸型 + 页边距）。
     */
    public static String buildPageCss(IExcelSheet sheet) {
        ExcelPageSetup setup = sheet.getPageSetup();
        SizeBean size = ExcelPrintHelper.getPaperSize(setup);
        RectangleBean area = ExcelPrintHelper.calculatePrintArea(size, sheet.getPageMargins());
        StringBuilder sb = new StringBuilder();
        sb.append("@page{size:").append(fmt(size.getWidth())).append("pt ").append(fmt(size.getHeight()))
                .append("pt;margin:").append(fmt(Math.max(0, area.getY())))
                .append("pt ").append(fmt(Math.max(0, size.getWidth() - area.getWidth() - area.getX())))
                .append("pt ").append(fmt(Math.max(0, size.getHeight() - area.getHeight() - area.getY())))
                .append("pt ").append(fmt(Math.max(0, area.getX()))).append("pt;}\n");
        return sb.toString();
    }

    /**
     * 内容区高度（纸高 - 上下边距 - 页眉页脚高度），行装箱的可用高度。
     */
    public static double contentHeight(IExcelSheet sheet) {
        ExcelPageSetup setup = sheet.getPageSetup();
        SizeBean size = ExcelPrintHelper.getPaperSize(setup);
        RectangleBean area = ExcelPrintHelper.calculatePrintArea(size, sheet.getPageMargins());
        return area.getHeight();
    }

    /**
     * 装箱：把 tbody 行区间 [headerCount, rowCount-footerCount) 按内容区高度切成若干页，
     * 每页为行区间 [from, to)（相对 table 全表行号）。断点避让 mergeDown 跨界合并块。
     */
    public static List<int[]> packPages(IExcelSheet sheet) {
        ITableView table = sheet.getTable();
        int headerCount = table.getHeaderCount();
        int end = table.getRowCount() - table.getFooterCount();
        double pageHeight = contentHeight(sheet);
        double defaultHeight = sheet.getDefaultRowHeight() == null ? 20 : sheet.getDefaultRowHeight();

        double headerHeight = headerHeight(table, headerCount, defaultHeight);
        double usable = Math.max(defaultHeight, pageHeight - headerHeight);

        List<int[]> pages = new ArrayList<>();
        int start = headerCount;
        while (start < end) {
            double acc = 0;
            int e = start;
            while (e < end) {
                double h = rowHeight(table.getRow(e), defaultHeight);
                if (e > start && acc + h > usable)
                    break;
                acc += h;
                e++;
            }
            // 断点避让 mergeDown 跨界合并块：块内任一行延伸越界则断点回退到该行
            e = avoidMergeBreak(table, start, e);
            if (e <= start)
                e = start + 1; // 超高单行（或整体合并块）独占一页，保证推进
            pages.add(new int[]{start, e});
            start = e;
        }
        return pages;
    }

    /**
     * 页脚文本：&P/&N 占位符求值；多 sheet 按 sheet 作用域各自编号。
     */
    public static String renderFooter(String template, int pageNo, int pageCount) {
        if (StringHelper.isEmpty(template))
            template = "第 " + VAR_PAGE + " / " + VAR_PAGES + " 页";
        return template.replace(VAR_PAGE, String.valueOf(pageNo))
                .replace(VAR_PAGES, String.valueOf(pageCount));
    }

    /**
     * 页脚模板：取 pageSetup 的页脚 center 文本（&P/&N 占位符语义与 Excel 一致）；未配置返回 null（用缺省页脚）。
     */
    public static String footerTemplate(IExcelSheet sheet) {
        ExcelPageSetup setup = sheet.getPageSetup();
        if (setup == null || setup.getFooter() == null)
            return null;
        return setup.getFooter().getCenter();
    }

    static int avoidMergeBreak(ITableView table, int start, int end) {
        int e = end;
        boolean changed = true;
        while (changed && e > start) {
            changed = false;
            for (int r = start; r < e; r++) {
                int mergeEnd = mergeEndRow(table.getRow(r), r);
                if (mergeEnd >= e) {
                    e = r;
                    changed = true;
                    break;
                }
            }
        }
        return e;
    }

    static int mergeEndRow(IRowView row, int rowIndex) {
        int max = -1;
        List<? extends ICellView> cells = row.getCells();
        if (cells == null)
            return max;
        for (ICellView cell : cells) {
            if (cell == null)
                continue;
            int mergeDown = cell.getMergeDown();
            if (mergeDown > 0) {
                int endRow = rowIndex + mergeDown;
                if (endRow > max)
                    max = endRow;
            }
        }
        return max;
    }

    static double headerHeight(ITableView table, int headerCount, double defaultHeight) {
        double h = 0;
        for (int i = 0; i < headerCount; i++)
            h += rowHeight(table.getRow(i), defaultHeight);
        return h;
    }

    static double rowHeight(IRowView row, double defaultHeight) {
        if (row == null)
            return defaultHeight;
        Double height = row.getHeight();
        return height == null ? defaultHeight : height;
    }

    static String fmt(double v) {
        if (v == Math.floor(v))
            return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
