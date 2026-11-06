/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.42 R4-02 W-03 (1.1-C5-006, C10-018, 红③): bounded failed-commit ledger +
 * escalation exit. A participant whose {@code finishCommit} keeps throwing (while
 * the task itself never fails) previously accumulated one ledger entry per
 * checkpoint epoch forever (~2880 entries/day at the default 30s cadence) with no
 * retry-round bound and no exit. Post-fix: consecutive retry failures increment a
 * per-participant counter; at N consecutive failures the participant is
 * deregistered ({@code removeParticipant}) and the escalation handler fires ONCE
 * with a typed error, after which the existing stillFailing-empty removal path
 * ({@code failedCommitParticipants.remove} + successMap cleanup) terminalizes
 * every ledger entry — the ledger stays bounded (≤ N entries) and the participant
 * leaves the coordinator.
 *
 * <p>Zero-regression faces verified alongside (adjudication A.2): the abort-gated
 * successMap semantics and the CLEARED recovery signal are untouched (this pin
 * drives the same notify/retry cycle the recovery path uses — a stillFailing-empty
 * round still removes entries and fires the existing CLEARED transition).
 */
class TestCheckpointCommitRetryEscalationR402 {

    @TempDir
    Path tempDir;

    private CheckpointCoordinator coordinator;

    /** Participant stub whose finishCommit always throws (the defect driver). */
    private static final class FailingParticipant implements CheckpointParticipant {
        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return TaskStateSnapshot.empty(new io.nop.stream.core.checkpoint.TaskLocation("r402", "pipeline-0", "v-fail", 1));
        }

        @Override
        public void prepareCommit(long epochId) {
            // no-op
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
            throw new IllegalStateException("finishCommit keeps failing (red③ driver)");
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
            // no-op
        }
    }

    @BeforeEach
    void setUp() {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .checkpointInterval(1000L)
                .checkpointTimeout(5000L)
                .minPause(0L)
                .maxConcurrentCheckpoints(1)
                .maxRetainedCheckpoints(3)
                .asyncSnapshotEnabled(false)
                .build();
        coordinator = new CheckpointCoordinator("r402-escalation-job", "pipeline-0",
                new CheckpointIDCounter(), storage, config);
        // S3 seam: a small threshold makes the escalation deterministic within a
        // handful of rounds (production default stays 6 —
        // nop.stream.checkpoint.commit-retry-escalation-threshold).
        coordinator.setCommitRetryEscalationThresholdForTest(3);
    }

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void continuouslyFailingParticipantIsBoundedAndEscalated() {
        FailingParticipant failing = new FailingParticipant();
        coordinator.addParticipant(failing);

        List<Throwable> escalations = new ArrayList<>();
        coordinator.setCommitRetryEscalationHandler(escalations::add);

        // M = N + 2 rounds of the "complete → retry" cycle (the commit-fanout
        // task's real sequence), driven synchronously — zero latch, zero async.
        int threshold = 3;
        int rounds = threshold + 2;
        for (int epoch = 1; epoch <= rounds; epoch++) {
            coordinator.notifyParticipantsFinishCommit(epoch, true);
            coordinator.retryFailedCommits();

            // Per-round bounded-ledger assertion (not just the terminal state —
            // guards against an exit-only mutation that still lets entries pile up).
            assertTrue(coordinator.failedCommitParticipants.size() <= threshold,
                    "failed-commit ledger must stay bounded by the escalation threshold (round "
                            + epoch + "): size=" + coordinator.failedCommitParticipants.size());
        }

        // After M rounds: the participant was deregistered at round N ...
        assertFalse(coordinator.getParticipants().contains(failing),
                "continuously failing participant must be deregistered by the escalation exit");
        // ... its ledger entries are terminalized via the existing removal path ...
        assertTrue(coordinator.failedCommitParticipants.isEmpty(),
                "failed-commit ledger must be empty after the escalation exit drained it: "
                        + coordinator.failedCommitParticipants);
        // ... and the typed escalation fired exactly once (one-shot terminal event,
        // not a per-retry WARN — adjudication A.2 / red line 9).
        assertEquals(1, escalations.size(),
                "escalation handler must fire exactly once, got: " + escalations.size());
    }

    /**
     * Recovery face zero-regression: a participant that fails once and then
     * recovers keeps the PRE-EXISTING semantics — the entry is removed by the
     * stillFailing-empty path and the failure counter resets (no escalation).
     */
    @Test
    void recoveredParticipantKeepsLegacyRemovalSemantics() {
        AtomicInteger calls = new AtomicInteger();
        CheckpointParticipant flaky = new CheckpointParticipant() {
            @Override
            public TaskStateSnapshot saveState(long epochId) {
                return TaskStateSnapshot.empty(new io.nop.stream.core.checkpoint.TaskLocation("r402", "pipeline-0", "v-fail", 1));
            }

            @Override
            public void prepareCommit(long epochId) {
                // no-op
            }

            @Override
            public void finishCommit(long epochId, boolean success) {
                // Fails the initial notification AND the first retry; recovers on
                // the second retry (the legacy stillFailing-empty removal path).
                if (calls.incrementAndGet() <= 2) {
                    throw new IllegalStateException("commit notification failing (transient)");
                }
            }

            @Override
            public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
                // no-op
            }
        };
        coordinator.addParticipant(flaky);

        List<Throwable> escalations = new ArrayList<>();
        coordinator.setCommitRetryEscalationHandler(escalations::add);

        coordinator.notifyParticipantsFinishCommit(1, true);
        coordinator.retryFailedCommits();
        assertFalse(coordinator.failedCommitParticipants.isEmpty(),
                "first failure must enter the retry ledger");
        assertEquals(0, escalations.size(), "a single failure must not escalate");

        // Retry succeeds → the legacy removal path terminalizes the entry.
        coordinator.retryFailedCommits();
        assertTrue(coordinator.failedCommitParticipants.isEmpty(),
                "recovered participant's entry must be removed by the legacy stillFailing-empty path");
        assertTrue(coordinator.getParticipants().contains(flaky),
                "a recovered participant stays registered");
        assertEquals(0, escalations.size(),
                "recovery must not fire the escalation handler");
    }
}
