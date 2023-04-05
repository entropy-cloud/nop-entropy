/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.coordinate;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.model.ExpandedCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 根据层次坐标获取到符合坐标条件的所有单元格
 */
public class CellCoordinateHelper {
    static final Logger LOG = LoggerFactory.getLogger(CellCoordinateHelper.class);

    public static List<ExpandedCell> resolveLayerCoordinate(ExpandedCell cell, CellLayerCoordinate layerCoord) {
        if (layerCoord.getRowCoordinates() == null && layerCoord.getColCoordinates() == null) {
            return resolveCell(cell, layerCoord.getCellName());
        }

        List<ExpandedCell> rowCells = resolveRowCoordinates(cell, layerCoord.getCellName(), layerCoord.getRowCoordinates());
        List<ExpandedCell> colCells = resolveColCoordinates(cell, layerCoord.getCellName(), layerCoord.getColCoordinates());
        if (rowCells != null && colCells != null) {
            return CollectionHelper.intersection(rowCells, colCells);
        } else if (rowCells != null) {
            return rowCells;
        } else if (colCells != null) {
            return colCells;
        } else {
            return null;
        }
    }

    private static List<ExpandedCell> resolveRowCoordinates(ExpandedCell cell, String cellName,
                                                            List<CellCoordinate> coords) {
        if (coords == null || coords.isEmpty())
            return null;

        ExpandedCell resolvedCell = null;

        for (CellCoordinate coord : coords) {
            List<ExpandedCell> cells;
            if (resolvedCell == null) {
                cells = resolveAllCellsInRowParent(cell, coord.getCellName());
            } else {
                cells = resolvedCell.getRowDescendants().get(coord.getCellName());
            }
            if (cells == null || cells.isEmpty()) {
                LOG.info("nop.xpt.cell-row-coordinate-resolve-to-null:cellName={}, coordinates={}",
                        coord.getCellName(), coords);
                return null;
            }

            int pos = coord.getPosition();
            if (coord.isReverse() && pos < 0) {
                int index = cells.size() + pos;
                if (index >= 0) {
                    resolvedCell = cells.get(index);
                }
            } else {
                if (coord.isRelative()) {
                    ExpandedCell parentCell = cell.getRowClosest(coord.getCellName());
                    if (parentCell == null) {
                        LOG.info("nop.xpt.cell-row-coordinate-resolve-to-null:cellName={}, coordinates={}",
                                coord.getCellName(), coords);
                        return null;
                    }
                    int index = getRowExpandIndex(parentCell);
                    if (index >= 0) {
                        // 这里pos为0或者小于0
                        index += pos;
                        if (index >= 0 && index < cells.size()) {
                            resolvedCell = cells.get(index);
                        }
                    }
                } else {
                    if (pos > 0 && pos <= cells.size()) {
                        resolvedCell = cells.get(pos - 1);
                    }
                }
            }

            if (resolvedCell == null)
                return null;
        }

        return resolvedCell.getRowDescendants().get(cellName);
    }

    public static int getRowExpandIndex(ExpandedCell cell) {
        if (cell.getExpandType() == XptExpandType.r) {
            return cell.getExpandIndex();
        }
        return -1;
    }

    public static int getColExpandIndex(ExpandedCell cell) {
        if (cell.getExpandType() == XptExpandType.c) {
            return cell.getExpandIndex();
        }
        return -1;
    }
//
//    public static int findRowIndex(List<ExpandedCell> cells, int rowIndex, ExpandedRow row) {
//        int index = -1;
//        // 查找到当前单元格所在行随对应的单元格，然后再确定相对位置
//        for (int i = 0, n = cells.size(); i < n; i++) {
//            ExpandedCell child = cells.get(i);
//            ExpandedRow childRow = child.getRow();
//            if (childRow == row) {
//                index = i;
//                break;
//            }
//            int childRowIndex = childRow.getRowIndex();
//            if (rowIndex >= childRowIndex && rowIndex <= childRowIndex + child.getMergeDown()) {
//                index = i;
//                break;
//            }
//        }
//        return index;
//    }
//
//    public static int findColIndex(List<ExpandedCell> cells, int colIndex, ExpandedCol col) {
//        int index = -1;
//        // 查找到当前单元格所在行随对应的单元格，然后再确定相对位置
//        for (int i = 0, n = cells.size(); i < n; i++) {
//            ExpandedCell child = cells.get(i);
//            ExpandedCol childCol = child.getCol();
//            if (childCol == col) {
//                index = i;
//                break;
//            }
//            int childColIndex = childCol.getColIndex();
//            if (colIndex >= childColIndex && colIndex <= childColIndex + child.getMergeDown()) {
//                index = i;
//                break;
//            }
//        }
//        return index;
//    }

