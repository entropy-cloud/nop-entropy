/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.validate;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.cache.ICache;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaLoader;
import io.nop.xlang.xmeta.ISchemaNode;
import io.nop.xlang.xmeta.SimpleSchemaValidator;
import io.nop.xlang.xmeta.utils.ObjMetaPropHelper;

import java.util.Collection;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.xlang.XLangErrors.ARG_DICT_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ARG_VALUE_CLASS;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_MANDATORY_PROP_IS_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_VALUE_NOT_COLLECTION;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_VALUE_NOT_IN_DICT;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_VALUE_NOT_MAP;

public class SchemaBasedValidator {
    static SchemaBasedValidator _instance = new SchemaBasedValidator();

    public static SchemaBasedValidator instance() {
        return _instance;
    }

    public void validate(ISchema schema, String bizObjName, String propName, Object value, FieldSelectionBean selection,
                         ValidationContext ctx) {
        if (schema.isObjSchema()) {
            validateObject(schema, bizObjName, value, selection, ctx);
        } else if (schema.isListSchema()) {
            if (!(value instanceof Collection)) {
                ctx.addError(ERR_SCHEMA_PROP_VALUE_NOT_COLLECTION)
                        .param(ARG_BIZ_OBJ_NAME, bizObjName)
                        .param(ARG_PROP_NAME, propName)
                        .param(ARG_VALUE_CLASS, value == null ? null : value.getClass());
                return;
            }

            Collection<?> collection = (Collection<?>) value;
            validateCollection(schema.getItemSchema(), bizObjName, propName, collection, selection, ctx);
            runValidator(schema, collection, ctx);
        } else if (schema.getMapValueSchema() != null) {
            if (!(value instanceof Map<?, ?>)) {
                ctx.addError(ERR_SCHEMA_PROP_VALUE_NOT_MAP)
                        .param(ARG_BIZ_OBJ_NAME, bizObjName)
                        .param(ARG_PROP_NAME, propName)
                        .param(ARG_VALUE_CLASS, value == null ? null : value.getClass());
                return;
            }
            if (schema.getBizObjName() != null)
                bizObjName = schema.getBizObjName();
            validateMap(schema.getMapValueSchema(), bizObjName, (Map<String, Object>) value, selection, ctx);
            runValidator(schema, value, ctx);
        } else {
            SimpleSchemaValidator.INSTANCE.validate(schema, null, bizObjName, propName,
                    value, ctx.getEvalScope(), ctx.getErrorCollector());

            String dictName = schema.getDict();
            if (dictName != null) {
                DictBean dict = getDict(dictName, ctx);
                if (dict != null) {
                    if (!dict.containsValue(value)) {
                        ctx.addError(ERR_SCHEMA_PROP_VALUE_NOT_IN_DICT)
                                .param(ARG_BIZ_OBJ_NAME, bizObjName)
                                .param(ARG_PROP_NAME, propName)
                                .param(ARG_DICT_NAME, dictName)
                                .param(ARG_VALUE, value);
                    }
                }
            }
        }
    }

    public void validateMap(ISchema itemSchema, String bizObjName, Map<String, ?> map,
                            FieldSelectionBean selection, ValidationContext ctx) {
        if (map == null || map.isEmpty())
            return;

        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (selection != null) {
                if (!selection.hasField(entry.getKey()))
                    continue;
            }

            FieldSelectionBean subSelection = selection == null ? null : selection.getField(entry.getKey());
            validate(itemSchema, bizObjName, entry.getKey(), entry.getValue(), subSelection, ctx);
        }
    }

    public void validateCollection(ISchema itemSchema, String bizObjName, String propName, Collection<?> collection,
                                   FieldSelectionBean selection, ValidationContext ctx) {
        if (collection == null || collection.isEmpty())
            return;

        for (Object item : collection) {
            validate(itemSchema, bizObjName, propName, item, selection, ctx);
        }
    }

    public void validateObject(IObjSchema schema, String bizObjName, Object entity,
                               FieldSelectionBean selection,
                               ValidationContext ctx) {
        for (IObjPropMeta propMeta : schema.getProps()) {
            String propName = propMeta.getName();
            if (selection != null) {
                if (!selection.hasField(propName))
                    continue;
            }

            Object value = getPropValue(entity, propMeta, ctx);
            if (propMeta.isMandatory()) {
                if (StringHelper.isEmptyObject(value)) {
                    ctx.addError(ERR_SCHEMA_MANDATORY_PROP_IS_EMPTY)
                            .param(ARG_BIZ_OBJ_NAME, bizObjName).param(ARG_PROP_NAME, propMeta.getName());
                }
            }

            if (value == null)
                continue;

            ISchema propSchema = propMeta.getSchema();
            if (propSchema == null)
                continue;

            ctx.enterProp(propMeta);
            try {
                String propBizObjName = getPropBizObjName(propMeta, bizObjName);
                FieldSelectionBean subSelection = selection == null ? null : selection.getField(propName);
                validate(propSchema, propBizObjName, propName, value, subSelection, ctx);
            } finally {
                ctx.leave();
            }
        }

        runValidator(schema, entity, ctx);
    }

    protected void runValidator(ISchemaNode schema, Object entity, ValidationContext ctx) {
        if (schema.getValidator() != null) {
            try {
                schema.getValidator().call1(null, entity, ctx.getEvalScope());
            } catch (Exception e) {
                ctx.getErrorCollector().addException(e);
            }
        }
    }

    protected String getPropBizObjName(IObjPropMeta propMeta, String bizObjName) {
        String propBizObjName = propMeta.getBizObjName();
        if (propBizObjName == null)
            propBizObjName = propMeta.getItemBizObjName();
        if (propBizObjName == null)
            propBizObjName = bizObjName;
        return propBizObjName;
    }

    protected DictBean getDict(String dictName, ValidationContext ctx) {
        ICache<Object, Object> cache = ctx.getCache();
        return DictProvider.instance().requireDict(ctx.getLocale(), dictName, cache, ctx);
    }

    protected ISchema getRefSchema(ISchema schema, ValidationContext ctx) {
        if (schema == null)
            return null;

        ISchema refSchema = schema.getRefSchema();
        if (refSchema != null)
            return refSchema;

        ISchemaLoader schemaLoader = ctx.getSchemaLoader();
        String ref = schema.getRef();
        if (!StringHelper.isEmpty(ref))
            return schemaLoader.loadSchema(ref);

        String bizObjName = schema.getBizObjName();
        if (!StringHelper.isEmpty(bizObjName))
            return schemaLoader.loadSchema(bizObjName);
        return null;
    }

    protected Object getPropValue(Object entity, IObjPropMeta propMeta, ValidationContext ctx) {
        if (ctx.isDisableGetter())
            return BeanTool.getProperty(entity, propMeta.getName());

        return ObjMetaPropHelper.getPropValue(entity, propMeta, ctx.getEvalScope());
    }
}
