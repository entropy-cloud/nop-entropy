/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;

class MemoryReducingState<T> implements ReducingState<T>, Serializable, TtlAware, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    ReducingStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, SimpleAccumulator<T>> storage = new HashMap<>();

    TtlContext<TypedNamespaceAndKey> ttl;

    MemoryReducingState(MemoryKeyedStateBackend<?> backend, ReducingStateDescriptor<T> descriptor) {
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

    /**
     * Stage 33 accumulator-state migration surface. The stored object is an
     * opaque {@link SimpleAccumulator}; this method passes the whole accumulator
     * to the user's migration function. Correctness of the migrated accumulator
     * is the user's responsibility (a wrong migration produces silently corrupt
     * state, not a no-op). The platform does not validate accumulator-migration
     * semantics.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        StateMigrationFunction<Object, Object> fn = (StateMigrationFunction<Object, Object>) migration;
        Map<TypedNamespaceAndKey, SimpleAccumulator<T>> migrated = new LinkedHashMap<>();
        for (Map.Entry<TypedNamespaceAndKey, SimpleAccumulator<T>> e : storage.entrySet()) {
            SimpleAccumulator<T> old = e.getValue();
            if (old == null) {
                migrated.put(e.getKey(), null);
            } else {
                migrated.put(e.getKey(), (SimpleAccumulator<T>) fn.migrate(old));
            }
        }
        storage.clear();
        storage.putAll(migrated);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (ReducingStateDescriptor<T>) newDescriptor;
    }

    @Override
    public void bindTtl(TtlContext<TypedNamespaceAndKey> ctx) {
        this.ttl = ctx;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() throws Exception {
        TypedNamespaceAndKey key = backend.getTypedNamespaceAndKey();
        if (ttl != null && ttl.readEviction(key, storage)) {
            return null;
        }
        SimpleAccumulator<T> acc = storage.get(key);
        if (ttl != null && acc != null) {
            ttl.recordRead(key);
        }
        return acc != null ? acc.getLocalValue() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(T value) throws Exception {
        TypedNamespaceAndKey key = backend.getTypedNamespaceAndKey();
        SimpleAccumulator<T> acc;
        if (ttl != null) {
            ttl.writeEviction(key, storage);
            acc = storage.get(key);
        } else {
            acc = storage.get(key);
        }
        if (acc == null) {
            try {
                acc = descriptor.getAccumulatorType().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new StreamException(ERR_STREAM_ACCUMULATOR_CREATE_FAILED, e).param(ARG_DETAIL, "ReducingState");
            }
        }
        acc.add(value);
        storage.put(key, acc);
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public void clear() {
        TypedNamespaceAndKey key = backend.getTypedNamespaceAndKey();
        storage.remove(key);
        if (ttl != null) {
            ttl.onClear(key);
        }
    }
}
