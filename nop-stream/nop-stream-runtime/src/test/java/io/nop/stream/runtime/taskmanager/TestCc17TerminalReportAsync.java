/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.protocol.TaskDeploymentDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CC-17 (R5, plan 2435) directed tests:
 *
 * <ol>
 *   <li><b>shutdown-race typed rejection</b>: {@code deployTask} after the
 *       task executor shut down fails LOUD through the
 *       rejectDeploy→FAILED channel (typed {@code StreamException} carrying
 *       the executor-race cause) instead of a bare
 *       {@code RejectedExecutionException} that a one-way RPC would swallow;</li>
 *   <li><b>async terminal report</b>: a dying task's thread must exit without
 *       blocking on a slow coordinator RPC — the terminal report is delivered
 *       by the notifier thread after the RPC unblocks.</li>
 * </ol>
 */
class TestCc17TerminalReportAsync {

    @Test
    void deployAfterExecutorShutdownFailsTypedThroughRejectDeploy() {
        TaskManager tm = new TaskManager("tm-cc17", "rpc:tm-cc17", 1, null, null, "coord-topic");
        // White-box: close the task executor FIRST (stop() shutdown race).
        tm.forceTaskExecutorShutdownForTest();

        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor(
                "cc17-job", "vertex-a", 0, "tm-cc17", "attempt", 1, 1L, null, null, "/tmp/cc17");

        StreamExceptionHolder holder = new StreamExceptionHolder();
        try {
            tm.deployTask(descriptor, 1L);
        } catch (io.nop.stream.core.exceptions.StreamException e) {
            holder.value = e;
        }
        assertTrue(holder.value != null,
                "deploy after executor shutdown must fail loud (typed StreamException)");
        assertTrue(String.valueOf(holder.value).contains("executor shutdown race")
                        || String.valueOf(holder.value.getCause()).contains("RejectedExecution"),
                "typed failure must identify the executor race: " + holder.value);
    }

    @Test
    void terminalReportIsAsyncAndStillDelivered() throws Exception {
        CountDownLatch coordinatorSlow = new CountDownLatch(1);
        CountDownLatch reportArrived = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();

        TaskManager tm = new TaskManager("tm-cc17b", "rpc:tm-cc17b", 1, null, null, "coord-topic");
        tm.terminalReportExecutorForTest(() -> {
            try {
                coordinatorSlow.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            delivered.incrementAndGet();
            reportArrived.countDown();
        });

        // Simulate the dying task's submission (RunningTask finally path).
        long start = System.currentTimeMillis();
        tm.submitTerminalReport(() -> {
        });
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 1000L,
                "submitting the terminal report must not block on the slow coordinator (took "
                        + elapsed + "ms)");

        // The report is delivered once the coordinator RPC unblocks.
        coordinatorSlow.countDown();
        assertTrue(reportArrived.await(5, TimeUnit.SECONDS),
                "terminal report must still be delivered after the RPC unblocks");
        assertEquals(1, delivered.get());
    }

    /** Mutable exception carrier (test-local). */
    static final class StreamExceptionHolder {
        io.nop.stream.core.exceptions.StreamException value;
    }
}
