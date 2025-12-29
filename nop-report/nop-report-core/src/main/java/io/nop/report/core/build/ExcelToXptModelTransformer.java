/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.build;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.excel.chart.model.ChartDynamicBindingsModel;
import io.nop.excel.imp.ImportModelHelper;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.XptSheetModel;
import io.nop.excel.util.MultiLineConfigParser;
import io.nop.report.core.XptConstants;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.api.source.IWithSourceCode;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdsl.json.DslXNodeToJsonTransformer;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.Map;

import static io.nop.report.core.XptConstants.XDEF_NODE_CHART_DYNAMIC_BINDINGS;
import static io.nop.report.core.XptErrors.ARG_PROP_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_UNDEFINED_CELL_MODEL_PROP;
import static io.nop.report.core.XptErrors.ERR_XPT_UNDEFINED_CHART_MODEL_PROP;
import static io.nop.report.core.XptErrors.ERR_XPT_UNDEFINED_IMAGE_MODEL_PROP;

/**
 * 将Excel模型转换为Xpt报表模型
 */
public class ExcelToXptModelTransformer {
    public static final ExcelToXptModelTransformer INSTANCE = new ExcelToXptModelTransformer();

    public void transform(ExcelWorkbook workbook) {
        ImportModel importModel = ImportModelHelper.getImportModel(XptConstants.XPT_IMP_MODEL_PATH);
        IXDefinition xptXDef = SchemaLoader.loadXDefinition(XptConstants.XDSL_SCHEMA_WORKBOOK);
        IXDefinition tableDef = SchemaLoader.loadXDefinition(XptConstants.XDSL_SCHEMA_EXCEL_TABLE);
        IXDefNode cellModelNode = tableDef.getXdefDefine(XptConstants.XDEF_NODE_EXCEL_CELL).getChild(XptConstants.PROP_MODEL);
        IXDefNode imageNode = xptXDef.getXdefDefine(XptConstants.XDEF_NODE_EXCEL_IMAGE);
        IXDefNode chartNode = xptXDef.getXdefDefine(XptConstants.XDEF_NODE_EXCEL_CHART);

        XptConfigParseHelper.parseWorkbookModel(workbook, importModel);

        ImportSheetModel impSheetModel = importModel.getSheet(XptConstants.SHEET_NAME_XPT_SHEET_MODEL);

        XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        DslXNodeToJsonTransformer transformer =
                new DslXNodeToJsonTransformer(false, xptXDef, compileTool);

        for (ExcelSheet sheet : workbook.getSheets()) {
            String sheetName = sheet.getName();
            if (!sheetName.endsWith(XptConstants.POSTFIX_XPT_SHEET_MODEL)) {
                String modelSheetName = sheetName + XptConstants.POSTFIX_XPT_SHEET_MODEL;
                ExcelSheet modelSheet = workbook.getSheet(modelSheetName);
                if (modelSheet != null) {
                    XptSheetModel sheetModel = importModel(impSheetModel, modelSheet, compileTool.getScope(),
                            XptSheetModel.class);
                    sheet.setModel(sheetModel);
                }
                parseCellModel(sheet.getTable(), cellModelNode, transformer);
                parseImageModel(sheet, imageNode, transformer);
                parseChartModel(sheet, chartNode, transformer);
            }
        }

        workbook.getSheets().removeIf(sheet -> {
            String sheetName = sheet.getName();
            return sheetName.equals(XptConstants.SHEET_NAME_XPT_WORKBOOK_MODEL)
                    || sheetName.endsWith(XptConstants.POSTFIX_XPT_SHEET_MODEL);
        });

        if (workbook.isEnableDump()) {
            dumpModel(workbook);
        }
    }

    private void dumpModel(ExcelWorkbook workbook) {
        ExcelReportHelper.dumpXptModel(workbook);
    }

    public void transformTable(ExcelTable table) {
        IXDefinition xptXDef = SchemaLoader.loadXDefinition(XptConstants.XDSL_SCHEMA_WORKBOOK);
        IXDefinition tableDef = SchemaLoader.loadXDefinition(XptConstants.XDSL_SCHEMA_EXCEL_TABLE);
        IXDefNode cellModelNode = tableDef.getXdefDefine(XptConstants.XDEF_NODE_EXCEL_CELL).getChild(XptConstants.PROP_MODEL);

        XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        DslXNodeToJsonTransformer transformer =
                new DslXNodeToJsonTransformer(false, xptXDef, compileTool);

        parseCellModel(table, cellModelNode, transformer);
    }

    private void parseCellModel(ExcelTable table, IXDefNode cellModelNode, DslXNodeToJsonTransformer transformer) {
        table.forEachRealCell((cell, rowIndex, colIndex) -> {
            ExcelCell ec = (ExcelCell) cell;
            if (ec.getComment() != null) {
                parseCellModelFromComment(ec, cellModelNode, transformer);
                ec.setComment(null);
            }

            return ProcessResult.CONTINUE;
        });
    }

