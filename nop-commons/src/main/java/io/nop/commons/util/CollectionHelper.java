/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.MapMaker;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.CaseInsensitiveMap;
import io.nop.commons.collections.IntArrayMap;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MapOfInt;
import io.nop.commons.collections.ReverseList;
import io.nop.commons.collections.bit.DefaultBitSet;
import io.nop.commons.collections.bit.FixedBitSet;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.collections.bit.SmallBitSet;
import io.nop.commons.functional.IEqualsChecker;
import io.nop.commons.util.objects.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.commons.CommonErrors.ARG_CLASS;
import static io.nop.commons.CommonErrors.ERR_COLLECTIONS_CAN_NOT_TRANSFORM_TO_ITERATOR;
import static io.nop.commons.CommonErrors.ERR_COLLECTIONS_NOT_LIST;
import static io.nop.commons.CommonErrors.ERR_COLLECTIONS_NOT_SUPPORT_STREAM;

public class CollectionHelper {
    /**
     * Map缺省的loadFactor
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    public static IBitSet newIndexBitSet(int expectedSize, int capacity) {
        if (capacity <= Long.SIZE)
            return new SmallBitSet();
        if (expectedSize * 2 >= capacity)
            return new FixedBitSet(capacity);
        return new DefaultBitSet();
    }

    public static IBitSet newFixedBitSet(int capacity) {
        if (capacity < Long.SIZE)
            return new SmallBitSet();
        return new FixedBitSet(capacity);
    }

    public static <V> MapOfInt<V> newIndexMap(int expectedSize, int capacity) {
        if (capacity <= Integer.SIZE || expectedSize * 2 >= capacity)
            return new IntArrayMap<>(capacity);
        return new IntHashMap<>((int) (expectedSize / 0.8));
    }

    public static int nextFreeIndex(BitSet bs, int fromIndex) {
        while (bs.get(fromIndex)) {
            fromIndex++;
        }
        return fromIndex;
    }

    public static boolean isEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    public static boolean isEmptyMap(Map<?, ?> map) {
        if (map == null)
            return true;
        return map.isEmpty();
    }

    public static int safeGetSize(Collection<?> c) {
        if (c == null)
            return -1;
        return c.size();
    }

    public static <T> List<T> toNotNull(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    public static <T> T first(Collection<T> c) {
        if (c == null || c.isEmpty())
            return null;
        return c.iterator().next();
    }

    public static <T> T last(List<T> c) {
        if (c == null || c.isEmpty())
            return null;
        return c.get(c.size() - 1);
    }

    public static <T> int identityIndexOf(List<T> c, T value) {
        for (int i = 0, n = c.size(); i < n; i++) {
            if (c.get(i) == value)
                return i;
        }
        return -1;
    }

    public static <T> boolean identityContains(Collection<T> ret, T value) {
        for (T item : ret) {
            if (item == value)
                return true;
        }
        return false;
    }

    public static int getSize(Collection<?> c) {
        if (c == null)
            return 0;
        return c.size();
    }

    public static <T> void setSize(List<T> list, int n) {
        for (int i = list.size() - 1; i >= n; i--) {
            list.remove(i);
        }
    }

    public static int calcInitSize(int expectedSize) {
        return (int) ((expectedSize / DEFAULT_LOAD_FACTOR) + 1);
    }

    public static <T> List<T> newList(long size) {
        if (size >= 0) {
            if (size >= MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("illegal array size:" + size);

            return new ArrayList<>((int) size);
        } else {
            return new ArrayList<>();
        }
    }

    public static <K, V> ConcurrentMap<K, V> newConcurrentWeakMap() {
        return new MapMaker().weakKeys().concurrencyLevel(32).makeMap();
    }

    public static <K, V> Map<K, V> newHashMap(int expectedSize) {
        return new HashMap<>(calcInitSize(expectedSize));
    }

    public static <K, V> Map<K, V> newLinkedHashMap(int expectedSize) {
        return new LinkedHashMap<>(calcInitSize(expectedSize));
    }

    public static <T> Set<T> newIdentityHashSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public static <K, V> SortedMap<K, V> toSortedMap(Map<K, V> map) {
        if (map == null)
            return null;
        if (map instanceof SortedMap)
            return (SortedMap) map;
        return new TreeMap<>(map);
    }

    public static <K, V> ConcurrentMap<K, V> newConcurrentMap(int expectedSize) {
        return new ConcurrentHashMap<>(expectedSize);
    }

    public static Properties mapToProperties(Map<String, ?> map) {
        if (map == null)
            return null;

        Properties props = new Properties();
        props.putAll(map);
        return props;
    }

    public static <K, V> boolean isFixedEmptyMap(Map<K, V> map) {
        return map == Collections.emptyMap();
    }

    public static <V> boolean isFixedEmptyList(List<V> list) {
        return list == Collections.<V>emptyList();
    }

    public static <V> boolean isFixedEmptySet(Set<V> set) {
        return set == Collections.<V>emptySet();
    }

    public static <K, V> Map<K, V> toMap(Collection<? extends K> keyList, Collection<? extends V> valueList) {
        if (keyList == null || valueList == null)
            return new LinkedHashMap<>(0);
        Iterator<? extends K> kIt = keyList.iterator();
        Iterator<? extends V> vIt = valueList.iterator();

        Map<K, V> ret = newLinkedHashMap(Math.min(keyList.size(), valueList.size()));

        while (kIt.hasNext() && vIt.hasNext()) {
            K key = kIt.next();
            V value = vIt.next();
            ret.put(key, value);
        }
        return ret;
    }

    public static <K, V> Map<K, V> toNonEmptyKeyMap(Collection<? extends K> keyList, Collection<? extends V> valueList) {
        if (keyList == null || valueList == null)
            return new LinkedHashMap<>(0);
        Iterator<? extends K> kIt = keyList.iterator();
        Iterator<? extends V> vIt = valueList.iterator();

        Map<K, V> ret = newLinkedHashMap(Math.min(keyList.size(), valueList.size()));

        while (kIt.hasNext() && vIt.hasNext()) {
            K key = kIt.next();
            V value = vIt.next();
            if (StringHelper.isEmptyObject(key))
                continue;
            ret.put(key, value);
        }
        return ret;
    }

    public static <T> List<T> reverseList(List<T> list) {
        if (list instanceof ReverseList) {
            return ((ReverseList<T>) list).getForwardList();
        } else {
            return new ReverseList<T>(list);
        }
    }

    public static <T> Map<String, T> newCaseInsensitiveMap(int capacity) {
        return new CaseInsensitiveMap<>(capacity);
    }

    public static <T> Set<T> newLinkedHashSet(Set<T> set) {
        if (set == null)
            return new LinkedHashSet<T>();
        return new LinkedHashSet<T>(set);
    }

    public static <T> List<T> cloneList(Collection<T> list) {
        if (list == null)
            return null;
        if (list == Collections.emptyList())
            return Collections.emptyList();
        return new ArrayList<T>(list);
    }

    public static <T> List<T> freezeList(List<T> list) {
        if (list == null)
            return Collections.emptyList();
        if (list == Collections.emptyList())
            return Collections.emptyList();
        if (UNMODIFIABLE_CLASSES.contains(list.getClass()))
            return list;
        return Collections.unmodifiableList(list);
    }

    public static <T> List<T> append(List<T> list, T elm) {
        if (list == null || list == Collections.<T>emptyList()) {
            list = new ArrayList<T>();
        }
        list.add(elm);
        return list;
    }

    public static <T> List<T> appendAll(List<T> list, Collection<? extends T> c) {
        if (c == null || c.isEmpty())
            return list;

        if (list == null || list == Collections.<T>emptyList()) {
            list = new ArrayList<T>();
        }
        list.addAll(c);
        return list;
    }

    public static <T> T get(List<T> list, int index) {
        if (list == null || list.size() <= index)
            return null;
        return list.get(index);
    }

    public static <T> T set(List<T> list, int index, T value) {
        for (int i = list.size(); i <= index; i++) {
            list.add(null);
        }
        return list.set(index, value);
    }

    public static List<String> toStringList(Collection<?> c) {
        if (c == null)
            return null;
        List<String> ret = new ArrayList<>(c.size());
        for (Object o : c) {
            ret.add(ConvertHelper.toString(o));
        }
        return ret;
    }

    public static List<String> trimStringList(Collection<String> c) {
        if (c == null)
            return null;
        List<String> ret = new ArrayList<>(c.size());
        for (String str : c) {
            if (str != null)
                str = str.trim();
            ret.add(str);
        }
        return ret;
    }

    public static <T> List<T> toList(Object o) {
        return toList(o, false);
    }

    public static <T> List<T> toList(Object c, boolean allowSplitString) {
        if (c == null)
            return null;
        if (c instanceof List<?>)
            return (List<T>) c;
        if (allowSplitString && c instanceof String) {
            return (List<T>) ConvertHelper.toCsvList(c.toString(), NopException::new);
        }
        if (c instanceof Collection<?>) {
            return new ArrayList<T>((Collection<T>) c);
        }
        if (c instanceof Iterable) {
            List<T> ret = new ArrayList<T>();
            for (T o : (Iterable<T>) c) {
                ret.add(o);
            }
            return ret;
        } else if (c instanceof Iterator) {
            return iteratorToList((Iterator) c);
        } else if (c instanceof Enumeration) {
            Enumeration<T> enumeration = (Enumeration<T>) c;
            return iteratorToList(enumeration.asIterator());
        }
        throw new NopException(ERR_COLLECTIONS_NOT_LIST).param(ARG_CLASS, c.getClass()).param(ARG_VALUE, c);
    }

    public static <T> Collection<T> toCollection(Object c, boolean allowSplitString) {
        if (c == null)
            return null;
        if (c instanceof Collection<?>)
            return (Collection<T>) c;
        if (allowSplitString && c instanceof String) {
            return (Collection<T>) ConvertHelper.toCsvList(c.toString(), NopException::new);
        }
        if (c instanceof Iterable) {
            List<T> ret = new ArrayList<T>();
            for (T o : (Iterable<T>) c) {
                ret.add(o);
            }
            return ret;
        } else if (c instanceof Iterator) {
            return iteratorToList((Iterator) c);
        }
        throw new NopException(ERR_COLLECTIONS_NOT_LIST).param(ARG_CLASS, c.getClass()).param(ARG_VALUE, c);
    }

    public static <T> List<T> iteratorToList(Iterator<T> it) {
        List<T> ret = new ArrayList<>();
        if (it == null)
            return ret;
        while (it.hasNext()) {
            ret.add(it.next());
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> toSet(Collection<? extends T> c) {
        if (c == null)
            return null;
        if (c instanceof Set)
            return (Set<T>) c;
        Set<T> ret = new LinkedHashSet<T>(c);
        return ret;
    }

    public static <T> Set<T> addToSet(Set<T> set, Collection<T> c) {
        if (c == null || c.isEmpty())
            return set;

        if (set == null || set == Collections.emptySet()) {
            set = new LinkedHashSet<T>();
        }
        set.addAll(c);
        return set;
    }

    public static <T> List<T> collect(Iterator<T> it) {
        if (it == null)
            return new ArrayList<T>(0);
        List<T> ret = new ArrayList<T>();
        while (it.hasNext()) {
            ret.add(it.next());
        }
        return ret;
    }

    public static <T> void collect(Iterator<T> it, Collection<T> ret) {
        if (it != null) {
            while (it.hasNext()) {
                ret.add(it.next());
            }
        }
    }

    public static <T> List<T> safeSingletonList(T element) {
        if (element == null)
            return Collections.emptyList();
        return Collections.singletonList(element);
    }

    public static <T> List<List<T>> splitChunk(Collection<T> allData, int chunkSize) {
        if (allData.size() < chunkSize)
            return Collections.singletonList(toList(allData));

        List<T> list = toList(allData);

        int batchCount = (int) Math.ceil(list.size() / (double) chunkSize);
        List<List<T>> ret = new ArrayList<List<T>>(batchCount);

        for (int i = 0; i < batchCount; i++) {

            int fromIndex = i * chunkSize;
            int toIndex = Math.min((i + 1) * chunkSize, list.size());

            List<T> subList = list.subList(fromIndex, toIndex);
            ret.add(subList);
        }
        return ret;
    }

    public static <K, V> Map<K, V> makeMap(Collection<? extends K> keys, Collection<? extends V> values) {
        Map<K, V> ret = new HashMap<K, V>(keys.size());
        Iterator<? extends K> kit = keys.iterator();
        Iterator<? extends V> vit = values.iterator();
        while (kit.hasNext() && vit.hasNext()) {
            K key = kit.next();
            V value = vit.next();
            ret.put(key, value);
        }
        return ret;
    }

    public static Map<String, String> toStringMap(Map<?, ?> map) {
        if (map == null)
            return null;
        if (map.isEmpty())
            return Collections.emptyMap();

        Map<String, String> ret = new HashMap<String, String>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = ConvertHelper.toString(entry.getKey());
            String value = ConvertHelper.toString(entry.getValue());
            ret.put(key, value);
        }
        return ret;
    }

    public static <T> boolean containsAny(Collection<T> c1, Collection<T> c2) {
        if (c1 == null || c2 == null || c1.isEmpty() || c2.isEmpty())
            return false;
        for (T o : c2) {
            if (c1.contains(o))
                return true;
        }
        return false;
    }

    public static <K, V> Map<V, K> reverseMap(Map<K, V> map, boolean keepLast) {
        Map<V, K> ret = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (keepLast) {
                ret.put(entry.getValue(), entry.getKey());
            } else {
                // 以第一个值为准
                ret.putIfAbsent(entry.getValue(), entry.getKey());
            }
        }
        return ret;
    }

    static final Set<Class<?>> UNMODIFIABLE_CLASSES = new HashSet<Class<?>>(Arrays.<Class<?>>asList(
            Collections.emptyList().getClass(), Collections.emptySet().getClass(), Collections.emptyMap().getClass(),
            Collections.unmodifiableCollection(Collections.emptyList()).getClass(),
            Collections.unmodifiableList(new ArrayList<>(0)).getClass(),
            Collections.unmodifiableList(new LinkedList<>()).getClass(),
            Collections.unmodifiableSet(new HashSet<>(0)).getClass(),
            Collections.unmodifiableSortedSet(new TreeSet<>()).getClass(),
            Collections.unmodifiableMap(new HashMap<>(0)).getClass(),
            Collections.unmodifiableSortedMap(new TreeMap<>()).getClass()

    ));

    public static <T> Set<T> immutableSet(Collection<T> c) {
        if (c.isEmpty())
            return Collections.emptySet();
        return ImmutableSet.copyOf(c);
    }

    public static <T> List<T> immutableList(Collection<T> c) {
        if (c.isEmpty())
            return Collections.emptyList();
        return ImmutableList.copyOf(c);
    }

    public static <K, V> Map<K, V> immutableMap(Map<K, V> map) {
        if (map.isEmpty())
            return Collections.emptyMap();
        return ImmutableMap.copyOf(map);
    }

    public static <K, V> Map<K, V> buildImmutableMap(Pair<K, V>... pairs) {
        if (pairs.length == 0)
            return Collections.emptyMap();
        Map<K, V> map = new HashMap<>();
        for (Pair<K, V> pair : pairs) {
            map.put(pair.getKey(), pair.getValue());
        }
        return ImmutableMap.copyOf(map);
    }

    public static <V> List<V> buildImmutableList(V... values) {
        if (values.length == 0)
            return Collections.emptyList();
        return ImmutableList.copyOf(values);
    }

    public static <V> Set<V> buildImmutableSet(V... values) {
        if (values.length == 0)
            return Collections.emptySet();
        return ImmutableSet.copyOf(values);
    }

    public static <K, V> Map<K, V> immutableSortedMap(Map<K, V> map) {
        return ImmutableSortedMap.copyOf(map);
    }

    public static boolean isUnmodifiableCollection(Object o) {
        if (o == null || o instanceof ImmutableCollection || o instanceof ImmutableMap)
            return true;
        return UNMODIFIABLE_CLASSES.contains(o.getClass());
    }

    public static <T> List<T> toUnmodifiableList(List<T> list) {
        if (list == null)
            return null;
        if (isUnmodifiableCollection(list))
            return list;
        return Collections.unmodifiableList(list);
    }

    public static <K, V> Map<K, V> toUnmodifiableMap(Map<K, V> map) {
        if (map == null)
            return null;
        if (isUnmodifiableCollection(map))
            return map;
        if (map instanceof SortedMap)
            return Collections.unmodifiableSortedMap((SortedMap<K, V>) map);
        return Collections.unmodifiableMap(map);
    }

    public static Object toUnmodifiable(Object o) {
        if (o != null && !isUnmodifiableCollection(o)) {
            if (o instanceof List)
                return Collections.unmodifiableList((List<?>) o);
            // if(o instanceof NavigableMap) //need jdk1.8
            // return
            // Collections.unmodifiableNavigableMap((NavigableMap<?,?>)o);
            if (o instanceof SortedMap)
                return Collections.unmodifiableSortedMap((SortedMap<?, ?>) o);
            if (o instanceof Map)
                return Collections.unmodifiableMap((Map<?, ?>) o);
            // if(o instanceof NavigableSet) // need jdk1.8
            // return Collections.unmodifiableNavigableSet((NavigableSet<?>)o);
            if (o instanceof SortedSet)
                return Collections.unmodifiableSortedSet((SortedSet<?>) o);
            if (o instanceof Set)
                return Collections.unmodifiableSet((Set<?>) o);

        }
        return o;
    }

    public static <T> List<T> emptyOrSingletonList(T o) {
        if (o == null)
            return Collections.emptyList();
        return Collections.singletonList(o);
    }

    /**
     * list1,list2的并集（在list1或list2中的对象），产生新List
     */
    public static <E, L extends Collection<E>> L union(final Collection<? extends E> list1,
                                                       final Collection<? extends E> list2, L result) {
        if (list1 != null)
            result.addAll(list1);
        if (list2 != null)
            result.addAll(list2);
        return result;
    }

