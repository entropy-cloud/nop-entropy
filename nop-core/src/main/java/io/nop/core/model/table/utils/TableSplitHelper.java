package io.nop.core.model.table.utils;

import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ITableView;

import java.util.ArrayList;
import java.util.List;

public class TableSplitHelper {

    /**
     * 拆分表格为多个单元格区域
     *
     * @param table              原始表格
     * @param maxWidth           最大允许宽度
     * @param maxHeight          最大允许高度
     * @param enableColumnPaging 是否启用列分页
     * @return 拆分后的单元格区域列表
     */
    public static List<CellRange> splitTable(ITableView table,
                                             double maxWidth,
                                             double maxHeight,
                                             double defaultColWidth,
                                             double defaultRowHeight,
                                             boolean enableColumnPaging) {
        List<CellRange> regions = new ArrayList<>();
        int startRow = 0;

        while (startRow < table.getRowCount()) {
            if (enableColumnPaging) {
                int startCol = 0;
                while (startCol < table.getColCount()) {
                    CellRange region = calculatePageRegion(table, startRow, startCol,
                            defaultColWidth, defaultRowHeight, maxWidth, maxHeight);
                    regions.add(region);

                    startCol = region.getLastColIndex() + 1;
                    if (startCol >= table.getColCount()) {
                        startRow = region.getLastRowIndex() + 1;
                    }
                }
            } else {
                CellRange region = calculateRowOnlyRegion(table, startRow, defaultRowHeight, maxHeight);
                regions.add(region);
                startRow = region.getLastRowIndex() + 1;
            }
        }
        return regions;
    }

    private static CellRange calculateRowOnlyRegion(ITableView table, int startRow, double defaultRowHeight, double maxHeight) {
        int endRow = startRow;
        int startCol = 0;
        int endCol = table.getColCount() - 1;
        double currentHeight = 0;

        for (int row = startRow; row < table.getRowCount(); row++) {
            double rowHeight = table.getRowHeight(row, defaultRowHeight);

            if (currentHeight + rowHeight > maxHeight && row > startRow) {
                break;
            }

            currentHeight += rowHeight;
            endRow = row;
        }

        return new CellRange(startRow, startCol, endRow, endCol);
    }

    private static CellRange calculatePageRegion(ITableView table, int startRow, int startCol,
                                                 double defaultColWidth, double defaultRowHeight,
                                                 double maxWidth, double maxHeight) {
        // 计算列范围
        int endCol = startCol;
        double currentWidth = 0;
        for (int col = startCol; col < table.getColCount(); col++) {
            double colWidth = table.getColWidth(col, defaultColWidth);
            if (currentWidth + colWidth > maxWidth && col > startCol) {
                break;
            }
            currentWidth += colWidth;
            endCol = col;
        }

        // 计算行范围
        int endRow = startRow;
        double currentHeight = 0;
        for (int row = startRow; row < table.getRowCount(); row++) {
            double rowHeight = table.getRowHeight(row, defaultRowHeight);
            if (currentHeight + rowHeight > maxHeight && row > startRow) {
                break;
            }
            currentHeight += rowHeight;
            endRow = row;
        }

        return new CellRange(startRow, startCol, endRow, endCol);
    }
}
