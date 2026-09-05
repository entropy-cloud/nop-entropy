/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.util.HashMap;
import java.util.Map;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;

class MemoryReducingState<T> extends AbstractMemoryState implements ReducingState<T>, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    ReducingStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, SimpleAccumulator<T>> storage = new HashMap<>();

    MemoryReducingState(MemoryKeyedStateBackend<?> backend, ReducingStateDescriptor<T> descriptor) {
        super(backend);
        this.descriptor = descriptor;
    }

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33 accumulator-state migration surface. The stored object is an
     * opaque {@link SimpleAccumulator}; the migration passes the whole
     * accumulator to the user's function (single point:
     * {@link AbstractMemoryState#applyWholeStorageMigration}). Correctness of
     * the migrated accumulator is the user's responsibility (a wrong migration
     * produces silently corrupt state, not a no-op). The platform does not
     * validate accumulator-migration semantics.
     */
    @Override
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        applyWholeStorageMigration(storage, migration);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (ReducingStateDescriptor<T>) newDescriptor;
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
        if (ttl != null) {
            ttl.writeEviction(key, storage);
        }
        SimpleAccumulator<T> acc = storage.get(key);
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
