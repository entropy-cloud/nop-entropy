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
import java.util.HashMap;
import java.util.Map;

import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;

class MemoryValueState<T> implements ValueState<T>, Serializable, TtlAware, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    ValueStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, T> storage = new HashMap<>();

    TtlContext<TypedNamespaceAndKey> ttl;

    MemoryValueState(MemoryKeyedStateBackend<?> backend, ValueStateDescriptor<T> descriptor) {
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
        for (Map.Entry<TypedNamespaceAndKey, T> e : storage.entrySet()) {
            T old = e.getValue();
            if (old == null) {
                continue;
            }
            e.setValue((T) fn.migrate(old));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (ValueStateDescriptor<T>) newDescriptor;
    }

    @Override
    public void bindTtl(TtlContext<TypedNamespaceAndKey> ctx) {
        this.ttl = ctx;
    }

    @Override
    public T value() throws IOException {
        TypedNamespaceAndKey k = backend.getTypedNamespaceAndKey();
        if (ttl != null && ttl.readEviction(k, storage)) {
            return descriptor.getDefaultValue();
        }
        T result = storage.get(k);
        if (ttl != null && result != null) {
            ttl.recordRead(k);
        }
        return result != null ? result : descriptor.getDefaultValue();
    }

    @Override
    public void update(T value) throws IOException {
        TypedNamespaceAndKey k = backend.getTypedNamespaceAndKey();
        if (value == null) {
            clear();
        } else {
            if (ttl != null) {
                ttl.writeEviction(k, storage);
            }
            storage.put(k, value);
            if (ttl != null) {
                ttl.recordWrite(k);
            }
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
