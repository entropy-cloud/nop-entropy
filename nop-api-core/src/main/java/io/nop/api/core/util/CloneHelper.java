/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class CloneHelper {
    public static final Map<String, Object> EMPTY_MAP = Collections.emptyMap();
    public static final SortedMap<String, Object> EMPTY_SORTED_MAP = Collections.emptySortedMap();
    public static final Set<Object> EMPTY_SET = Collections.emptySet();
    public static final SortedSet<Object> EMPTY_SORTED_SET = Collections.emptySortedSet();
    public static final List<Object> EMPTY_LIST = Collections.emptyList();

    /**
     * 只处理IDeepCloneable/Collection/Map三种接口，其他类型都保持原样返回
     *
     * @param o
     * @return
     */
    public static <T> T deepClone(Object o) {
        if (o == null)
            return null;

        if (o == EMPTY_MAP || o == EMPTY_LIST)
            return (T) o;

        if (o instanceof IDeepCloneable)
            return (T) ((IDeepCloneable) o).deepClone();

        if (o instanceof Collection) {
            Collection<Object> c = (Collection<Object>) o;
            Collection<Object> ret = newCollection(c);
            for (Object elm : c) {
                ret.add(deepClone(elm));
            }
            return (T) ret;
        } else if (o instanceof Map) {
            Map<Object, Object> src = (Map<Object, Object>) o;
            return (T) deepCloneMap(src);
        } else if (o instanceof ICloneable) {
            return (T) ((ICloneable) o).cloneInstance();
        } else {
            return (T) o;
        }
    }

    public static <K, V> Map<K, V> deepCloneMap(Map<K, V> map) {
        if (map == null)
            return null;
        Map<K, V> ret = newMap(map);
        for (Map.Entry<K, V> entry : map.entrySet()) {
            V value = entry.getValue();
            ret.put(entry.getKey(), (V) deepClone(value));
        }
        return ret;
    }

    public static <K, V> void deepCloneMapTo(Map<K, V> map, Map<K, V> ret) {
        if (map == null)
            return;
        map.forEach((k, v) -> {
            ret.put(k, deepClone(v));
        });
    }

    public static <T> List<T> deepCloneList(List<T> list) {
        if (list == null)
            return null;

        List<T> ret = new ArrayList<>(list.size());
        for (T item : list) {
            ret.add(deepClone(item));
        }
        return ret;
    }

    /**
     * 合并m1和m2。如果可能，直接修改m1的内容
     *
     * @param m1 目标Map, 如果为null或者为EMPTY_MAP，则会新建集合，否则直接修改此集合
     * @param m2 待合并的map
     * @return m1或者新建的Map
     */
    public static Map<String, Object> deepMerge(Map<String, Object> m1, Map<String, Object> m2) {
        if (m1 == null || m1 == EMPTY_MAP || m1 == EMPTY_SORTED_MAP)
            return (Map<String, Object>) deepClone(m2);
        if (m2 == null || m2 == EMPTY_MAP || m2 == EMPTY_SORTED_MAP)
            return m1;
        for (Map.Entry<String, Object> entry : m2.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Object oldValue = m1.get(key);
            if (oldValue == null) {
                m1.put(key,value);
                continue;
            }
            if (oldValue instanceof IMergeable) {
                Object merged = ((IMergeable) oldValue).merge(value);
                if (merged != oldValue) {
                    m1.put(key, merged);
                }
                continue;
            }

            if (oldValue instanceof Map && value instanceof Map) {
                Map<String, Object> map = deepMerge((Map<String, Object>) oldValue, (Map<String, Object>) value);
                if (map != oldValue)
                    m1.put(key, map);
            } else {
                m1.put(key, value);
            }
        }
        return m1;
    }

    /**
     * 不修改m1或者m2, 合并内容存放到ret中
     *
     * @param ret 存放合并结果
     * @param m1
     * @param m2
     */
    public static void deepMerge(Map<String, Object> ret, Map<String, Object> m1, Map<String, Object> m2) {
        Guard.checkArgument(ret != null && ret != EMPTY_MAP, "invalid merge result");
        deepMerge(ret, m1);
        deepMerge(ret, m2);
    }

    static Collection<Object> newCollection(Collection<Object> src) {
        if (src instanceof List)
            return new ArrayList<>(src.size());

        if (src.getClass() == HashSet.class)
            return new HashSet<>(src.size());

        if (src instanceof SortedSet)
            return new TreeSet<>(((SortedSet<Object>) src).comparator());
        return new LinkedHashSet<>(src.size());
    }

    static Map newMap(Map src) {
        if (src.getClass() == HashMap.class)
            return new HashMap<>(calcInitSize(src.size()));
        if (src instanceof SortedMap)
            return new TreeMap<>(((SortedMap<Object, Object>) src).comparator());
        return new LinkedHashMap<>(calcInitSize(src.size()));
    }

    /**
     * Map缺省的loadFactor
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static int calcInitSize(int expectedSize) {
        return (int) (expectedSize / DEFAULT_LOAD_FACTOR) + 1;
    }

}