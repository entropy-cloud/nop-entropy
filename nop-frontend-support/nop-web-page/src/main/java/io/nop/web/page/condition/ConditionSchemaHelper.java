/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page.condition;

import io.nop.commons.type.StdDataType;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将schema定义转换为AMIS的condition fields定义
 */
public class ConditionSchemaHelper {
    public static List<Map<String, Object>> schemaToFields(String prefix, ISchema schema) {

        List<? extends IObjPropMeta> props = schema.getProps();
        if (props == null || props.isEmpty())
            return null;

        List<Map<String, Object>> ret = new ArrayList<>();
        for (IObjPropMeta prop : props) {
            ret.add(propToField(prefix, prop));
        }
        return ret;
    }

    static Map<String, Object> propToField(String prefix, IObjPropMeta propMeta) {
        Map<String, Object> ret = new LinkedHashMap<>();

        String label = propMeta.getDisplayName();
        String name = addPrefix(prefix, propMeta.getName());
        String type = null;

        ret.put("label", label);

        ISchema schema = propMeta.getSchema();
        if (schema != null) {
            if (schema.hasProps()) {
                return propToGroup(name, schema, ret);
            } else {
                type = getPropType(schema);
            }

            if (type.equals("select")) {
                ret.put("source", "@dict:" + schema.getDict());
            }
        } else {
            type = "text";
        }
        ret.put("name", name);
        ret.put("type", type);

        return ret;
    }

    static String addPrefix(String prefix, String name) {
        if (prefix == null)
            return name;
        return prefix + "." + name;
    }

    static Map<String, Object> propToGroup(String prefix, ISchema schema, Map<String, Object> ret) {
        List<Map<String, Object>> children = schemaToFields(prefix, schema);
        ret.put("children", children);
        return ret;
    }

    /**
     * FieldType:
     * | 'text'
     * | 'number'
     * | 'boolean'
     * | 'date'
     * | 'time'
     * | 'datetime'
     * | 'select'
     * | 'custom';
     *
     * @param schema
     * @return
     */
    static String getPropType(ISchema schema) {
        if (schema.getDict() != null)
            return "select";

        StdDataType dataType = schema.getStdDataType();
        if (dataType == null) {
            String stdDomain = schema.getStdDomain();
            if (stdDomain != null) {
                IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
                dataType = handler.getGenericType(false, null).getStdDataType();
            }
        }

        if (dataType == null)
            return "text";

        if (dataType == StdDataType.STRING)
            return "text";
        if (dataType == StdDataType.BOOLEAN)
            return "boolean";
        if (dataType == StdDataType.DATE)
            return "date";
        if (dataType == StdDataType.TIME)
            return "time";
        if (dataType == StdDataType.DATETIME || dataType == StdDataType.TIMESTAMP)
            return "datetime";

        if (dataType.isNumericType())
            return "number";

        return "text";
    }
}
