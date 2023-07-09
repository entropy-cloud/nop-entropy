/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.crud;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.auth.api.AuthApiErrors;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.lang.Undefined;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.TagsHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.orm.OrmConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import io.nop.xlang.xmeta.impl.ObjConditionExpr;
import io.nop.xlang.xmeta.impl.ObjSelectionMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import static io.nop.auth.api.AuthApiErrors.ARG_FIELD_DISPLAY_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_FIELD_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_OBJ_TYPE_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_PERMISSION;
import static io.nop.auth.api.AuthApiErrors.ARG_ROLES;
import static io.nop.biz.BizConstants.METHOD_GET;
import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_DICT;
import static io.nop.biz.BizErrors.ARG_FILTER_VALUE;
import static io.nop.biz.BizErrors.ARG_OPTION_VALUE;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ARG_PROP_VALUE;
import static io.nop.biz.BizErrors.ARG_SELECTION_ID;
import static io.nop.biz.BizErrors.ERR_BIZ_INVALID_DICT_OPTION;
import static io.nop.biz.BizErrors.ERR_BIZ_MANDATORY_PROP_IS_EMPTY;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_TYPE_CONVERT_FAIL;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_VALUE_NOT_MATCH_FILTER_CONDITION;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_PROP;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_SELECTION;
import static io.nop.biz.crud.BizSchemaHelper.getPropSchema;
import static io.nop.biz.crud.BizSchemaHelper.newError;
import static io.nop.orm.OrmConstants.PROP_ID;

public class ObjMetaBasedValidator {
    private final IBizObjectManager bizObjectManager;
    private final IObjMeta objMeta;
    private final String bizObjName;
    private final IServiceContext context;

    private final boolean checkWriteAuth;

    public ObjMetaBasedValidator(IBizObjectManager bizObjManager, String bizObjName, IObjMeta objMeta,
                                 IServiceContext context, boolean checkWriteAuth) {
        this.bizObjectManager = bizObjManager;
        this.bizObjName = bizObjName;
        this.objMeta = objMeta;
        this.context = context;
        this.checkWriteAuth = checkWriteAuth;
    }

    public Map<String, Object> validateAndConvert(Map<String, Object> data, FieldSelectionBean selection,
                                                  BiPredicate<IObjPropMeta, FieldSelectionBean> filter) {

        if (data == null)
            data = new LinkedHashMap<>();

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, BizConstants.VAR_ROOT, data);