    /**
     * list1, list2的交集（同时在list1和list2的对象），产生新List
     */
    public static <T, L extends Collection<T>> L intersection(final Collection<? extends T> list1,
                                                              final Collection<? extends T> list2, L result) {
        if (list1 == null && list2 == null) {
            return result;
        }
        if (list1 == null) {
            result.addAll(list2);
            return result;
        }

        if (list2 == null) {
            result.addAll(list1);
            return result;
        }

        Collection<? extends T> smaller = list1;
        Collection<? extends T> larger = list2;
        if (list1.size() > list2.size()) {
            smaller = list2;
            larger = list1;
        }

        // 克隆一个可修改的副本
        List<T> newSmaller = new ArrayList<T>(smaller);
        for (final T e : larger) {
            if (newSmaller.contains(e)) {
                result.add(e);
                newSmaller.remove(e);
            }
        }
        return result;
    }

    /**
     * list1, list2的交集（同时在list1和list2的对象），产生新List
     */
    public static <T> List<T> intersection(final Collection<? extends T> list1, final Collection<? extends T> list2) {
        List<T> ret = new ArrayList<>();
        intersection(list1, list2, ret);
        return ret;
    }

    /**
     * list1, list2的差集（在list1，不在list2中的对象），产生新List.
     */
    public static <T, L extends Collection<T>> L difference(final Collection<? extends T> list1,
                                                            final Collection<? extends T> list2, L result) {
        if (list1 == null)
            return result;

        if (list2 == null) {
            result.addAll(list1);
            return result;
        }

        for (T item : list1) {
            if (!list2.contains(item))
                result.add(item);
        }

        return result;
    }

