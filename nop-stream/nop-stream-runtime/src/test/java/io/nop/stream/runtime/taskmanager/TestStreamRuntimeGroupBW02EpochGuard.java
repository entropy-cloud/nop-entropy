/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.protocol.TaskStatusReport;
import io.nop.stream.runtime.testsupport.TestAwait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA Phase 8.41 R4-01 (plan 2534) 1.1 unit, Group B — red① driver (C10-010,
 * W-02) + the displacement structure pin.
 *
 * <p>Adjudicated shape (design ruling B.1 + review R1 Blocker-1 re-form):
 *
 * <ul>
 *   <li><b>Variant b (main driver)</b> — same-generation duplicate assignment:
 *       {@code updateFencingToken(N)} then the same-key {@code receiveAssignment(gen-N)}
 *       twice. Pre-fix the duplicate hit the {@code containsKey} branch and was
 *       silently ignored (zero coordinator report) — the slot is held by a
 *       healthy task but the recovery protocol sees nothing. Post-fix the
 *       duplicate is rejected through a single {@code runningTasks.compute}
 *       generation dispatch and a typed FAILED
 *       {@link TaskStatusReport} reaches the coordinator (CC-17 channel)
 *       while the incumbent stays untouched.</li>
 *   <li><b>Variant c</b> — cross-generation mixed install: gen-N slot occupied,
 *       fencing rotated to N+1 (removeIf clears the slot), gen-N+1 re-assigned
 *       into a fresh slot, then {@code installInvokable} with the OLD epoch N.
 *       Pre-fix the invokable was installed regardless (mixed-generation set);
 *       post-fix the epoch-equality check rejects the install (WARN, no
 *       {@code setInvokable}) and the matching-epoch install still succeeds
 *       (over-rejection guard).</li>
 *   <li><b>Displacement structure pin</b> — a zombie slot from an older
 *       generation (only reachable through the in-flight
 *       {@code updateFencingToken} race; injected directly at package level)
 *       is displaced by a newer-generation assignment (CC-02 replacement
 *       shape): the zombie is fenced (permit released via the
 *       {@code semaphoreReleased} CAS) and the new task owns the slot.</li>
 * </ul>
 *
 * <p>The full three-step timing of the original draft was adjudicated pseudo-green
 * (review R1 Blocker-1: {@code updateFencingToken}'s removeIf clears the old slot
 * before the re-assignment can hit the duplicate branch); this class implements
 * the re-adjudicated variant b / variant c drivers.
 */
class TestStreamRuntimeGroupBW02EpochGuard {

