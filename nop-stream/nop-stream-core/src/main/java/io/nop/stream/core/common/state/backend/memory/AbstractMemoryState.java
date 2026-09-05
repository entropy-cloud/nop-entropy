/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;

/**
 * item 21 D-2 convergence: shared base of every memory keyed-state class.
 * Holds the backend reference, the TTL sidecar slot, rebind, and the
 * TTL-aware surface, so the per-family classes only carry their storage shape
 * and state semantics. The whole-storage migration (Reducing / Aggregating /
 * InternalAggregating / InternalAppending previously carried four verbatim
 * copies) lives here as the single {@link #applyWholeStorageMigration}
 * implementation.
 */
abstract class AbstractMemoryState implements Serializable, TtlAware {

    private static final long serialVersionUID = 1L;

    MemoryKeyedStateBackend<?> backend;

    TtlContext<TypedNamespaceAndKey> ttl;

    AbstractMemoryState(MemoryKeyedStateBackend<?> backend) {
        this.backend = backend;
    }

    void rebind(MemoryKeyedStateBackend<?> newBackend) {
        this.backend = newBackend;
    }

    @Override
    public void bindTtl(TtlContext<TypedNamespaceAndKey> ctx) {
        this.ttl = ctx;
    }

    @Override
    public TtlContext<TypedNamespaceAndKey> ttlContext() {
        return ttl;
    }

    /**
     * Single point for the whole-storage migration shape: read every stored
     * value, pass it through {@code migrate}, write back under the same key.
     * Null values map to null (key preserved). The migrated object's type
     * correctness is the user's responsibility (Stage 33 accumulator-migration
     * caveat, see {@code MigratableKeyedState}).
     */
    @SuppressWarnings("unchecked")
    static <V> void applyWholeStorageMigration(Map<TypedNamespaceAndKey, V> storage,
                                               StateMigrationFunction<?, ?> migration) {
        StateMigrationFunction<Object, Object> fn = (StateMigrationFunction<Object, Object>) migration;
        Map<TypedNamespaceAndKey, V> migrated = new LinkedHashMap<>();
        for (Map.Entry<TypedNamespaceAndKey, V> e : storage.entrySet()) {
            V old = e.getValue();
            migrated.put(e.getKey(), old == null ? null : (V) fn.migrate(old));
        }
        storage.clear();
        storage.putAll(migrated);
    }
}