    public static <T> int findIndex(List<T> list, Predicate<T> filter) {
        for (int i = 0, n = list.size(); i < n; i++) {
            if (filter.test(list.get(i)))
                return i;
        }
        return -1;
    }

    public static <T> List<T> difference(Collection<? extends T> list1, Collection<? extends T> list2) {
        List<T> ret = new ArrayList<T>();
        difference(list1, list2, ret);
        return ret;
    }

    /**
     * list1, list2的补集（在list1或list2中，但不在交集中的对象，又叫反交集）产生新List.
     */
    public static <T, L extends Collection<T>> L disjoint(final Collection<? extends T> list1,
                                                          final Collection<? extends T> list2, L result) {
        if (list1 == null && list2 == null)
            return result;
        if (list1 == null) {
            result.addAll(list2);
            return result;
        }
        if (list2 == null) {
            result.addAll(list1);
            return result;
        }

        for (T o : list1) {
            boolean b1 = list1.contains(o);
            boolean b2 = list2.contains(o);
            if (b1 && !b2 || (!b1 && b2)) {
                result.add(o);
            }
        }

        for (T o : list2) {
            boolean b1 = list1.contains(o);
            boolean b2 = list2.contains(o);
            if (b1 && !b2 || (!b1 && b2)) {
                result.add(o);
            }
        }

        return result;
    }

