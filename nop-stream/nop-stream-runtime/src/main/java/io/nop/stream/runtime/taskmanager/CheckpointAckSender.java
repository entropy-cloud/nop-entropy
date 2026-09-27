/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;

/**
 * Sends checkpoint ACKs from a TaskManager to the coordinator (control topic
 * / RPC path), with a bounded retry for transient failures. The ACK is the
 * completion-critical message of the checkpoint protocol: losing it to a
 * transient backend hiccup previously forced the coordinator to wait for
 * the full checkpoint timeout and abort the epoch. Retries are attempted
 * inline with a short backoff; when the budget is exhausted the failure is
 * logged and counted (observable) and the coordinator-side checkpoint
 * timeout remains the subsuming safety net.
 */
class CheckpointAckSender {

    private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

    /** Bounded retry budget for checkpoint ACK sends. */
    static final int ACK_SEND_ATTEMPTS = 3;

    /** Linear backoff base (ms) between ACK send attempts. */
    static final long ACK_RETRY_BACKOFF_MS = 200L;

    private final TaskManager owner;

    CheckpointAckSender(TaskManager owner) {
        this.owner = owner;
    }

    /**
     * Sends a checkpoint ACK to the coordinator on behalf of the owning
     * TaskManager.
     *
     * @param checkpointId the checkpoint ID
     * @param snapshot     the task state snapshot
     */
    void send(long checkpointId, TaskStateSnapshot snapshot) {
        CheckpointAckMessage ack = new CheckpointAckMessage(
                snapshot.getTaskLocation(),
                checkpointId,
                snapshot,
                owner.currentFencingEpoch.get());

        if (owner.coordinatorRpcService == null) {
            LOG.error("Failed to send checkpoint ACK for checkpoint {}: no coordinator RPC service available",
                    checkpointId);
            if (owner.nodeMetrics != null) {
                owner.nodeMetrics.ackSendFailed();
            }
            return;
        }

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= ACK_SEND_ATTEMPTS; attempt++) {
            try {
                owner.coordinatorRpcService.receiveCheckpointAck(ack);
                if (attempt > 1) {
                    LOG.info("Checkpoint ACK for checkpoint {} delivered on attempt {}/{}",
                            checkpointId, attempt, ACK_SEND_ATTEMPTS);
                }
                LOG.debug("Sent checkpoint ACK for checkpoint {} from {}",
                        checkpointId, snapshot.getTaskLocation());
                return;
            } catch (Exception e) {
                lastFailure = e;
                LOG.warn("Failed to send checkpoint ACK for checkpoint {} (attempt {}/{})",
                        checkpointId, attempt, ACK_SEND_ATTEMPTS, e);
                if (attempt < ACK_SEND_ATTEMPTS) {
                    try {
                        Thread.sleep(ACK_RETRY_BACKOFF_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        // #24 — no silent skip: budget exhausted, failure logged and counted.
        // The coordinator-side checkpoint timeout aborts the epoch as the
        // subsuming safety net.
        LOG.error("Failed to send checkpoint ACK for checkpoint {} after {} attempts",
                checkpointId, ACK_SEND_ATTEMPTS, lastFailure);
        if (owner.nodeMetrics != null) {
            owner.nodeMetrics.ackSendFailed();
        }
    }
}
