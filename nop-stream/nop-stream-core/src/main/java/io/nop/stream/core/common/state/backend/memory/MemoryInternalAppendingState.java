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

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

class MemoryInternalAppendingState<K, N, IN, ACC>
        implements InternalAppendingState<K, N, IN, ACC, ACC>, Serializable {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final ReducingStateDescriptor<IN> descriptor;
    private transient SimpleAccumulator<IN> accumulator;
    final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

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
        return storage.get(key);
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
        ACC current = storage.get(key);
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
        storage.put(key, (ACC) accumulator.getLocalValue());
    }

    @Override
    public void clear() {
        storage.remove(getStorageKey());
    }

    private TypedNamespaceAndKey getStorageKey() {
        if (currentNamespace == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
        }
        return new TypedNamespaceAndKey(currentNamespace, backend.routeKey(backend.getCurrentKey()));
    }
}