    public static <T> T getByIndex(Iterable<T> c, int index) {
        if (c instanceof List) {
            List<T> list = (List<T>) c;
            if (list.size() <= index)
                return null;
            return list.get(index);
        } else if (c instanceof Collection) {
            if (((Collection<T>) c).size() <= index)
                return null;
        }
        int n = 0;
        for (T item : c) {
            if (n == index)
                return item;
            n++;
        }
        return null;
    }

    /**
     * 根据指定的数组下标从源数组中获取数据，构成一个新的数组
     *
     * @param list  源数组
     * @param index 一组给定的数组下标
     * @return
     */
    public static <T> List<T> getMultiByIndex(List<T> list, List<Integer> index) {
        List<T> ret = new ArrayList<T>(index.size());
        for (int i = 0, n = index.size(); i < n; i++) {
            int idx = index.get(i);
            ret.add(list.get(idx));
        }
        return ret;
    }

    public static <T> void getMultiByIndex(List<T> list, List<Integer> index, List<T> out) {
        for (int i = 0, n = index.size(); i < n; i++) {
            Integer idx = index.get(i);
            if (idx == null) {
                out.add(null);
            } else {
                out.add(list.get(idx));
            }
        }
    }

    public static <T> List<T> getMultiByIndex(List<T> list, int[] index) {
        List<T> ret = new ArrayList<T>(index.length);
        for (int i = 0, n = index.length; i < n; i++) {
            int idx = index[i];
            ret.add(list.get(idx));
        }
        return ret;
    }

