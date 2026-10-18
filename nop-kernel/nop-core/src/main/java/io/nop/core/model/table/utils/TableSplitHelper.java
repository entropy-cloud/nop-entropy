package io.nop.core.model.table.utils;

import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ITableView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        return splitTable(table, maxWidth, maxHeight, defaultColWidth, defaultRowHeight, enableColumnPaging,
                null, null);
    }

    /**
     * 拆分表格为多个单元格区域，支持手动分页符（WI3）。
     *
     * @param rowBreakAfter 0-based 行索引集合：该行是分页前页面的最后一行（对齐 OOXML brk@id 语义）；
     *                      null/空集表示无手动行分页符
     * @param colBreakAfter 0-based 列索引集合：语义同上（列方向）
     */
    public static List<CellRange> splitTable(ITableView table,
                                             double maxWidth,
                                             double maxHeight,
                                             double defaultColWidth,
                                             double defaultRowHeight,
                                             boolean enableColumnPaging,
                                             Set<Integer> rowBreakAfter,
                                             Set<Integer> colBreakAfter) {
        List<CellRange> regions = new ArrayList<>();
        int startRow = 0;

        // 防御：0 列（或 0 行）表在列分页分支中 startCol 永不推进，外层 while 会死循环
        if (table.getColCount() == 0 || table.getRowCount() == 0)
            return regions;

        while (startRow < table.getRowCount()) {
            if (enableColumnPaging) {
                int startCol = 0;
                while (startCol < table.getColCount()) {
                    CellRange region = calculatePageRegion(table, startRow, startCol,
                            defaultColWidth, defaultRowHeight, maxWidth, maxHeight,
                            rowBreakAfter, colBreakAfter);
                    regions.add(region);

                    startCol = region.getLastColIndex() + 1;
                    if (startCol >= table.getColCount()) {
                        startRow = region.getLastRowIndex() + 1;
                    }
                }
            } else {
                CellRange region = calculateRowOnlyRegion(table, startRow, defaultRowHeight, maxHeight, rowBreakAfter);
                regions.add(region);
                startRow = region.getLastRowIndex() + 1;
            }
        }
        return regions;
    }

    private static CellRange calculateRowOnlyRegion(ITableView table, int startRow, double defaultRowHeight,
                                                    double maxHeight, Set<Integer> rowBreakAfter) {
        int endRow = startRow;
        int startCol = 0;
        int endCol = table.getColCount() - 1;
        double currentHeight = 0;

        for (int row = startRow; row < table.getRowCount(); row++) {
            double rowHeight = table.getRowHeight(row, defaultRowHeight);

            if (row > startRow && rowBreakAfter != null && rowBreakAfter.contains(row - 1)) {
                // 手动分页符：前一页以 row-1 行结束
                break;
            }

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
                                                 double maxWidth, double maxHeight,
                                                 Set<Integer> rowBreakAfter, Set<Integer> colBreakAfter) {
        // 计算列范围
        int endCol = startCol;
        double currentWidth = 0;
        for (int col = startCol; col < table.getColCount(); col++) {
            double colWidth = table.getColWidth(col, defaultColWidth);
            if (col > startCol && colBreakAfter != null && colBreakAfter.contains(col - 1)) {
                // 手动分页符：本页以 col-1 列结束
                break;
            }
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
            if (row > startRow && rowBreakAfter != null && rowBreakAfter.contains(row - 1)) {
                break;
            }
            if (currentHeight + rowHeight > maxHeight && row > startRow) {
                break;
            }
            currentHeight += rowHeight;
            endRow = row;
        }

        return new CellRange(startRow, startCol, endRow, endCol);
    }
}
