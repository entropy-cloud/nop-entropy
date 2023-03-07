/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.imp;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.ICellView;
import io.nop.excel.imp.ITableDataEventListener;
import io.nop.excel.imp.TableDataParser;
import io.nop.excel.imp.model.IFieldContainer;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.XptSheetModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.source.SourceEvalAction;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析导入模板，根据导入模型配置识别其中的变量区域，然后将导入模板转换为XPT报表模型
 */
public class ExcelTemplateToXptModelTransformer {
    public void transform(ExcelWorkbook template, ImportModel model) {
        for (ExcelSheet sheet : template.getSheets()) {
            ImportSheetModel sheetModel = getSheetModel(model, sheet);
            if (sheetModel != null) {
                if (sheetModel.isIgnore()) {
                    continue;
                }
                transformSheet(sheet, sheetModel);
            }
        }
    }

    private void transformSheet(ExcelSheet sheet, ImportSheetModel sheetModel) {
        new TableDataParser().parse(sheet.getName(), sheet.getTable(), sheetModel,
                new DataListener(sheet));
    }

    private ImportSheetModel getSheetModel(ImportModel model, ExcelSheet sheet) {
        ImportSheetModel sheetModel = model.getSheet(sheet.getName());
        if (sheetModel != null)
            return sheetModel;

        for (ImportSheetModel sheetM : model.getSheets()) {
            if (sheetM.matchNamePattern(sheet.getName()))
                return sheetM;
        }

        return null;
    }

    enum RangeType {
        simpleList,
        cardList,
        object;
    }

    static class DataListener implements ITableDataEventListener {
        private final ExcelSheet sheet;
        private final List<FieldRange> parents = new ArrayList<>();

        private final XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);

        static class FieldRange {
            int rowIndex;
            int colIndex;
            int maxRowIndex;
            int maxColIndex;
            IFieldContainer container;
            RangeType rangeType;
            int childIndex;
            int childCount;

            public FieldRange(int rowIndex, int colIndex, int maxRowIndex,
                              int maxColIndex, IFieldContainer container, RangeType rangeType) {
                this.rowIndex = rowIndex;
                this.colIndex = colIndex;
                this.maxRowIndex = maxRowIndex;
                this.maxColIndex = maxColIndex;
                this.container = container;
                this.rangeType = rangeType;
            }
        }

        public DataListener(ExcelSheet sheet) {
            this.sheet = sheet;
        }

        public ExcelTable getTable() {
            return sheet.getTable();
        }

        @Override
        public void beginSheet(String sheetName, ImportSheetModel sheetModel) {

            XptSheetModel xptModel = new XptSheetModel();
            sheet.setModel(xptModel);

            if (sheetModel.isMultiple()) {
                String sheetVarName = sheetModel.getSheetVarName();
                if (sheetVarName == null)
                    sheetVarName = sheetModel.getFieldName() + "__item";
                xptModel.setLoopVarName(sheetVarName);
                xptModel.setSheetVarName(sheetVarName);
                xptModel.setLoopItemsName(XptConstants.VAR_ENTITY + "." + sheetModel.getFieldName());
                if (sheetModel.getSheetNameProp() != null)
                    xptModel.setSheetNameExpr(getSheetNameExpr(sheetVarName, sheetModel.getSheetNameProp()));
            } else {
                String sheetVarName = sheetModel.getSheetVarName();
                if (sheetVarName == null)
                    sheetVarName = XptConstants.VAR_ENTITY;
                xptModel.setSheetVarName(sheetVarName);
            }
        }

        private static IEvalAction getSheetNameExpr(String sheetVarName, String name) {
            String field = sheetVarName + '.' + name;
            return new SourceEvalAction(field, ctx -> {
                return ctx.getEvalScope().getValueByPropPath(field);
            });
        }

        @Override
        public void endSheet(ImportSheetModel sheetModel) {

        }

