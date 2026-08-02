/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;

/**
 * Stage 33: internal contract implemented by every concrete keyed-state class
 * (e.g. {@code MemoryValueState}, {@code RocksDBValueState}, ...) so that the
 * enclosing state backend can drive a full-scan schema migration when the
 * restored fingerprint differs from the current descriptor's fingerprint.
 *
 * <p>The backend's {@code verifySchemaCompatibility} looks up a registered
 * {@link StateMigrationFunction} and, on a hit, calls
 * {@link #applyMigration} to transform every stored value, then
 * {@link #replaceDescriptor} so the next {@code getState()} checksum comparison
 * matches (idempotency invariant).
 *
 * <p><b>Atomicity</b>: implementations must perform the full scan synchronously
 * under the backend's single-threaded (mailbox) assumption. A mid-scan exception
 * must propagate to the caller — it leaves the backend in an unusable state
 * rather than persisting a half-old/half-new mix (no "migration-in-progress"
 * marker is supported).
 *
 * <p><b>Accumulator-state caveat</b>: for {@code ReducingState} /
 * {@code AggregatingState} / internal appending variants, the migrated object
 * is the opaque accumulator (ACC). Correctness depends entirely on the user's
 * {@code StateMigrationFunction} implementation; the platform does not verify
 * accumulator-migration semantics (see {@code checkpoint-design.md} §8.4.1).
 */
@Internal
public interface MigratableKeyedState {

    /**
     * Current descriptor held by this state object. Before migration this is the
     * restored (old) descriptor; after {@link #replaceDescriptor} it is the new
     * one.
     */
    StateDescriptor<?> getMigrationDescriptor();

    /**
     * Iterate every stored entry, pass each stored value through
     * {@link StateMigrationFunction#migrate}, and write the result back. The
     * state object's storage layout (scalar value, inner map, list, opaque ACC)
     * determines what counts as a "stored value":
     *
     * <ul>
     *   <li>{@code ValueState}: the value itself</li>
     *   <li>{@code MapState}: each value in the inner map (map keys are not
     *       migrated)</li>
     *   <li>{@code ListState}/{@code InternalListState}: each element of the
     *       inner list</li>
     *   <li>{@code ReducingState}/{@code AggregatingState}/{@code InternalAppendingState}:
     *       the opaque accumulator object (correctness is user responsibility)</li>
     * </ul>
     *
     * <p>Implementations must NOT swallow exceptions from
     * {@code migrate(...)}; any exception propagates to the caller and surfaces
     * as a failed migration (no silent skip / no-op).
     *
     * @param migration the resolved migration function selected by the backend
     */
    void applyMigration(StateMigrationFunction<?, ?> migration);

    /**
     * Swap this state object's descriptor reference to {@code newDescriptor}.
     * Called by the backend after a successful {@link #applyMigration} so that
     * the next {@code getState()} checksum comparison matches and migration is
     * not repeated.
     *
     * @param newDescriptor the current (post-migration) descriptor
     */
    void replaceDescriptor(StateDescriptor<?> newDescriptor);
}
