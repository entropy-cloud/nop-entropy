/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.build;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICell;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.XptRowModel;
import io.nop.excel.model.constants.XptExpandType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.report.core.XptErrors.ARG_CELL_POS;
import static io.nop.report.core.XptErrors.ARG_COL_PARENT;
import static io.nop.report.core.XptErrors.ARG_ROW_PARENT;
import static io.nop.report.core.XptErrors.ARG_SHEET_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_COL_PARENT_CONTAINS_LOOP;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_COL_PARENT;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_ROW_PARENT;
import static io.nop.report.core.XptErrors.ERR_XPT_ROW_PARENT_CONTAINS_LOOP;

/**
 * 分析rowParent和colParent的关联关系，产生的分析结果保存在XptCellModel中
 */
public class XptModelBuilder {

    public void build(ExcelWorkbook workbook) {
        for (ExcelSheet sheet : workbook.getSheets()) {
            buildSheetModel(sheet);
        }
    }

    public void buildSheetModel(ExcelSheet sheet) {
        Map<String, ExcelCell> cells = new TreeMap<>();
        initCols(sheet);
        initXptModels(sheet, cells);
        initParentChildren(sheet, cells);
        initDuplicateCells(sheet, cells);
        initExtendCells(sheet, cells);
    }

    private void initCols(ExcelSheet sheet) {
        ExcelTable table = sheet.getTable();
        if (table != null) {
            List<ExcelColumnConfig> cols = table.getCols();
            if (cols != null) {
                for (int i = 0, n = cols.size(); i < n; i++) {
                    ExcelColumnConfig col = cols.get(i);
                    if (col == null) {
                        col = new ExcelColumnConfig();
                        cols.set(i, col);
                    }
                }
            }
        }
    }

