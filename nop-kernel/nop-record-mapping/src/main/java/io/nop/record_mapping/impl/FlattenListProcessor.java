package io.nop.record_mapping.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import static io.nop.record_mapping.RecordMappingErrors.ARG_FIELD_NAME;
import static io.nop.record_mapping.RecordMappingErrors.ERR_RECORD_INVALID_FLATTEN_LIST_FIELD_NAME;

public class FlattenListProcessor {
    static FlattenListProcessor _INSTANCE = new FlattenListProcessor();

    public static FlattenListProcessor instance() {
        return _INSTANCE;
    }

    public List<Map<String, Object>> parseFromFlattenObj(Object obj, String fromName, boolean disableToPropPath) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
        Map<Integer, Map<String, Object>> indexMap = new TreeMap<>();
        Set<String> extPropNames = beanModel.getExtPropertyNames(obj);
        if (extPropNames != null) {
            String prefix = fromName + '-';
            for (String extPropName : extPropNames) {
                if (extPropName.startsWith(prefix)) {
                    Object value = beanModel.getExtProperty(obj, extPropName);
                    parseField(indexMap, extPropName, prefix, value, disableToPropPath);
                }
            }
        }
        return buildListFromIndexMap(indexMap);
    }

    public void generateFlattenObj(Object obj, Collection<?> coll, String fromName, boolean disableToPropPath,
                                   Function<Object, Map<String, Object>> itemValuesGetter) {
        if (coll == null || coll.isEmpty())
            return;

        int index = 1;
        for (Object item : coll) {
            Map<String, Object> itemValues = itemValuesGetter.apply(item);
            if (itemValues != null) {
                for (Map.Entry<String, Object> entry : itemValues.entrySet()) {
                    String key = fromName + '-' + index + '-' + entry.getKey();
                    Object value = entry.getValue();
                    if (disableToPropPath) {
                        BeanTool.setProperty(obj, key, value);
                    } else {
                        BeanTool.setComplexProperty(obj, key, value);
                    }
                }
            }
            index++;
        }
    }

    protected void parseField(Map<Integer, Map<String, Object>> indexMap, String extPropName,
                              String prefix, Object value, boolean disableToPropPath) {
        int pos = extPropName.indexOf('-', prefix.length());
        if (pos < 0 || pos == extPropName.length() - 1)
            throw new NopException(ERR_RECORD_INVALID_FLATTEN_LIST_FIELD_NAME)
                    .param(ARG_FIELD_NAME, extPropName);
        String index = extPropName.substring(prefix.length(), pos);
        if (!StringHelper.isInt(index))
            throw new NopException(ERR_RECORD_INVALID_FLATTEN_LIST_FIELD_NAME)
                    .param(ARG_FIELD_NAME, extPropName);

        int indexValue = StringHelper.parseInt(index, 10);
        String subField = extPropName.substring(pos + 1);
        Map<String, Object> map = indexMap.computeIfAbsent(indexValue, k -> new LinkedHashMap<>());
        if (disableToPropPath) {
            map.put(subField, value);
        } else {
            BeanTool.setComplexProperty(map, subField, value);
        }
    }

    protected List<Map<String, Object>> buildListFromIndexMap(Map<Integer, Map<String, Object>> indexMap) {
        List<Map<String, Object>> ret = new ArrayList<>(indexMap.size());
        for (Map<String, Object> item : indexMap.values()) {
            if (!item.isEmpty()) {
                ret.add(item);
            }
        }
        return ret;
    }
}