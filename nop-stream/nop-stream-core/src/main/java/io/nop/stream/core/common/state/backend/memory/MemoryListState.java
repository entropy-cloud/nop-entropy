/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

/**
 * item 21 D-2 convergence: the public/internal list pair shares this single
 * implementation. {@link MemoryInternalListState} only overrides
 * {@link #storageKey()} (namespaced key with fail-fast guard) instead of
 * duplicating every method.
 */
class MemoryListState<T> extends AbstractMemoryState implements ListState<T>, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    ListStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, List<T>> storage = new HashMap<>();

    MemoryListState(MemoryKeyedStateBackend<?> backend, ListStateDescriptor<T> descriptor) {
        super(backend);
        this.descriptor = descriptor;
    }

    /** Storage key for the current access; internal subclasses override with their own namespace. */
    protected TypedNamespaceAndKey storageKey() {
        return backend.getTypedNamespaceAndKey();
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
    public Iterable<T> get() throws IOException {
        TypedNamespaceAndKey k = storageKey();
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
        TypedNamespaceAndKey k = storageKey();
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
        TypedNamespaceAndKey k = storageKey();
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
        TypedNamespaceAndKey k = storageKey();
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
        TypedNamespaceAndKey k = storageKey();
        storage.remove(k);
        if (ttl != null) {
            ttl.onClear(k);
        }
    }
}