    public static <T> List<T> findMany(Collection<? extends T> coll, Predicate<T> filter) {
        List<T> ret = new ArrayList<T>();
        for (T o : coll) {
            if (filter.test(o))
                ret.add(o);
        }
        return ret;
    }

    public static <T> T findFirst(Collection<? extends T> coll, Predicate<T> filter) {
        for (T o : coll) {
            if (filter.test(o))
                return o;
        }
        return null;
    }

    public static <T> T findLast(List<? extends T> coll, Predicate<T> filter) {
        for (int i = coll.size() - 1; i >= 0; i--) {
            T item = coll.get(i);
            if (filter.test(item))
                return item;
        }
        return null;
    }

    public static <T> Stream<T> iteratorToStream(Iterator<T> sourceIterator, boolean parallel) {
        Iterable<T> iterable = () -> sourceIterator;
        return StreamSupport.stream(iterable.spliterator(), parallel);
    }

    public static <T> Stream<T> toStream(Object o, boolean wrapSingleton, boolean parallel) {
        if (o == null)
            return collectionToStream(Collections.emptyList(), parallel);
        if (o instanceof Stream)
            return (Stream) o;

        if (o instanceof Collection)
            return collectionToStream((Collection<T>) o, parallel);
        if (o instanceof Object[]) {
            return collectionToStream(Arrays.asList((T[]) o), parallel);
        }
        if (o instanceof Iterable) {
            return StreamSupport.stream(((Iterable) o).spliterator(), parallel);
        }
        if (o instanceof Iterator)
            return iteratorToStream((Iterator<T>) o, parallel);

        // 不自动转化Map类型，否则EL表达式中对象形式和序列化后的Map形式不等价
        // if(o instanceof Map)
        // return(Stream<T>)((Map<?, ?>) o).entrySet().stream();

        if (wrapSingleton)
            return Collections.singletonList((T) o).stream();

        throw new NopException(ERR_COLLECTIONS_NOT_SUPPORT_STREAM).param(ARG_VALUE, o).param(ARG_CLASS, o.getClass());
    }

