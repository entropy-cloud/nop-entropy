/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;

class MemoryInternalListState<K, N, T>
        implements InternalListState<K, N, T>, Serializable {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final ListStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();

    private transient N currentNamespace;

    MemoryInternalListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    public void setCurrentNamespace(N namespace) {
        this.currentNamespace = namespace;
    }

    @Override
    public N getCurrentNamespace() {
        return currentNamespace;
    }

    @Override
    public Iterable<T> get() throws IOException {
        List<T> list = storage.get(getStorageKey());
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public void add(T value) throws IOException {
        storage.computeIfAbsent(getStorageKey(), k -> new ArrayList<>()).add(value);
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        List<T> list = storage.computeIfAbsent(getStorageKey(), k -> new ArrayList<>());
        for (T value : values) {
            list.add(value);
        }
    }

    @Override
    public void update(Iterable<T> values) throws IOException {
        List<T> newList = new ArrayList<>();
        for (T value : values) {
            newList.add(value);
        }
        storage.put(getStorageKey(), newList);
    }

    @Override
    public void clear() {
        storage.remove(getStorageKey());
    }

    private TypedNamespaceAndKey getStorageKey() {
        if (currentNamespace == null) {
            throw new IllegalStateException(
                    "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
        }
        return new TypedNamespaceAndKey(currentNamespace, backend.routeKey(backend.getCurrentKey()));
    }
}
