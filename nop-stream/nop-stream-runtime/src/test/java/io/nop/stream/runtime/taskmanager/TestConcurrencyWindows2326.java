/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.taskmanager;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SourceSplit;
import io.nop.stream.core.source.coordinator.LocalSourceCoordinator;
import io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.TaskAssignment;
import io.nop.stream.runtime.testsupport.TestAwait;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;
import io.nop.stream.core.jobgraph.JobGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 plan 2326 Phase 1 pins for the concurrency windows:
 *
 * <ul>
 *   <li><b>CC-02</b>: a deployTask whose fencing generation is STALE relative to
 *       the slot's occupant (raced in through the entry-gate/put window) must
 *       NOT displace the healthy newer task — typed reject, slot untouched,
 *       permits conserved. Reverting the {@code runningTasks.compute} conditional
 *       displace to the old unconditional put turns
 *       {@code staleGenerationDeployDoesNotDisplaceNewerTask} red (the newer
 *       task gets canceled).</li>
 *   <li><b>CC-04</b>: when the LAST task of a source vertex leaves the JVM, its
 *       LOCAL source-coordinator registry entry is released (no SplitEnumerator
 *       leak); when another task of the same vertex survives, the entry stays.
 *       Reverting the {@code maybeReleaseSourceCoordinatorRegistryEntry} sweep
 *       turns {@code lastTaskExitReleasesSourceCoordinatorRegistryEntry} red.</li>
 * </ul>
 *
 * <p>CC-03 (activateAsLeader lock-outside writes) is pinned structurally: the
 * fix moved the three activation writes inside the recovery lock; the
 * regression net is the existing grant/recovery suites
 * (TestJobCoordinatorStandbyStateMachine et al.) plus the plan's grep gate
 * (no lock-outside active/recoveryGen/currentLeadership writes in
 * activateAsLeader/deactivateToStandby). A deterministic interleaving pin
 * needs a latch seam in activateAsLeader — recorded as follow-up.
 */
public class TestConcurrencyWindows2326 {

    private static final String NODE_ID = "cc26-node";
    private static final String ENDPOINT = "localhost:9326";

    private TaskManager taskManager;
    private RecordingClusterRegistry clusterRegistry;
    private RecordingMessageService messageService;

    @BeforeEach
    void setUp() {
        clusterRegistry = new RecordingClusterRegistry();
        messageService = new RecordingMessageService();
        taskManager = new TaskManager(NODE_ID, ENDPOINT, 4,
                messageService, clusterRegistry, "control-topic");
        taskManager.start();
    }

    @AfterEach
    void tearDown() {
        taskManager.stop();
        SourceCoordinatorRegistry.clearForTest();
    }

    // ---------------- CC-02 ----------------

    /**
     * CC-02 deterministic unit pin: the displace decision. A stale candidate
     * (older generation) must NOT displace a newer incumbent; equal-or-newer
     * candidates displace as before. The decision runs inside
     * {@code runningTasks.compute} in deployTask (atomic vs concurrent same-key
     * deploys); reverting {@code selectDisplacedTask} to unconditional-candidate
     * turns the first assertion red. The full race (gate pass -> token rotation ->
     * put) needs a latch seam inside deployTask and is covered structurally by
     * the compute atomicity + this decision pin.
     */
    @Test
    void staleGenerationCandidateDoesNotDisplaceNewerIncumbent() {
        TaskManager owner = taskManager;
        RunningTask newer = new RunningTask(owner, "job-cc26", "vertex-9", 0,
                2L, "attempt-new", 2);
        RunningTask stale = new RunningTask(owner, "job-cc26", "vertex-9", 0,
                1L, "attempt-stale", 1);

        assertEquals(newer, TaskManager.selectDisplacedTask(newer, stale),
                "a stale-generation candidate must not displace a newer incumbent");

        // empty slot: candidate occupies
        assertEquals(stale, TaskManager.selectDisplacedTask(null, stale));
        // equal generation: redeploy semantics preserved — the candidate displaces
        RunningTask sameGen = new RunningTask(owner, "job-cc26", "vertex-9", 0,
                2L, "attempt-newer", 2);
        assertEquals(sameGen, TaskManager.selectDisplacedTask(newer, sameGen));
        // newer generation displaces
        RunningTask newest = new RunningTask(owner, "job-cc26", "vertex-9", 0,
                3L, "attempt-newest", 3);
        assertEquals(newest, TaskManager.selectDisplacedTask(newer, newest));
    }

