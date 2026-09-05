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

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

/**
 * item 21 D-2 convergence: the public/internal aggregating pair shares this
 * single implementation. {@link MemoryInternalAggregatingState} only overrides
 * {@link #storageKey()} (namespaced key with fail-fast guard) and adds the
 * raw-accumulator accessors.
 */
class MemoryAggregatingState<IN, ACC, OUT> extends AbstractMemoryState
        implements AggregatingState<IN, OUT>, MigratableKeyedState {
    private static final long serialVersionUID = 1L;

    AggregatingStateDescriptor<IN, ACC, OUT> descriptor;
    final Map<TypedNamespaceAndKey, ACC> storage = new HashMap<>();

    MemoryAggregatingState(MemoryKeyedStateBackend<?> backend, AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        super(backend);
        this.descriptor = descriptor;
    }

    /** Storage key for the current access; internal subclasses override with their own namespace. */
    protected TypedNamespaceAndKey storageKey() {
        return backend.getTypedNamespaceAndKey();
    }

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33 accumulator-state migration surface. The stored object is an
     * opaque ACC; the migration passes it to the user's function (single
     * point: {@link AbstractMemoryState#applyWholeStorageMigration}).
     * Correctness of the migrated ACC is the user's responsibility (a wrong
     * migration produces silently corrupt state, not a no-op). The platform
     * does not validate accumulator-migration semantics.
     */
    @Override
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        applyWholeStorageMigration(storage, migration);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (AggregatingStateDescriptor<IN, ACC, OUT>) newDescriptor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OUT get() throws Exception {
        TypedNamespaceAndKey key = storageKey();
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
        TypedNamespaceAndKey key = storageKey();
        AggregateFunction<IN, ACC, OUT> aggFn = descriptor.getAggregateFunction();
        if (ttl != null) {
            ttl.writeEviction(key, storage);
        }
        ACC accumulator = storage.get(key);
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
        TypedNamespaceAndKey key = storageKey();
        storage.remove(key);
        if (ttl != null) {
            ttl.onClear(key);
        }
    }
}
