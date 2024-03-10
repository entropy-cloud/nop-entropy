/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.diff;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.utils.Underscore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class CsvDataDiffer {
    public static CsvDataDiffer INSTANCE = new CsvDataDiffer();

    /**
     * 返回 a- b 从a中删除所有与b中完全一致的条目
     */
    public Map<String, Object> diffMap(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> ret = new HashMap<>();
        for (Map.Entry<String, Object> entry : a.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (!Objects.equals(value, b.get(name))) {
                ret.put(name, value);
            }
        }
        return ret;
    }

    public Map<String, Object> mergeMap(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> ret = new HashMap<>(a);
        for (Map.Entry<String, Object> entry : b.entrySet()) {
            if ("*".equals(entry.getValue()))
                continue;
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    public List<Map<String, Object>> diffList(List<String> idCols, List<Map<String, Object>> listA,
                                              List<Map<String, Object>> listB) {
        List<Map<String, Object>> ret = new ArrayList<>(listA.size());
        Map<String, Map<String, Object>> mapA = indexById(idCols, listA);
        Map<String, Map<String, Object>> mapB = indexById(idCols, listB);
        for (Map.Entry<String, Map<String, Object>> entry : mapA.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> valueA = entry.getValue();

            Map<String, Object> valueB = mapB.get(key);
            if (valueB == null) {
                ret.add(valueA);
            } else {
                Map<String, Object> map = diffMap(valueA, valueB);
                if (!map.isEmpty()) {
                    addIds(map, idCols, valueA);
                    ret.add(map);
                }
            }
        }
        return ret;
    }

    // tell cpd to start ignoring code - CPD-OFF
    public List<Map<String, Object>> mergeList(List<String> idCols, List<Map<String, Object>> listA,
                                               List<Map<String, Object>> listB) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        Map<String, Map<String, Object>> mapA = indexById(idCols, listA);
        Map<String, Map<String, Object>> mapB = indexById(idCols, listB);

        for (Map.Entry<String, Map<String, Object>> entry : mapA.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> valueA = entry.getValue();

            Map<String, Object> valueB = mapB.get(key);
            if (valueB == null) {
                merged.put(key, new HashMap<>(valueA));
            } else {
                Map<String, Object> value = mergeMap(valueA, valueB);
                merged.put(key, value);
            }
        }

        for (Map.Entry<String, Map<String, Object>> entry : mapB.entrySet()) {
            String key = entry.getKey();
            if (!merged.containsKey(key)) {
                merged.put(key, new HashMap<>(entry.getValue()));
            }
        }
        return new ArrayList<>(merged.values());
    }
    // resume CPD analysis - CPD-ON

    void addIds(Map<String, Object> ret, List<String> ids, Map<String, Object> map) {
        for (String id : ids) {
            ret.put(id, map.get(id));
        }
    }

    Map<String, Map<String, Object>> indexById(List<String> idCols, List<Map<String, Object>> a) {
        Function<Map<String, Object>, String> fn = (Map<String, Object> v) -> getId(idCols, v);
        return Underscore.indexBy(a, fn);
    }

    String getId(List<String> idCols, Map<String, Object> a) {
        if (idCols.size() == 1) {
            return ConvertHelper.toString(a.get(idCols.get(0)), "");
        }

        List<Object> values = new ArrayList<>(idCols.size());
        for (String col : idCols) {
            values.add(a.get(col));
        }
        return StringHelper.join(values, "~");
    }
}