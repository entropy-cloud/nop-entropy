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
import io.nop.stream.core.common.state.TtlContext;

class MemoryAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT>, Serializable, TtlAware {
    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;
    final AggregatingStateDescriptor<IN, ACC, OUT> descriptor;
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