    public static <T> Stream<T> collectionToStream(Collection<T> c, boolean parallel) {
        return parallel ? c.parallelStream() : c.stream();
    }

    public static <T> Iterator<T> toIterator(Object o, boolean wrapSingleton) {
        return toIterator(o, wrapSingleton, err -> new NopException(ERR_COLLECTIONS_CAN_NOT_TRANSFORM_TO_ITERATOR)
                .param(ARG_VALUE, o).param(ARG_CLASS, o.getClass()));
    }

    public static <T> Iterator<T> toIterator(Object o, boolean wrapSingleton,
                                             Function<ErrorCode, NopException> errorFactory) {
        if (o == null)
            return Collections.emptyListIterator();

        if (o instanceof Iterator)
            return (Iterator<T>) o;

        if (o instanceof Iterable)
            return ((Iterable) o).iterator();

        if (o instanceof Object[])
            return (Iterator<T>) Arrays.asList((Object[]) o).iterator();

        // if(o instanceof Map)
        // return (Iterator<T>)((Map<?, ?>) o).entrySet().iterator();

        if (wrapSingleton)
            return Collections.singletonList((T) o).iterator();

        throw errorFactory.apply(ERR_COLLECTIONS_CAN_NOT_TRANSFORM_TO_ITERATOR).param(ARG_VALUE, o).param(ARG_CLASS,
                o.getClass());
    }

