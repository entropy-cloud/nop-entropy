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
import java.util.Map;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

class MemoryReducingState<T> implements ReducingState<T>, Serializable {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final ReducingStateDescriptor<T> descriptor;
    final Map<TypedNamespaceAndKey, SimpleAccumulator<T>> storage = new HashMap<>();

    MemoryReducingState(MemoryKeyedStateBackend<?> backend, ReducingStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() throws Exception {
        SimpleAccumulator<T> acc = storage.get(backend.getTypedNamespaceAndKey());
        return acc != null ? acc.getLocalValue() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(T value) throws Exception {
        TypedNamespaceAndKey key = backend.getTypedNamespaceAndKey();
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
    }

    @Override
    public void clear() {
        storage.remove(backend.getTypedNamespaceAndKey());
    }
}
