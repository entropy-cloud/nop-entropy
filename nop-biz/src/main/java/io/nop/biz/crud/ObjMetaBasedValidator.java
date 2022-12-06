package io.nop.biz.crud;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.ObjConditionExpr;
import io.nop.xlang.xmeta.impl.ObjSelectionMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_DICT;
import static io.nop.biz.BizErrors.ARG_FILTER_VALUE;
import static io.nop.biz.BizErrors.ARG_MAX_LENGTH;
import static io.nop.biz.BizErrors.ARG_OPTION_VALUE;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ARG_PROP_VALUE;
import static io.nop.biz.BizErrors.ARG_SELECTION_ID;
import static io.nop.biz.BizErrors.ERR_BIZ_INVALID_DICT_OPTION;
import static io.nop.biz.BizErrors.ERR_BIZ_MANDATORY_PROP_IS_EMPTY;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_EXCEED_MAX_LENGTH;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_TYPE_CONVERT_FAIL;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_VALUE_NOT_MATCH_FILTER_CONDITION;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_PROP;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_SELECTION;
import static io.nop.biz.crud.BizSchemaHelper.getPropSchema;
import static io.nop.biz.crud.BizSchemaHelper.newError;

public class ObjMetaBasedValidator {
    private final IBizObjectManager bizObjectManager;
    private final IObjMeta objMeta;
    private final String bizObjName;
    private final IServiceContext context;

    public ObjMetaBasedValidator(IBizObjectManager bizObjManager, String bizObjName, IObjMeta objMeta,
                                 IServiceContext context) {
        this.bizObjectManager = bizObjManager;
        this.bizObjName = bizObjName;
        this.objMeta = objMeta;
        this.context = context;
    }

    public Map<String, Object> validateAndConvert(Map<String, Object> data, FieldSelectionBean selection,
                                                  BiPredicate<IObjPropMeta, FieldSelectionBean> filter) {

        if (data == null)
            data = new LinkedHashMap<>();

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, BizConstants.VAR_ROOT, data);