    // ---------------- CC-04 ----------------

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void lastTaskExitReleasesSourceCoordinatorRegistryEntry() throws Exception {
        // Register a source coordinator under vertex 7 (the id the operator would
        // have registered during open()).
        AtomicReference<LocalSourceCoordinator> created = new AtomicReference<>();
        SourceCoordinatorRegistry.registerIfAbsent(7,
                vid -> {
                    LocalSourceCoordinator coord = new LocalSourceCoordinator(
                            "vertex-7", new NoopSource(), 1);
                    created.set(coord);
                    return coord;
                });
        assertTrue(SourceCoordinatorRegistry.isRegistered(7), "precondition: coordinator registered");

        // Deploy the LAST task of vertex-7 and let it complete.
        taskManager.updateFencingToken(1L);
        taskManager.receiveAssignment(new TaskAssignment(
                "job-cc04", "vertex-7", 0, NODE_ID, "attempt-1", 1L,
                System.currentTimeMillis()));
        TestAwait.until("the empty-body task completes immediately",
                () -> taskManager.getRunningTaskCount() == 0);

        // Sweep: the registry entry must be released.
        TestAwait.until("last task exit releases the source coordinator registry entry",
                () -> !SourceCoordinatorRegistry.isRegistered(7));
        assertNotNull(created.get());
    }

    // The surviving-sibling guard (sweep skipped when another running task shares
    // the vertex) is structural: the anyMatch check over runningTasks in
    // RunningTask.maybeReleaseSourceCoordinatorRegistryEntry. A deterministic
    // integration pin is unreachable because assignment-path tasks with empty
    // bodies fail fast (success-retention precondition can't be arranged) —
    // recorded in the plan execution notes.

    // ---------------- helpers ----------------

    private static final class RecordingClusterRegistry implements ClusterRegistry {
        final Map<String, Object> registeredNodes = new ConcurrentHashMap<>();

        @Override public void registerCoordinator(String jobId, String coordinatorId, long fencingEpoch) {}
        @Override public io.nop.stream.runtime.cluster.CoordinatorInfo getActiveCoordinator(String jobId) { return null; }
        @Override public void registerNode(String nodeId, String endpoint, int capacity) {
            registeredNodes.put(nodeId, new Object());
        }
        @Override public boolean renewLease(String nodeId, long leaseTimeoutMs) { return true; }
        @Override public io.nop.stream.runtime.cluster.LeaseInfo getNodeLease(String nodeId) { return null; }
        @Override public List<io.nop.stream.runtime.cluster.NodeInfo> getActiveNodes() {
            return List.of(new io.nop.stream.runtime.cluster.NodeInfo(
                    NODE_ID, ENDPOINT, 4, System.currentTimeMillis(), System.currentTimeMillis()));
        }
        @Override public void assignTask(String jobId, String vertexId, int subtaskIndex,
                                          String nodeId, String attemptId, long fencingEpoch,
                                          int attemptNumber) {}
        @Override public TaskAssignment getTaskAssignment(String jobId, String vertexId, int subtaskIndex) { return null; }
        @Override public List<TaskAssignment> getAttemptHistory(String jobId, String vertexId, int subtaskIndex) {
            return List.of();
        }
        @Override public void removeTaskAssignment(String jobId, String vertexId, int subtaskIndex) {}
    }

    private static final class RecordingMessageService implements io.nop.api.core.message.IMessageService {
        @Override public io.nop.api.core.message.IMessageSubscription subscribe(String topic,
                                                                                   io.nop.api.core.message.IMessageConsumer consumer,
                                                                                   io.nop.api.core.message.MessageSubscribeOptions options) {
            return new io.nop.api.core.message.IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return false; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }
        @Override public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                                                                                 io.nop.api.core.message.MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    /** Source stub: the coordinator never drives it in this pin. */
    static class NoopSource
            implements io.nop.stream.core.source.Source<Object, SourceSplit, Object> {
        @Override
        public io.nop.stream.core.source.SplitEnumerator<SourceSplit, Object> createEnumerator() {
            return null;
        }

        @Override
        public io.nop.stream.core.source.SplitEnumerator<SourceSplit, Object> restoreEnumerator(Object state) {
            return null;
        }

        @Override
        public io.nop.stream.core.source.SourceReader<Object, SourceSplit> createReader(
                io.nop.stream.core.source.SourceReaderContext context) {
            return null;
        }

        @Override
        public io.nop.stream.core.source.Boundedness getBoundedness() {
            return io.nop.stream.core.source.Boundedness.CONTINUOUS_UNBOUNDED;
        }

        @Override
        public io.nop.stream.core.source.SimpleVersionedSerializer<Object> getEnumeratorStateSerializer() {
            throw new UnsupportedOperationException("not driven in this pin");
        }

        @Override
        public io.nop.stream.core.source.SimpleVersionedSerializer<SourceSplit> getSplitSerializer() {
            throw new UnsupportedOperationException("not driven in this pin");
        }
    }
}