    private void parseCellModelFromComment(ExcelCell cell, IXDefNode cellModelNode, DslXNodeToJsonTransformer transformer) {
        XptCellModel cellModel = cell.makeModel();

        Map<String, ValueWithLocation> config =
                MultiLineConfigParser.INSTANCE.parseConfig(cell.getLocation(), cell.getComment());

        // 如果设置了formatExpr，而不是利用Excel内置的格式化机制来实现格式化，则一般导出时应该保持格式化后的值，而不是导出原始值
        if (!StringHelper.isEmptyObject(config.get(XptConstants.PROP_FORMULA_EXPR))) {
            cellModel.setExportFormattedValue(true);
        }

        for (Map.Entry<String, ValueWithLocation> entry : config.entrySet()) {
            String varName = entry.getKey();
            ValueWithLocation vl = entry.getValue();

            IXDefAttribute attr = cellModelNode.getAttribute(varName);
            if (attr != null) {
                Object value = transformer.parseValue(vl, varName, attr.getType());
                value = addSource(vl, value);
                BeanTool.instance().setProperty(cellModel, attr.getPropName(), value);
            } else {
                IXDefNode child = cellModelNode.getChild(varName);
                if (child != null && child.getXdefValue() != null) {
                    Object value = transformer.parseValue(vl, varName, child.getXdefValue());
                    value = addSource(vl, value);
                    BeanTool.instance().setProperty(cellModel, child.getXdefBeanProp(), value);
                } else if (varName.indexOf(':') > 0) {
                    cellModel.prop_set(varName, vl.getValue());
                } else {
                    throw new NopException(ERR_XPT_UNDEFINED_CELL_MODEL_PROP)
                            .source(vl)
                            .param(ARG_PROP_NAME, entry.getKey());
                }
            }
        }
    }

    private Object addSource(ValueWithLocation vl, Object value) {
        if (value instanceof IWithSourceCode)
            return value;

        if (value instanceof ExprEvalAction && vl.getValue() instanceof String) {
            return EvalCode.addSource((ExprEvalAction) value, (String) vl.getValue());
        }
        return value;
    }

    private <T> T importModel(ImportSheetModel impModel, ExcelSheet sheet, IEvalScope scope, Class<T> clazz) {
        return ImportModelHelper.parseSheet(impModel, sheet, scope, clazz);
    }

    private void parseImageModel(ExcelSheet sheet, IXDefNode defNode, DslXNodeToJsonTransformer transformer) {
        if (sheet.getImages() == null)
            return;

        for (ExcelImage image : sheet.getImages()) {
            String desc = image.getDescription();
            if (desc == null)
                continue;

            int pos = desc.indexOf("----");
            if (pos < 0)
                break;

            for (; pos < desc.length(); pos++) {
                if (desc.charAt(pos) != '-')
                    break;
            }

            String str = desc.substring(pos);
            Map<String, ValueWithLocation> config =
                    MultiLineConfigParser.INSTANCE.parseConfig(sheet.getLocation(), str);

            for (Map.Entry<String, ValueWithLocation> entry : config.entrySet()) {
                String varName = entry.getKey();
                ValueWithLocation vl = entry.getValue();
                if (varName.equals("testExpr") || varName.equals("dataExpr")) {
                    IXDefNode child = defNode.getChild(varName);
                    Object value = transformer.parseValue(vl, varName, child.getXdefValue());
                    value = addSource(vl, value);
                    BeanTool.setProperty(image, varName, value);
                } else {
                    throw new NopException(ERR_XPT_UNDEFINED_IMAGE_MODEL_PROP)
                            .source(vl)
                            .param(ARG_PROP_NAME, entry.getKey());
                }
            }
        }
    }

    private void parseChartModel(ExcelSheet sheet, IXDefNode chartNode, DslXNodeToJsonTransformer transformer) {
        if (sheet.getCharts() == null)
            return;

        IXDefNode defNode = chartNode.getChild(XDEF_NODE_CHART_DYNAMIC_BINDINGS);

        for (ExcelChartModel chart : sheet.getCharts()) {
            String desc = chart.getDescription();
            if (desc == null)
                continue;

            int pos = desc.indexOf("----");
            if (pos < 0)
                break;

            for (; pos < desc.length(); pos++) {
                if (desc.charAt(pos) != '-')
                    break;
            }

            ChartDynamicBindingsModel bindings = new ChartDynamicBindingsModel();
            bindings.setLocation(chart.getLocation());
            chart.setDynamicBindings(bindings);

            String str = desc.substring(pos);
            Map<String, ValueWithLocation> config =
                    MultiLineConfigParser.INSTANCE.parseConfig(sheet.getLocation(), str);

            for (Map.Entry<String, ValueWithLocation> entry : config.entrySet()) {
                String varName = entry.getKey();
                ValueWithLocation vl = entry.getValue();

                IXDefAttribute attr = defNode.getAttribute(varName);
                XDefTypeDecl type = null;
                if (attr != null) {
                    type = attr.getType();
                } else {
                    IXDefNode child = defNode.getChild(varName);
                    type = child.getXdefValue();
                }
                if (type != null) {
                    Object value = transformer.parseValue(vl, varName, type);
                    value = addSource(vl, value);
                    BeanTool.setProperty(bindings, varName, value);
                } else if (varName.indexOf(':') > 0) {
                    BeanTool.setProperty(bindings, varName, vl.getValue());
                } else {
                    throw new NopException(ERR_XPT_UNDEFINED_CHART_MODEL_PROP)
                            .source(vl)
                            .param(ARG_PROP_NAME, entry.getKey());
                }
            }
        }
    }
}