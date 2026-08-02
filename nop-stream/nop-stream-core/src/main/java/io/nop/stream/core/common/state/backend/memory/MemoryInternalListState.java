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
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

class MemoryInternalListState<K, N, T>
        implements InternalListState<K, N, T>, Serializable, TtlAware, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    ListStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();

    TtlContext<TypedNamespaceAndKey> ttl;

    private transient N currentNamespace;

    MemoryInternalListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        StateMigrationFunction<Object, Object> fn = (StateMigrationFunction<Object, Object>) migration;
        for (List<T> list : storage.values()) {
            for (int i = 0; i < list.size(); i++) {
                T old = list.get(i);
                if (old != null) {
                    list.set(i, (T) fn.migrate(old));
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (ListStateDescriptor<T>) newDescriptor;
    }

    @Override
    public void bindTtl(TtlContext<TypedNamespaceAndKey> ctx) {
        this.ttl = ctx;
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
        TypedNamespaceAndKey key = getStorageKey();
        if (ttl != null && ttl.readEviction(key, storage)) {
            return Collections.emptyList();
        }
        List<T> list = storage.get(key);
        if (ttl != null && list != null) {
            ttl.recordRead(key);
        }
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public void add(T value) throws IOException {
        TypedNamespaceAndKey key = getStorageKey();
        if (ttl != null) {
            ttl.writeEviction(key, storage);
        }
        storage.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        TypedNamespaceAndKey key = getStorageKey();
        if (ttl != null) {
            ttl.writeEviction(key, storage);
        }
        List<T> list = storage.computeIfAbsent(key, k -> new ArrayList<>());
        for (T value : values) {
            list.add(value);
        }
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public void update(Iterable<T> values) throws IOException {
        TypedNamespaceAndKey key = getStorageKey();
        List<T> newList = new ArrayList<>();
        for (T value : values) {
            newList.add(value);
        }
        storage.put(key, newList);
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public void clear() {
        TypedNamespaceAndKey key = getStorageKey();
        storage.remove(key);
        if (ttl != null) {
            ttl.onClear(key);
        }
    }

    private TypedNamespaceAndKey getStorageKey() {
        if (currentNamespace == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
        }
        return new TypedNamespaceAndKey(currentNamespace, backend.routeKey(backend.getCurrentKey()));
    }
}