        Map<String, Object> map = _validate(objMeta.getRootSchema(), data, selection, filter, scope);
        appendEqCondition(map);
        return map;
    }

    // 将objMeta的filter中定义的eq条件作为固定的属性值设置到对象中
    private void appendEqCondition(Map<String, Object> map) {
        TreeBean filter = objMeta.getFilter();
        if (filter == null || filter.getChildCount() <= 0)
            return;

        for (TreeBean child : filter.getChildren()) {
            if (FilterBeanConstants.FILTER_OP_EQ.equals(child.getTagName())) {
                String name = (String) child.getAttr(FilterBeanConstants.FILTER_ATTR_NAME);
                Object value = child.getAttr(FilterBeanConstants.FILTER_ATTR_VALUE);
                Object propValue = map.get(name);
                if (propValue != null && !Objects.equals(value, propValue))
                    throw new NopException(ERR_BIZ_PROP_VALUE_NOT_MATCH_FILTER_CONDITION)
                            .param(ARG_BIZ_OBJ_NAME, bizObjName).param(ARG_PROP_NAME, name)
                            .param(ARG_FILTER_VALUE, value).param(ARG_PROP_VALUE, propValue);
                map.put(name, value);
            }
        }
    }

    private Map<String, Object> _validate(ISchema schema, Map<String, Object> data, FieldSelectionBean selection,
                                          BiPredicate<IObjPropMeta, FieldSelectionBean> filter, IEvalScope scope) {
        scope.setLocalValue(null, BizConstants.VAR_DATA, data);

        Map<String, Object> ret = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            if (GraphQLConstants.PROP_ID.equals(name)) {
                if (StringHelper.isEmptyObject(value))
                    value = null;
                ret.put(name, value);
                continue;
            }

            IObjPropMeta propMeta = schema.getProp(name);
            if (propMeta == null)
                throw new NopException(ERR_BIZ_UNKNOWN_PROP).source(objMeta).param(ARG_PROP_NAME, name)
                        .param(ARG_BIZ_OBJ_NAME, bizObjName);

            if (!filter.test(propMeta, selection)) {
                continue;
            }

            if (propMeta.isMandatory()) {
                if (StringHelper.isEmptyObject(value)) {
                    throw newError(ERR_BIZ_MANDATORY_PROP_IS_EMPTY, propMeta).param(ARG_BIZ_OBJ_NAME, bizObjName);
                }
            }

            if (StringHelper.isEmptyObject(value)) {
                ret.put(name, null);
                continue;
            }

            FieldSelectionBean propSelection = null;
            if (selection != null) {
                propSelection = selection.getField(name);
                if (propSelection == null)
                    continue;
            }

            if (value != null) {
                value = transformIn(value, propMeta, data, ret);
            }

            if (value instanceof Collection) {
                ISchema propSchema = getPropSchema(propMeta, true, bizObjectManager, bizObjName);
                if (propSchema != null) {
                    List<Object> list = CollectionHelper.toList(value);
                    List<Object> converted = new ArrayList<>(list.size());
                    for (Object item : list) {
                        item = _validate(propSchema, (Map<String, Object>) item, propSelection, filter, scope);
                        converted.add(item);
                    }
                    value = converted;
                }
            } else if (value instanceof Map) {
                ISchema propSchema = getPropSchema(propMeta, false, bizObjectManager, bizObjName);
                if (propSchema != null) {
                    value = _validate(propSchema, (Map<String, Object>) value, propSelection, filter, scope);
                }
            } else {
                validateValue(propMeta.getSchema(), value, propMeta);
                value = convertValue(propMeta, value, data, ret);
            }
            ret.put(name, value);
        }
        return ret;
    }

    private Object transformIn(Object value, IObjPropMeta propMeta, Map<String, Object> data, Map<String, Object> ret) {
        IEvalAction action = propMeta.getTransformIn();
        if (action != null) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, BizConstants.VAR_DATA, data);
            scope.setLocalValue(null, BizConstants.VAR_VALUE, value);
            scope.setLocalValue(null, BizConstants.VAR_TRNAS_DATA, ret);
            value = action.invoke(scope);
        }
        return value;
    }

    private void validateValue(ISchema schema, Object value, IObjPropMeta propMeta) {
        if (schema != null) {
            String dictName = schema.getDict();
            if (dictName != null) {
                DictBean dictBean = DictProvider.instance().getDict(ContextProvider.currentLocale(), dictName,
                        context.getCache());
                DictOptionBean option = dictBean.getOptionByValue(value);
                if (option == null) {
                    String dict = dictName;
                    if (dictBean.getLabel() != null)
                        dict = dictBean.getLabel();

                    throw newError(ERR_BIZ_INVALID_DICT_OPTION, propMeta).param(ARG_DICT, dict).param(ARG_OPTION_VALUE,
                            value);
                }
            }

            if (schema.isSimpleSchema()) {
                Integer len = schema.getMaxLength();
                if (len != null) {
                    String str = value.toString();
                    if (str.length() > len)
                        throw newError(ERR_BIZ_PROP_EXCEED_MAX_LENGTH, propMeta).param(ARG_MAX_LENGTH, len);
                }
            }
        }
    }

    private Object convertValue(IObjPropMeta propMeta, Object value, Map<String, Object> data,
                                Map<String, Object> ret) {
        IEvalAction action = propMeta.getTransformIn();
        if (action != null) {
            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue(null, BizConstants.VAR_DATA, data);
            scope.setLocalValue(null, BizConstants.VAR_TRNAS_DATA, ret);
            scope.setLocalValue(null, BizConstants.VAR_VALUE, value);
            value = action.invoke(scope);
        }

        StdDataType type = propMeta.getStdDataType();
        if (type == null)
            return value;
        return type.convert(value, errCode -> {
            return new NopException(ERR_BIZ_PROP_TYPE_CONVERT_FAIL).param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_PROP_NAME, propMeta.getName());
        });
    }

    public Map<String, Object> validateForSave(Map<String, Object> data, FieldSelectionBean selection) {
        Map<String, Object> ret = validateAndConvert(data, selection, (propMeta, sel) -> selection != null
                ? selection.hasField(propMeta.getName()) : propMeta.isInsertable());
        runAutoExpr(BizConstants.METHOD_SAVE, ret);
        return ret;
    }

    public Map<String, Object> validateForUpdate(Map<String, Object> data, FieldSelectionBean selection) {
        Map<String, Object> ret = validateAndConvert(data, selection,
                (propMeta, sel) -> selection != null ? selection.hasField(propMeta.getName()) : propMeta.isUpdatable());
        runAutoExpr(BizConstants.METHOD_UPDATE, ret);
        return ret;
    }

    /**
     * 运行自动初始化表达式
     */
    void runAutoExpr(String action, Map<String, Object> data) {
        IEvalScope scope = context.getEvalScope().newChildScope();
        scope.setLocalValue(null, BizConstants.VAR_DATA, data);

        for (IObjPropMeta propMeta : objMeta.getProps()) {
            ObjConditionExpr autoExpr = propMeta.getAutoExpr();
            if (autoExpr == null)
                continue;

            if (data.containsKey(propMeta.getName()))
                continue;

            if (!autoExpr.getWhen().contains(action))
                continue;

            Object value = null;
            if (autoExpr.getSource() != null) {
                value = autoExpr.getSource().invoke(scope);
            }
            data.put(propMeta.getName(), value);
        }
    }

    public Map<String, Object> validateForSelection(Map<String, Object> data, String selectionId) {
        ObjSelectionMeta selectionMeta = objMeta.getSelection(selectionId);
        if (selectionMeta == null)
            throw new NopException(ERR_BIZ_UNKNOWN_SELECTION).param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_SELECTION_ID, selectionId);

        return validateAndConvert(data, selectionMeta.getMapping(),
                (propMeta, sel) -> sel.hasField(propMeta.getName()));
    }
}