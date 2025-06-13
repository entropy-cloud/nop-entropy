/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.excel.ExcelConstants;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.IXDslModel;
import io.nop.xlang.xdsl.XDslKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.excel.ExcelErrors.ARG_KEY_PROP;
import static io.nop.excel.ExcelErrors.ARG_NAME_PATTERN;
import static io.nop.excel.ExcelErrors.ARG_SHEET_NAME;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_MISSING_MANDATORY_SHEET;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_SHEET_WITH_DUPLICATE_KEY_PROP;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_UNKNOWN_SHEET;
import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;

/**
 * 从表格数据中解析得到对象结构。通过ImportModel提供对象结构信息
 */
public class ImportExcelParser {
    static final Logger LOG = LoggerFactory.getLogger(ImportExcelParser.class);

    private final ImportModel importModel;
    private final XLangCompileTool compileTool;
    private final ICache<Object, Object> cache = LocalCache.newCache("dict-cache", newConfig(100));

    private boolean returnDynamicObject;
    private final IEvalScope scope;

    public boolean isReturnDynamicObject() {
        return returnDynamicObject;
    }

    public void setReturnDynamicObject(boolean returnDynamicObject) {
        this.returnDynamicObject = returnDynamicObject;
    }

    public ImportExcelParser(ImportModel importModel, XLangCompileTool compileTool, IEvalScope scope) {
        this.importModel = importModel;
        this.compileTool = compileTool;
        this.scope = scope;
    }

    public ImportExcelParser(ImportModel importModel, XLangCompileTool compileTool) {
        this(importModel, compileTool, XLang.newEvalScope());
    }

    public ImportExcelParser(ImportModel importModel) {
        this(importModel, XLang.newCompileTool().allowUnregisteredScopeVar(true), XLang.newEvalScope());
    }

    public ImportExcelParser() {
        this(null, XLang.newCompileTool().allowUnregisteredScopeVar(true), XLang.newEvalScope());
    }

    public Object parseFromWorkbook(ExcelWorkbook workbook) {
        DynamicObject obj = newObject(workbook);
        if (importModel.getXdef() != null) {
            obj.prop_set(XDslKeys.DEFAULT.SCHEMA, importModel.getXdef());
        }

        scope.setLocalValue(null, ExcelConstants.VAR_ROOT_RECORD, obj);
        scope.setLocalValue(null, ExcelConstants.VAR_WORKBOOK, workbook);

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

        if (importModel.getNormalizeFieldsExpr() != null) {
            importModel.getNormalizeFieldsExpr().invoke(scope);
        }

        if (importModel.getValidator() != null) {
            try {
                importModel.getValidator().invoke(scope);
            } catch (NopException e) {
                e.addXplStack(workbook.resourcePath());
                throw e;
            }
        }

        Object result = obj;
        if (importModel.getResultType() != null) {
            result = BeanTool.buildBean(obj, importModel.getResultType());
            if (result instanceof IXDslModel) {
                IXDslModel model = (IXDslModel) result;
                if (obj.prop_has(XDslKeys.DEFAULT.SCHEMA)) {
                    model.setXdslSchema((String) obj.prop_get(XDslKeys.DEFAULT.SCHEMA));
                }
            }
            scope.setLocalValue(null, ExcelConstants.VAR_ROOT_MODEL, result);
        }

        if (importModel.getAfterParse() != null) {
            importModel.getAfterParse().invoke(scope);
        }


        dump(workbook, result);

        if (result instanceof INeedInit) {
            ((INeedInit) result).init();
        }
        return result;
    }

    private void dump(ExcelWorkbook wk, Object obj) {
        String resourceStdPath = wk.resourceStdPath();
        if (resourceStdPath == null)
            return;

        boolean dump = ConvertHelper.toPrimitiveBoolean(BeanTool.getProperty(obj, XDslKeys.DEFAULT.DUMP));
        if (dump) {
            String xdefPath = this.importModel.getXdef();
            if (xdefPath != null) {
                XNode node = DslModelHelper.dslModelToXNode(xdefPath, obj);
                IResource dumpResource = ResourceHelper.getDumpResourceWithExt(resourceStdPath, "xml");
                ResourceHelper.writeXml(dumpResource, node);
            } else {
                IResource dumpResource = ResourceHelper.getDumpResourceWithExt(resourceStdPath, "json");
                ResourceHelper.writeText(dumpResource, JsonTool.serialize(obj, true));
            }
        }
    }

    protected IResource getResource(ExcelWorkbook wk) {
        String resourcePath = wk.resourceStdPath();
        if (resourcePath == null)
            return null;
        return VirtualFileSystem.instance().getResource(resourcePath);
    }


    DynamicObject newObject(ISourceLocationGetter wk) {
        DynamicObject entity = new DynamicObject(DynamicObject.class.getName(), null);
        entity.setLocation(wk.getLocation());
        return entity;
    }

    private void parseSheets(ImportSheetModel sheetModel, List<ExcelSheet> sheets, DynamicObject obj, IEvalScope scope) {
        List<Object> list = sheetModel.getKeyProp() == null ? new ArrayList<>() :
                new KeyedList<>(o -> BeanTool.instance().getProperty(o, sheetModel.getKeyProp()));
        ImportDataCollector builder = new ImportDataCollector(scope, cache, compileTool, obj, list);

        for (ExcelSheet sheet : sheets) {
            parseSheet(sheetModel, sheet, scope, builder);
        }

        if (list.size() != sheets.size())
            throw new NopException(ERR_IMPORT_SHEET_WITH_DUPLICATE_KEY_PROP)
                    .param(ARG_SHEET_NAME, sheetModel.getName())
                    .param(ARG_KEY_PROP, sheetModel.getKeyProp());

        if (sheetModel.isMultipleAsMap()) {
            Map<String, Object> map = new LinkedHashMap<>();

            for (int i = 0, n = sheets.size(); i < n; i++) {
                ExcelSheet sheet = sheets.get(i);
                map.put(sheet.getName(), list.get(i));
            }

            if (sheetModel.getFieldName() != null) {
                obj.prop_set(sheetModel.getFieldName(), map);
            } else {
                map.forEach(obj::prop_set);
            }
        } else {
            if (sheetModel.getFieldName() != null) {
                obj.prop_set(sheetModel.getFieldName(), list);
            }
        }
    }

    public DynamicObject parseSheet(ImportSheetModel sheetModel, ExcelSheet sheet, IEvalScope scope) {
        return parseSheet(sheetModel, sheet, newObject(sheetModel), scope);
    }

    public DynamicObject parseSheet(ImportSheetModel sheetModel, ExcelSheet sheet, DynamicObject obj, IEvalScope scope) {
        parseSheet(sheetModel, sheet, scope, new ImportDataCollector(scope, cache, compileTool, obj));
        return obj;
    }

    protected void parseSheet(ImportSheetModel sheetModel, ExcelSheet sheet, IEvalScope scope, ImportDataCollector builder) {
        scope.setLocalValue(ExcelConstants.VAR_IMPORT_SHEET_MODEL, sheetModel);
        scope.setLocalValue(ExcelConstants.VAR_SHEET, sheet);

        if (sheetModel.getParse() != null) {
            sheetModel.getParse().invoke(scope);
        } else {
            new TreeTableDataParser(scope).parse(sheet.getName(), sheet.getTable(), sheetModel, builder);
        }
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