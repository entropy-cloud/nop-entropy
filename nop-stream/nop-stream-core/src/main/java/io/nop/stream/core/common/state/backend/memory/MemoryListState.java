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

import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;

class MemoryListState<T> implements ListState<T>, Serializable {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final ListStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();

    MemoryListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    public Iterable<T> get() throws IOException {
        List<T> list = storage.get(backend.getTypedNamespaceAndKey());
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public void add(T value) throws IOException {
        storage.computeIfAbsent(backend.getTypedNamespaceAndKey(), k -> new ArrayList<>()).add(value);
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        List<T> list = storage.computeIfAbsent(backend.getTypedNamespaceAndKey(), k -> new ArrayList<>());
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
        storage.put(backend.getTypedNamespaceAndKey(), newList);
    }

    @Override
    public void clear() {
        storage.remove(backend.getTypedNamespaceAndKey());
    }
}
