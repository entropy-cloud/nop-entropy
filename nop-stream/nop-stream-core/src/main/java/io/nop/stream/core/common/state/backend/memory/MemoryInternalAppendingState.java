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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;

class MemoryInternalAppendingState<K, N, IN, ACC>
        implements InternalAppendingState<K, N, IN, ACC, ACC>, Serializable, TtlAware, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    ReducingStateDescriptor<IN> descriptor;
    private transient SimpleAccumulator<IN> accumulator;
    final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

    TtlContext<TypedNamespaceAndKey> ttl;

    private transient N currentNamespace;

    @SuppressWarnings("unchecked")
    MemoryInternalAppendingState(MemoryKeyedStateBackend<?> backend,
            ReducingStateDescriptor<IN> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
        this.accumulator = createAccumulator();
    }

    private SimpleAccumulator<IN> createAccumulator() {
        try {
            return descriptor.getAccumulatorType().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_ACCUMULATOR_CREATE_FAILED, e);
        }
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
        if (this.accumulator == null) {
            this.accumulator = createAccumulator();
        }
    }

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33 accumulator-state migration surface. The stored object is an
     * opaque ACC (the reduce accumulator value); this method passes it to the
     * user's migration function. Correctness is user responsibility; the
     * platform does not validate accumulator-migration semantics.
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
        this.descriptor = (ReducingStateDescriptor<IN>) newDescriptor;
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
    public ACC getAccumulator() throws Exception {
        TypedNamespaceAndKey key = getStorageKey();
        if (ttl != null && ttl.readEviction(key, storage)) {
            return null;
        }
        ACC acc = storage.get(key);
        if (ttl != null && acc != null) {
            ttl.recordRead(key);
        }
        return acc;
    }

    @Override
    public void setAccumulator(ACC accumulator) throws Exception {
        TypedNamespaceAndKey key = getStorageKey();
        storage.put(key, accumulator);
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public ACC get() throws IOException {
        try {
            return getAccumulator();
        } catch (Exception e) {
            throw new IOException("Failed to get accumulator", e);
        }
    }

    @Override
    public void add(IN value) throws IOException {
        TypedNamespaceAndKey key = getStorageKey();
        @SuppressWarnings("unchecked")
        ACC current;
        if (ttl != null) {
            ttl.writeEviction(key, storage);
            current = storage.get(key);
        } else {
            current = storage.get(key);
        }
        if (current != null && !descriptor.getValueType().isInstance(current)) {
            throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                    .param(ARG_EXPECTED_TYPE, descriptor.getValueType().getName())
                    .param(ARG_ACTUAL_TYPE, current.getClass().getName());
        }
        accumulator.resetLocal();
        if (current != null) {
            accumulator.add((IN) current);
        }
        accumulator.add(value);
        Object localValue = accumulator.getLocalValue();
        if (localValue instanceof List) {
            localValue = new ArrayList<>((List<?>) localValue);
        }
        storage.put(key, (ACC) localValue);
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
