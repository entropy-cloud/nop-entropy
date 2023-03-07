/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.lattice;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapLattice<K, V> implements ILattice<Map<K, ILattice<V>>> {
    private Map<K, ILattice<V>> value;

    public MapLattice() {
        value = bot();
    }

    public MapLattice(Map<K, ILattice<V>> map) {
        this.value = map == null ? bot() : map;
    }

    @Override
    public Map<K, ILattice<V>> bot() {
        return Collections.emptyMap();
    }

    @Override
    public Map<K, ILattice<V>> value() {
        return value;
    }

    public V get(Object key) {
        ILattice<V> v = value.get(key);
        if (v == null)
            return null;
        return v.value();
    }

    @Override
    public void merge(Map<K, ILattice<V>> e) {
        if (e == null || e.isEmpty())
            return;

        if (value == Collections.<String, ILattice<V>>emptyMap()) {
            value = new HashMap<>(e);
        } else {
            for (Map.Entry<K, ILattice<V>> entry : e.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    public void put(K key, ILattice<V> lattice) {
        ILattice<V> v = value.get(key);
        if (v == null) {
            v = lattice.cloneInstance();
            value.put(key, v);
        } else {
            v.merge(lattice.value());
        }
    }

    public MapLattice<K, V> intersect(Map<K, ? extends ILattice<V>> map) {
        if (map == null || map.isEmpty()) {
            return new MapLattice<>(bot());
        }

        if (value.isEmpty())
            return new MapLattice<>(bot());

        MapLattice<K, V> ret = new MapLattice<>(new HashMap<K, ILattice<V>>(map.size()));
        for (Map.Entry<K, ILattice<V>> entry : value.entrySet()) {
            ILattice<V> v2 = map.get(entry.getKey());
            if (v2 != null) {
                ret.put(entry.getKey(), entry.getValue());
                ret.put(entry.getKey(), v2);
            }
        }
        return ret;
    }

    @Override
    public void assign(Map<K, ILattice<V>> e) {
        if (e == null)
            e = bot();
        this.value = e;
    }

    public void assign(K key, ILattice<V> e) {
        if (value == Collections.<K, ILattice<V>>emptyMap()) {
            value = new HashMap<>();
        }
        value.put(key, e);
    }

    @Override
    public MapLattice<K, V> cloneInstance() {
        if (value == Collections.<K, ILattice<V>>emptyMap())
            return new MapLattice<>(value);

        return new MapLattice<>(new HashMap<>(value));
    }

    public int size() {
        return value.size();
    }

    public boolean containsKey(Object key) {
        return value.containsKey(key);
    }

    public V remove(Object key) {
        ILattice<V> lattice = value.remove(key);
        return lattice == null ? null : lattice.value();
    }

    public Set<K> keySet() {
        return value.keySet();
    }

    public SetLattice<K> keyLattice() {
        return new SetLattice<>(new HashSet<>(value.keySet()));
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }
}