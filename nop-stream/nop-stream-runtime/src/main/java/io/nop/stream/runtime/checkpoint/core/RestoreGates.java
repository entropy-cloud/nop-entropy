/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.core;

import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * WI11 (plan 2432): shared manifest-first restore gate — the validation half
 * of a durable-state rebuild, executed before any raw checkpoint-row restore.
 * Both driver-face entry points run it: the JobCoordinator failover rebuild
 * (LEADERSHIP_GRANT) and the JobCoordinatorMain launch path.
 *
 * <p>Sequence (WI10 6a semantics, plan 2426): read the latest durable epoch
 * manifest (missing → nothing to validate, raw path proceeds unchanged);
 * fail closed when the manifest carries a model fingerprint that this
 * coordinator cannot prove compatible (a missing fingerprint source counts as
 * unprovable); advance the epoch counter past the MANIFEST epoch
 * (monotonic-only, idempotent — closes the shadow window where the manifest
 * epoch exceeds the latest readable data epoch). The caller's raw
 * {@code restoreFromCheckpoint()} still runs afterwards to populate the
 * in-memory view.
 *
 * <p>Semantics ruling (plan 2432): on the launch path a gate failure THROWS —
 * the launch fails loud rather than silently fresh-starting against an
 * unprovable durable state.
 */
public final class RestoreGates {

    private RestoreGates() {
    }

    /**
     * Runs the manifest-first validation gate. Throws
     * {@link StreamException}{@code (ERR_STREAM_INVALID_STATE)} when the gate
     * fails closed (fingerprint mismatch or unprovable compatibility).
     */
    public static void applyManifestGate(CheckpointCoordinator coordinator, String jobId) throws Exception {
        EpochManifest gateManifest = coordinator.restoreLatestEpochManifest();
        if (gateManifest == null) {
            return;
        }
        StreamModelFingerprint carried = gateManifest.getStreamModelFingerprint();
        if (carried != null) {
            StreamModelFingerprint current = coordinator.getCurrentFingerprint();
            if (current == null || !carried.isCompatibleWith(current)) {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                        "Restore gate rejected: durable epoch manifest "
                                + gateManifest.getEpochId() + " for job " + jobId
                                + " carries a stream-model fingerprint that this "
                                + "coordinator cannot prove compatible (carried="
                                + carried + ", current=" + current
                                + "). Restore via a fingerprint-capable entry "
                                + "or regenerate the job.");
            }
        }
        coordinator.advanceCheckpointIdCounterAfterRestore(gateManifest.getEpochId());
    }
}
