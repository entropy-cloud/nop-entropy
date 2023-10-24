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
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.model.graph.DefaultDirectedGraph;
import io.nop.core.model.graph.DefaultEdge;
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
import io.nop.excel.model.XptSheetModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.initialize.TemplateReportExprStdDomainHandler;
import io.nop.xlang.api.XLangCompileTool;

import java.util.List;
import java.util.Set;

import static io.nop.report.core.XptConstants.EXCEL_MODEL_FIELD_PREFIX;
import static io.nop.report.core.XptErrors.ARG_CELL_POS;
import static io.nop.report.core.XptErrors.ARG_COL_PARENT;
import static io.nop.report.core.XptErrors.ARG_DS_NAME;
import static io.nop.report.core.XptErrors.ARG_EXPR;
import static io.nop.report.core.XptErrors.ARG_FIELD_NAME;
import static io.nop.report.core.XptErrors.ARG_ROW_PARENT;
import static io.nop.report.core.XptErrors.ARG_SHEET_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_CELL_EXPR_NO_DS_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_COL_PARENT_CONTAINS_LOOP;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_COL_PARENT;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_DS_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_FIELD_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_ROW_PARENT;
import static io.nop.report.core.XptErrors.ERR_XPT_ROW_PARENT_CONTAINS_LOOP;

/**
 * 分析rowParent和colParent的关联关系，产生的分析结果保存在XptCellModel中
 */
public class XptModelInitializer {
    private final XLangCompileTool cp;

    public XptModelInitializer(XLangCompileTool cp) {
        this.cp = cp;
    }

    public void initialize(ExcelWorkbook workbook) {
        if (workbook.getModel() == null)
            workbook.setModel(new XptWorkbookModel());

        for (ExcelSheet sheet : workbook.getSheets()) {
            buildSheetModel(sheet);
        }
    }

