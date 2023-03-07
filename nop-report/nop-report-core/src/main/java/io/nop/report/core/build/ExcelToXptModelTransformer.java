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
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.excel.imp.WorkbookDataParser;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.XptSheetModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.XptConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.json.DslXNodeToJsonTransformer;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.List;
import java.util.Map;

import static io.nop.report.core.XptConstants.EXCEL_MODEL_FIELD_PREFIX;
import static io.nop.report.core.XptErrors.ARG_DS_NAME;
import static io.nop.report.core.XptErrors.ARG_FIELD_NAME;
import static io.nop.report.core.XptErrors.ARG_PROP_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_DS_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_FIELD_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_UNDEFINED_CELL_MODEL_PROP;

/**
 * 将Excel模型转换为Xpt报表模型
 */
public class ExcelToXptModelTransformer {

    public void transform(ExcelWorkbook workbook) {
        ImportModel importModel = (ImportModel) ResourceComponentManager.instance().loadComponentModel(XptConstants.XPT_IMP_MODEL_PATH);
        IXDefinition xptXDef = SchemaLoader.loadXDefinition(XptConstants.XDSL_SCHEMA_WORKBOOK);
        IXDefNode cellModelNode = xptXDef.getXdefDefine(XptConstants.XDEF_NODE_EXCEL_CELL).getChild(XptConstants.PROP_MODEL);

        XptWorkbookModel workbookModel = importModel(
                importModel.getSheet(XptConstants.SHEET_NAME_XPT_WORKBOOK_MODEL),
                workbook.getSheet(XptConstants.SHEET_NAME_XPT_WORKBOOK_MODEL),
                XptWorkbookModel.class);

        if (workbookModel != null) {
            List<DynamicObject> namedStyles = (List<DynamicObject>) workbookModel.prop_get(XptConstants.PROP_NAMED_STYLES);
            if (namedStyles != null) {
                workbookModel.prop_remove(XptConstants.PROP_NAMED_STYLES);
                for (DynamicObject namedStyle : namedStyles) {
                    ExcelStyle style = (ExcelStyle) namedStyle.prop_get(XptConstants.PROP_STYLE);
                    if (style != null) {
                        String id = (String) namedStyle.prop_get(XptConstants.PROP_ID);
                        if (!StringHelper.isEmpty(id)) {
                            style = style.cloneInstance();
                            style.setId(id);
                            workbook.addStyle(style);
                        }
                    }
                }
            }
            workbook.setModel(workbookModel);
        }

        ImportSheetModel impSheetModel = importModel.getSheet(XptConstants.SHEET_NAME_XPT_SHEET_MODEL);

        XLangCompileTool compileTool = XLang.newCompileTool();
        DslXNodeToJsonTransformer transformer =
                new DslXNodeToJsonTransformer(false, xptXDef, compileTool);

        for (ExcelSheet sheet : workbook.getSheets()) {
            String sheetName = sheet.getName();
            if (!sheetName.endsWith(XptConstants.POSTFIX_XPT_SHEET_MODEL)) {
                String modelSheetName = sheetName + XptConstants.POSTFIX_XPT_SHEET_MODEL;
                ExcelSheet modelSheet = workbook.getSheet(modelSheetName);
                if (modelSheet != null) {
                    XptSheetModel sheetModel = importModel(impSheetModel, modelSheet, XptSheetModel.class);
                    sheet.setModel(sheetModel);
                }
                parseCellModel(sheet, cellModelNode, transformer);
            }
        }

        workbook.getSheets().removeIf(sheet -> {
            String sheetName = sheet.getName();
            return sheetName.equals(XptConstants.SHEET_NAME_XPT_WORKBOOK_MODEL)
                    || sheetName.endsWith(XptConstants.POSTFIX_XPT_SHEET_MODEL);
        });
    }

    private void parseCellModel(ExcelSheet sheet, IXDefNode cellModelNode, DslXNodeToJsonTransformer transformer) {
        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            XptCellModel cellModel = new XptCellModel();
            ExcelCell ec = (ExcelCell) cell;
            ec.setModel(cellModel);

            if (ec.getComment() != null) {
                parseCellModelFromComment(ec, cellModelNode, transformer);
            }

            String text = cell.getText();
            if (text != null) {
                // 解析 *=^ds!myField 这种形式的单元格表达式
                if (text.startsWith(EXCEL_MODEL_FIELD_PREFIX)) {
                    parseCellExpr(cellModel, ec.getLocation(), text.substring(EXCEL_MODEL_FIELD_PREFIX.length()).trim());
                } else if (text.contains("${") && text.contains("}")) {
                    // 解析 ${x}这种xpl模板表达式
                    IEvalAction valueExpr = transformer.getCompileTool().compileTemplateExpr(ec.getLocation(),
                            text, false, ExprPhase.eval);
                    cellModel.setValueExpr(valueExpr);
                }
            }
            return ProcessResult.CONTINUE;
        });
    }

    private void parseCellModelFromComment(ExcelCell cell, IXDefNode cellModelNode, DslXNodeToJsonTransformer transformer) {
        XptCellModel cellModel = cell.getModel();

        Map<String, ValueWithLocation> config =
                MultiLineConfigParser.INSTANCE.parseConfig(cell.getLocation(), cell.getComment());

        for (Map.Entry<String, ValueWithLocation> entry : config.entrySet()) {
            String varName = entry.getKey();
            ValueWithLocation vl = entry.getValue();

            IXDefAttribute attr = cellModelNode.getAttribute(varName);
            if (attr != null) {
                Object value = transformer.parseValue(vl, varName, attr.getType());
                BeanTool.instance().setProperty(cellModel, attr.getPropName(), value);
            } else {
                IXDefNode child = cellModelNode.getChild(varName);
                if (child != null && child.getXdefValue() != null) {
                    Object value = transformer.parseValue(vl, varName, child.getXdefValue());
                    BeanTool.instance().setProperty(cellModel, child.getXdefBeanProp(), value);
                } else {
                    throw new NopException(ERR_XPT_UNDEFINED_CELL_MODEL_PROP)
                            .source(vl)
                            .param(ARG_PROP_NAME, entry.getKey());
                }
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

    private <T> T importModel(ImportSheetModel impModel, ExcelSheet sheet, Class<T> clazz) {
        if (sheet == null)
            return null;
        DynamicObject obj = new WorkbookDataParser().parseSheet(impModel, sheet, XLang.newEvalScope());
        return BeanTool.buildBean(obj, clazz);
    }
}