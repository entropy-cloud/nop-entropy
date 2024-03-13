/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.ICache;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.validator.ModelBasedValidator;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.excel.ExcelConstants;
import io.nop.excel.imp.model.IFieldContainer;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_CELL_POS;
import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.excel.ExcelErrors.ARG_ALLOWED_VALUES;
import static io.nop.excel.ExcelErrors.ARG_DISPLAY_NAME;
import static io.nop.excel.ExcelErrors.ARG_FIELD_LABEL;
import static io.nop.excel.ExcelErrors.ARG_FIELD_NAME;
import static io.nop.excel.ExcelErrors.ARG_SHEET_NAME;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_FIELD_VALUE_NOT_IN_DICT;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_MANDATORY_FIELD_IS_EMPTY;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_MISSING_MANDATORY_FIELD;

public class ImportDataCollector implements ITableDataEventListener {
    static final Logger LOG = LoggerFactory.getLogger(ImportDataCollector.class);

    private List<Object> entityParents;
    private String sheetName;
    private List<IListAdapter> listAdapters;

    private final IEvalScope scope;
    private final ICache<Object, Object> cache;
    private final XLangCompileTool compileTool;
    private final DynamicObject rootObj;

    private final List<Object> multipleResults;

    public ImportDataCollector(IEvalScope scope, ICache<Object, Object> cache,
                               XLangCompileTool compileTool, DynamicObject rootObj, List<Object> multipleResults) {
        this.scope = scope;
        this.cache = cache;
        this.compileTool = compileTool;
        this.rootObj = rootObj;
        this.multipleResults = multipleResults;
    }

    public ImportDataCollector(IEvalScope scope, ICache<Object, Object> cache,
                               XLangCompileTool compileTool, DynamicObject rootObj) {
        this(scope, cache, compileTool, rootObj, Collections.emptyList());
    }

    @Override
    public void beginSheet(String sheetName, ImportSheetModel sheetModel) {
        this.sheetName = sheetName;
        this.entityParents = new ArrayList<>();
        this.listAdapters = new ArrayList<>();

        this.entityParents.add(rootObj);

        scope.setLocalValue(null, ExcelConstants.VAR_RECORD_PARENTS, entityParents);
        scope.setLocalValue(null, ExcelConstants.VAR_SHEET_NAME, sheetName);
        scope.setLocalValue(null, ExcelConstants.VAR_ROOT_RECORD, rootObj);

        if (sheetModel.getBeforeParse() != null) {
            sheetModel.getBeforeParse().invoke(scope);
        }
    }

    @Override
    public void endSheet(ImportSheetModel sheetModel) {
        scope.setLocalValue(null, ExcelConstants.VAR_ROOT_RECORD, rootObj);

        if (sheetModel.getAfterParse() != null) {
            sheetModel.getAfterParse().invoke(scope);
        }
    }

    @Override
    public void beginList(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                          IFieldContainer fieldModel, boolean cardList) {
        IListAdapter adapter = fieldModel.getKeyProp() != null ? new KeyedListAdapter(fieldModel.getKeyProp())
                : ArrayListAdapter.INSTANCE;
        listAdapters.add(adapter);

        List<Object> list = adapter.newList();
        pushObject(list);
    }

    @Override
    public void endList(int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
        popObject(fieldModel, true);
    }

    @Override
    public void beginObject(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
        if (fieldModel.getFieldName() != null) {
            DynamicObject obj = new DynamicObject(getObjName(rowIndex, colIndex, maxRowIndex, maxColIndex));
            pushObject(obj);
        }
    }

    @Override
    public void endObject(IFieldContainer fieldModel) {
        DynamicObject obj = (DynamicObject) entityParents.get(entityParents.size() - 1);

        validateMandatory(fieldModel.getFieldNameMap(), obj);
        addDefaults(fieldModel.getFieldNameMap(), obj);

        if (fieldModel.getNormalizeFieldsExpr() != null) {
            scope.setLocalValue(null, ExcelConstants.VAR_RECORD, obj);
            fieldModel.getNormalizeFieldsExpr().invoke(scope);
        }

        if (fieldModel.getFieldName() != null) {
            popObject(fieldModel, false);
        }
    }

