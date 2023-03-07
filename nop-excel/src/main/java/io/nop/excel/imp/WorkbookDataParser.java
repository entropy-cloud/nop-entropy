/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.imp;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.util.INeedInit;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.model.validator.ModelBasedValidator;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.excel.ExcelConstants;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.excel.ExcelErrors.ARG_NAME_PATTERN;
import static io.nop.excel.ExcelErrors.ARG_SHEET_NAME;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_MISSING_MANDATORY_SHEET;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_UNKNOWN_SHEET;
import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;

/**
 * 从表格数据中解析得到对象结构。通过ImportModel提供对象结构信息
 */
public class WorkbookDataParser {
    static final Logger LOG = LoggerFactory.getLogger(WorkbookDataParser.class);

    private final ImportModel importModel;
    private final XLangCompileTool compileTool = XLang.newCompileTool();
    private final ICache<Object, Object> cache = LocalCache.newCache("dict-cache", newConfig(100));

    private boolean returnDynamicObject;

    public boolean isReturnDynamicObject() {
        return returnDynamicObject;
    }

    public void setReturnDynamicObject(boolean returnDynamicObject) {
        this.returnDynamicObject = returnDynamicObject;
    }

    public WorkbookDataParser(ImportModel importModel) {
        this.importModel = importModel;
    }

    public WorkbookDataParser() {
        this.importModel = null;
    }

    public Object parseFromWorkbook(ExcelWorkbook workbook) {
        DynamicObject obj = newObject(workbook);
        IEvalScope scope = XLang.newEvalScope();

        scope.setLocalValue(null, ExcelConstants.VAR_ROOT_RECORD, obj);

        if (importModel.getBeforeParse() != null)
            importModel.getBeforeParse().invoke(scope);

        Map<String, ExcelSheet> sheets = new LinkedHashMap<>();
        for (ExcelSheet sheet : workbook.getSheets()) {
            sheets.put(sheet.getName(), sheet);
        }

        for (ImportSheetModel sheetModel : importModel.getSheets()) {
            if (sheetModel.isMultiple()) {
                List<ExcelSheet> matched = collectMatchedSheets(sheetModel, sheets, scope);
                if (sheetModel.isIgnore())
                    continue;

                parseSheets(sheetModel, matched, obj, scope);
            } else {
                ExcelSheet sheet = getMatchedSheet(sheetModel, sheets, scope);
                if (sheetModel.isIgnore())
                    continue;

                if (sheet != null) {
                    parseSheet(sheetModel, sheet, obj, scope);
                }
            }
        }

        if (!sheets.isEmpty()) {
            if (importModel.isIgnoreUnknownSheet()) {
                LOG.info("nop.imp.ignore-sheets:sheetNames={},path={}", sheets.keySet(), workbook.resourcePath());
            } else {
                throw new NopException(ERR_IMPORT_UNKNOWN_SHEET).source(workbook)
                        .param(ARG_SHEET_NAME, CollectionHelper.first(sheets.keySet()))
                        .param(ARG_ALLOWED_NAMES, importModel.keySet_sheets());
            }
        }

        if (importModel.getValidator() != null) {
            new ModelBasedValidator(importModel.getValidator()).validate(scope, error -> {
                throw NopRebuildException.rebuild(error).param(ARG_RESOURCE_PATH, workbook.resourcePath());
            });
        }

        Object result = obj;
        if (importModel.getResultType() != null) {
            result = BeanTool.buildBean(obj, importModel.getResultType());
            scope.setLocalValue(null, ExcelConstants.VAR_ROOT_MODEL, result);
        }

        if (importModel.getAfterParse() != null) {
            importModel.getAfterParse().invoke(scope);
        }

        if (result instanceof INeedInit) {
            ((INeedInit) result).init();
        }
        return result;
    }

    DynamicObject newObject(ISourceLocationGetter wk) {
        DynamicObject entity = new DynamicObject(DynamicObject.class.getName(), null);
        entity.setLocation(wk.getLocation());
        return entity;
    }

    private void parseSheets(ImportSheetModel sheetModel, List<ExcelSheet> sheets, DynamicObject obj, IEvalScope scope) {
        List<Object> list = new ArrayList<>();
        ImportDataCollector builder = new ImportDataCollector(scope, cache, compileTool, obj,list);

        for (ExcelSheet sheet : sheets) {
            new TableDataParser().parse(sheet.getName(), sheet.getTable(), sheetModel, builder);
        }

        if (sheetModel.getFieldName() != null) {
            obj.prop_set(sheetModel.getFieldName(), list);
        }
    }

    public DynamicObject parseSheet(ImportSheetModel sheetModel, ExcelSheet sheet, IEvalScope scope) {
        DynamicObject obj = newObject(sheet);
        parseSheet(sheetModel, sheet, obj, scope);
        return obj;
    }

    private void parseSheet(ImportSheetModel sheetModel, ExcelSheet sheet, DynamicObject obj, IEvalScope scope) {
        //new SheetBeanParser(sheetModel, compileTool, cache, importModel.isDump()).parseFromSheet(sheet, obj, scope);
        ImportDataCollector builder = new ImportDataCollector(scope, cache, compileTool, obj);
        new TableDataParser().parse(sheet.getName(), sheet.getTable(), sheetModel, builder);
    }

    private List<ExcelSheet> collectMatchedSheets(ImportSheetModel sheetModel, Map<String, ExcelSheet> sheets,
                                                  IEvalScope scope) {
        List<ExcelSheet> ret = new ArrayList<>();
        for (ExcelSheet sheet : sheets.values()) {
            if (sheetModel.getNamePattern() != null) {
                if (!sheetModel.matchNamePattern(sheet.getName()))
                    continue;
            } else if (!sheet.getName().equals(sheetModel.getName())) {
                continue;
            }
            if (sheetModel.getWhen() != null) {
                scope.setLocalValue(null, ExcelConstants.VAR_SHEET, sheet);
                if (!sheetModel.getWhen().passConditions(scope)) {
                    continue;
                }
            }
            ret.add(sheet);
        }

        if (ret.isEmpty() && sheetModel.isMandatory())
            throw new NopException(ERR_IMPORT_MISSING_MANDATORY_SHEET).param(ARG_SHEET_NAME, sheetModel.getName())
                    .param(ARG_NAME_PATTERN, sheetModel.getNamePattern());

        for (ExcelSheet sheet : ret) {
            sheets.remove(sheet.getName());
        }
        return ret;
    }

    private ExcelSheet getMatchedSheet(ImportSheetModel sheetModel, Map<String, ExcelSheet> sheets, IEvalScope scope) {
        ExcelSheet sheet = null;
        if (sheetModel.getNamePattern() == null) {
            sheet = sheets.get(sheetModel.getName());
        } else {
            for (ExcelSheet sh : sheets.values()) {
                if (sheetModel.matchNamePattern(sh.getName())) {
                    sheet = sh;
                    break;
                }
            }
        }

        if (sheet != null && sheetModel.getWhen() != null) {
            scope.setLocalValue(null, ExcelConstants.VAR_SHEET, sheet);
            if (!sheetModel.getWhen().passConditions(scope)) {
                sheet = null;
            }
        }

        if (sheet == null && sheetModel.isMandatory()) {
            throw new NopException(ERR_IMPORT_MISSING_MANDATORY_SHEET).param(ARG_SHEET_NAME, sheetModel.getName())
                    .param(ARG_NAME_PATTERN, sheetModel.getNamePattern());
        }

        if (sheet != null) {
            sheets.remove(sheet.getName());
        }

        return sheet;
    }
}