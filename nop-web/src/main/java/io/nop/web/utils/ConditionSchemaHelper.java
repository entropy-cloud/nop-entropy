package io.nop.web.utils;

import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConditionSchemaHelper {
    public static List<Map<String, Object>> schemaToFields(ISchema schema) {

        List<? extends IObjPropMeta> props = schema.getProps();
        if (props == null || props.isEmpty())
            return null;

        List<Map<String, Object>> ret = new ArrayList<>();
        for (IObjPropMeta prop : props) {
            ret.add(propToField(prop));
        }
        return ret;
    }

    static Map<String, Object> propToField(IObjPropMeta propMeta) {
        Map<String, Object> ret = new LinkedHashMap<>();
        
        ISchema schema = propMeta.getSchema();
        if (schema != null) {
            if (schema.hasProps()) {

            }
        }

        return ret;
    }
}