    private void pushObject(Object obj) {
        this.entityParents.add(obj);
        scope.setLocalValue(null, ExcelConstants.VAR_RECORD, obj);
    }

    private void popObject(IFieldContainer fieldModel, boolean endList) {
        Object entity = entityParents.remove(entityParents.size() - 1);

        Object last = CollectionHelper.last(entityParents);
        scope.setLocalValue(null, ExcelConstants.VAR_RECORD, last);

        if (fieldModel.isList()) {
            if (endList) {
                listAdapters.remove(listAdapters.size() - 1);
                if (fieldModel.isMultiple()) {
                    multipleResults.add(entity);
                } else if (fieldModel.getFieldName() != null) {
                    setProp(last, fieldModel.getPropOrName(), entity);
                }
            } else {
                IListAdapter adapter = listAdapters.get(listAdapters.size() - 1);
                adapter.add((List<Object>) last, entity);
            }
        } else if (fieldModel.getFieldName() != null) {
            if (fieldModel.isMultiple()) {
                multipleResults.add(entity);
            } else {
                setProp(last, fieldModel.getPropOrName(), entity);
            }
        }
    }

    private void setProp(Object bean, String propName, Object value) {
        BeanTool.setComplexProperty(bean, propName, value);
    }

    private String getObjName(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex) {
        return CellPosition.toABString(rowIndex, colIndex) + ':' + CellPosition.toABString(maxRowIndex, maxColIndex)
                + '!' + sheetName;
    }

