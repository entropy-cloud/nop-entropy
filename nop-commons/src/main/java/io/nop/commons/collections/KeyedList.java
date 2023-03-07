/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FreezeHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IFreezable;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.Function;

import static io.nop.commons.CommonErrors.ERR_LIST_NOT_ALLOW_NULL_ELEMENT;

public class KeyedList<T> extends AbstractList<T> implements IKeyedList<T>, IFreezable, RandomAccess {

    private static final int DEFAULT_CAPACITY = 10;

    private static final KeyedList EMPTY_LIST = new KeyedList(Collections.emptyList(), Collections.emptyMap(), o -> "")
            .freezeList();

    public static <T> KeyedList<T> emptyList() {
        return (KeyedList<T>) EMPTY_LIST;
    }

    private final List<T> list;
    private final Map<String, T> map;
    private final Function<T, ?> keyFn;

    private boolean frozen;

    public KeyedList(Function<T, ?> keyFn) {
        this(DEFAULT_CAPACITY, keyFn);
    }

    public KeyedList(int size, Function<T, ?> keyFn) {
        this.list = new ArrayList<>(size);
        this.map = CollectionHelper.newHashMap(size);
        this.keyFn = Guard.notNull(keyFn, "keyFn");
    }

    private KeyedList(List<T> list, Map<String, T> map, Function<T, String> keyFn) {
        this.list = list;
        this.map = map;
        this.keyFn = Guard.notNull(keyFn, "keyFn");
    }

    public static <T> KeyedList<T> fromList(List<T> list, Function<T, ?> keyFn) {
        if (list == null)
            return emptyList();
        if (list instanceof KeyedList)
            return ((KeyedList<T>) list);
        KeyedList<T> ret = new KeyedList<>(list.size(), keyFn);
        ret.addAll(list);
        return ret;
    }

    public String toString() {
        return list.toString();
    }

    public Set<String> keySet() {
        return Collections.unmodifiableSet(map.keySet());
    }

    public KeyedList<T> freezeList() {
        freeze(true);
        return this;
    }

    public String getKey(T obj) {
        if (obj == null)
            return null;
        return StringHelper.toString(keyFn.apply(obj), null);
    }

    @Override
    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    @Override
    public boolean frozen() {
        return frozen;
    }

    @Override
    public void freeze(boolean cascade) {
        this.frozen = true;
        if (cascade)
            FreezeHelper.deepFreezeObjects(list);
    }

    public void sort() {
        Collections.sort(this.list, SafeOrderedComparator.DEFAULT);
    }

    @Override
    public T getByKey(String key) {
        return map.get(key);
    }

    @Override
    public T removeByKey(String key) {
        FreezeHelper.checkNotFrozen(this);
        T item = map.remove(key);
        if (item != null) {
            list.remove(item);
        }
        return item;
    }

    @Override
    public T get(int index) {
        return list.get(index);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean add(T t) {
        FreezeHelper.checkNotFrozen(this);

        if (t == null) {
            throw new NopException(ERR_LIST_NOT_ALLOW_NULL_ELEMENT);
        }
        T item = map.put(getKey(t), t);
        if (item != null) {
            if (item != t) {
                int index = list.indexOf(item);
                list.set(index, t);
                return true;
            } else {
                return false;
            }
        } else {
            return list.add(t);
        }
    }

    @Override
    public T set(int index, T element) {
        FreezeHelper.checkNotFrozen(this);
        if (element == null) {
            throw new NopException(ERR_LIST_NOT_ALLOW_NULL_ELEMENT);
        }

        T old = list.set(index, element);
        if (old != element) {
            // 排序的中间过程可能会出现重复的元素
            if (old != null && !list.contains(old)) {
                map.remove(getKey(old));
            }
            if (element != null) {
                map.put(getKey(element), element);
            }
        }
        return old;
    }

    @Override
    public void add(int index, T element) {
        FreezeHelper.checkNotFrozen(this);
        if (element == null) {
            throw new NopException(ERR_LIST_NOT_ALLOW_NULL_ELEMENT);
        }
        list.add(index, element);
        T old = map.put(getKey(element), element);

        if (old != element && old != null) {
            list.remove(old);
        }
    }

    @Override
    public T remove(int index) {
        FreezeHelper.checkNotFrozen(this);
        T item = list.remove(index);
        if (item != null) {
            map.remove(getKey(item));
        }
        return item;
    }

    @Override
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    @Override
    public void clear() {
        FreezeHelper.checkNotFrozen(this);
        list.clear();
        map.clear();
    }

    @Override
    public boolean contains(Object o) {
        if (o == null)
            return false;
        String key = getKey((T) o);
        return o.equals(map.get(key));
    }

    @Override
    public boolean remove(Object o) {
        FreezeHelper.checkNotFrozen(this);
        if (o == null) {
            return false;
        }
        String key = getKey((T) o);
        T item = map.get(key);
        if (o.equals(item)) {
            list.remove(item);
            map.remove(key);
            return true;
        }
        return false;
    }

    public void sort(Comparator<? super T> comparator) {
        this.list.sort(comparator);
    }
}