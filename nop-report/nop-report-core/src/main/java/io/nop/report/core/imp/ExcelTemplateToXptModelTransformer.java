/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.imp;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
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
import io.nop.report.core.build.XptConfigParseHelper;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析导入模板，根据导入模型配置识别其中的变量区域，然后将导入模板转换为XPT报表模型
 */
public class ExcelTemplateToXptModelTransformer {
    private final IEvalScope scope;

    public ExcelTemplateToXptModelTransformer(IEvalScope scope) {
        this.scope = scope;
    }

    public void transform(ExcelWorkbook template, ImportModel model) {
        XptConfigParseHelper.parseWorkbookModel(template);

        for (ExcelSheet sheet : template.getSheets()) {
            ImportSheetModel sheetModel = getSheetModel(model, sheet);
            if (sheetModel != null) {
                if (sheetModel.isIgnore()) {
                    continue;
                }
                transformSheet(sheet, sheetModel);
            }
        }
        if (AppConfig.isDebugMode())
            ExcelReportHelper.dumpXptModel(template);
    }

    private void transformSheet(ExcelSheet sheet, ImportSheetModel sheetModel) {
        new TableDataParser(scope).parse(sheet.getName(), sheet.getTable(), sheetModel,
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

            String field;

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

            XNode beforeExpandNode = XNode.fromValue(sheetModel.prop_get(XptConstants.EXT_PROP_XPT_BEFORE_EXPAND));
            if (beforeExpandNode != null) {
                IEvalAction action = compileTool.compileTagBodyWithSource(beforeExpandNode, XLangOutputMode.none);
                xptModel.setBeforeExpand(action);
            }

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
                if (sheetVarName == null && sheetModel.getFieldName() != null && !sheetModel.isList()) {
                    sheetVarName = XptConstants.VAR_ENTITY + "." + sheetModel.getFieldName();
                }
                if (sheetVarName == null) {
                    sheetVarName = XptConstants.VAR_ENTITY;
                }
                xptModel.setSheetVarName(sheetVarName);
            }
        }

        private static IEvalAction getSheetNameExpr(String sheetVarName, String name) {
            String field = sheetVarName + '.' + name;
            return new EvalCode(null, field, ctx -> {
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
                cellModel.setKeepExpandEmpty(true);
            }
        }

        @Override
        public void onColHeader(int rowIndex, int colIndex, ICellView cell, ImportFieldModel field, String fieldLabel) {
            ExcelCell ec = (ExcelCell) cell;
            XptCellModel cellModel = new XptCellModel();
            ec.setModel(cellModel);

            XNode labelExpandExpr = (XNode) field.prop_get(XptConstants.EXT_PROP_XPT_LABEL_EXPAND_EXPR);
            if (labelExpandExpr != null) {
                IEvalAction expandExpr = compileTool.compileTagBodyWithSource(labelExpandExpr, XLangOutputMode.none);
                cellModel.setExpandType(XptExpandType.c);
                cellModel.setExpandExpr(expandExpr);
            }

            initLabelField(cellModel, field);
        }

        @Override
        public void onFieldLabel(int rowIndex, int colIndex, ICellView cell, ImportFieldModel field, String fieldLabel) {
            ExcelCell ec = (ExcelCell) cell;
            XptCellModel cellModel = new XptCellModel();
            ec.setModel(cellModel);

            initLabelField(cellModel, field);
        }

        private void initLabelField(XptCellModel cellModel, ImportFieldModel field) {
            XNode labelValueExpr = (XNode) field.prop_get(XptConstants.EXT_PROP_XPT_LABEL_VALUE_EXPR);
            if (labelValueExpr != null) {
                IEvalAction valueExpr = compileTool.compileTagBodyWithSource(labelValueExpr, XLangOutputMode.none);
                cellModel.setValueExpr(valueExpr);
            }

            XNode labelStyleIdExpr = XNode.fromValue(field.prop_get(XptConstants.EXT_PROP_XPT_LABEL_STYLE_ID_EXPR));
            if (labelStyleIdExpr != null) {
                IEvalAction styleIdExpr = compileTool.compileTagBodyWithSource(labelStyleIdExpr, XLangOutputMode.none);
                cellModel.setStyleIdExpr(styleIdExpr);
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
            } else {
                String prop = fieldModel.getPropOrName();
                if(!fieldModel.isList() && !(fieldModel instanceof ImportSheetModel)) {
                    if (!parents.isEmpty()) {
                        FieldRange parent = parents.get(parents.size() - 1);
                        if (!parent.container.isList() && !(parent.container instanceof ImportSheetModel)) {
                            prop = parent.field + '.' + prop;
                        }
                    }
                    range.field = prop;
                }
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
        public void simpleField(int rowIndex, int colIndex, ICellView cell, ImportFieldModel fieldModel, String label) {
            ExcelCell ec = (ExcelCell) cell;
            if (ec == null) {
                ec = new ExcelCell();
                getTable().setCell(rowIndex, colIndex, ec);
            }
            ec.setName(CellPosition.toABString(rowIndex, colIndex));

            XptCellModel cellModel = ec.makeModel();
            FieldRange parent = parents.get(parents.size() - 1);

            String formatExpr = (String) fieldModel.prop_get(XptConstants.EXT_PROP_XPT_FORMAT_EXPR);
            if (!StringHelper.isEmpty(formatExpr)) {
                cellModel.setFormatExpr(buildFormatExpr(fieldModel.getLocation(), formatExpr));
            }

            // 只考虑第一行
            if (parent.childIndex > 0)
                return;

            XNode valueExprNode = XNode.fromValue(fieldModel.prop_get(XptConstants.EXT_PROP_XPT_VALUE_EXPR));
            if (valueExprNode != null && valueExprNode.hasBody()) {
                IEvalAction action = compileTool.compileTagBodyWithSource(valueExprNode, XLangOutputMode.none);
                cellModel.setValueExpr(action);
            } else {
                if (!fieldModel.isVirtual()) {
                    String prop = fieldModel.getPropOrName();
                    if(parent.field != null)
                        prop = parent.field + '.' + prop;

                    if (cellModel.getExpandType() != null) {
                        // 序号列对应于实际字段
                        cellModel.setValueExpr(getExpandFieldAction(prop));
                    } else {
                        initCellField(cellModel, parent, prop);
                    }
                }
            }

            XNode styleIdExpr = XNode.fromValue(fieldModel.prop_get(XptConstants.EXT_PROP_XPT_STYLE_ID_EXPR));
            if (styleIdExpr != null) {
                cellModel.setStyleIdExpr(compileTool.compileTagBodyWithSource(styleIdExpr, XLangOutputMode.none));
            }
        }

        void initCellField(XptCellModel cellModel, FieldRange parent, String fieldName) {
            cellModel.setField(fieldName);
        }

        IEvalAction buildFormatExpr(SourceLocation loc, String formatExpr) {
            IEvalAction expr = compileTool.compileFullExpr(loc, formatExpr);
            if (expr == null)
                return null;
            return new EvalCode(loc, formatExpr, expr);
        }
    }

    private static IEvalAction getExpandIndexAction() {
        return new EvalCode(null, "cell.expandIndex+1", ctx -> {
            return ((IXptRuntime) ctx).getCell().getExpandIndex() + 1;
        });
    }

    private static IEvalAction getExpandFieldAction(String name) {
        return new EvalCode(null, "cell.getExpandField('" + name + "')", ctx -> {
            return ((IXptRuntime) ctx).getCell().getExpandField(ctx.getEvalScope(), name);
        });
    }
}