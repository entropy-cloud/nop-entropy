/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.observe;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ObservableMap<K, V> implements Map<K, V> {
    private final Map<K, V> map;
    private final ICollectionObserver observer;

    public ObservableMap(Map<K, V> map, ICollectionObserver observer) {
        this.map = map;
        this.observer = observer;
    }

    protected ICollectionObserver getObserver() {
        return observer;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public V put(K key, V value) {
        observer.beforeModify(map);
        V old = map.put(key, value);
        observer.afterModify(true);
        return old;
    }

    @Override
    public V remove(Object key) {
        observer.beforeModify(map);
        boolean b = map.containsKey(key);
        if (!b) {
            observer.afterModify(false);
            return null;
        } else {
            V old = map.remove(key);
            observer.afterModify(true);
            return old;
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        observer.beforeModify(map);
        map.putAll(m);
        observer.afterModify(true);
    }

    @Override
    public void clear() {
        observer.beforeModify(map);
        boolean empty = map.isEmpty();
        if (empty) {
            observer.afterModify(false);
        } else {
            map.clear();
            observer.afterModify(true);
        }
    }

    private Set<K> _keySet;
    private Collection<V> _values;

    @Override
    public Set<K> keySet() {
        if (_keySet == null) {
            _keySet = new ObservableSet<>(map.keySet(), observer);
        }
        return _keySet;
    }

    @Override
    public Collection<V> values() {
        if (_values == null)
            _values = new ObservableCollection<>(map.values(), observer);
        return _values;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new ObservableEntrySet<>(map.entrySet(), observer);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        map.forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        observer.beforeModify(map);
        boolean empty = map.isEmpty();
        map.replaceAll(function);
        observer.afterModify(!empty);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        observer.beforeModify(map);
        V old = map.putIfAbsent(key, value);
        observer.afterModify(true);
        return old;
    }

    @Override
    public boolean remove(Object key, Object value) {
        observer.beforeModify(map);
        boolean b = map.remove(key, value);
        observer.afterModify(b);
        return b;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        observer.beforeModify(map);
        boolean b = map.replace(key, oldValue, newValue);
        observer.afterModify(b);
        return b;
    }

    @Override
    public V replace(K key, V value) {
        observer.beforeModify(map);
        V old = map.replace(key, value);
        observer.afterModify(true);
        return old;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        observer.beforeModify(map);
        V old = map.computeIfAbsent(key, mappingFunction);
        observer.afterModify(true);
        return old;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        observer.beforeModify(map);
        V old = map.computeIfPresent(key, remappingFunction);
        observer.afterModify(old != null);
        return old;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        observer.beforeModify(map);
        V old = map.compute(key, remappingFunction);
        observer.afterModify(true);
        return old;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        observer.beforeModify(map);
        V old = map.merge(key, value, remappingFunction);
        observer.afterModify(true);
        return old;
    }
}