/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.SerializerFingerprint;

/**
 * Stage 33: per-state schema migration function.
 *
 * <p>When a state's restored {@link SerializerFingerprint} checksum differs from the
 * current descriptor's checksum, the state backend consults the registered
 * migration functions (via {@code StreamComponents}) before failing fast. If a
 * function is registered whose {@link #sourceFingerprint()} matches the restored
 * fingerprint and {@link #targetFingerprint()} matches the current fingerprint,
 * the backend performs a full scan: every stored value of the old type is read,
 * passed through {@link #migrate}, and written back as the new type.
 *
 * <p>This is an {@code @Internal} metadata-layer API. Operators and business code
 * never call it directly; advanced users register instances via
 * {@code StreamComponents.registerStateMigrationFunction} before job execution.
 *
 * <p><b>Migration timing</b>: migration runs synchronously on the first
 * {@code getState()} call during operator {@code initializeState} (before any
 * element is processed). Element-midstream lazy {@code getState()} migration is
 * not supported (would interleave migration with element processing and break
 * all-or-nothing semantics).
 *
 * <p><b>Idempotency invariant</b>: after a successful migration the affected
 * state's descriptor is updated to the new schema, so the next {@code getState()}
 * checksum comparison matches and migration is not repeated.
 *
 * <p><b>Accumulator-state caveat</b>: for {@code ReducingState} /
 * {@code AggregatingState} / internal appending variants, the migrated object is
 * the opaque accumulator (ACC). Correctness of the migrated ACC depends on the
 * user's {@code StateMigrationFunction} implementation and
 * {@code AggregateFunction} / {@code ReduceFunction} contract; an incorrect
 * migration produces silently corrupt state rather than a no-op.
 *
 * @param <Old> source value type (matches {@link #sourceFingerprint()})
 * @param <New> target value type (matches {@link #targetFingerprint()})
 */
@Internal
public interface StateMigrationFunction<Old, New> {

    /**
     * Convert a single old-schema value to the new schema.
     *
     * <p>Must be deterministic and free of side effects on external state: the
     * state backend calls this once per stored entry during a full scan, and on
     * crash-retry the scan restarts from the previous successful checkpoint (the
     * pre-migration state) rather than from a partial migration.
     *
     * @param oldValue the value stored under the old schema
     * @return the equivalent value under the new schema; {@code null} is permitted
     *         and clears the entry
     */
    New migrate(Old oldValue);

    /**
     * Fingerprint of the source schema this function migrates from. Must equal
     * the restored state's fingerprint (as computed by
     * {@link StateSchemaResolver#fromDescriptor}) for the backend to select this
     * function.
     */
    SerializerFingerprint sourceFingerprint();

    /**
     * Fingerprint of the target schema this function migrates to. Must equal the
     * current live descriptor's fingerprint for the backend to select this
     * function.
     */
    SerializerFingerprint targetFingerprint();
}
