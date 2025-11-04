/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.utils;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.ITriFunction;
import io.nop.commons.util.CollectionHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class JsonTransformHelper {

    public static Object transform(Object value, Function<Object, Object> transformer) {
        return transform(value, transformer, null);
    }

    public static Object transform(Object value, Function<Object, Object> transformer, Predicate<Object> predicate) {
        if (value instanceof Collection<?>) {
            return transformList((Collection<?>) value, transformer, predicate);
        } else if (value instanceof Map) {
            if (predicate != null && predicate.test(value)) {
                return transformer.apply(value);
            }
            return transformMap((Map<String, ?>) value, transformer, predicate);
        } else {
            if (predicate == null || predicate.test(value))
                return transformer.apply(value);
            return value;
        }
    }

    public static List<Object> transformList(Collection<?> list, Function<Object, Object> transformer,
                                             Predicate<Object> predicate) {
        if (list == null)
            return null;

        List<Object> ret = new ArrayList<>(list.size());
        for (Object value : list) {
            ret.add(transform(value, transformer, predicate));
        }
        return ret;
    }

    public static Object transformMap(Map<String, ?> map, Function<Object, Object> transformer,
                                      Predicate<Object> predicate) {
        if (map == null)
            return null;

        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(map.size());
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            Object value = transform(entry.getValue(), transformer, predicate);
            ret.put(entry.getKey(), value);
        }
        return ret;
    }

    public static Object transformMapEntryInPlace(Object value, BiPredicate<String, Object> filter,
                                                  ITriFunction<SourceLocation, String, Object, Object> fn) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                String name = entry.getKey();
                Object v = entry.getValue();

                if (filter.test(name, v)) {
                    SourceLocation loc = SourceLocationHelper.getLocation(map, name);
                    Object v2 = fn.apply(loc, name, v);
                    entry.setValue(v2);
                } else {
                    Object v2 = transformMapEntryInPlace(v, filter, fn);
                    if (v2 != v)
                        entry.setValue(v2);
                }
            }
            return map;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            for (int i = 0, n = list.size(); i < n; i++) {
                Object v = list.get(i);
                Object v2 = transformMapEntryInPlace(v, filter, fn);
                if (v2 != v) {
                    list.set(i, v2);
                }
            }
            return list;
        } else {
            return value;
        }
    }

    public static Object transformInPlace(Object value, Function<Object, Object> fn) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            fn.apply(map);
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                Object v = entry.getValue();
                Object v2 = transformInPlace(v, fn);
                if (v != v2) {
                    entry.setValue(v2);
                }
            }
            return map;
        } else if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            for (int i = 0, n = list.size(); i < n; i++) {
                Object v = list.get(i);
                Object v2 = transformInPlace(v, fn);
                if (v2 != v) {
                    list.set(i, v2);
                }
            }
            return list;
        } else {
            return fn.apply(value);
        }
    }

    public static Object trim(Object value, boolean trimKey, boolean trimValue) {
        if (value instanceof Map) {
            Map<String, Object> ret = new LinkedHashMap<>();
            ((Map<String, Object>) value).forEach((key, v) -> {
                if (trimKey)
                    key = key.trim();
                Object v2 = trim(v, trimKey, trimValue);
                ret.put(key, v2);
            });
            return ret;
        } else if (value instanceof Collection) {
            List<Object> ret = new ArrayList<>();
            ((Collection<Object>) value).forEach(v -> {
                Object v2 = trim(v, trimKey, trimValue);
                ret.add(v2);
            });
            return ret;
        } else {
            if (trimValue)
                value = trimValue(value);
            return value;
        }
    }

    static Object trimValue(Object value) {
        if (value instanceof String)
            return value.toString().trim();
        return value;
    }
}
