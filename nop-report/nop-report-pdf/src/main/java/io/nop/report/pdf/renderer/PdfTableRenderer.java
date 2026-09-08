package io.nop.report.pdf.renderer;

import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.ITableView;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.IExcelStyleProvider;
import io.nop.report.pdf.utils.PdfStyleHelper;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.List;

public class PdfTableRenderer {

    private final PdfRenderer renderer;
    private final IExcelStyleProvider styleProvider;
    private final double defaultColWidth;
    private final double defaultRowHeight;


    public PdfTableRenderer(PdfRenderer renderer, IExcelStyleProvider styleProvider,
                            double defaultColWidth, double defaultRowHeight) {
        this.renderer = renderer;
        this.styleProvider = styleProvider;
        this.defaultColWidth = defaultColWidth;
        this.defaultRowHeight = defaultRowHeight;
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
    public void renderTable(PDPageContentStream contentStream, ITableView excelTable) throws IOException {
        double currentY = 0;

        for (int row = 0; row < excelTable.getRowCount(); row++) {
            double rowHeight = rowHeight(excelTable.getRowHeight(row));
            double currentX = 0;

            List<? extends ICellView> cells = excelTable.getRowCells(row);
            for (int col = 0; col < cells.size(); col++) {
                double colWidth = colWidth(excelTable.getColWidth(col));
                ICellView cell = cells.get(col);

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
                        (float) (currentX),
                        (float) ((currentY - mergedHeight)),
                        (float) (mergedWidth),
                        (float) (mergedHeight)
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
                cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                cellRect.getWidth(), cellRect.getHeight());

        // 绘制文本
        String text = cell.getText();
        if (text != null && !text.isEmpty()) {
            // 控制字符/不换行空格归一化：必须先于字形回退选择，否则
            // 归一化后可编码的文本会因归一化前的不可编码字符误选/误抛
            text = PdfStyleHelper.normalizeControlChars(text);
            // 字形回退：base-14字体无法编码中文等字符时切换CJK回退字体
            PDFont font = renderer.fontForText(text, getFont(style));
            float fontSize = getFontSize(style);

            PdfStyleHelper.drawText(contentStream, text,
                    font, fontSize, cellRect, style);
        }

        // 绘制边框
        PdfStyleHelper.drawBorder(contentStream, cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                cellRect.getWidth(), cellRect.getHeight(), style);
    }

    /**
     * 渲染空单元格（只绘制边框）
     */
    private void renderEmptyCell(PDPageContentStream contentStream, PDRectangle cellRect) throws IOException {
        // 绘制默认边框
        PdfStyleHelper.drawBorder(contentStream, cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                cellRect.getWidth(), cellRect.getHeight(), styleProvider.getDefaultStyle());
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
        return PdfStyleHelper.getFontSize(getStyleFont(style));
    }
}