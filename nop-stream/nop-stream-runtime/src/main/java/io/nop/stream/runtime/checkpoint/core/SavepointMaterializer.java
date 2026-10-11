/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.core;

import java.util.concurrent.TimeUnit;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.storage.ICheckpointStorage;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;

/**
 * WI10 (plan 2426, route (a)): shared savepoint materialization — the
 * driver-face-agnostic half of a SAVEPOINT-type checkpoint trigger. Lifted
 * verbatim from the local face ({@code GraphModelCheckpointExecutor#
 * triggerSavepoint}, C2/C3 bounded-retry semantics included): trigger with
 * bounded retry, fan the barrier into live invokables (skipping an
 * already-assembled terminal epoch), await completion, materialize per-subtask
 * KeyGroup ownership and store the completed checkpoint, returning the
 * durable path.
 *
 * <p>The barrier fan-out and KeyGroup-ownership steps are driver-face
 * mechanics injected by the caller (they touch the face's invokables and
 * execution plan); the trigger budget here is the termination budget
 * ({@link CheckpointConfig#resolveTerminationCheckpointTimeout()}), not the
 * general checkpoint timeout. Consumers: the local face delegates here
 * byte-for-byte (zero behavior change); the distributed face's
 * EXPORT_SAVEPOINT keeps its own product contract (no path return, job keeps
 * running — WI9 §4.3 4a) and evaluates adoption in WI11.
 */
public final class SavepointMaterializer {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(SavepointMaterializer.class);

    /** Consumer variant that admits the checked exceptions of the face mechanics. */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    private SavepointMaterializer() {
    }

    /**
     * Triggers, awaits and materializes one SAVEPOINT epoch.
     *
     * @param barrierFanout       fans the pending checkpoint's barrier into the
     *                            face's live invokables (skipped when the epoch
     *                            assembled inline from terminal states)
     * @param ownershipMaterializer materializes per-subtask KeyGroupRange
     *                            ownership onto the completed checkpoint
     * @return the durable savepoint path, or {@code null} when the bounded
     *         trigger short-circuited as NO_TASKS_TO_ACK (all-terminal epoch
     *         with nothing to snapshot — C3 semantics)
     */
    public static String materialize(CheckpointCoordinator coordinator,
                                     ThrowingConsumer<PendingCheckpoint> barrierFanout,
                                     ThrowingConsumer<CompletedCheckpoint> ownershipMaterializer,
                                     ICheckpointStorage storage,
                                     CheckpointConfig checkpointConfig) throws Exception {
        // C2 (plan 369): bounded-retry trigger. A collision with an in-flight
        // periodic checkpoint (maxConcurrent=1) no longer silently skips the
        // savepoint — the trigger retries within the termination budget. A
        // NO_TASKS_TO_ACK rejection short-circuits as success (null return:
        // with the C3 participant contraction every task is terminal, so
        // there is nothing to snapshot). A final trigger failure throws
        // (loud) instead of returning a null savepoint path.
        PendingCheckpoint savepointPending = coordinator.triggerCheckpointBounded(
                CheckpointType.SAVEPOINT, checkpointConfig.resolveTerminationCheckpointTimeout());
        String savepointPath = null;
        if (savepointPending != null) {
            // C2×C3: an all-terminal epoch is completed inline by the
            // coordinator from inherited states — no live invokable left to
            // fan a barrier into; the fan-out itself is contained, but
            // skipping it for an already-done epoch avoids misleading
            // per-task barrier warnings.
            if (!savepointPending.getCompletableFuture().isDone()) {
                barrierFanout.accept(savepointPending);
            } else {
                LOG.info("Savepoint {} assembled from terminal states (no live participants); "
                        + "barrier fan-out skipped", savepointPending.getCheckpointId());
            }

            // The AWAIT budget stays the general checkpoint timeout (wait on a
            // durable result, not a trigger decision — budgets are separate).
            CompletedCheckpoint completed = (CompletedCheckpoint) savepointPending.getCompletableFuture()
                    .get(checkpointConfig.getCheckpointTimeout(), TimeUnit.MILLISECONDS);
            if (completed != null) {
                // Materialize per-subtask KeyGroupRange ownership so the
                // savepoint records which subtask owned which range (the restore
                // path can then route keyed state on a parallelism change).
                ownershipMaterializer.accept(completed);
                savepointPath = storage.storeCheckPoint(completed);
            }
        }
        return savepointPath;
    }
}