    public void buildSheetModel(ExcelSheet sheet) {
        if (sheet.getModel() == null)
            sheet.setModel(new XptSheetModel());

        initCols(sheet);
        //初始化每个单元格对应的XptCellModel模型
        initXptModels(sheet);

        // 根据rowParent和colParent配置建立父子关联，初始化parent的childCells集合
        // 缺省左侧最近的展开单元格为行父格，上方最近的展开单元格为列父格
        initParentChildren(sheet);

        // 根据父子关系计算得到父单元格所管辖的所有子单元格
        initDuplicateCells(sheet);

        // 根据父子关系计算得到单元格展开时哪些父单元格需要被自动延展。如果父单元格与子单元格不在一行或者一列中，则不需要被延展。
        initExtendCells(sheet);

        // new XptStructureToNode().buildNodeForSheet(sheet).dump();
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

    private void initXptModels(ExcelSheet sheet) {
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

                XptCellModel xptModel = ec.getModel();
                if (xptModel == null) {
                    xptModel = new XptCellModel();
                    ec.setModel(xptModel);
                }
                xptModel.setCellPosition(CellPosition.of(i, j));
                xptModel.setName(name);

                normalizeValueExpr(ec);
            }
        }
    }

    private void normalizeValueExpr(ExcelCell cell) {
        XptCellModel cellModel = cell.getModel();
        String text = cell.getText();
        if (!StringHelper.isEmpty(text)) {
            // 解析 *=^ds!myField 这种形式的单元格表达式
            if (text.startsWith(EXCEL_MODEL_FIELD_PREFIX)) {
                parseCellExpr(cellModel, cell.getLocation(), text.substring(EXCEL_MODEL_FIELD_PREFIX.length()).trim());
            } else if (text.contains("${") && text.contains("}")) {
                // 解析 ${x}这种xpl模板表达式
                IEvalAction valueExpr = (IEvalAction) TemplateReportExprStdDomainHandler.INSTANCE.parseProp(null, cell.getLocation(),
                        "valueExpr", text, cp);
                cellModel.setValueExpr(valueExpr);
            }
        }
    }

    private void parseCellExpr(XptCellModel cellModel, SourceLocation loc, String text) {
        XptExpandType expandType = null;
        if (text.startsWith("^")) {
            expandType = XptExpandType.r;
            text = text.substring(1).trim();
        } else if (text.startsWith(">")) {
            expandType = XptExpandType.c;
            text = text.substring(1).trim();
        }

        if (expandType != null) {
            cellModel.setExpandType(expandType);
        }

        int pos = text.indexOf('!');
        if (pos < 0 && expandType != null)
            throw new NopException(ERR_XPT_CELL_EXPR_NO_DS_NAME)
                    .loc(loc)
                    .param(ARG_EXPR, text);

        if (pos > 0) {
            String ds = text.substring(0, pos);
            String field = text.substring(pos + 1);
            if (!StringHelper.isValidJavaVarName(ds))
                throw new NopException(ERR_XPT_INVALID_DS_NAME)
                        .loc(loc)
                        .param(ARG_DS_NAME, ds);

            if (!StringHelper.isValidPropPath(field))
                throw new NopException(ERR_XPT_INVALID_FIELD_NAME)
                        .loc(loc)
                        .param(ARG_FIELD_NAME, field);
            cellModel.setField(field);
            cellModel.setDs(ds);
        } else {
            if (!StringHelper.isValidPropPath(text))
                throw new NopException(ERR_XPT_INVALID_FIELD_NAME)
                        .loc(loc)
                        .param(ARG_FIELD_NAME, text);
            cellModel.setField(text);
        }
    }

    private void initParentChildren(ExcelSheet sheet) {
        sheet.getTable().forEachRealCell((ic, rowIndex, colIndex) -> {
            ExcelCell cell = (ExcelCell) ic;
            XptCellModel xptModel = cell.getModel();
            ExcelCell rowParent = getRowParent(sheet, cell);
            if (rowParent != null) {
                if (xptModel.getRowParent() == null) {
                    xptModel.setRowParent(rowParent.getModel().getCellPosition());
                }
                xptModel.setRowParentCell(rowParent);
                rowParent.getModel().addRowChildCell(cell);
            }

            ExcelCell colParent = getColParent(sheet, cell);
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

    private ExcelCell getRowParent(ExcelSheet sheet, ExcelCell cell) {
        XptCellModel xptModel = cell.getModel();

        if (xptModel.getRowParent() == CellPosition.NONE)
            return null;

        if (xptModel.getRowParent() != null) {
            return resolveRowParent(sheet, cell, xptModel.getRowParent());
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
            CellPosition pos = ec.getModel().getRowParent();
            if (pos == null || pos == CellPosition.NONE)
                return null;

            // 如果第一个单元格的父指向自身，则忽略
            if (pos.equals(cell.getModel().getCellPosition()))
                return null;

            return resolveRowParent(sheet, cell, pos);
        }
    }

    private ExcelCell getColParent(ExcelSheet sheet, ExcelCell cell) {
        XptCellModel xptModel = cell.getModel();

        if (xptModel.getColParent() == CellPosition.NONE)
            return null;

        if (xptModel.getColParent() != null) {
            return resolveColParent(sheet, cell, xptModel.getColParent());
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
            if (pos == null || pos == CellPosition.NONE)
                return null;

            // 如果第一个单元格的父指向自身，则忽略
            if (pos.equals(cell.getModel().getCellPosition()))
                return null;

            return resolveColParent(sheet, cell, pos);
        }
    }

    private ExcelCell resolveRowParent(ExcelSheet sheet, ExcelCell cell, CellPosition pos) {
        ExcelCell rowParent = (ExcelCell) sheet.getTable().getCell(pos.getRowIndex(), pos.getColIndex());
        if (rowParent == null) {
            throw new NopException(ERR_XPT_INVALID_ROW_PARENT)
                    .param(ARG_SHEET_NAME, sheet.getName())
                    .param(ARG_CELL_POS, cell.getModel().getName())
                    .param(ARG_ROW_PARENT, pos);
        }
        return rowParent.getRealCell();
    }

    private ExcelCell resolveColParent(ExcelSheet sheet, ExcelCell cell, CellPosition pos) {
        ExcelCell colParent = (ExcelCell) sheet.getTable().getCell(pos.getRowIndex(), pos.getColIndex());
        if (colParent == null) {
            throw new NopException(ERR_XPT_INVALID_COL_PARENT)
                    .param(ARG_SHEET_NAME, sheet.getName())
                    .param(ARG_CELL_POS, cell.getModel().getName())
                    .param(ARG_COL_PARENT, pos.toABString());
        }
        return colParent.getRealCell();
    }

    private void initDuplicateCells(ExcelSheet sheet) {
        checkLoop(sheet);

        sheet.getTable().forEachRealCell((c, rowIndex, colIndex) -> {
            ExcelCell cell = (ExcelCell) c;
            XptCellModel xptModel = cell.getModel();
            // 如果是最顶层的单元格
            if (xptModel.getRowParentCell() == null) {
                collectRowChild(sheet, cell);
                if (xptModel.getExpandType() == XptExpandType.r)
                    addDefaultRowParents(cell, sheet.getTable());
            }

            if (xptModel.getColParentCell() == null) {
                collectColChild(sheet, cell);
                if (xptModel.getExpandType() == XptExpandType.c)
                    addDefaultColParents(cell, sheet.getTable());
            }
            return ProcessResult.CONTINUE;
        });
    }

    private void checkLoop(ExcelSheet sheet) {
        DefaultDirectedGraph<ExcelCell, DefaultEdge<ExcelCell>> rowDepends = DefaultDirectedGraph.create();

        DefaultDirectedGraph<ExcelCell, DefaultEdge<ExcelCell>> colDepends = DefaultDirectedGraph.create();

        sheet.getTable().forEachRealCell((c, rowIndex, colIndex) -> {
            ExcelCell cell = (ExcelCell) c;
            XptCellModel cellModel = cell.getModel();
            rowDepends.addVertex(cell);
            if (cellModel.getRowParentCell() != null) {
                rowDepends.addEdge(cell, cellModel.getRowParentCell());
            }

            colDepends.addVertex(cell);
            if (cellModel.getColParentCell() != null) {
                colDepends.addEdge(cell, cellModel.getColParentCell());
            }
            return ProcessResult.CONTINUE;
        });

        Set<ExcelCell> rowCycles = rowDepends.findCycles();
        Set<ExcelCell> colCycles = colDepends.findCycles();

        if (!rowCycles.isEmpty()) {
            ExcelCell cell = CollectionHelper.first(rowCycles);
            if (cell.getModel().getRowParent(cell.getModelCellName()) != null) {
                throw new NopException(ERR_XPT_ROW_PARENT_CONTAINS_LOOP)
                        .source(sheet)
                        .param(ARG_SHEET_NAME, sheet.getName())
                        .param(ARG_CELL_POS, cell.getModel().getName())
                        .param(ARG_ROW_PARENT, cell.getModel().getRowParent());
            }
        }

        if (!colCycles.isEmpty()) {
            ExcelCell cell = CollectionHelper.first(colCycles);
            if (cell.getModel().getColParent(cell.getModelCellName()) != null) {
                throw new NopException(ERR_XPT_COL_PARENT_CONTAINS_LOOP)
                        .source(sheet)
                        .param(ARG_SHEET_NAME, sheet.getName())
                        .param(ARG_CELL_POS, cell.getModel().getName())
                        .param(ARG_ROW_PARENT, cell.getModel().getRowParent());
            }
        }
    }

    private void collectRowChild(ExcelSheet sheet, ExcelCell cell) {
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

            collectRowChild(sheet, child);

            xptModel.addRowDuplicateCells(childModel.getRowDuplicateCells());

            int startIndex = childModel.getRowIndex() + childModel.getRowExpandOffset();
            minRowIndex = Math.min(minRowIndex, startIndex);
            maxRowIndex = Math.max(maxRowIndex, startIndex + childModel.getRowExpandSpan());
        }

        xptModel.setRowExpandOffset(minRowIndex - xptModel.getRowIndex());
        xptModel.setRowExpandSpan(maxRowIndex - minRowIndex);
    }

    private void collectColChild(ExcelSheet sheet, ExcelCell cell) {
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
            collectColChild(sheet, child);

            xptModel.addColDuplicateCells(childModel.getColDuplicateCells());

            int startIndex = childModel.getColIndex() + childModel.getColExpandOffset();
            minColIndex = Math.min(minColIndex, startIndex);
            maxColIndex = Math.max(maxColIndex, startIndex + childModel.getColExpandSpan());
        }

        xptModel.setColExpandOffset(minColIndex - xptModel.getColIndex());
        xptModel.setColExpandSpan(maxColIndex - minColIndex);
    }

    private void initExtendCells(ExcelSheet sheet) {
        sheet.getTable().forEachRealCell((c, rowIndex, colIndex) -> {
            ExcelCell cell = (ExcelCell) c;
            XptCellModel xptModel = cell.getModel();
            if (xptModel.getExpandType() == XptExpandType.r) {
                collectRowExtendCells(sheet, cell);
            } else if (xptModel.getExpandType() == XptExpandType.c) {
                collectColExtendCells(sheet, cell);
            }
            return ProcessResult.CONTINUE;
        });
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
            if (rc == cell)
                continue;
            XptCellModel rcModel = rc.getModel();
            String name = rc.getModel().getName();

            // 1. 整个单元格的延展范围包含了展开单元格的范围
            // 2. 且不是展开单元格的子单元格
            if (rcModel.getRowIndex() <= beginIndex
                    && rcModel.getRowIndex() + rc.getRowSpan() >= endIndex) {
                if (!xptModel.getRowDuplicateCells().containsKey(name)) {
                    if (rcModel.isRowExtendForSibling()
                            || rcModel.getRowIndex() + rc.getRowSpan() > endIndex // 除非不延展就会被插入的新行撕裂
                            || xptModel.getRowParent(name) != null)
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
            if (rc == cell)
                continue;

            XptCellModel rcModel = rc.getModel();
            String name = rc.getModel().getName();

            // 1. 整个单元格的延展范围包含了展开单元格的范围
            // 2. 且不是展开单元格的子单元格
            if (rcModel.getColIndex() <= beginIndex
                    && rcModel.getColIndex() + rc.getColSpan() >= endIndex) {
                if (!xptModel.getColDuplicateCells().containsKey(name)) {
                    if (rcModel.isColExtendForSibling()
                            || rcModel.getColIndex() + rc.getColSpan() > endIndex // 除非不延展就会被插入的新列撕裂
                            || xptModel.getColParent(name) != null)
                        xptModel.addColExtendCell(rc);
                }
            }
        }
    }

    private void addDefaultRowParents(ExcelCell cell, ExcelTable table) {
        XptCellModel cellModel = cell.getModel();
        int startIndex = cellModel.getRowIndex() + cellModel.getRowExpandOffset();
        int endIndex = startIndex + cellModel.getRowExpandSpan();

        // 在单元格的展开范围内，所有没有rowParent且不与展开单元格在同一行的单元格缺省都应该以cell为父
        for (int i = startIndex; i < endIndex; i++) {
            ExcelRow row = table.getRow(i);
            for (ExcelCell c : row.getCells()) {
                if (c != null && c != cell && !c.isProxyCell()) {
                    XptCellModel cm = c.getModel();
                    if (cm.getRowParent() == null) {
                        if (cm.getRowIndex() + c.getRowSpan() <= cellModel.getRowIndex()
                                || cm.getRowIndex() >= cellModel.getRowIndex() + cell.getRowSpan()) {
                            cm.setRowParent(cellModel.getCellPosition());
                            cm.setRowParentCell(cell);
                            cellModel.addRowChildCell(c);
                            cellModel.addRowDuplicateCell(c);
                            cellModel.addRowDuplicateCells(cm.getRowDuplicateCells());
                        }
                    }
                }
            }
        }
    }

    private void addDefaultColParents(ExcelCell cell, ExcelTable table) {
        XptCellModel cellModel = cell.getModel();
        int startIndex = cellModel.getColIndex() + cellModel.getColExpandOffset();
        int endIndex = startIndex + cellModel.getColExpandSpan();

        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            for (int j = startIndex; j < endIndex; j++) {
                ICell ic = table.getCell(i, j);
                if (ic != null && ic != cell && !ic.isProxyCell()) {
                    ExcelCell c = (ExcelCell) ic.getRealCell();
                    XptCellModel cm = c.getModel();
                    if (cm.getColParent() == null) {
                        if (cm.getColIndex() + c.getColSpan() <= cellModel.getColIndex()
                                || cm.getColIndex() >= cellModel.getColIndex() + cell.getColSpan()) {
                            cm.setColParent(cellModel.getCellPosition());
                            cm.setColParentCell(cell);
                            cellModel.addColChildCell(c);
                            cellModel.addColDuplicateCell(c);
                            cellModel.addColDuplicateCells(cm.getColDuplicateCells());
                        }
                    }
                }
            }
        }
    }
}