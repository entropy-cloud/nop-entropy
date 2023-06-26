package io.nop.excel.imp;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

public class ImportModelHelper {
    public static ImportModel getImportModel(String impPath) {
        return (ImportModel) ResourceComponentManager.instance().loadComponentModel(impPath);
    }

    public static DynamicObject parseSheet(ImportSheetModel impModel, ExcelSheet sheet, XLangCompileTool cp, IEvalScope scope) {
        return new WorkbookDataParser(null, cp).parseSheet(impModel, sheet, scope);
    }

    public static DynamicObject parseSheet(ImportSheetModel impModel, ExcelSheet sheet, IEvalScope scope) {
        return parseSheet(impModel, sheet, XLang.newCompileTool().allowUnregisteredScopeVar(true), scope);
    }

    public static <T> T parseSheet(ImportSheetModel impModel, ExcelSheet sheet, XLangCompileTool cp,
                                   IEvalScope scope, Class<T> clazz) {
        DynamicObject obj = parseSheet(impModel, sheet, cp, scope);
        return BeanTool.buildBean(obj, clazz);
    }

    public static <T> T parseSheet(ImportSheetModel impModel, ExcelSheet sheet, IEvalScope scope, Class<T> clazz) {
        if (sheet == null)
            return null;
        return parseSheet(impModel, sheet, XLang.newCompileTool().allowUnregisteredScopeVar(true), scope, clazz);
    }
}