    private void initXptModels(ExcelSheet sheet, Map<String, ExcelCell> cells) {
        ExcelTable table = sheet.getTable();
        int colCount = table.getColCount();
        int rowCount = table.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            ExcelRow row = table.getRow(i);
            XptRowModel model = row.getModel();
            if (model == null) {
                model = new XptRowModel();
                row.setModel(model);
            }

            for (int j = 0; j < colCount; j++) {
                ICell cell = row.getCell(j);
                if (cell == null) {
                    cell = new ExcelCell();
                    row.internalSetCell(j, cell);
                } else if (cell.isProxyCell()) {
                    continue;
                }

                ExcelCell ec = (ExcelCell) cell;
                String name = CellPosition.toABString(i, j);
                cells.put(name, ec);

                XptCellModel xptModel = ec.getModel();
                if (xptModel == null) {
                    xptModel = new XptCellModel();
                    ec.setModel(xptModel);
                }
                xptModel.setRowIndex(i);
                xptModel.setColIndex(j);
                xptModel.setName(name);
            }
        }
    }

    private void initParentChildren(ExcelSheet sheet, Map<String, ExcelCell> cells) {
        sheet.getTable().forEachRealCell((ic, rowIndex, colIndex) -> {
            ExcelCell cell = (ExcelCell) ic;
            XptCellModel xptModel = cell.getModel();
            ExcelCell rowParent = getRowParent(sheet, cell, cells);
            if (rowParent != null) {
                if (xptModel.getRowParent() == null) {
                    xptModel.setRowParent(rowParent.getModel().getCellPosition());
                }
                xptModel.setRowParentCell(rowParent);
                rowParent.getModel().addRowChildCell(cell);
            }

            ExcelCell colParent = getColParent(sheet, cell, cells);
            if (colParent != null) {
                if (xptModel.getColParent() == null) {
                    xptModel.setColParent(colParent.getModel().getCellPosition());
                }
                xptModel.setColParentCell(colParent);
                colParent.getModel().addColChildCell(cell);
            }
            return ProcessResult.CONTINUE;
        });
    }

    private ExcelCell getRowParent(ExcelSheet sheet, ExcelCell cell, Map<String, ExcelCell> cells) {
        XptCellModel xptModel = cell.getModel();

        if (xptModel.getRowParent() != null) {
            return resolveRowParent(sheet, cell, cells, xptModel.getRowParent());
        } else {
            if (xptModel.getColIndex() == 0) {
                return null;
            }

            ExcelRow row = sheet.getTable().getRow(xptModel.getRowIndex());
            // 向左查找第一个行展开单元格作为行父格
            for (int i = xptModel.getColIndex() - 1; i >= 0; i--) {
                ICell prevCell = row.getCell(i);
                if (prevCell == null)
                    continue;
                ExcelCell ec = (ExcelCell) prevCell.getRealCell();
                if (ec.getModel().getExpandType() == XptExpandType.r) {
                    return ec;
                }
            }

            // 如果未找到，则以最左侧单元格具有相同的行父格
            ICell firstCell = row.getCell(0);
            if (firstCell == null)
                return null;
            ExcelCell ec = (ExcelCell) firstCell.getRealCell();
            CellPosition pos = ec.getModel().getColParent();
            if (pos == null)
                return null;
            return resolveRowParent(sheet, cell, cells, pos);
        }
    }

    private ExcelCell getColParent(ExcelSheet sheet, ExcelCell cell, Map<String, ExcelCell> cells) {
        XptCellModel xptModel = cell.getModel();

        if (xptModel.getColParent() != null) {
            return resolveColParent(sheet, cell, cells, xptModel.getColParent());
        } else {
            if (xptModel.getRowIndex() == 0) {
                return null;
            }

            ExcelTable table = sheet.getTable();
            int colIndex = xptModel.getColIndex();
            // 向上查找第一个列展开单元格作为列父格
            for (int i = xptModel.getRowIndex() - 1; i >= 0; i--) {
                ICell prevCell = table.getCell(i, colIndex);
                if (prevCell == null)
                    continue;
                ExcelCell ec = (ExcelCell) prevCell.getRealCell();
                if (ec.getModel().getExpandType() == XptExpandType.c) {
                    return ec;
                }
            }

            // 如果未找到，则与第一行的单元格具有相同的行父格
            ICell firstCell = table.getCell(0, colIndex);
            if (firstCell == null)
                return null;
            ExcelCell ec = (ExcelCell) firstCell.getRealCell();
            CellPosition pos = ec.getModel().getColParent();
            if (pos == null)
                return null;
            return resolveColParent(sheet, cell, cells, pos);
        }
    }

    private ExcelCell resolveRowParent(ExcelSheet sheet, ExcelCell cell, Map<String, ExcelCell> cells, CellPosition pos) {
        ExcelCell rowParent = cells.get(pos.toABString());
        if (rowParent == null || rowParent.getModel().getExpandType() != XptExpandType.r) {
            throw new NopException(ERR_XPT_INVALID_ROW_PARENT)
                    .param(ARG_SHEET_NAME, sheet.getName())
                    .param(ARG_CELL_POS, cell.getModel().getName())
                    .param(ARG_ROW_PARENT, pos);
        }
        return rowParent;
    }

    private ExcelCell resolveColParent(ExcelSheet sheet, ExcelCell cell, Map<String, ExcelCell> cells, CellPosition pos) {
        ExcelCell colParent = cells.get(pos.toABString());
        if (colParent == null || colParent.getModel().getExpandType() != XptExpandType.c) {
            throw new NopException(ERR_XPT_INVALID_COL_PARENT)
                    .param(ARG_SHEET_NAME, sheet.getName())
                    .param(ARG_CELL_POS, cell.getModel().getName())
                    .param(ARG_COL_PARENT, pos.toABString());
        }
        return colParent;
    }

    private void initDuplicateCells(ExcelSheet sheet, Map<String, ExcelCell> cells) {
        for (ExcelCell cell : cells.values()) {
            XptCellModel xptModel = cell.getModel();
            // 如果是最顶层的单元格
            if (xptModel.getRowParentCell() == null) {
                collectRowChild(sheet, cell, new HashSet<>());
            }

            if (xptModel.getColParentCell() == null) {
                collectColChild(sheet, cell, new HashSet<>());
            }
        }
    }

    private void collectRowChild(ExcelSheet sheet, ExcelCell cell, Set<ExcelCell> visiting) {
        if (!visiting.add(cell))
            throw new NopException(ERR_XPT_ROW_PARENT_CONTAINS_LOOP)
                    .param(ARG_SHEET_NAME, sheet.getName())
                    .param(ARG_CELL_POS, cell.getModel().getName())
                    .param(ARG_ROW_PARENT, cell.getModel().getRowParent());

        XptCellModel xptModel = cell.getModel();

        if (xptModel.getRowChildCells().isEmpty()) {
            // 最底层节点
            xptModel.setRowExpandOffset(0);
            xptModel.setRowExpandSpan(cell.getRowSpan());
            return;
        }

        xptModel.addRowDuplicateCells(xptModel.getRowChildCells());

        int minRowIndex = xptModel.getRowIndex();
        int maxRowIndex = xptModel.getRowIndex() + cell.getRowSpan();

        int childLevel = xptModel.getRowExpandLevel() + 1;

        for (ExcelCell child : xptModel.getRowChildCells().values()) {
            XptCellModel childModel = child.getModel();
            childModel.setRowExpandLevel(childLevel);

            collectRowChild(sheet, child, visiting);

            xptModel.addRowDuplicateCells(childModel.getRowDuplicateCells());

            int startIndex = childModel.getRowIndex() + childModel.getRowExpandOffset();
            minRowIndex = Math.min(minRowIndex, startIndex);
            maxRowIndex = Math.max(maxRowIndex, startIndex + childModel.getRowExpandSpan());
        }

        xptModel.setRowExpandOffset(minRowIndex - xptModel.getRowIndex());
        xptModel.setRowExpandSpan(maxRowIndex - minRowIndex);
    }

    private void collectColChild(ExcelSheet sheet, ExcelCell cell, Set<ExcelCell> visiting) {
        if (!visiting.add(cell))
            throw new NopException(ERR_XPT_COL_PARENT_CONTAINS_LOOP)
                    .param(ARG_SHEET_NAME, sheet.getName())
                    .param(ARG_CELL_POS, cell.getModel().getName())
                    .param(ARG_COL_PARENT, cell.getModel().getColParent());

        XptCellModel xptModel = cell.getModel();

        if (xptModel.getColChildCells().isEmpty()) {
            // 最底层节点
            xptModel.setColExpandOffset(0);
            xptModel.setColExpandSpan(cell.getColSpan());
            return;
        }

        xptModel.addColDuplicateCells(xptModel.getColChildCells());

        int minColIndex = xptModel.getColIndex();
        int maxColIndex = xptModel.getColIndex() + cell.getColSpan();

        int childLevel = xptModel.getColExpandLevel() + 1;

        for (ExcelCell child : xptModel.getColChildCells().values()) {
            XptCellModel childModel = child.getModel();
            childModel.setColExpandLevel(childLevel);
            collectColChild(sheet, child, visiting);

            xptModel.addColDuplicateCells(childModel.getColDuplicateCells());

            int startIndex = childModel.getColIndex() + childModel.getColExpandOffset();
            minColIndex = Math.min(minColIndex, startIndex);
            maxColIndex = Math.max(maxColIndex, startIndex + childModel.getColExpandSpan());
        }

        xptModel.setColExpandOffset(minColIndex - xptModel.getColIndex());
        xptModel.setColExpandSpan(maxColIndex - minColIndex);
    }

    private void initExtendCells(ExcelSheet sheet, Map<String, ExcelCell> cells) {
        for (ExcelCell cell : cells.values()) {
            XptCellModel xptModel = cell.getModel();
            if (xptModel.getExpandType() == XptExpandType.r) {
                collectRowExtendCells(sheet, cell);
            } else if (xptModel.getExpandType() == XptExpandType.c) {
                collectColExtendCells(sheet, cell);
            }
        }
    }

    private void collectRowExtendCells(ExcelSheet sheet, ExcelCell cell) {
        XptCellModel xptModel = cell.getModel();
        int beginIndex = xptModel.getRowIndex() + xptModel.getRowExpandOffset();
        int endIndex = beginIndex + xptModel.getRowExpandSpan();

        ExcelTable table = sheet.getTable();
        ExcelRow row = table.getRow(beginIndex);
        for (int i = 0, n = row.getCells().size(); i < n; i++) {
            ICell ic = row.getCells().get(i);
            if (ic == null)
                continue;
            if (ic.isProxyCell()) {
                i += ic.getMergeAcross();
            }
            ExcelCell rc = (ExcelCell) ic.getRealCell();
            XptCellModel rcModel = rc.getModel();
            String name = rc.getModel().getName();

            // 1. 如果是展开单元格的父单元格
            // 2. 且整个单元格的延展范围包含了展开单元格的范围
            if (xptModel.getRowParent(name) != null) {
                if (rcModel.getRowIndex() <= beginIndex
                        && rcModel.getRowIndex() + rc.getRowSpan() >= endIndex) {
                    xptModel.addRowExtendCell(rc);
                }
            }
        }
    }

    private void collectColExtendCells(ExcelSheet sheet, ExcelCell cell) {
        XptCellModel xptModel = cell.getModel();
        int beginIndex = xptModel.getColIndex() + xptModel.getColExpandOffset();
        int endIndex = beginIndex + xptModel.getColExpandSpan();

        int colIndex = xptModel.getColIndex();

        ExcelTable table = sheet.getTable();
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            ICell ic = table.getCell(i, colIndex);
            if (ic == null)
                continue;
            if (ic.isProxyCell()) {
                i += ic.getMergeDown();
            }
            ExcelCell rc = (ExcelCell) ic.getRealCell();
            XptCellModel rcModel = rc.getModel();
            String name = rc.getModel().getName();

            // 1. 如果是展开单元格的父单元格
            // 2. 且整个单元格的延展范围包含了展开单元格的范围
            if (xptModel.getColParent(name) != null) {
                if (rcModel.getColIndex() <= beginIndex
                        && rcModel.getColIndex() + ic.getColSpan() >= endIndex) {
                    xptModel.addColExtendCell(rc);
                }
            }
        }
    }
}