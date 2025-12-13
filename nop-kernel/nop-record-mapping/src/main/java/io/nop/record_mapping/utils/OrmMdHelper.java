package io.nop.record_mapping.utils;

import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrmMdHelper {
    public static List<Map<String, Object>> parseJoinText(String joinText) {
        if (StringHelper.isBlank(joinText))
            return null;

        Map<String, String> map = StringHelper.parseStringMap(joinText, '=', ',');
        List<Map<String, Object>> ret = new ArrayList<>(map.size());
        map.forEach((key, value) -> {
            Map<String, Object> on = new LinkedHashMap<>();
            if (StringHelper.isValidPropName(key)) {
                if (StringHelper.isValidPropName(value)) {
                    on.put("leftProp", key);
                    on.put("rightProp", value);
                } else {
                    on.put("leftProp", key);
                    on.put("rightValue", value);
                }
            } else {
                if (StringHelper.isValidPropName(value)) {
                    on.put("leftValue", key);
                    on.put("rightProp", value);
                } else {
                    on.put("leftValue", key);
                    on.put("rightValue", value);
                }
            }
            ret.add(on);
        });
        return ret;
    }

    public static String genJoinText(List<Object> join) {
        if (join == null || join.isEmpty())
            return null;

        StringBuilder sb = new StringBuilder();
        for (Object on : join) {
            if (sb.length() > 0)
                sb.append(',');
            String leftProp = (String) BeanTool.getProperty(on, "leftProp");
            String rightProp = (String) BeanTool.getProperty(on, "rightProp");
            Object leftValue = BeanTool.getProperty(on, "leftValue");
            Object rightValue = BeanTool.getProperty(on, "rightValue");

            if (leftProp != null) {
                sb.append(leftProp);
                if (rightProp != null) {
                    if (!leftProp.equals(rightProp)) {
                        sb.append('=').append(rightProp);
                    }
                } else {
                    sb.append('=').append(encodeValue(rightValue));
                }
            } else {
                sb.append(encodeValue(leftValue));
                if (rightProp != null) {
                    sb.append('=').append(rightProp);
                } else {
                    sb.append('=').append(encodeValue(rightValue));
                }
            }
        }
        return sb.toString();
    }

    static String encodeValue(Object value) {
        if (value == null)
            return "";
        if (value instanceof String)
            return '\'' + value.toString() + '\'';
        return value.toString();
    }
}
