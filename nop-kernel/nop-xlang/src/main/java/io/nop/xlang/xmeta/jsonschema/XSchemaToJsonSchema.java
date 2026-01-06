/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.jsonschema;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.commons.type.StdDataType;
import io.nop.core.context.IServiceContext;
import io.nop.core.dict.DictProvider;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XSchemaToJsonSchema {
    static XSchemaToJsonSchema _instance = new XSchemaToJsonSchema();

    public static void registerInstance(XSchemaToJsonSchema instance) {
        _instance = instance;
    }

    public static XSchemaToJsonSchema instance() {
        return _instance;
    }

    public Map<String, Object> toJsonSchema(ISchema schema, IServiceContext context) {
        Map<String, Object> ret = new LinkedHashMap<>();
        if (schema == null) {
            return ret;
        }

        if (schema.isObjSchema()) {
            toObjectSchema(ret, schema, context);
        } else if (schema.isListSchema()) {
            ret.put("type", "array");
            addArraySpecificProps(ret, schema);
            ret.put("items", toJsonSchema(schema.getItemSchema(), context));
        } else if (schema.isUnionSchema()) {
            List<ISchema> schemas = schema.getOneOf();
            if (schemas != null) {
                List<Map<String, Object>> list = new ArrayList<>(schemas.size());
                for (ISchema subSchema : schemas) {
                    list.add(toJsonSchema(subSchema, context));
                }
                ret.put("anyOf", schemas);
            }
        } else {
            toSimpleSchema(ret, schema, context);
        }
        return ret;
    }

    void addArraySpecificProps(Map<String, Object> ret, ISchema schema) {
        if (schema.getMinItems() != null)
            ret.put("minItems", schema.getMinItems());
        if (schema.getMaxItems() != null) {
            ret.put("maxItems", schema.getMaxItems());
        }
    }

    void addObjectSpecificProps(Map<String, Object> ret, IObjSchema schema) {
        if (schema.getMinProperties() != null) {
            ret.put("minProperties", schema.getMinProperties());
        }
        if (schema.getMaxProperties() != null) {
            ret.put("maxProperties", schema.getMaxProperties());
        }
    }

    void toObjectSchema(Map<String, Object> ret, IObjSchema schema, IServiceContext context) {
        ret.put("type", "object");
        addObjectSpecificProps(ret, schema);

        List<String> required = new ArrayList<>();
        List<? extends IObjPropMeta> props = schema.getProps();
        Map<String, Object> propSchemas = new LinkedHashMap<>();
        if (props != null) {
            props.forEach(prop -> {
                if (prop.isMandatory())
                    required.add(prop.getName());

                propSchemas.put(prop.getName(), toJsonSchema(prop.getSchema(), context));
            });
        }
        ret.put("properties", propSchemas);
        if (!required.isEmpty())
            ret.put("required", required);
    }

    void toSimpleSchema(Map<String, Object> ret, ISchema schema, IServiceContext context) {
        if (schema == null)
            return;

        StdDataType dataType = schema.getStdDataType();
        if (dataType == null)
            dataType = StdDataType.STRING;

        if (dataType == StdDataType.MAP) {
            toMapSchema(ret);
        } else {
            String jsonType = dataType.getJsonType();
            ret.put("type", jsonType);
            if (schema.getMax() != null) {
                ret.put("maximum", schema.getMax());
            }
            if (schema.getMin() != null) {
                ret.put("minimum", schema.getMin());
            }

            if (schema.getMinLength() != null)
                ret.put("minLength", schema.getMinLength());
            if (schema.getMaxLength() != null)
                ret.put("maxLength", schema.getMaxLength());
            if (schema.getPattern() != null)
                ret.put("pattern", schema.getPattern());

            String format = getFormat(schema);
            if (format != null)
                ret.put("format", format);

            String dict = schema.getDict();
            if (dict != null) {
                String locale = ContextProvider.currentLocale();
                DictBean dictBean = DictProvider.instance().getDict(locale, dict, context.getCache(), context);
                if (dictBean != null) {
                    ret.put("enum", dictBean.getValues());
                }
            }
        }
    }

    protected String getFormat(ISchema schema) {
        StdDataType dataType = schema.getStdDataType();
        if (dataType == StdDataType.DATETIME)
            return "date-time";
        if (dataType == StdDataType.TIME)
            return "time";
        if (dataType == StdDataType.DATE)
            return "date";

        if (dataType == StdDataType.DURATION)
            return "duration";

        String domain = schema.getStdDomain();
        if (domain == null)
            return null;
        switch (domain) {
            case "email":
                return "email";
            case "url":
                return "url";
            case "ipv4":
                return "ipv4";
            case "ipv6":
                return "ipv6";
        }
        return null;
    }

    void toMapSchema(Map<String, Object> ret) {
        ret.put("type", "object");
        ret.put("additionalProperties", true);
        Map<String, Object> pattern = new HashMap<>();
        pattern.put(".*", new HashMap<>());
        ret.put("patternProperties", pattern);
    }
}
