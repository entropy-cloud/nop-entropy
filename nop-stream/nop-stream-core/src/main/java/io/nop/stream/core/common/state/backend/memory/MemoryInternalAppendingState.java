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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;

class MemoryInternalAppendingState<K, N, IN, ACC> extends AbstractMemoryState
        implements InternalAppendingState<K, N, IN, ACC, ACC>, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    ReducingStateDescriptor<IN> descriptor;
    private transient SimpleAccumulator<IN> accumulator;
    final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

    private transient N currentNamespace;

    @SuppressWarnings("unchecked")
    MemoryInternalAppendingState(MemoryKeyedStateBackend<?> backend,
            ReducingStateDescriptor<IN> descriptor) {
        super(backend);
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

    @Override
    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        super.rebind(newBackend);
        if (this.accumulator == null) {
            this.accumulator = createAccumulator();
        }
    }

    private TypedNamespaceAndKey storageKey() {
        if (currentNamespace == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
        }
        return new TypedNamespaceAndKey(currentNamespace, backend.routeKey(backend.getCurrentKey()));
    }

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33 accumulator-state migration surface. The stored object is an
     * opaque ACC (the reduce accumulator value); the migration passes it to
     * the user's function (single point:
     * {@link AbstractMemoryState#applyWholeStorageMigration}). Correctness is
     * user responsibility; the platform does not validate accumulator-migration
     * semantics.
     */
    @Override
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        applyWholeStorageMigration(storage, migration);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (ReducingStateDescriptor<IN>) newDescriptor;
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
        TypedNamespaceAndKey key = storageKey();
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
        TypedNamespaceAndKey key = storageKey();
        storage.put(key, accumulator);
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public ACC get() throws IOException {
        try {
            return getAccumulator();
        } catch (StreamException e) {
            // S-5 (2026-09-01 core audit): preserve module-convention error codes.
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to get accumulator", e);
        }
    }

    @Override
    public void add(IN value) throws IOException {
        TypedNamespaceAndKey key = storageKey();
        ACC current;
        if (ttl != null) {
            ttl.writeEviction(key, storage);
        }
        current = storage.get(key);
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
        @SuppressWarnings("unchecked")
        ACC stored = (ACC) localValue;
        storage.put(key, stored);
        if (ttl != null) {
            ttl.recordWrite(key);
        }
    }

    @Override
    public void clear() {
        TypedNamespaceAndKey key = storageKey();
        storage.remove(key);
        if (ttl != null) {
            ttl.onClear(key);
        }
    }
}
