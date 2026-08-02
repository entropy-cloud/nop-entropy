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
import io.nop.stream.core.common.state.TtlContext;

class MemoryListState<T> implements ListState<T>, Serializable, TtlAware {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final ListStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();

    TtlContext<TypedNamespaceAndKey> ttl;

    MemoryListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
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

    @Override
    public Iterable<T> get() throws IOException {
        TypedNamespaceAndKey k = backend.getTypedNamespaceAndKey();
        if (ttl != null && ttl.readEviction(k, storage)) {
            return Collections.emptyList();
        }
        List<T> list = storage.get(k);
        if (ttl != null && list != null) {
            ttl.recordRead(k);
        }
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public void add(T value) throws IOException {
        TypedNamespaceAndKey k = backend.getTypedNamespaceAndKey();
        if (ttl != null) {
            ttl.writeEviction(k, storage);
        }
        storage.computeIfAbsent(k, kk -> new ArrayList<>()).add(value);
        if (ttl != null) {
            ttl.recordWrite(k);
        }
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        TypedNamespaceAndKey k = backend.getTypedNamespaceAndKey();
        if (ttl != null) {
            ttl.writeEviction(k, storage);
        }
        List<T> list = storage.computeIfAbsent(k, kk -> new ArrayList<>());
        for (T value : values) {
            list.add(value);
        }
        if (ttl != null) {
            ttl.recordWrite(k);
        }
    }

    @Override
    public void update(Iterable<T> values) throws IOException {
        TypedNamespaceAndKey k = backend.getTypedNamespaceAndKey();
        List<T> newList = new ArrayList<>();
        for (T value : values) {
            newList.add(value);
        }
        storage.put(k, newList);
        if (ttl != null) {
            ttl.recordWrite(k);
        }
    }

    @Override
    public void clear() {
        TypedNamespaceAndKey k = backend.getTypedNamespaceAndKey();
        storage.remove(k);
        if (ttl != null) {
            ttl.onClear(k);
        }
    }
}
