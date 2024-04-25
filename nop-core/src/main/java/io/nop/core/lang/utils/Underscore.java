/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.utils;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.lang.Deterministic;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.collections.IKeyedList;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.collections.SafeOrderedComparator;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.handler.BuildJObjectJsonHandler;
import io.nop.core.reflect.bean.BeanTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 仿照js的underscore库所提供的一些帮助函数
 */
@Description("仿照js的underscore库所提供的一些针对集合对象的帮助函数")
public class Underscore {

    /**
     * isEmpty() 如果 object 不包含任何值(没有可枚举的属性)，返回 true。 对于字符串和类数组（array-like）对象， 如果 length 属性为 0，那么_.isEmpty 检查返回 true。
     */
    @Deterministic
    public static boolean isEmpty(Object v) {
        if (v == null)
            return true;

        if (v instanceof String)
            return v.toString().isEmpty();

        if (v instanceof Collection)
            return ((Collection<?>) v).isEmpty();

        if (v instanceof Map)
            return ((Map<?, ?>) v).isEmpty();

        return false;
    }

    @Deterministic
    public static <T> T first(List<T> c) {
        if (c.isEmpty())
            return null;
        return c.get(0);
    }

    @Deterministic
    public static <T> List<T> first(List<T> c, int n) {
        if (c.isEmpty())
            return Collections.emptyList();
        if (c.size() <= n)
            return new ArrayList<>(c);
        List<T> ret = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ret.add(c.get(i));
        }
        return ret;
    }

    @Deterministic
    public static <T> T last(List<T> c) {
        if (c.isEmpty())
            return null;
        return c.get(c.size() - 1);
    }

    @Deterministic
    public static <T> List<T> last(List<T> c, int n) {
        if (c.isEmpty())
            return Collections.emptyList();
        if (c.size() <= n)
            return new ArrayList<>(c);
        List<T> ret = new ArrayList<>(n);
        for (int i = c.size() - n, m = c.size(); i < m; i++) {
            ret.add(c.get(i));
        }
        return ret;
    }

    /**
     * Looks through the list and returns the first value that matches all of the key-value pairs listed in properties.
     * <p>
     * If no match is found, or if list is empty, null will be returned.
     * <p>
     * _.findWhere(publicServicePulitzers, {newsroom: "The New York Times"}); => {year: 1918, newsroom: "The New York
     * Times", reason: "For its public service in publishing in full so many official reports, documents and speeches by
     * European statesmen relating to the progress and conduct of the war."}
     */
    @Deterministic
    public static <T> T findWhere(Collection<T> c, Map<String, Object> props) {
        for (T item : c) {
            if (isMatch(item, props))
                return item;
        }
        return null;
    }

    @Deterministic
    public static <T> T findWhere(Collection<T> c, String key, Object value) {
        for (T item : c) {
            if (isMatch(item, key, value))
                return item;
        }
        return null;
    }

    @Deterministic
    public static <T> int findIndex(Collection<T> c, Predicate<T> filter) {
        int index = 0;
        for (T item : c) {
            if (filter.test(item)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /**
     * Looks through each value in the list, returning an array of all the values that matches the key-value pairs
     * listed in properties.
     * <p>
     * _.where(listOfPlays, {author: "Shakespeare", year: 1611}); => [{title: "Cymbeline", author: "Shakespeare", year:
     * 1611}, {title: "The Tempest", author: "Shakespeare", year: 1611}]
     */
    @Deterministic
    public static <T> List<T> where(Collection<T> c, Map<String, Object> props) {
        List<T> ret = new ArrayList<>();
        for (T item : c) {
            if (isMatch(item, props))
                ret.add(item);
        }
        return ret;
    }

    @Deterministic
    public static <T> List<T> where(Collection<T> c, String key, Object value) {
        List<T> ret = new ArrayList<>();
        for (T item : c) {
            if (isMatch(item, key, value))
                ret.add(item);
        }
        return ret;
    }

    private static <T> boolean isMatch(T item, Map<String, Object> props) {
        if (item == null)
            return false;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String name = entry.getKey();
            if (!Objects.equals(entry.getValue(), getFieldValue(item, name)))
                return false;
        }
        return true;
    }

    private static <T> boolean isMatch(T item, String key, Object value) {
        if (item == null)
            return false;

        if (!Objects.equals(value, getFieldValue(item, key)))
            return false;
        return true;
    }

    @Deterministic
    public static <T> List<T> unique(Collection<T> c) {
        if (c instanceof Set)
            return new ArrayList<>(c);

        Set<T> set = new LinkedHashSet<>(c);
        return new ArrayList<>(set);
    }

    /**
     * 获取列表元素的指定属性值，例如
     *
     * <pre>{@code
     *    list = [{a:1,b:2},{a:3,b:4}];
     *    pluck(list,"a") 返回[1,3]
     * }</pre>
     * <p>
     * [{a,1}
     *
     * @param c        对象集合
     * @param propName 属性名称
     * @return 属性值构成的集合
     */
    @Deterministic
    public static List<Object> pluck(Collection<?> c, String propName) {
        if (c == null)
            return null;

        List<Object> ret = new ArrayList<>(c.size());
        for (Object o : c) {
            ret.add(getFieldValue(o, propName));
        }
        return ret;
    }

    @Deterministic
    public static String pluckThenJoin(Collection<?> c, String propName) {
        if (c == null)
            return null;

        StringBuilder sb = new StringBuilder();
        for (Object o : c) {
            if (sb.length() > 0)
                sb.append(',');
            sb.append((Object) getFieldValue(o, propName));
        }
        return sb.toString();
    }

    @Deterministic
    public static <T> List<List<T>> chunk(Collection<T> c, int chunkSize) {
        return CollectionHelper.splitChunk(c, chunkSize);
    }

    /**
     * Split list into two arrays: one whose elements all satisfy predicate and one whose elements all do not satisfy
     * predicate. predicate is transformed through iteratee to facilitate shorthand syntaxes.
     * <p>
     * _.partition([0, 1, 2, 3, 4, 5], isOdd); => [[1, 3, 5], [0, 2, 4]]
     */
    @Deterministic
    public static <T> List<List<T>> partition(Collection<T> c, Predicate<T> predicate) {
        List<T> trueList = new ArrayList<>();
        List<T> falseList = new ArrayList<>();
        for (T item : c) {
            if (predicate.test(item)) {
                trueList.add(item);
            } else {
                falseList.add(item);
            }
        }
        return Arrays.asList(trueList, falseList);
    }

    /**
     * Returns a copy of the list with all falsy values removed. In JavaScript, false, null, 0, "", undefined and NaN
     * are all falsy.
     * <p>
     * _.compact([0, 1, false, 2, '', 3]); => [1, 2, 3]
     */
    @Deterministic
    public static <T> List<T> compact(Collection<T> c) {
        List<T> ret = new ArrayList<>(c.size());
        for (T item : c) {
            if (!ConvertHelper.toFalsy(item)) {
                ret.add(item);
            }
        }
        return ret;
    }

    @Deterministic
    public static <T> Number sum(Collection<T> c, Function<T, ?> fn) {
        Number ret = 0;
        for (T item : c) {
            Object v = fn == null ? item : fn.apply(item);
            ret = MathHelper.add(ret, v);
        }
        return ret;
    }

    @Deterministic
    public static <T> Number sum(Collection<T> c) {
        return sum(c, null);
    }

    @Deterministic
    public static <T> Number avg(Collection<T> c, Function<T, ?> fn) {
        Number value = sum(c, fn);
        if (value == null)
            return null;
        return MathHelper.divide(value, c.size());
    }

    @Deterministic
    public static <T> Number avg(Collection<T> c) {
        return avg(c, null);
    }

    @Deterministic
    public static <T> T max(Collection<T> c, Function<T, ?> fn) {
        Object max = null;
        T ret = null;
        Comparator<Object> comparator = SafeOrderedComparator.DEFAULT;
        for (T item : c) {
            Object v = fn == null ? item : fn.apply(item);
            if (comparator.compare(max, v) < 0) {
                max = v;
                ret = item;
            }
        }
        return ret;
    }

    @Deterministic
    public static <T> T max(Collection<T> c) {
        return max(c, null);
    }

    @Deterministic
    public static <T> T min(Collection<T> c, Function<T, ?> fn) {
        Object min = null;
        T ret = null;
        Comparator<Object> comparator = SafeOrderedComparator.DEFAULT;
        for (T item : c) {
            Object v = fn == null ? item : fn.apply(item);
            if (min == null || comparator.compare(min, v) > 0) {
                min = v;
                ret = item;
            }
        }
        return ret;
    }

    @Deterministic
    public static <T> T min(Collection<T> c) {
        return min(c, null);
    }

    /**
     * Returns a (stably) sorted copy of list, ranked in ascending order by the results of running each value through
     * iteratee. iteratee may also be the string name of the property to sort by (eg. length). This function uses
     * operator < (note).
     * <p>
     * _.sortBy([1, 2, 3, 4, 5, 6], function(num){ return Math.sin(num); }); => [5, 4, 6, 3, 1, 2]
     * <p>
     * var stooges = [{name: 'moe', age: 40}, {name: 'larry', age: 50}, {name: 'curly', age: 60}]; _.sortBy(stooges,
     * 'name'); => [{name: 'curly', age: 60}, {name: 'larry', age: 50}, {name: 'moe', age: 40}];
     */
    @Deterministic
    public static <T> List<T> sortBy(Collection<T> c, Object keyOrFn) {
        List<T> ret = new ArrayList<>(c);
        Function<T, Object> fn = keyOrFn instanceof Function ? (Function<T, Object>) keyOrFn
                : t -> getFieldValue(t, (String) keyOrFn);

        Comparator<T> comparator = (v1, v2) -> SafeOrderedComparator.DEFAULT.compare(fn.apply(v1), fn.apply(v2));
        ret.sort(comparator);
        return ret;
    }

    /**
     * Splits a collection into sets, grouped by the result of running each value through iteratee. If iteratee is a
     * string instead of a function, groups by the property named by iteratee on each of the values.
     * <p>
     * _.groupBy([1.3, 2.1, 2.4], function(num){ return Math.floor(num); }); => {1: [1.3], 2: [2.1, 2.4]}
     * <p>
     * _.groupBy(['one', 'two', 'three'], 'length'); => {3: ["one", "two"], 5: ["three"]}
     */
    @Deterministic
    public static <K, T> Map<K, List<T>> groupBy(Collection<T> c, Object keyOrFn) {
        Map<K, List<T>> ret = new LinkedHashMap<>();
        if (keyOrFn instanceof Function) {
            Function<T, K> fn = (Function<T, K>) keyOrFn;
            for (T item : c) {
                K key = fn.apply(item);
                List<T> list = ret.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    ret.put(key, list);
                }
                list.add(item);
            }
        } else {
            for (T item : c) {
                K key = getFieldValue(item, (String) keyOrFn);
                List<T> list = ret.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    ret.put(key, list);
                }
                list.add(item);
            }
        }
        return ret;
    }

    /**
     * Given a list, and an iteratee function that returns a key for each element in the list (or a property name),
     * returns an object with an index of each item. Just like groupBy, but for when you know your keys are unique.
     * <p>
     * var stooges = [{name: 'moe', age: 40}, {name: 'larry', age: 50}, {name: 'curly', age: 60}]; _.indexBy(stooges,
     * 'age'); => { "40": {name: 'moe', age: 40}, "50": {name: 'larry', age: 50}, "60": {name: 'curly', age: 60} }
     */
    @Deterministic
    public static <K, T> Map<K, T> indexBy(Collection<T> c, Object keyOrFn) {

        Map<K, T> ret = CollectionHelper.newLinkedHashMap(c.size());
        if (keyOrFn instanceof Function) {
            Function<T, K> fn = (Function<T, K>) keyOrFn;
            for (T item : c) {
                K key = fn.apply(item);
                ret.put(key, item);
            }
        } else {
            for (T item : c) {
                K key = getFieldValue(item, (String) keyOrFn);
                ret.put(key, item);
            }
        }
        return ret;
    }

    @Deterministic
    public static <T> Map<Object, T> indexByFields(Collection<T> c, String... fieldNames) {
        Guard.checkArgument(fieldNames.length >= 1, "fieldNames must not be empty");
        if (fieldNames.length == 1)
            return indexBy(c, fieldNames[0]);

        Function<Object, List<Object>> fn = (Object obj) -> {
            List<Object> ret = new ArrayList<>(fieldNames.length);
            for (String fieldName : fieldNames) {
                ret.addAll(getFieldValue(obj, fieldName));
            }
            return ret;
        };

        return indexBy(c, fn);
    }

    @Deterministic
    public static Map<String, Object> pluckAsMap(Collection<?> c, String keyProp, String valueProp) {
        Map<String, Object> map = CollectionHelper.newLinkedHashMap(c.size());
        for (Object item : c) {
            Object key = getFieldValue(item, keyProp);
            Object value = getFieldValue(item, valueProp);
            map.put(StringHelper.toString(key, null), value);
        }
        return map;
    }

    /**
     * Sorts a list into groups and returns a count for the number of objects in each group. Similar to groupBy, but
     * instead of returning a list of values, returns a count for the number of values in that group.
     * <p>
     * _.countBy([1, 2, 3, 4, 5], function(num) { return num % 2 == 0 ? 'even': 'odd'; }); => {odd: 3, even: 2}
     */
    @Deterministic
    public static <K, T> Map<K, Integer> countBy(Collection<T> c, Object keyOrFn) {
        Map<K, Integer> ret = new LinkedHashMap<>();
        if (keyOrFn instanceof Function) {
            Function<T, K> iteratee = (Function<T, K>) keyOrFn;
            for (T item : c) {
                K key = iteratee.apply(item);
                Integer count = ret.get(key);
                if (count == null)
                    count = 0;
                ret.put(key, count + 1);
            }
        } else {
            for (T item : c) {
                K key = getFieldValue(item, (String) keyOrFn);
                Integer count = ret.get(key);
                if (count == null)
                    count = 0;
                ret.put(key, count + 1);
            }
        }
        return ret;
    }

    public static <V> V getFieldValue(Object obj, String key) {
        return (V) BeanTool.getComplexProperty(obj, key);
    }

    /**
     * Convert an object into a list of [key, value] pairs. The opposite of object.
     * <p>
     * _.pairs({one: 1, two: 2, three: 3}); => [["one", 1], ["two", 2], ["three", 3]]
     */
    @Deterministic
    public static <T> List<Object> pairs(Map<?, ?> map) {
        List<Object> ret = new ArrayList<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            ret.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        return ret;
    }

    /**
     * Returns a copy of the object where the keys have become the values and the values the keys. For this to work, all
     * of your object's values should be unique and string serializable.
     * <p>
     * _.invert({Moe: "Moses", Larry: "Louis", Curly: "Jerome"}); => {Moses: "Moe", Louis: "Larry", Jerome: "Curly"};
     */
    @Deterministic
    public static <K, V> Map<V, K> invert(Map<K, V> map) {
        Map<V, K> ret = CollectionHelper.newLinkedHashMap(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            ret.put(entry.getValue(), entry.getKey());
        }
        return ret;
    }

    @Deterministic
    public static String join(Collection<?> c, String sep) {
        return StringHelper.join(c, sep);
    }

    @Deterministic
    public static Object toJObject(Object o) {
        BuildJObjectJsonHandler handler = new BuildJObjectJsonHandler();
        JsonTool.instance().serializeTo(o, handler);
        return handler.getResult();
    }

    @Description("除去Map或者List中的null值")
    @Deterministic
    public static Object filterNull(@Name("mapOrList") Object mapOrList) {
        if (mapOrList instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) mapOrList;
            if (map.isEmpty())
                return map;

            Map<String, Object> ret = CollectionHelper.newLinkedHashMap(map.size());
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    ret.put(entry.getKey(), entry.getValue());
                }
            }
            return ret;
        } else if (mapOrList instanceof Collection) {
            Collection<Object> list = (Collection<Object>) mapOrList;
            if (list.isEmpty())
                return list;

            List<Object> ret = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    ret.add(item);
                }
            }
            return ret;
        }
        return mapOrList;
    }

    @Deterministic
    public static Map<String, Object> omit(Map<String, Object> map, Collection<String> names) {
        Guard.notNull(map, "map");
        Guard.notNull(names, "names");

        if (map.isEmpty() || names.isEmpty())
            return map;

        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (names.contains(entry.getKey()))
                continue;
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }

    @Deterministic
    public static Map<String, Object> delete(Map<String, Object> map, Collection<String> names) {
        Guard.notNull(map, "map");
        Guard.notNull(names, "names");

        if (map.isEmpty() || names.isEmpty())
            return map;
        map.keySet().removeAll(names);
        return map;
    }

    @Deterministic
    public static Map<String, Object> pick(@Name("mapOrObj") Object mapOrObj, @Name("names") Collection<String> names) {
        Guard.notNull(mapOrObj, "map");
        Guard.notNull(names, "names");

        if (names.isEmpty())
            return new LinkedHashMap<>(0);

        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(names.size());
        if (mapOrObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) mapOrObj;
            for (String name : names) {
                Object value = map.get(name);
                if (value != null || map.containsKey(name))
                    ret.put(name, value);
            }
        } else {
            for (String name : names) {
                Object value = BeanTool.instance().getProperty(mapOrObj, name);
                ret.put(name, value);
            }
        }
        return ret;
    }

    @Deterministic
    public static Map<String, Object> pickNotNull(@Name("mapOrObj") Object mapOrObj,
                                                  @Name("names") Collection<String> names) {
        if (mapOrObj == null)
            return null;

        Guard.notNull(names, "names");

        if (names.isEmpty())
            return new LinkedHashMap<>(0);

        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(names.size());
        if (mapOrObj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) mapOrObj;
            for (String name : names) {
                Object value = map.get(name);
                if (value != null)
                    ret.put(name, value);
            }
        } else {
            for (String name : names) {
                Object value = BeanTool.instance().getProperty(mapOrObj, name);
                if (value != null)
                    ret.put(name, value);
            }
        }
        return ret;
    }

    @Deterministic
    public static Map<String, Object> rename(Map<String, Object> map, Map<String, String> mapping) {
        if (map == null || mapping == null)
            return map;

        if (map.isEmpty() || mapping.isEmpty())
            return map;

        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String rename = mapping.get(entry.getKey());
            if (rename != null) {
                ret.put(rename, entry.getValue());
            } else {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    @Deterministic
    public static <T> List<T> removeWhere(Collection<T> c, String key, Object value) {
        List<T> ret = where(c, key, value);
        c.removeAll(ret);
        return ret;
    }

    @Deterministic
    public static <T> List<T> removeAllWhere(Collection<T> c, String key, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        Iterator<T> it = c.iterator();
        List<T> ret = new ArrayList<>();
        while (it.hasNext()) {
            T item = it.next();
            Object value = getFieldValue(item, key);
            if (values.contains(value)) {
                it.remove();
                ret.add(item);
            }
        }
        return ret;
    }

    @Deterministic
    public static <T> List<T> retainAllWhere(Collection<T> c, String key, Collection<?> values) {
        if (values == null)
            values = Collections.emptyList();
        if (values.isEmpty()) {
            List<T> ret = new ArrayList<>(c);
            c.clear();
            return ret;
        }

        Iterator<T> it = c.iterator();
        List<T> ret = new ArrayList<>();
        while (it.hasNext()) {
            T item = it.next();
            Object value = getFieldValue(item, key);
            if (!values.contains(value)) {
                it.remove();
                ret.add(item);
            }
        }
        return ret;
    }

    @Deterministic
    public static Map<String, Object> mergeMap(Map<String, Object> mapA, Map<String, Object> mapB) {
        if (mapB == null || mapB == mapA) {
            return mapA;
        }

        if (mapA == null)
            mapA = new LinkedHashMap<>();
        mapA.putAll(mapB);
        return mapA;
    }

    @Deterministic
    public static <T> IKeyedList<T> toKeyedList(Collection<T> c, String keyProp) {
        return KeyedList.fromList(CollectionHelper.toList(c), item -> getFieldValue(item, keyProp));
    }

    /**
     * 在内存中执行Hash join，将两个集合按照指定的属性进行关联，将右侧关联数据设置到左侧集合中
     *
     * @param refProp 如果传入Map，则认为是属性映射关系，key为左侧属性，value为右侧属性。如果传入Collection，则认为是左右侧相同的属性列表。
     *                否则认为是String类型，它对应于将左侧对象整体设置到右侧对象的属性名
     */
    @Deterministic
    public static <T> void leftjoinMerge(List<T> list, List<?> refList, String leftProp, String rightProp, Object refProp) {
        Map<String, T> leftMap = new HashMap<>();
        for (T item : list) {
            String key = ConvertHelper.toString(getFieldValue(item, leftProp));
            leftMap.put(key, item);
        }

        for (Object item : refList) {
            String key = ConvertHelper.toString(getFieldValue(item, rightProp));
            T left = leftMap.get(key);
            if (left != null) {
                if (refProp instanceof Map) {
                    Map<String, String> map = (Map<String, String>) refProp;
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        Object rightValue = getFieldValue(item, entry.getValue());
                        BeanTool.setComplexProperty(left, entry.getKey(), rightValue);
                    }
                } else if (refProp instanceof Collection) {
                    for (String propName : (Collection<String>) refProp) {
                        Object rightValue = getFieldValue(item, propName);
                        BeanTool.setComplexProperty(left, propName, rightValue);
                    }
                } else {
                    BeanTool.setComplexProperty(left, (String) refProp, item);
                }
            }
        }
    }
}