package io.nop.report.pdf.renderer;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.ITableView;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.IExcelStyleProvider;
import io.nop.report.pdf.utils.PdfStyleHelper;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.List;

public class PdfTableRenderer {

    private final PdfRenderer renderer;
    private final IExcelStyleProvider styleProvider;
    private final double margin;
    private final double defaultColWidth;
    private final double defaultRowHeight;


    public PdfTableRenderer(PdfRenderer renderer, IExcelStyleProvider styleProvider,
                            double margin, double defaultColWidth, double defaultRowHeight) {
        this.renderer = renderer;
        this.styleProvider = styleProvider;
        this.margin = margin;
        this.defaultColWidth = defaultColWidth;
        this.defaultRowHeight = defaultRowHeight;
    }

    public double getMargin() {
        return margin;
    }

    /**
     * 渲染Excel表格到PDF
     *
     * @param excelTable Excel表格模型
     * @param pageSize   页面大小
     * @throws IOException 如果渲染过程中出错
     */
    public void render(ITableView excelTable, PDRectangle pageSize) throws IOException {
        PDPage page = renderer.addPage(pageSize);

        try (PDPageContentStream contentStream = renderer.newContentStream(page)) {
            // 计算表格起始位置
            double startX = margin;
            double startY = pageSize.getHeight() - margin;

            // 计算表格总宽度和高度
            double tableWidth = calculateTableWidth(excelTable);
            double tableHeight = calculateTableHeight(excelTable);

            // 调整起始位置，使表格居中
            if (tableWidth < pageSize.getWidth() - 2 * margin) {
                startX = (pageSize.getWidth() - tableWidth) / 2;
            }

            // 渲染表格
            renderTable(contentStream, excelTable, startX, startY);
        }
    }

    /**
     * 计算表格总宽度
     */
    private double calculateTableWidth(ITableView excelTable) {
        double width = 0;
        for (int i = 0; i < excelTable.getColCount(); i++) {
            width += colWidth(excelTable.getColWidth(i));
        }
        return width;
    }

    /**
     * 计算表格总高度
     */
    private double calculateTableHeight(ITableView excelTable) {
        double height = 0;
        for (int i = 0; i < excelTable.getRowCount(); i++) {
            height += rowHeight(excelTable.getRowHeight(i));
        }
        return height;
    }

    double rowHeight(Double value) {
        if (value == null)
            return defaultRowHeight;
        return value;
    }

    double colWidth(Double value) {
        if (value == null)
            return defaultColWidth;
        return value;
    }

    /**
     * 渲染整个表格
     */
    private void renderTable(PDPageContentStream contentStream, ITableView excelTable, double startX, double startY) throws IOException {
        double currentY = startY;

        for (int row = 0; row < excelTable.getRowCount(); row++) {
            double rowHeight = excelTable.getRowHeight(row);
            double currentX = startX;

            List<? extends ICellView> cells = excelTable.getRowCells(row);
            for (int col = 0; col < cells.size(); col++) {
                double colWidth = excelTable.getColWidth(col);
                ICellView cell = cells.get(row);

                // 跳过合并单元格的非主单元格
                if (cell != null && cell.isMergedCell() && !cell.isMergeParent()) {
                    currentX += colWidth;
                    continue;
                }

                // 处理合并单元格
                int rowSpan = cell != null ? cell.getMergeDown() + 1 : 1;
                int colSpan = cell != null ? cell.getMergeAcross() + 1 : 1;

                // 计算合并后的单元格宽度和高度
                double mergedWidth = calculateMergedWidth(excelTable, col, colSpan);
                double mergedHeight = calculateMergedHeight(excelTable, row, rowSpan);

                // 创建单元格矩形区域
                PDRectangle cellRect = new PDRectangle(
                        (float) currentX,
                        (float) (currentY - mergedHeight),
                        (float) mergedWidth,
                        (float) mergedHeight
                );

                // 渲染单元格
                if (cell != null) {
                    renderCell(contentStream, cell, cellRect);
                } else {
                    // 空单元格只绘制边框
                    renderEmptyCell(contentStream, cellRect);
                }

                currentX += colWidth;
            }

            currentY -= rowHeight;
        }
    }

    /**
     * 计算合并单元格的宽度
     */
    private double calculateMergedWidth(ITableView excelTable, int startCol, int colSpan) {
        double width = 0;
        for (int i = 0; i < colSpan; i++) {
            width += colWidth(excelTable.getColWidth(startCol + i));
        }
        return width;
    }

    /**
     * 计算合并单元格的高度
     */
    private double calculateMergedHeight(ITableView excelTable, int startRow, int rowSpan) {
        double height = 0;
        for (int i = 0; i < rowSpan; i++) {
            height += rowHeight(excelTable.getRowHeight(startRow + i));
        }
        return height;
    }

    /**
     * 渲染单元格内容
     */
    private void renderCell(PDPageContentStream contentStream, ICellView cell, PDRectangle cellRect) throws IOException {
        ExcelStyle style = getStyle(cell.getStyleId());

        // 设置单元格背景
        PdfStyleHelper.setCellBackground(contentStream, style,
                cellRect.getLowerLeftX(), cellRect.getUpperRightY(),
                cellRect.getWidth(), cellRect.getHeight());

        // 绘制文本
        String text = cell.getText();
        if (text != null && !text.isEmpty()) {
            PDFont font = getFont(style);
            float fontSize = getFontSize(style);

            PdfStyleHelper.drawText(contentStream, text,
                    font, fontSize, cellRect, style);
        }

        // 绘制边框
        PdfStyleHelper.drawBorder(contentStream, cellRect, style);
    }

    /**
     * 渲染空单元格（只绘制边框）
     */
    private void renderEmptyCell(PDPageContentStream contentStream, PDRectangle cellRect) throws IOException {
        // 绘制默认边框
        PdfStyleHelper.drawBorder(contentStream, cellRect, styleProvider.getDefaultStyle());
    }

    protected ExcelStyle getStyle(String styleId) {
        if (StringHelper.isEmpty(styleId)) {
            return styleProvider.getDefaultStyle();
        }
        ExcelStyle style = styleProvider.getStyle(styleId);
        if (style == null)
            style = styleProvider.getDefaultStyle();
        return style;
    }

    /**
     * 获取单元格字体
     */
    private PDFont getFont(ExcelStyle style) {
        ExcelFont font = getStyleFont(style);
        String fontName = null;
        boolean bold = false;
        boolean italic = false;
        if (font != null) {
            fontName = font.getFontName();
            bold = font.isBold();
            italic = font.isItalic();
        }

        return renderer.getFont(fontName, bold, italic);
    }

    protected ExcelFont getStyleFont(ExcelStyle style) {
        if (style == null) {
            return styleProvider.getDefaultFont();
        }
        ExcelFont font = style.getFont();
        if (font == null)
            font = styleProvider.getDefaultFont();
        return font;
    }

    /**
     * 获取单元格字体大小
     */
    private float getFontSize(ExcelStyle style) {
        return getStyleFont(style).getFontSize();
    }
}