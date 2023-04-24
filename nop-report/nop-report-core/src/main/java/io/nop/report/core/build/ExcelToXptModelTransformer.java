/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.build;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.excel.imp.ImportModelHelper;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.MultiLineConfigParser;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.XptSheetModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.report.core.XptConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.source.SourceEvalAction;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.json.DslXNodeToJsonTransformer;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.List;
import java.util.Map;

import static io.nop.report.core.XptErrors.ARG_PROP_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_UNDEFINED_CELL_MODEL_PROP;

/**
 * 将Excel模型转换为Xpt报表模型
 */
public class ExcelToXptModelTransformer {

    public void transform(ExcelWorkbook workbook) {
        ImportModel importModel = ImportModelHelper.getImportModel(XptConstants.XPT_IMP_MODEL_PATH);
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

        if (AppConfig.isDebugMode()) {
            dumpModel(workbook);
        }
    }

    private void dumpModel(ExcelWorkbook workbook) {
        String path = workbook.resourcePath();
        if (StringHelper.isEmpty(path))
            path = "unknown.xpt.xlsx";
        path = StringHelper.removeTail(path, ".xlsx");

        path = ResourceHelper.getDumpPath(path);

        IResource resource = VirtualFileSystem.instance().getResource(path);
        DslModelHelper.saveDslModel(XptConstants.XDSL_SCHEMA_WORKBOOK, workbook, resource);

    }

    private void parseCellModel(ExcelSheet sheet, IXDefNode cellModelNode, DslXNodeToJsonTransformer transformer) {
        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            XptCellModel cellModel = new XptCellModel();
            ExcelCell ec = (ExcelCell) cell;
            ec.setModel(cellModel);

            if (ec.getComment() != null) {
                parseCellModelFromComment(ec, cellModelNode, transformer);
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
                value = addSource(vl, value);
                BeanTool.instance().setProperty(cellModel, attr.getPropName(), value);
            } else {
                IXDefNode child = cellModelNode.getChild(varName);
                if (child != null && child.getXdefValue() != null) {
                    Object value = transformer.parseValue(vl, varName, child.getXdefValue());
                    value = addSource(vl, value);
                    BeanTool.instance().setProperty(cellModel, child.getXdefBeanProp(), value);
                } else {
                    throw new NopException(ERR_XPT_UNDEFINED_CELL_MODEL_PROP)
                            .source(vl)
                            .param(ARG_PROP_NAME, entry.getKey());
                }
            }
        }
    }

    private Object addSource(ValueWithLocation vl, Object value) {
        if (value instanceof IEvalAction && vl.getValue() instanceof String) {
            return new SourceEvalAction((String) vl.getValue(), (IEvalAction) value);
        }
        return value;
    }

    private <T> T importModel(ImportSheetModel impModel, ExcelSheet sheet, Class<T> clazz) {
        if (sheet == null)
            return null;
        return ImportModelHelper.parseSheet(impModel, sheet, clazz);
    }
}