    // tell cpd to start ignoring code - CPD-OFF
    private static List<ExpandedCell> resolveColCoordinates(ExpandedCell cell, String cellName,
                                                            List<CellCoordinate> coords) {
        if (coords == null || coords.isEmpty())
            return null;

        ExpandedCell resolvedCell = null;

        for (CellCoordinate coord : coords) {
            List<ExpandedCell> cells;
            if (resolvedCell == null) {
                cells = resolveAllCellsInColParent(cell, coord.getCellName());
            } else {
                cells = resolvedCell.getColDescendants().get(coord.getCellName());
            }
            if (cells == null) {
                LOG.info("nop.xpt.cell-col-coordinate-resolve-to-null:cellName={}, coordinates={}",
                        coord.getCellName(), coords);
                return null;
            }

            int pos = coord.getPosition();
            if (coord.isReverse() && pos < 0) {
                int index = cells.size() + pos;
                if (index >= 0) {
                    resolvedCell = cells.get(index);
                }
            } else {
                if (coord.isRelative()) {
                    ExpandedCell parentCell = cell.getColClosest(coord.getCellName());
                    if (parentCell == null) {
                        LOG.info("nop.xpt.cell-col-coordinate-resolve-to-null:cellName={}, coordinates={}",
                                coord.getCellName(), coords);
                        return null;
                    }
                    int index = getColExpandIndex(parentCell);
                    if (index >= 0) {
                        // 这里pos为0或者小于0
                        index += pos;
                        if (index >= 0 && index < cells.size()) {
                            resolvedCell = cells.get(index);
                        }
                    }
                } else {
                    if (pos < cells.size()) {
                        resolvedCell = cells.get(pos - 1);
                    }
                }
            }

            if (resolvedCell == null)
                return null;
        }

        return resolvedCell.getColDescendants().get(cellName);
    }
    // resume CPD analysis - CPD-ON

    /**
     * 以cell的当前位置为基准，查找名称为指定cellName的最近的父格或者兄弟子格。
     *
     * @param cell     当前cell
     * @param cellName 待查找的单元格名称
     */
    public static List<ExpandedCell> resolveCell(ExpandedCell cell, String cellName) {
        List<ExpandedCell> rowCells = resolveCellInRowParents(cell, cellName);
        List<ExpandedCell> colCells = resolveCellInColParents(cell, cellName);
        if (rowCells != null && colCells != null) {
            return CollectionHelper.intersection(rowCells, colCells);
        } else if (rowCells != null) {
            return rowCells;
        } else if (colCells != null) {
            return colCells;
        } else {
            return cell.getTable().getNamedCells(cellName);
        }
    }

    public static List<ExpandedCell> resolveCellRange(ExpandedCell cell, CellRange range) {
        List<ExpandedCell> ret = new ArrayList<>();
        int lastRow = range.getLastRowIndex();
        int lastCol = range.getLastColIndex();
        for (int i = range.getFirstRowIndex(); i <= lastRow; i++) {
            for (int j = range.getFirstColIndex(); j <= lastCol; j++) {
                String cellName = CellPosition.toABString(i, j);
                List<ExpandedCell> cells = resolveCell(cell, cellName);
                ret.addAll(cells);
            }
        }
        return ret;
    }

    private static List<ExpandedCell> resolveAllCellsInRowParent(ExpandedCell cell, String cellName) {
        ExpandedCell parent = cell.getRowParent();
        if (parent == null) {
            return cell.getTable().getNamedCells(cellName);
        }

        if (cellName.equals(cell.getName())) {
            if (cell.getExpandType() == XptExpandType.r) {
                return parent.getRowDescendants().get(cellName);
            } else {
                return Collections.singletonList(cell);
            }
        } else {
            return resolveAllCellsInRowParent(parent, cellName);
        }
    }

    private static List<ExpandedCell> resolveAllCellsInColParent(ExpandedCell cell, String cellName) {
        ExpandedCell parent = cell.getColParent();
        if (parent == null) {
            return cell.getTable().getNamedCells(cellName);
        }

        if (cellName.equals(cell.getName())) {
            if (cell.getExpandType() == XptExpandType.c) {
                return parent.getRowDescendants().get(cellName);
            } else {
                return Collections.singletonList(cell);
            }
        } else {
            return resolveAllCellsInColParent(parent, cellName);
        }
    }

    private static List<ExpandedCell> resolveCellInRowParents(ExpandedCell cell, String cellName) {
        ExpandedCell rowParent = cell.getRowParent();
        if (rowParent == null)
            return null;

        if (rowParent.getName().equals(cellName)) {
            return Collections.singletonList(rowParent);
        }

        List<ExpandedCell> cells = rowParent.getRowDescendants().get(cellName);
        if (cells != null)
            return cells;

        return resolveCellInRowParents(rowParent, cellName);
    }

    private static List<ExpandedCell> resolveCellInColParents(ExpandedCell cell, String cellName) {
        ExpandedCell colParent = cell.getColParent();
        if (colParent == null)
            return null;

        if (colParent.getName().equals(cellName)) {
            return Collections.singletonList(colParent);
        }

        List<ExpandedCell> cells = colParent.getColDescendants().get(cellName);
        if (cells != null)
            return cells;

        return resolveCellInColParents(colParent, cellName);
    }
}