    private static final String NODE_ID = "gb-w02-node";
    private static final String ENDPOINT = "localhost:9410";
    private static final int CAPACITY = 4;

    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        taskManager = new TaskManager(NODE_ID, ENDPOINT, CAPACITY,
                new TestTaskManagerLivenessAndReporting.NoopMessageService(),
                new TestTaskManagerLivenessAndReporting.NoopClusterRegistry(), "control-topic");
        taskManager.start();
    }

    @AfterEach
    void tearDown() {
        taskManager.stop();
    }

    // ---------------- red① variant b: same-generation duplicate ----------------

    @Test
    void sameGenerationDuplicateAssignmentIsRejectedAndReported() throws Exception {
        TestTaskManager.MockCoordinatorRpcService rpc = new TestTaskManager.MockCoordinatorRpcService();
        taskManager.setCoordinatorRpcService(rpc);
        long epoch = 7L;
        taskManager.updateFencingToken(epoch);

        TaskAssignment assignment = new TaskAssignment(
                "job-gb1", "v-a", 0, NODE_ID, "attempt-1", epoch,
                System.currentTimeMillis(), 1);

        // The incumbent parks (never finishes, never hits the invokable-install
        // timeout) so the only observable report is the duplicate's rejection.
        CountDownLatch park = new CountDownLatch(1);
        try {
            taskManager.receiveAssignment(assignment);
            taskManager.installInvokable("job-gb1", "v-a", 0,
                    new TestStreamRuntimeGroupBW12StopWindow.ParkedInvokable(
                            new TestStreamRuntimeGroupBW12StopWindow.RecordingParticipant(), park), epoch);
            assertEquals(1, taskManager.getRunningTaskCount(), "precondition: incumbent occupies the slot");
            assertTrue(rpc.statusReports.isEmpty(), "precondition: accepted assignment reports nothing");

            taskManager.receiveAssignment(assignment); // same key, same epoch — duplicate

            // The duplicate must NOT be silently ignored: a typed FAILED report
            // has to reach the coordinator. Pre-fix this is deterministically red
            // (zero reports — silent ignore in the containsKey branch).
            TestAwait.until("duplicate assignment rejection is reported to the coordinator",
                    () -> !rpc.statusReports.isEmpty(), 10_000);
            assertEquals(1, rpc.statusReports.size(),
                    "exactly one FAILED report for the duplicate (the accepted one reports nothing)");
            assertEquals(TaskStatusReport.TerminalState.FAILED, rpc.statusReports.get(0).getTerminalState());
            assertEquals("v-a", rpc.statusReports.get(0).getVertexId());

            // The incumbent is unaffected: still the one running task, same attempt.
            assertEquals(1, taskManager.getRunningTaskCount(),
                    "the incumbent must not be displaced by a same-generation duplicate");
            RunningTask incumbent = taskManager.runningTasks.get("job-gb1/v-a/0");
            assertNotNull(incumbent);
            assertEquals("attempt-1", incumbent.attemptId, "incumbent slot keeps its original attempt");
            assertEquals(7L, incumbent.getFencingEpoch());

            // Permit conservation: the duplicate acquired and released its permit.
            assertEquals(CAPACITY - 1, taskManager.availablePermits(),
                    "the rejected duplicate releases the permit it acquired");
        } finally {
            park.countDown();
        }
    }

    // ---------------- red① variant c: cross-generation mixed install ----------------

    @Test
    void crossGenerationInvokableInstallIsRejected() throws Exception {
        long epochN = 10L;
        taskManager.updateFencingToken(epochN);
        TaskAssignment genN = new TaskAssignment(
                "job-gb1c", "v-c", 0, NODE_ID, "attempt-n", epochN,
                System.currentTimeMillis(), 1);
        taskManager.receiveAssignment(genN);

        // Rotate the fencing epoch: removeIf cancels and clears the gen-N slot.
        long epochN1 = 11L;
        taskManager.updateFencingToken(epochN1);
        TestAwait.until("gen-N slot cleared by the fencing rotation",
                () -> !taskManager.hasRegistryEntry("job-gb1c", "v-c", 0));

        // Re-assign the same key under gen N+1 — fresh slot.
        TaskAssignment genN1 = new TaskAssignment(
                "job-gb1c", "v-c", 0, NODE_ID, "attempt-n1", epochN1,
                System.currentTimeMillis(), 2);
        taskManager.receiveAssignment(genN1);
        assertEquals(1, taskManager.getRunningTaskCount(), "precondition: gen-N+1 slot occupied");
        RunningTask slot = taskManager.runningTasks.get("job-gb1c/v-c/0");
        assertNotNull(slot);
        assertEquals(epochN1, slot.getFencingEpoch());

        CountingInvokable staleInvokable = new CountingInvokable();
        // Install with the OLD epoch — must be rejected (no mixed-generation set).
        taskManager.installInvokable("job-gb1c", "v-c", 0, staleInvokable, epochN);
        assertNull(slot.invokable,
                "an invokable carrying a stale epoch must NOT be installed into the newer-generation slot");

        // Over-rejection guard: the matching-epoch install still succeeds.
        CountingInvokable matchingInvokable = new CountingInvokable();
        taskManager.installInvokable("job-gb1c", "v-c", 0, matchingInvokable, epochN1);
        assertNotNull(slot.invokable, "the matching-epoch install must still be delivered");
    }

    // ---------------- displacement structure pin (CC-02 replacement shape) ----------------

    @Test
    void newerGenerationAssignmentDisplacesZombieSlot() throws Exception {
        long currentEpoch = 20L;
        taskManager.updateFencingToken(currentEpoch);

        // Simulate the in-flight race residue: a zombie slot from an OLDER
        // generation occupying the key (the state that receiveAssignment's
        // duplicate branch can observe between updateFencingToken's getAndSet
        // and its removeIf sweep). The zombie holds the permit it would have
        // acquired when it was deployed.
        RunningTask zombie = new RunningTask(taskManager, "job-gb1z", "v-z", 0,
                19L, "attempt-zombie", 1);
        taskManager.capacitySemaphore.acquire();
        taskManager.runningTasks.put("job-gb1z/v-z/0", zombie);

        TaskAssignment newer = new TaskAssignment(
                "job-gb1z", "v-z", 0, NODE_ID, "attempt-new", currentEpoch,
                System.currentTimeMillis(), 2);
        taskManager.receiveAssignment(newer);

        // The zombie is displaced (CC-02 replacement shape): the new task owns
        // the slot and the zombie's permit was released through the
        // semaphoreReleased CAS.
        RunningTask occupant = taskManager.runningTasks.get("job-gb1z/v-z/0");
        assertNotNull(occupant, "the slot is owned by the newer-generation task");
        assertNotSame(zombie, occupant, "the zombie must not remain the slot occupant");
        assertEquals(currentEpoch, occupant.getFencingEpoch());
        assertEquals("attempt-new", occupant.attemptId);
        assertTrue(zombie.semaphoreReleased.get(),
                "the displaced zombie's permit is released via the semaphoreReleased CAS");
        assertEquals(CAPACITY - 1, taskManager.availablePermits(),
                "permit conservation: one task in the slot holds exactly one permit");
    }

    // ---------------- stubs ----------------

    /** Minimal invokable: runs to completion immediately (install observability only). */
    static final class CountingInvokable extends io.nop.stream.core.execution.task.StreamTaskInvokable {
        CountingInvokable() {
            super(new io.nop.stream.core.jobgraph.OperatorChain(
                    java.util.Collections.<io.nop.stream.core.operators.StreamOperator<?>>singletonList(
                            new io.nop.stream.core.operators.StreamMap<Object, Object>(x -> x))));
        }

        @Override
        public void invoke() {
            // no-op body: the install observable is the slot's invokable field
        }
    }
}
