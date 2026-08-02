/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.TtlContext;

class MemoryMapState<UK, UV> implements MapState<UK, UV>, Serializable, TtlAware {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final MapStateDescriptor<UK, UV> descriptor;
    final Map<TypedNamespaceAndKey, Map<UK, UV>> storage = new HashMap<>();

    TtlContext<TypedNamespaceAndKey> ttl;

    MemoryMapState(MemoryKeyedStateBackend<?> backend, MapStateDescriptor<UK, UV> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    public void bindTtl(TtlContext<TypedNamespaceAndKey> ctx) {
        this.ttl = ctx;
    }

    private TypedNamespaceAndKey currentKey() {
        return backend.getTypedNamespaceAndKey();
    }

    private Map<UK, UV> getOrCreateMap() {
        TypedNamespaceAndKey k = currentKey();
        if (ttl != null) {
            ttl.writeEviction(k, storage);
        }
        return storage.computeIfAbsent(k, key -> new HashMap<>());
    }

    private Map<UK, UV> getMap() {
        TypedNamespaceAndKey k = currentKey();
        Map<UK, UV> map = storage.get(k);
        if (map != null && ttl != null && ttl.readEviction(k, storage)) {
            return null;
        }
        if (map != null && ttl != null) {
            ttl.recordRead(k);
        }
        return map;
    }

    @Override
    public UV get(UK key) {
        Map<UK, UV> map = getMap();
        return map != null ? map.get(key) : null;
    }

    @Override
    public void put(UK key, UV value) {
        TypedNamespaceAndKey k = currentKey();
        if (ttl != null) {
            ttl.writeEviction(k, storage);
        }
        storage.computeIfAbsent(k, kk -> new HashMap<>()).put(key, value);
        if (ttl != null) {
            ttl.recordWrite(k);
        }
    }

    @Override
    public void putAll(Map<UK, UV> map) {
        TypedNamespaceAndKey k = currentKey();
        if (ttl != null) {
            ttl.writeEviction(k, storage);
        }
        storage.computeIfAbsent(k, kk -> new HashMap<>()).putAll(map);
        if (ttl != null) {
            ttl.recordWrite(k);
        }
    }

    @Override
    public void remove(UK key) {
        Map<UK, UV> map = getMap();
        if (map != null) {
            map.remove(key);
        }
    }

    @Override
    public boolean contains(UK key) {
        Map<UK, UV> map = getMap();
        return map != null && map.containsKey(key);
    }

    @Override
    public Iterable<Map.Entry<UK, UV>> entries() {
        Map<UK, UV> map = getMap();
        return map != null ? map.entrySet() : Collections.emptyList();
    }

    @Override
    public Iterable<UK> keys() {
        Map<UK, UV> map = getMap();
        return map != null ? map.keySet() : Collections.emptyList();
    }

    @Override
    public Iterable<UV> values() {
        Map<UK, UV> map = getMap();
        return map != null ? map.values() : Collections.emptyList();
    }

    @Override
    public Iterator<Map.Entry<UK, UV>> iterator() {
        Map<UK, UV> map = getMap();
        return map != null ? map.entrySet().iterator() : Collections.emptyIterator();
    }

    @Override
    public boolean isEmpty() {
        Map<UK, UV> map = getMap();
        return map == null || map.isEmpty();
    }

    @Override
    public void clear() {
        TypedNamespaceAndKey k = currentKey();
        storage.remove(k);
        if (ttl != null) {
            ttl.onClear(k);
        }
    }
}
