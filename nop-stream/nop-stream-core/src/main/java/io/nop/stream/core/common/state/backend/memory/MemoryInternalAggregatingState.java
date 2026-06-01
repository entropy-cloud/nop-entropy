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

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

class MemoryInternalAggregatingState<K, N, IN, ACC, OUT>
        implements InternalAppendingState<K, N, IN, ACC, OUT>, Serializable {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final AggregatingStateDescriptor<IN, ACC, OUT> descriptor;
    final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

    private transient N currentNamespace;

    MemoryInternalAggregatingState(MemoryKeyedStateBackend<?> backend,
            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
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
    public OUT get() throws IOException {
        try {
            TypedNamespaceAndKey key = getStorageKey();
            ACC accumulator = storage.get(key);
            if (accumulator == null) {
                return null;
            }
            return descriptor.getAggregateFunction().getResult(accumulator);
        } catch (Exception e) {
            throw new IOException("Failed to get aggregated state", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(IN value) throws IOException {
        try {
            TypedNamespaceAndKey key = getStorageKey();
            AggregateFunction<IN, ACC, OUT> aggFn = descriptor.getAggregateFunction();
            ACC accumulator = storage.get(key);
            if (accumulator == null) {
                accumulator = aggFn.createAccumulator();
            }
            accumulator = aggFn.add(value, accumulator);
            storage.put(key, accumulator);
        } catch (Exception e) {
            throw new IOException("Failed to add to aggregated state", e);
        }
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
