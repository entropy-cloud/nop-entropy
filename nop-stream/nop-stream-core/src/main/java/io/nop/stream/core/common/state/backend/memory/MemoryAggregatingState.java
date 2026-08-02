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

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

class MemoryAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT>, Serializable, TtlAware, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    AggregatingStateDescriptor<IN, ACC, OUT> descriptor;
    final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

    TtlContext<TypedNamespaceAndKey> ttl;

    MemoryAggregatingState(MemoryKeyedStateBackend<?> backend, AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
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
     * opaque ACC; this method passes it to the user's migration function.
     * Correctness of the migrated ACC is the user's responsibility (a wrong
     * migration produces silently corrupt state, not a no-op). The platform
     * does not validate accumulator-migration semantics.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        StateMigrationFunction<Object, Object> fn = (StateMigrationFunction<Object, Object>) migration;
        Map<TypedNamespaceAndKey, ACC> migrated = new LinkedHashMap<>();
        for (Map.Entry<TypedNamespaceAndKey, ACC> e : storage.entrySet()) {
            ACC old = e.getValue();
            if (old == null) {
                migrated.put(e.getKey(), null);
            } else {
                migrated.put(e.getKey(), (ACC) fn.migrate(old));
            }
        }
        storage.clear();
        storage.putAll(migrated);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (AggregatingStateDescriptor<IN, ACC, OUT>) newDescriptor;
    }

    @Override
    public void bindTtl(TtlContext<TypedNamespaceAndKey> ctx) {
        this.ttl = ctx;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OUT get() throws Exception {
        TypedNamespaceAndKey key = backend.getTypedNamespaceAndKey();
        if (ttl != null && ttl.readEviction(key, storage)) {
            return null;
        }
        ACC accumulator = storage.get(key);
        if (ttl != null && accumulator != null) {
            ttl.recordRead(key);
        }
        if (accumulator == null) {
            return null;
        }
        return descriptor.getAggregateFunction().getResult(accumulator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(IN value) throws Exception {
        TypedNamespaceAndKey key = backend.getTypedNamespaceAndKey();
        AggregateFunction<IN, ACC, OUT> aggFn = descriptor.getAggregateFunction();
        ACC accumulator;
        if (ttl != null) {
            ttl.writeEviction(key, storage);
            accumulator = storage.get(key);
        } else {
            accumulator = storage.get(key);
        }
        if (accumulator == null) {
            accumulator = aggFn.createAccumulator();
        }
        accumulator = aggFn.add(value, accumulator);
        storage.put(key, accumulator);
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
