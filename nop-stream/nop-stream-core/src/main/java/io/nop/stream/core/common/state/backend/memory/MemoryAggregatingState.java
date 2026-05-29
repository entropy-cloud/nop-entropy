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

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;

class MemoryAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT>, Serializable {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final AggregatingStateDescriptor<IN, ACC, OUT> descriptor;
    final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

    MemoryAggregatingState(MemoryKeyedStateBackend<?> backend, AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        this.backend = backend;
        this.descriptor = descriptor;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OUT get() throws Exception {
        ACC accumulator = storage.get(backend.getTypedNamespaceAndKey());
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
        ACC accumulator = storage.get(key);
        if (accumulator == null) {
            accumulator = aggFn.createAccumulator();
        }
        accumulator = aggFn.add(value, accumulator);
        storage.put(key, accumulator);
    }

    @Override
    public void clear() {
        storage.remove(backend.getTypedNamespaceAndKey());
    }
}