    public static <T> boolean isSameList(List<T> listA, List<T> listB, IEqualsChecker<T> checker) {
        if (listA == null)
            return listB == null;
        if (listB == null)
            return listA == null;
        if (listA.size() != listB.size())
            return false;

        for (int i = 0, n = listA.size(); i < n; i++) {
            T itemB = listB.get(i);
            if (itemB == null)
                return false;
            T itemA = listA.get(i);
            if (!checker.isEquals(itemA, itemB))
                return false;
        }
        return true;
    }

    public static <K, V> boolean isSameMap(Map<K, V> mapA, Map<K, V> mapB, IEqualsChecker<V> checker) {
        if (mapA == null)
            return mapB == null;
        if (mapB == null)
            return mapA == null;
        if (mapA.size() != mapB.size())
            return false;

        for (Map.Entry<K, V> entry : mapA.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();
            V valueB = mapB.get(key);
            if (valueB == null) {
                if (value == null)
                    continue;
                return false;
            }
            if (!checker.isEquals(value, valueB))
                return false;
        }
        return true;
    }

    public static <T> List<T> newListWithInit(int count, T init) {
        List<T> ret = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ret.add(init);
        }
        return ret;
    }

    public static <T> List<T> copyTail(List<T> list, int fromIndex) {
        int size = list.size();
        if (fromIndex >= size)
            return new ArrayList<>(0);
        List<T> ret = new ArrayList<>(list.size() - fromIndex);
        for (int i = fromIndex; i < size; i++) {
            ret.add(list.get(i));
        }
        return ret;
    }

    public static <K, V> void putAllIfAbsent(Map<K, V> m1, Map<K, ? extends V> m2) {
        if (m2 != null) {
            for (Map.Entry<K, ? extends V> entry : m2.entrySet()) {
                m1.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void removeFrom(List<?> list, int fromIndex, int count) {
        int endIndex = Math.min(list.size(), fromIndex + count);
        for (int i = endIndex - 1; i >= fromIndex; i--) {
            list.remove(i);
        }
    }

    public static <T> int sumInt(List<T> list, ToIntFunction<T> fn) {
        int ret = 0;
        for (T item : list) {
            ret += fn.applyAsInt(item);
        }
        return ret;
    }

    public static <T> long sumLong(List<T> list, ToLongFunction<T> fn) {
        long ret = 0;
        for (T item : list) {
            ret += fn.applyAsLong(item);
        }
        return ret;
    }

    public static <T> double sumDouble(List<T> list, ToDoubleFunction<T> fn) {
        int ret = 0;
        for (T item : list) {
            ret += fn.applyAsDouble(item);
        }
        return ret;
    }

    public static <T> Set<T> mergeSet(Set<T> a, Set<T> b) {
        if (a == null || a.isEmpty())
            return b;
        if (b == null || b.isEmpty())
            return a;

        Set<T> ret = new HashSet<>();
        ret.addAll(a);
        ret.addAll(b);
        return ret;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <K, V> Map<K, V> mergeMap(Map<K, V> result, Map<?, ?>... m) {
        if (m == null || m.length == 0)
            return result;

        if (result == null) {
            result = new LinkedHashMap<K, V>();
        }
        for (Map<?, ?> item : m) {
            if (item != null && item != result)
                result.putAll((Map) item);
        }
        return result;
    }

    public static Map<String, Object> flattenMap(Map<String, ?> map) {
        if (map == null)
            return null;

        Map<String, Object> ret = new LinkedHashMap<>();
        _flattenMap(map, null, ret);
        return ret;
    }

    private static void _flattenMap(Map<String, ?> map, String prefix, Map<String, Object> ret) {
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            String key = prefix == null ? name : prefix + '.' + name;
            if (value instanceof Map) {
                _flattenMap((Map<String, ?>) value, key, ret);
            } else {
                ret.put(key, value);
            }
        }
    }

    public static <T> List<T> concatList(List<T> aList, List<T> bList) {
        if (aList == null)
            aList = Collections.emptyList();
        if (bList == null)
            bList = Collections.emptyList();
        List<T> ret = new ArrayList<>(aList.size() + bList.size());
        ret.addAll(aList);
        ret.addAll(bList);
        return ret;
    }

    public static <T> List<T> prepend(List<T> list, T item) {
        if (list == null || list == Collections.emptyList()) {
            list = new ArrayList<>();
        }
        list.add(0, item);
        return list;
    }
}