        @Override
        public void beginList(int rowIndex, int colIndex,
                              int maxRowIndex, int maxColIndex, IFieldContainer fieldModel, boolean cardList) {
            Guard.notEmpty(fieldModel.getFieldName(), "fieldName of list container should not be empty");

            FieldRange range = new FieldRange(rowIndex, colIndex, maxRowIndex, maxColIndex, fieldModel, cardList ? RangeType.cardList : RangeType.simpleList);
            parents.add(range);

            if (!cardList) {
                rowIndex++;
            }

            ExcelCell cell = (ExcelCell) getTable().getCell(rowIndex, colIndex);
            if (StringHelper.isNumber(cell.getText())) {
                XptCellModel cellModel = new XptCellModel();
                cell.setModel(cellModel);
                initCellField(cellModel, range, fieldModel.getFieldName());
                cellModel.setExpandType(XptExpandType.r);
                cellModel.setValueExpr(getExpandIndexAction());
            }
        }

        private void clearIndexCell(int rowIndex, int colIndex) {
            ExcelTable table = getTable();
            for (int i = rowIndex, n = table.getRowCount(); i < n; i++) {
                ICell cell = table.getCell(i, colIndex);
                if (cell == null || cell.isProxyCell())
                    break;
                if (!StringHelper.isNumber(cell.getText())) {
                    break;
                }
                cell.setValue("");
            }
        }

        @Override
        public void endList(int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
            FieldRange range = parents.remove(parents.size() - 1);

            int rowIndex = range.rowIndex;
            if (range.rangeType != RangeType.cardList)
                rowIndex++;

            ExcelCell cell = (ExcelCell) getTable().getCell(rowIndex, range.colIndex);
            if (StringHelper.isNumber(cell.getText())) {
                XptCellModel cellModel = cell.getModel();
                clearIndexCell(rowIndex, range.colIndex);
                cellModel.setExpandInplaceCount(range.childCount);
            }
        }

        @Override
        public void beginObject(int rowIndex, int colIndex,
                                int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {

            FieldRange range = new FieldRange(rowIndex, colIndex, maxRowIndex, maxColIndex, fieldModel, RangeType.object);
            if (fieldModel.isList()) {
                FieldRange parent = parents.get(parents.size() - 1);
                range.childIndex = parent.childCount;
                parent.childCount++;
            }
            parents.add(range);

//            ExcelCell cell = (ExcelCell) getTable().getCell(rowIndex, colIndex);
//
//            XptCellModel cellModel = cell.makeModel();
//            cellModel.setField(fieldModel.getFieldName());
        }

        @Override
        public void endObject(IFieldContainer fieldModel) {
            parents.remove(parents.size() - 1);
        }

        @Override
        public void simpleField(int rowIndex, int colIndex, ICellView cell, ImportFieldModel fieldModel) {
            ExcelCell ec = (ExcelCell) cell;
            if (ec == null) {
                ec = new ExcelCell();
                getTable().setCell(rowIndex, colIndex, ec);
            }
            ec.setName(CellPosition.toABString(rowIndex, colIndex));

            XptCellModel cellModel = ec.makeModel();
            FieldRange parent = parents.get(parents.size() - 1);

            String formatExpr = (String) fieldModel.prop_get(XptConstants.EXT_PROP_FORMAT_EXPR);
            if (!StringHelper.isEmpty(formatExpr)) {
                cellModel.setFormatExpr(buildFormatExpr(fieldModel.getLocation(), formatExpr));
            }

            // 只考虑第一行
            if (parent.childIndex > 0)
                return;

            // 忽略虚拟字段
            if (fieldModel.isVirtual()) {
                return;
            }

            if (cellModel.getExpandType() != null) {
                // 序号列对应于实际字段
                cellModel.setValueExpr(getExpandFieldAction(fieldModel.getName()));
            } else {
                initCellField(cellModel, parent, fieldModel.getFieldName());
            }
        }

        void initCellField(XptCellModel cellModel, FieldRange parent, String fieldName) {
            cellModel.setField(fieldName);
        }

        IEvalAction buildFormatExpr(SourceLocation loc, String formatExpr) {
            IEvalAction expr = compileTool.compileFullExpr(loc, formatExpr);
            if (expr == null)
                return null;
            return new SourceEvalAction(formatExpr, expr);
        }
    }

    private static IEvalAction getExpandIndexAction() {
        return new SourceEvalAction("cell.expandIndex+1", ctx -> {
            return ((IXptRuntime) ctx).getCell().getExpandIndex() + 1;
        });
    }

    private static IEvalAction getExpandFieldAction(String name) {
        return new SourceEvalAction("cell.getExpandField('" + name + "')", ctx -> {
            return ((IXptRuntime) ctx).getCell().getExpandField(ctx.getEvalScope(), name);
        });
    }
}