/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.build;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.object.DynamicObject;
import io.nop.excel.ExcelConstants;
import io.nop.excel.imp.ImportModelHelper;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.report.core.XptConstants;
import io.nop.xlang.api.XLang;

import java.util.List;

public class XptConfigParseHelper {

    public static void parseWorkbookModel(ExcelWorkbook workbook) {
        ImportModel importModel = ImportModelHelper.getImportModel(XptConstants.XPT_IMP_MODEL_PATH);
        parseWorkbookModel(workbook, importModel);
    }

    public static void parseWorkbookModel(ExcelWorkbook workbook, ImportModel importModel) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(ExcelConstants.VAR_WORKBOOK, workbook);

        XptWorkbookModel workbookModel = ImportModelHelper.parseSheet(
                importModel.getSheet(XptConstants.SHEET_NAME_XPT_WORKBOOK_MODEL),
                workbook.removeSheet(XptConstants.SHEET_NAME_XPT_WORKBOOK_MODEL), scope,
                XptWorkbookModel.class);

        if (workbookModel != null) {
            List<DynamicObject> namedStyles = (List<DynamicObject>) workbookModel.prop_get(XptConstants.PROP_NAMED_STYLES);
            if (namedStyles != null) {
                workbookModel.prop_remove(XptConstants.PROP_NAMED_STYLES);
                for (DynamicObject namedStyle : namedStyles) {
                    ExcelStyle style = getStyle(workbook,(String)namedStyle.prop_get(XptConstants.PROP_STYLE));
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
    }

    static ExcelStyle getStyle(ExcelWorkbook wk, String styleId){
        if(StringHelper.isEmpty(styleId))
            return null;
        return wk.getStyle(styleId);
    }
}