        Map<String, Object> map = _validate(bizObjName, objMeta.getRootSchema(), null, data, selection, filter, scope);
        appendEqCondition(map);
        return map;
    }

    // 将objMeta的filter中定义的eq条件作为固定的属性值设置到对象中
    private void appendEqCondition(Map<String, Object> map) {
        ITreeBean filter = objMeta.getFilter();
        if (filter == null || filter.getChildCount() <= 0)
            return;

        for (ITreeBean child : filter.getChildren()) {
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

    private Map<String, Object> _validate(String bizObjName, ISchema schema, String propName, Map<String, Object> data, FieldSelectionBean selection,
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

            if (OrmConstants.FOR_ADD.endsWith(name))
                continue;

            IObjPropMeta propMeta = schema.getProp(name);
            if (propMeta == null)
                throw new NopException(ERR_BIZ_UNKNOWN_PROP).source(objMeta).param(ARG_PROP_NAME, name)
                        .param(ARG_BIZ_OBJ_NAME, bizObjName);

            if (!filter.test(propMeta, selection)) {
                continue;
            }

            if (checkWriteAuth) {
                doCheckWriteAuth(bizObjName, propMeta);
            }

            if (propMeta.isMandatory()) {
                if (StringHelper.isEmptyObject(value)) {
                    throw newError(ERR_BIZ_MANDATORY_PROP_IS_EMPTY, propMeta).param(ARG_BIZ_OBJ_NAME, bizObjName);
                }
            }

            if (StringHelper.isEmptyObject(value)) {
                setIn(ret, schema, propMeta, null);
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

            String subPropName = propName == null ? propMeta.getName() : propName + '.' + propMeta.getName();

            if (value instanceof Collection) {
                ISchema propSchema = getPropSchema(propMeta, true, bizObjectManager, bizObjName);
                if (propSchema != null) {
                    List<Object> list = CollectionHelper.toList(value);
                    List<Object> converted = new ArrayList<>(list.size());
                    for (Object item : list) {
                        item = _validate(propSchema.getBizObjName(), propSchema, subPropName, (Map<String, Object>) item, propSelection, filter, scope);
                        converted.add(item);
                    }
                    value = converted;
                }
            } else if (value instanceof Map) {
                ISchema propSchema = getPropSchema(propMeta, false, bizObjectManager, bizObjName);
                if (propSchema != null) {

                    value = _validate(propSchema.getBizObjName(), propSchema, subPropName, (Map<String, Object>) value, propSelection, filter, scope);
                }
            } else {
                validateValue(propMeta.getSchema(), subPropName, value, propMeta);
                value = convertValue(propMeta, value, data, ret);
            }
            setIn(ret, schema, propMeta, value);
        }
        return ret;
    }

    private void doCheckWriteAuth(String objTypeName, IObjPropMeta propMeta) {
        ActionAuthMeta auth = propMeta.getWriteAuth();
        if (auth == null)
            return;

        IActionAuthChecker authChecker = this.context.getActionAuthChecker();
        if (authChecker == null)
            return;

        if (auth.getRoles() != null && !auth.getRoles().isEmpty()) {
            if (this.context.getUserContext().isUserInAnyRole(auth.getRoles()))
                return;
        }

        if (auth.getPermissions() != null && !auth.getPermissions().isEmpty()) {
            if (authChecker.isPermissionSetSatisfied(auth.getPermissions(), context))
                return;
        }

        throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION_FOR_FIELD)
                .param(ARG_FIELD_NAME, propMeta.getName())
                .param(ARG_PERMISSION, auth.getPermissions())
                .param(ARG_ROLES, auth.getRoles())
                .param(ARG_OBJ_TYPE_NAME, objTypeName)
                .param(ARG_FIELD_DISPLAY_NAME, propMeta.getDisplayName());
    }

    private void setIn(Map<String, Object> ret, ISchema schema, IObjPropMeta propMeta, Object value) {
        // json component 转换为对jsonText的赋值
        String propName = propMeta.getName();
        if (propName.endsWith("Component") && TagsHelper.contains(propMeta.getTagSet(), OrmConstants.TAG_JSON)) {
            String textPropName = propName.substring(0, propName.length() - "Component".length());
            IObjPropMeta textProp = schema.getProp(textPropName);
            if (textProp != null) {
                if (StringHelper.isEmptyObject(value)) {
                    value = null;
                } else {
                    value = JsonTool.stringify(value);
                }
                ret.put(textPropName, value);
                return;
            }
        }
        ret.put(propMeta.getName(), value);
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

    private void validateValue(ISchema schema, String subPropName, Object value, IObjPropMeta propMeta) {
        if (schema != null) {
            if (schema.isSimpleSchema()) {
                SimpleSchemaValidator.INSTANCE.validate(schema, null, subPropName, value, IValidationErrorCollector.THROW_ERROR);
            }

            String dictName = schema.getDict();
            if (dictName != null) {
                DictBean dictBean = DictProvider.instance().requireDict(ContextProvider.currentLocale(), dictName,
                        context.getCache(),context);
                DictOptionBean option = dictBean.getOptionByValue(value);
                if (option == null) {
                    String dict = dictName;
                    if (dictBean.getLabel() != null)
                        dict = dictBean.getLabel();

                    throw newError(ERR_BIZ_INVALID_DICT_OPTION, propMeta).param(ARG_DICT, dict).param(ARG_OPTION_VALUE,
                            value);
                }
            } else {
                String relation = (String) propMeta.prop_get(BizConstants.EXT_RELATION);
                if (relation != null) {
                    IObjPropMeta relProp = objMeta.getProp(relation);
                    if (relProp == null)
                        throw newError(ERR_BIZ_UNKNOWN_PROP, propMeta)
                                .param(ARG_PROP_NAME, relation);
                    validateRefValue(relProp, value);
                }
            }
        }
    }

    private void validateRefValue(IObjPropMeta relProp, Object value) {
        ISchema relSchema = relProp.getSchema();
        if (relSchema != null) {
            String bizObjName = relSchema.getBizObjName();
            if (bizObjName != null) {
                Map<String, Object> request = new HashMap<>();
                request.put(PROP_ID, value);
                // 确保对象可见
                bizObjectManager.getBizObject(bizObjName).invoke(METHOD_GET, request, null, context);
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

            if (autoExpr.getWhen() != null && !autoExpr.getWhen().contains(action))
                continue;

            Object value = null;
            if (autoExpr.getSource() != null) {
                value = autoExpr.getSource().invoke(scope);
            }

            if (value != Undefined.undefined)
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