package io.nop.report.core.util;

import io.nop.commons.util.StringHelper;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;

import java.util.Objects;
import java.util.function.BiPredicate;

public class ReportDataHelper {

    public static boolean isCellEmpty(ExpandedCell prev, ExpandedCell cell) {
        return StringHelper.isBlank(cell.getText());
    }

    public static boolean isCellSameText(ExpandedCell prev, ExpandedCell cell) {
        return Objects.equals(prev.getText(), cell.getText());
    }

    public static void mergeDownWithEmpty(IXptRuntime xptRt, String cellName) {
        mergeDown(xptRt, cellName, ReportDataHelper::isCellEmpty);
    }

    public static void mergeDownWithSameText(IXptRuntime xptRt, String cellName) {
        mergeDown(xptRt, cellName, ReportDataHelper::isCellSameText);
    }

    public static void mergeDown(IXptRuntime xptRt, String cellName, BiPredicate<ExpandedCell, ExpandedCell> checkAllowMerge) {
        ExpandedCellSet cellSet = xptRt.getNamedCellSet(cellName);
        if (cellSet.size() <= 1)
            return;

        ExpandedCell prev = cellSet.get(0);
        int mergeDown = 0;
        for (int i = 1, n = cellSet.size(); i < n; i++) {
            ExpandedCell cell = cellSet.get(i);
            if (checkAllowMerge.test(prev, cell)) {
                mergeDown++;
                cell.setRealCell(prev.getRealCell());
            } else {
                if (mergeDown > 0) {
                    prev.setMergeDown(mergeDown);
                    mergeDown = 0;
                }
                prev = cell;
            }
        }

        if (mergeDown > 0) {
            prev.setMergeDown(mergeDown);
        }
    }

    public static void mergeTreeWithEmpty(IXptRuntime xptRt, String startCellName, String endCellName) {
        mergeTree(xptRt, startCellName, endCellName, ReportDataHelper::isCellEmpty);
    }

    public static void mergeTree(IXptRuntime xptRt, String startCellName, String endCellName,
                                 BiPredicate<ExpandedCell, ExpandedCell> checkAllowMerge) {
        ExpandedCellSet cellSet = xptRt.getNamedCellSet(startCellName);
        if (cellSet.size() <= 1)
            return;

        ExpandedCell startCell = cellSet.get(0);
        ExpandedCell endCell = xptRt.getNamedCellSet(endCellName).getFirstCell();
        int startColIndex = startCell.getColIndex();
        int endColIndex = endCell.getColIndex();

        _mergeDown(startCell, cellSet.getRowCount() - 1, checkAllowMerge);
        int colCount = endColIndex - startColIndex;
        for (int i = 0, n = cellSet.size(); i < n; i++) {
            ExpandedCell cell = cellSet.get(i);
            mergeRight(cell, colCount, checkAllowMerge);
            i += cell.getMergeDown();
        }
    }

    private static void mergeRight(ExpandedCell cell, int count, BiPredicate<ExpandedCell, ExpandedCell> checkAllowMerge) {
        if (cell.getMergeDown() == 0 || count <= 0)
            return;

        _mergeDown(cell.getRight(), cell.getMergeDown(), checkAllowMerge);
        if (count > 1) {
            ExpandedCell prev = cell.getRight();
            int i = 0;
            int maxDown = cell.getMergeDown();
            while (i < maxDown) {
                ExpandedCell next = prev.getDown();
                if (next == null)
                    break;
                if (!next.isProxyCell()) {
                    mergeRight(next, count - 1, checkAllowMerge);
                }
                prev = next;
                i++;
            }
        }
    }

    private static void _mergeDown(ExpandedCell cell, int maxDown,
                                   BiPredicate<ExpandedCell, ExpandedCell> checkAllowMerge) {
        int i = 0;
        int mergeDown = 0;
        ExpandedCell prev = cell;
        while (i < maxDown) {
            ExpandedCell next = prev.getDown();
            if (next == null)
                break;
            if (checkAllowMerge.test(prev, next)) {
                mergeDown++;
                next.setRealCell(prev.getRealCell());
            } else {
                if (mergeDown > 0) {
                    prev.getRealCell().changeRowSpan(mergeDown);
                    mergeDown = 0;
                }
            }
            prev = next;
            i++;
        }
        if (mergeDown > 0) {
            prev.getRealCell().changeRowSpan(mergeDown);
        }
    }
}