    @Override
    public void simpleField(int rowIndex, int colIndex, ICellView cell, LabelData labelData) {
        ImportFieldModel field = labelData.getField();
        Object value = cell == null ? null : cell.getValue();
        LOG.trace("nop.imp.parse-field:name={},r={},c={},value={}", field.getName(), rowIndex, colIndex, value);

        if (Boolean.TRUE.equals(field.getStripText())) {
            if (value instanceof String)
                value = StringHelper.strip(value.toString());
        }

        String fieldLabel = labelData.getFieldLabel();
        ;
        scope.setLocalValue(ExcelConstants.VAR_FIELD_LABEL, fieldLabel);
        scope.setLocalValue(ExcelConstants.VAR_CELL, cell);
        scope.setLocalValue(ExcelConstants.VAR_LABEL_DATA, labelData);

        if (field.getValueExpr() != null) {
            scope.setLocalValue(null, ExcelConstants.VAR_VALUE, value);
            value = field.getValueExpr().invoke(scope);
        }

        if (field.isMandatory()) {
            if (StringHelper.isEmptyObject(value)) {
                if (field.isIgnoreWhenEmpty())
                    return;

                throw new NopException(ERR_IMPORT_MANDATORY_FIELD_IS_EMPTY).param(ARG_SHEET_NAME, sheetName)
                        .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex))
                        .param(ARG_FIELD_NAME, field.getName()).param(ARG_DISPLAY_NAME, field.getDisplayName());
            }
        }

        if (value != null && field.getSchema() != null) {
            String dict = field.getSchema().getDict();
            if (dict != null) {
                DictBean dictBean;
                try {
                    dictBean = DictProvider.instance().requireDict(null, dict, cache, scope);
                } catch (NopException e) {
                    e.addXplStack("parse-cell-value:cellPos=" + CellPosition.toABString(rowIndex, colIndex) + ",sheet="
                            + sheetName);
                    throw e;
                }
                DictOptionBean option = dictBean.getOptionByValue(value);
                if (option == null) {
                    option = dictBean.getOptionByLabel(value.toString());
                }
                if (option == null) {
                    throw new NopException(ERR_IMPORT_FIELD_VALUE_NOT_IN_DICT).param(ARG_SHEET_NAME, sheetName)
                            .param(ARG_FIELD_NAME, field.getName())
                            .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex)).param(ARG_VALUE, value)
                            .param(ARG_ALLOWED_VALUES, dictBean.getValues());
                } else {
                    value = option.getValue();
                }
            }
            String stdDomain = field.getSchema().getStdDomain();
            if (stdDomain != null) {
                IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
                if (handler != null) {
                    String source = value.toString();
                    SourceLocation loc = getLocation(sheetName, rowIndex, colIndex);
                    value = handler.parseProp(null, loc, field.getName(), value,
                            compileTool);
                    if (value instanceof IEvalAction) {
                        value = EvalCode.addSource(loc, (IEvalAction) value, source);
                    }
                }
            }

            if (field.getSchema().getValidator() != null) {
                scope.setLocalValue(null, ExcelConstants.VAR_VALUE, value);

                new ModelBasedValidator(field.getSchema().getValidator()).validate(scope, error -> {
                    throw NopRebuildException.rebuild(error).param(ARG_SHEET_NAME, sheetName)
                            .param(ARG_FIELD_NAME, field.getName()).param(ARG_FIELD_LABEL, field.getFieldLabel())
                            .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));
                });
            }
        }

        if (!shouldIgnore(field, value)) {
            Object obj = entityParents.get(entityParents.size() - 1);
            if (!field.isVirtual())
                setProp(obj, field.getPropOrName(), value);

            if (field.getNormalizeFieldsExpr() != null) {
                scope.setLocalValue(ExcelConstants.VAR_RECORD, obj);
                field.getNormalizeFieldsExpr().invoke(scope);
            }
        }
    }

    private boolean shouldIgnore(ImportFieldModel field, Object value) {
        if (!StringHelper.isEmptyObject(value))
            return false;
        return field.isIgnoreWhenEmpty();
    }


    private void addDefaults(Map<String, ImportFieldModel> fieldMap, DynamicObject obj) {
        for (ImportFieldModel field : fieldMap.values()) {
            if (field.isComputed()) {
                if (field.getValueExpr() != null) {
                    scope.setLocalValue(null, ExcelConstants.VAR_RECORD, obj);
                    scope.removeLocalValue(ExcelConstants.VAR_VALUE);
                    Object value = field.getValueExpr().invoke(scope);
                    setProp(obj, field.getName(), value);
                }
            } else if (obj != null && !field.isVirtual() && !field.isIgnoreWhenEmpty()) {
                obj.makeComplexPropDefault(field.getName(), null);
            }
        }
    }

    private void validateMandatory(Map<String, ImportFieldModel> fieldMap, DynamicObject obj) {
        for (ImportFieldModel field : fieldMap.values()) {
            if (field.isMandatory()) {
                if (isPropEmpty(obj, field.getPropOrName()))
                    throw new NopException(ERR_IMPORT_MISSING_MANDATORY_FIELD).param(ARG_SHEET_NAME, sheetName)
                            .param(ARG_FIELD_NAME, field.getName()).param(ARG_FIELD_LABEL, field.getFieldLabel());
            }
        }
    }

    private boolean isPropEmpty(DynamicObject dynObj, String propName) {
        if (propName.indexOf('.') > 0) {
            return StringHelper.isEmptyObject(BeanTool.getComplexProperty(dynObj, propName));
        }
        if (!dynObj.prop_has(propName))
            return true;

        Object value = dynObj.prop_get(propName);
        return StringHelper.isEmptyObject(value);
    }

    private SourceLocation getLocation(String sheetName, int rowIndex, int colIndex) {
        String path = "<sheet>";
        return new SourceLocation(path, 0, 0, 0, 0, sheetName, CellPosition.toABString(rowIndex, colIndex), null);
    }
}