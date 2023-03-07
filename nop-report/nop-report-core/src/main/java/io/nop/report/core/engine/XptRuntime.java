/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.Underscore;
import io.nop.excel.format.ExcelFormatHelper;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptCellModel;
import io.nop.report.core.XptConstants;
import io.nop.report.core.dataset.DynamicReportDataSet;
import io.nop.report.core.dataset.KeyedReportDataSet;
import io.nop.report.core.dataset.ReportDataSet;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCol;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;

import java.text.Format;
import java.util.List;

import static io.nop.report.core.XptErrors.ARG_CELL_POS;
import static io.nop.report.core.XptErrors.ARG_SHEET_NAME;

public class XptRuntime implements IXptRuntime, IVariableScope {
    private final IEvalScope scope;
    private ExpandedCell cell;
    private ExpandedTable table;
    private ExpandedSheet sheet;
    private ExpandedRow row;
    private ExcelWorkbook workbook;

    public XptRuntime(IEvalScope scope) {
        this.scope = scope.newChildScope();
        scope.setLocalValue(null, XptConstants.VAR_XPT_RT, this);
        scope.setExtension(this);
    }

    public ExcelWorkbook getWorkbook() {
        return workbook;
    }

    public void setWorkbook(ExcelWorkbook workbook) {
        this.workbook = workbook;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public ExpandedCell getCell() {
        return cell;
    }

    @Override
    public void setCell(ExpandedCell cell) {
        this.cell = cell;
    }

    @Override
    public ExpandedTable getTable() {
        return table;
    }

    @Override
    public ExpandedSheet getSheet() {
        return sheet;
    }

    @Override
    public void setSheet(ExpandedSheet sheet) {
        this.sheet = sheet;
        this.table = sheet == null ? null : sheet.getTable();
    }

    @Override
    public boolean containsValue(String name) {
        return getValue(name) != null;
    }

    @Override
    public Object getValue(String name) {
        if (XptConstants.VAR_CELL.equals(name))
            return cell;
        if (XptConstants.VAR_TABLE.equals(name))
            return table;
        if (XptConstants.VAR_SHEET.equals(name))
            return sheet;
        if (XptConstants.VAR_ROW.equals(name))
            return row;
        return null;
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        return getValue(propPath);
    }

    @Override
    public ExpandedRow getRow() {
        return row;
    }

    @Override
    public void setRow(ExpandedRow row) {
        this.row = row;
    }

    @Override
    public Object evaluateCell(ExpandedCell cell) {
        if (cell.isEvaluated()) {
            return cell.getValue();
        }

        // 避免循环依赖时导致死循环
        cell.setEvaluated(true);
        XptCellModel cellModel = cell.getModel();
        if (cellModel == null) {
            return null;
        }

        ExpandedCell curCell = this.cell;
        try {
            this.cell = cell;

            IEvalAction valueExpr = cellModel.getValueExpr();
            if (valueExpr != null) {
                Object value = valueExpr.invoke(this);
                cell.setValue(value);
            } else if (cellModel.getExpandType() != null) {
                // 对于展开单元格，如果没有特别指定valueExpr，则以展开值为单元格的值
                Object value = cell.getExpandValue();
                if (value instanceof KeyedReportDataSet) {
                    cell.setValue(((KeyedReportDataSet) value).getKey());
                } else {
                    Object expandValue = cell.getExpandValue();
                    if (expandValue instanceof ReportDataSet) {
                        ReportDataSet ds = (ReportDataSet) expandValue;
                        if (cellModel.getField() != null) {
                            cell.setValue(ds.field(scope, cellModel.getField()));
                        } else {
                            cell.setValue(ds.first(scope));
                        }
                    } else {
                        if (cellModel.getField() != null) {
                            cell.setValue(Underscore.getFieldValue(expandValue, cellModel.getField()));
                        } else {
                            cell.setValue(expandValue);
                        }
                    }
                }
            } else if (cellModel.getField() != null) {
                // 如果指定了field,则按照坐标条件先查找上下文关联对象，如果未找到，则取全局的变量
                cell.setValue(field(cellModel.getField()));
            }

            IEvalAction styleExpr = cellModel.getStyleIdExpr();
            if (styleExpr != null) {
                String styleId = ConvertHelper.toString(styleExpr.invoke(this));
                if (styleId != null)
                    cell.setStyleId(styleId);
            }

            IEvalAction formatExpr = cellModel.getFormatExpr();
            if (formatExpr != null) {
                Object formattedValue = formatExpr.invoke(this);
                cell.setFormattedValue(formattedValue);
            } else if (cell.getStyleId() != null && workbook != null && cell.getValue() instanceof Number) {
                ExcelStyle style = workbook.getStyle(cell.getStyleId());
                if (style != null && style.getNumberFormat() != null) {
                    Format format = ExcelFormatHelper.getFormat(style.getNumberFormat());
                    if (format != null) {
                        cell.setFormattedValue(format.format(cell.getValue()));
                    }
                }
            }

            IEvalAction linkExpr = cellModel.getLinkExpr();
            if (linkExpr != null) {
                String linkUrl = ConvertHelper.toString(linkExpr.invoke(this));
                cell.setLinkUrl(linkUrl);
            }
            evalTestExpr(cellModel);
            return cell.getValue();
        } catch (NopException e) {
            e.param(ARG_CELL_POS, cell.getName())
                    .param(ARG_SHEET_NAME, sheet.getName());
            throw e;
        } finally {
            this.cell = curCell;
        }
    }

    private void evalTestExpr(XptCellModel cellModel) {
        if (cellModel.getRowTestExpr() != null) {
            if (!cellModel.getRowTestExpr().passConditions(this)) {
                removeRow(cell);
            }
        }

        if (cellModel.getColTestExpr() != null) {
            if (!cellModel.getColTestExpr().passConditions(this)) {
                removeCol(cell);
            }
        }
    }

    private void removeRow(ExpandedCell cell) {
        int startIndex = cell.getRowIndex();
        int lastIndex = startIndex + cell.getMergeDown();
        ExpandedTable table = cell.getTable();
        for (int i = startIndex; i <= lastIndex; i++) {
            ExpandedRow row = table.getRow(i);
            row.setRemoved(true);
        }
    }

    private void removeCol(ExpandedCell cell) {
        int startIndex = cell.getColIndex();
        int lastIndex = startIndex + cell.getMergeAcross();
        ExpandedTable table = cell.getTable();
        for (int i = startIndex; i <= lastIndex; i++) {
            ExpandedCol col = table.getCol(i);
            col.setRemoved(true);
        }
    }

    public Object field(String field) {
        if (cell == null)
            return scope.getValueByPropPath(field);

        List<Object> items = DynamicReportDataSet.getDsItems(cell, null);
        if (items != null) {
            Object item = CollectionHelper.first(items);
            return Underscore.getFieldValue(item, field);
        }

        if (cell.getRowParent() != null) {
            return cell.getRowParent().getExpandField(scope, field);
        }

        if (cell.getColParent() != null) {
            return cell.getColParent().getExpandField(scope, field);
        }

        if (sheet.getModel().getSheetVarName() != null) {
            Object value = scope.getValueByPropPath(sheet.getModel().getSheetVarName());
            if (value == null)
                return null;
            return Underscore.getFieldValue(value, field);
        }

        return scope.getValueByPropPath(field);
    }
}