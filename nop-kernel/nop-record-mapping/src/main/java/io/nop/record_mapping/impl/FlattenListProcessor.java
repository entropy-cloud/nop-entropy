package io.nop.record_mapping.impl;

import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class FlattenListProcessor {
    static FlattenListProcessor _INSTANCE = new FlattenListProcessor();

    public static FlattenListProcessor instance() {
        return _INSTANCE;
    }

    public List<Map<String, Object>> parseFromFlattenObj(Object obj, String fromName) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
        Map<Integer, Map<String, Object>> indexMap = new TreeMap<>();
        Set<String> extPropNames = beanModel.getExtPropertyNames(obj);
        if (extPropNames != null) {
            String prefix = fromName + '-';
            for (String extPropName : extPropNames) {
                if (extPropName.startsWith(prefix)) {

                }
            }
        }
        return buildListFromIndexMap(indexMap);
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
