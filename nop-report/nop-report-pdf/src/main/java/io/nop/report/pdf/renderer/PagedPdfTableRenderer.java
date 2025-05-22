package io.nop.report.pdf.renderer;

import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.ITableView;
import io.nop.excel.model.IExcelStyleProvider;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PagedPdfTableRenderer {
    private final PdfTableRenderer tableRenderer;
    private boolean enableColumnPaging = true; // 默认开启列分页

    public PagedPdfTableRenderer(PdfRenderer renderer, IExcelStyleProvider styleProvider,
                                 double margin, double defaultColWidth, double defaultRowHeight) {
        this.tableRenderer = new PdfTableRenderer(renderer, styleProvider, margin, defaultColWidth, defaultRowHeight);
    }

    /**
     * 设置是否启用列分页
     *
     * @param enable 是否启用列分页
     */
    public void setEnableColumnPaging(boolean enable) {
        this.enableColumnPaging = enable;
    }

    /**
     * 渲染Excel表格到PDF，自动分页
     *
     * @param excelTable Excel表格模型
     * @param pageSize   页面大小
     * @throws IOException 如果渲染过程中出错
     */
    public void render(ITableView excelTable, PDRectangle pageSize) throws IOException {
        // 将大表格拆分成适合页面大小的多个小表格
        List<ITableView> tableViews = splitTableToPages(excelTable, pageSize);

        // 渲染每个分页表格
        for (ITableView tableView : tableViews) {
            renderTableView(tableView, pageSize);
        }
    }

    /**
     * 将大表格拆分成适合页面大小的多个小表格
     * 根据enableColumnPaging决定是否对列进行分页
     */
    private List<ITableView> splitTableToPages(ITableView excelTable, PDRectangle pageSize) {
        List<ITableView> tableViews = new ArrayList<>();

        // 计算页面可用区域（减去边距和表头）
        double maxTableWidth = pageSize.getWidth() - 2 * tableRenderer.getMargin();
        double maxTableHeight = pageSize.getHeight() - 2 * tableRenderer.getMargin();

        // 当前分页的起始行
        int startRow = 0;

        while (startRow < excelTable.getRowCount()) {
            if (enableColumnPaging) {
                // 启用列分页模式
                int startCol = 0;
                while (startCol < excelTable.getColCount()) {
                    PageRegion region = calculatePageRegion(excelTable, startRow, startCol,
                            maxTableWidth, maxTableHeight);

                    ITableView tableView = createTableView(excelTable, region);
                    tableViews.add(tableView);

                    startCol = region.endCol + 1;

                    // 如果列已经处理完，移动到下一组行
                    if (startCol >= excelTable.getColCount()) {
                        startRow = region.endRow + 1;
                    }
                }
            } else {
                // 禁用列分页，只进行行分页
                PageRegion region = calculateRowOnlyPageRegion(excelTable, startRow,
                        maxTableWidth, maxTableHeight);

                ITableView tableView = createTableView(excelTable, region);
                tableViews.add(tableView);

                startRow = region.endRow + 1;
            }
        }

        return tableViews;
    }

    /**
     * 只考虑行分页的区域计算
     */
    private PageRegion calculateRowOnlyPageRegion(ITableView excelTable, int startRow,
                                                  double maxWidth, double maxHeight) {
        PageRegion region = new PageRegion();
        region.startRow = startRow;
        region.startCol = 0; // 总是从第一列开始
        region.endCol = excelTable.getColCount() - 1; // 总是到最后一列
        region.width = calculateRegionWidth(excelTable, region);
        region.height = 0;

        for (int row = startRow; row < excelTable.getRowCount(); row++) {
            double rowHeight = calculateRowHeightInRegion(excelTable, row, region.startCol, region.endCol);

            if (region.height + rowHeight > maxHeight && row > startRow) {
                break;
            }

            region.height += rowHeight;
            region.endRow = row;
        }

        // 检查合并单元格是否跨页
        adjustForMergedCells(excelTable, region);

        return region;
    }

    /**
     * 计算当前分页能容纳的行列范围
     */
    private PageRegion calculatePageRegion(ITableView excelTable, int startRow, int startCol,
                                           double maxWidth, double maxHeight) {
        PageRegion region = new PageRegion();
        region.startRow = startRow;
        region.startCol = startCol;
        region.endRow = startRow;
        region.endCol = startCol;
        region.width = 0;
        region.height = 0;

        // 1. 先确定横向能容纳多少列
        for (int col = startCol; col < excelTable.getColCount(); col++) {
            double colWidth = excelTable.getColWidth(col);

            // 如果加上这列会超出宽度，则停止
            if (region.width + colWidth > maxWidth && col > startCol) {
                break;
            }

            region.width += colWidth;
            region.endCol = col;
        }

        // 2. 在确定的列范围内，计算纵向能容纳多少行
        for (int row = startRow; row < excelTable.getRowCount(); row++) {
            // 计算当前行在选定列范围内的最大高度（考虑合并行）
            double rowHeight = calculateRowHeightInRegion(excelTable, row, region.startCol, region.endCol);

            // 如果加上这行会超出高度，则停止
            if (region.height + rowHeight > maxHeight && row > startRow) {
                break;
            }

            region.height += rowHeight;
            region.endRow = row;
        }

        // 3. 检查合并单元格是否跨页，调整分页边界
        adjustForMergedCells(excelTable, region);

        return region;
    }

    /**
     * 计算行在指定列范围内的最大高度（考虑合并行）
     */
    private double calculateRowHeightInRegion(ITableView excelTable, int row, int startCol, int endCol) {
        double maxHeight = excelTable.getRowHeight(row);

        // 检查该行在指定列范围内的所有单元格
        for (int col = startCol; col <= endCol; col++) {
            ICellView cell = excelTable.getCell(row, col);
            if (cell != null && cell.isMergeParent()) {
                // 计算合并单元格跨越的行数
                int mergeDown = cell.getMergeDown();
                int mergeAcross = cell.getMergeAcross();

                // 如果合并单元格超出当前列范围，需要调整
                if (col + mergeAcross > endCol) {
                    // 这种情况需要特殊处理，这里简化处理为不拆分合并单元格
                    // 实际应用中可能需要更复杂的逻辑
                }

                // 计算合并单元格的总高度
                double mergedHeight = 0;
                for (int r = row; r <= row + mergeDown && r < excelTable.getRowCount(); r++) {
                    mergedHeight += excelTable.getRowHeight(r);
                }

                if (mergedHeight > maxHeight) {
                    maxHeight = mergedHeight;
                }
            }
        }

        return maxHeight;
    }

    /**
     * 调整分页边界以适应合并单元格
     */
    private void adjustForMergedCells(ITableView excelTable, PageRegion region) {
        // 检查分页边界上的单元格是否有跨页合并
        for (int row = region.startRow; row <= region.endRow; row++) {
            for (int col = region.startCol; col <= region.endCol; col++) {
                ICellView cell = excelTable.getCell(row, col);
                if (cell != null && cell.isMergeParent()) {
                    int mergeDown = cell.getMergeDown();
                    int mergeAcross = cell.getMergeAcross();

                    // 检查垂直合并是否跨页
                    if (row + mergeDown > region.endRow) {
                        // 需要扩展分页的行范围或拆分合并单元格
                        // 这里选择扩展行范围
                        region.endRow = Math.min(row + mergeDown, excelTable.getRowCount() - 1);
                        // 重新计算高度
                        region.height = calculateRegionHeight(excelTable, region);
                    }

                    // 检查水平合并是否跨页
                    if (col + mergeAcross > region.endCol) {
                        // 需要扩展分页的列范围或拆分合并单元格
                        // 这里选择扩展列范围
                        region.endCol = Math.min(col + mergeAcross, excelTable.getColCount() - 1);
                        // 重新计算宽度
                        region.width = calculateRegionWidth(excelTable, region);
                    }
                }
            }
        }
    }

    /**
     * 计算区域总高度
     */
    private double calculateRegionHeight(ITableView excelTable, PageRegion region) {
        double height = 0;
        for (int row = region.startRow; row <= region.endRow; row++) {
            height += excelTable.getRowHeight(row);
        }
        return height;
    }

    /**
     * 计算区域总宽度
     */
    private double calculateRegionWidth(ITableView excelTable, PageRegion region) {
        double width = 0;
        for (int col = region.startCol; col <= region.endCol; col++) {
            width += excelTable.getColWidth(col);
        }
        return width;
    }

    /**
     * 创建分页表格视图
     */
    private ITableView createTableView(ITableView excelTable, PageRegion region) {
        return excelTable.getSubTable(region.startRow, region.startCol, region.endRow, region.endCol).clip();
    }

    /**
     * 渲染单个分页表格
     */
    private void renderTableView(ITableView tableView, PDRectangle pageSize) throws IOException {
        tableRenderer.render(tableView, pageSize);
    }

    /**
     * 内部类，用于表示分页区域
     */
    private static class PageRegion {
        int startRow;
        int endRow;
        int startCol;
        int endCol;
        double width;
        double height;
    }
}



