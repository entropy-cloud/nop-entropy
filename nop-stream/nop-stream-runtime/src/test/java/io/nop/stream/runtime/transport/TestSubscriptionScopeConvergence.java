/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Items 28+31 (D1, Phase 2 focused tests): subscription-scope convergence.
 *
 * <p>Anti-hollow anchor: every assertion runs against the REAL
 * {@link SubtaskPlanBuilder#buildSubtaskPlan} / {@link RemoteGraphExecutionPlanBuilder#buildRemoteOnly}
 * paths with a recording {@link IMessageService}, asserting the exact set of
 * topics that became active subscriptions — not type existence.
 *
 * <p>Coverage required by the plan:
 * <ul>
 *   <li>TM remote-deploy of ONE subtask subscribes exactly that subtask's
 *       input topics (multi-upstream and multi-in-edge shapes included);
 *       no other subtask's channels, no all-pair residue;</li>
 *   <li>union semantics — two consecutive deployments on the same service
 *       accumulate (input sets' union, never reset);</li>
 *   <li>full plan STRUCTURE preserved regardless of scope (checkpoint/rescale
 *       consumers of {@code DeployedSubtaskPlan.getPlan()});</li>
 *   <li>full-subscription default (null scope) — single-JVM executor semantics
 *       (every (s,t) channel subscribes);</li>
 *   <li>zero-scope (empty set) — the remoteDeployMode=true coordinator form
 *       (no channel subscribes);</li>
 *   <li>reading an unsubscribed (scope-converged, construct-only) channel
 *       fails fast with a typed error (wiring-misuse guard, guide #24).</li>
 * </ul>
 */
class TestSubscriptionScopeConvergence {

    /** Records every subscribe() call; send() is a no-op (no data flows here). */
    static final class RecordingMessageService implements IMessageService {
        final List<String> subscribedTopics = new CopyOnWriteArrayList<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                              MessageSubscribeOptions options) {
            subscribedTopics.add(topic);
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }
            };
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                                                                    MessageSendOptions options) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private RecordingMessageService messageService;
    private SubtaskPlanBuilder builder;

    @BeforeEach
    void setUp() {
        messageService = new RecordingMessageService();
        builder = new SubtaskPlanBuilder(messageService, null);
    }

    @AfterEach
    void tearDown() {
        // nothing to stop: the recording service owns no threads
    }

    // ==================== fixture graph ====================

    /**
     * Graph: {@code A(p=2) → B(p=2) → C(p=1)} plus a second fan-in edge
     * {@code D(p=1) → B} — B is a multi-in-edge vertex (A→B and D→B), A is a
     * pure source, C a sink with parallelism 1.
     */
    private static JobGraph buildGraph(String jobId) {
        JobGraph graph = new JobGraph(jobId);
        for (String vertex : new String[]{"A", "B", "C", "D"}) {
            int parallelism = "B".equals(vertex) ? 2 : ("A".equals(vertex) ? 2 : 1);
            // Serializable identity map operator: survives both the plan build
            // and the per-subtask deepCopy (serialization-based copyForSubtask).
            OperatorChain chain = new OperatorChain(Collections.singletonList(
                    new io.nop.stream.core.operators.StreamMap<>(new IdentityMap<>())));
            graph.addVertex(new JobVertex(vertex, vertex, parallelism,
                    Collections.singletonList(chain),
                    new StreamTaskInvokable(chain)));
        }
        graph.addEdge(new JobEdge("A", "B", ResultPartitionType.PIPELINED));
        graph.addEdge(new JobEdge("B", "C", ResultPartitionType.PIPELINED));
        graph.addEdge(new JobEdge("D", "B", ResultPartitionType.PIPELINED));
        return graph;
    }

    /** Serializable identity function (lambdas would break the deepCopy serialization). */
    public static final class IdentityMap<T> implements io.nop.stream.core.common.functions.MapFunction<T, T> {
        private static final long serialVersionUID = 1L;

        @Override
        public T map(T value) {
            return value;
        }
    }

    private static DeploymentPlan buildDeploymentPlan(String jobId) {
        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("A", new PartitionedPlan.VertexPlan("A", 2, null));
        vertexPlans.put("B", new PartitionedPlan.VertexPlan("B", 2, null));
        vertexPlans.put("C", new PartitionedPlan.VertexPlan("C", 1, null));
        vertexPlans.put("D", new PartitionedPlan.VertexPlan("D", 1, null));
        List<PartitionedPlan.EdgePlan> edgePlans = new java.util.ArrayList<>();
        edgePlans.add(new PartitionedPlan.EdgePlan("A", "B", PartitionPolicy.FORWARD));
        edgePlans.add(new PartitionedPlan.EdgePlan("B", "C", PartitionPolicy.FORWARD));
        edgePlans.add(new PartitionedPlan.EdgePlan("D", "B", PartitionPolicy.FORWARD));
        return new DeploymentPlan(jobId, "pipeline-0",
                new PartitionedPlan(jobId, "pipeline-0", vertexPlans, edgePlans, null, null),
                "local", "memory", "local", null, null);
    }

    private static TaskDeploymentDescriptor descriptor(String jobId, String vertexId, int subtaskIndex) {
        return new TaskDeploymentDescriptor(jobId, vertexId, subtaskIndex, "tm-x",
                "attempt-" + vertexId + "-" + subtaskIndex, 1, 1L,
                buildGraph(jobId), buildDeploymentPlan(jobId), null);
    }

    // ==================== TM remote-deploy scope ====================

    @Test
    void tmDeployOfMiddleSubtaskSubscribesExactlyItsInputTopics() {
        // Deploy B/1: inputs are A/0→B/1, A/1→B/1 (edge A→B) and D/0→B/1 (edge D→B).
        SubtaskPlanBuilder.DeployedSubtaskPlan deployed = builder.buildSubtaskPlan(descriptor("job-scope-1", "B", 1));

        Set<String> expected = new LinkedHashSet<>();
        expected.add(StreamTopicNaming.buildTopic("job-scope-1", "A->B", 0, 1));
        expected.add(StreamTopicNaming.buildTopic("job-scope-1", "A->B", 1, 1));
        expected.add(StreamTopicNaming.buildTopic("job-scope-1", "D->B", 0, 1));
        Set<String> actual = new LinkedHashSet<>(messageService.subscribedTopics);
        assertEquals(expected, actual,
                "TM deploying B/1 must subscribe exactly B/1's input topics (multi-upstream + "
                        + "multi-in-edge), no other subtask's channels, no all-pair residue");
    }

    @Test
    void tmDeployOfSinkSubtaskSubscribesAllUpstreamChannelsOfThatSubtask() {
        // Deploy C/0: input is B/0→C/0 and B/1→C/0 (both upstream subtasks of C/0).
        builder.buildSubtaskPlan(descriptor("job-scope-2", "C", 0));

        Set<String> expected = new LinkedHashSet<>();
        expected.add(StreamTopicNaming.buildTopic("job-scope-2", "B->C", 0, 0));
        expected.add(StreamTopicNaming.buildTopic("job-scope-2", "B->C", 1, 0));
        assertEquals(expected, new LinkedHashSet<>(messageService.subscribedTopics),
                "TM deploying C/0 must see ALL of C/0's upstream channels (InputGate semantics, "
                        + "D1 hard constraint (a))");
    }

    @Test
    void tmDeployOfSourceSubtaskSubscribesNothing() {
        // Deploy A/0: a source has no input edges — zero subscriptions (its two
        // out-edge producer partitions are still constructed, but producers
        // never subscribe).
        builder.buildSubtaskPlan(descriptor("job-scope-3", "A", 0));
        assertEquals(Collections.emptySet(), new LinkedHashSet<>(messageService.subscribedTopics),
                "TM deploying a source subtask must activate ZERO input subscriptions");
    }

    @Test
    void consecutiveDeploysOnTheSameTmAccumulateUnionOfInputSets() {
        // Union semantics: deploy B/0 then B/1 on the same service — the
        // subscription set is the UNION of both input sets (never reset).
        builder.buildSubtaskPlan(descriptor("job-scope-4", "B", 0));
        builder.buildSubtaskPlan(descriptor("job-scope-4", "B", 1));

        Set<String> expected = new LinkedHashSet<>();
        // B/0 inputs
        expected.add(StreamTopicNaming.buildTopic("job-scope-4", "A->B", 0, 0));
        expected.add(StreamTopicNaming.buildTopic("job-scope-4", "A->B", 1, 0));
        expected.add(StreamTopicNaming.buildTopic("job-scope-4", "D->B", 0, 0));
        // B/1 inputs
        expected.add(StreamTopicNaming.buildTopic("job-scope-4", "A->B", 0, 1));
        expected.add(StreamTopicNaming.buildTopic("job-scope-4", "A->B", 1, 1));
        expected.add(StreamTopicNaming.buildTopic("job-scope-4", "D->B", 0, 1));
        assertEquals(expected, new LinkedHashSet<>(messageService.subscribedTopics),
                "consecutive deployments accumulate the union of input sets");
    }

    @Test
    void fullPlanStructurePreservedUnderScopedBuild() {
        // D1 hard constraint (c): the deployed plan still mirrors the GLOBAL
        // topology (RemoteTaskDeploySupport builds its checkpoint plan and the
        // rescale-aware restore from this structure).
        SubtaskPlanBuilder.DeployedSubtaskPlan deployed = builder.buildSubtaskPlan(descriptor("job-scope-5", "C", 0));
        GraphExecutionPlan plan = deployed.getPlan();
        assertNotNull(plan.getSubtasks("A"), "vertex A must exist in the full plan");
        assertNotNull(plan.getSubtasks("B"), "vertex B must exist in the full plan");
        assertEquals(2, plan.getSubtasks("A").size(), "A's parallelism (2) preserved in plan structure");
        assertEquals(2, plan.getSubtasks("B").size(), "B's parallelism (2) preserved in plan structure");
        assertEquals(1, plan.getSubtasks("C").size(), "C's parallelism (1) preserved in plan structure");
        assertNotNull(deployed.getInvokable(), "assigned subtask's invokable resolved");
    }

    // ==================== builder-level scope semantics ====================

    @Test
    void nullScopeSubscribesEveryChannelPair_singleJvmExecutorSemantics() {
        // D1 hard constraint (b): the null-scope (default) form keeps the
        // full-matrix subscription of the single-JVM executors.
        RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                messageService, null, 1L);
        planBuilder.buildRemoteOnly(buildGraph("job-scope-6"), buildDeploymentPlan("job-scope-6"), true, null);

        // A→B: 2×2 channels; B→C: 2×1; D→B: 1×2 → 8 total subscriptions
        assertEquals(8, messageService.subscribedTopics.size(),
                "full-subscription build must subscribe every (src,tgt) channel of the matrix: "
                        + messageService.subscribedTopics);
    }

    @Test
    void emptyScopeSubscribesNothingWhileBuildingFullPlan_remoteDeployCoordinatorForm() {
        // D1(f): the remoteDeployMode=true coordinator builds the plan with a
        // ZERO subscription scope — full structure, no delivery surface.
        RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                messageService, null, 1L);
        GraphExecutionPlan plan = planBuilder.buildRemoteOnly(
                buildGraph("job-scope-7"), buildDeploymentPlan("job-scope-7"), true,
                Collections.emptySet());
        assertEquals(Collections.emptySet(), new LinkedHashSet<>(messageService.subscribedTopics),
                "zero-scope build must activate no subscriptions (coordinator-side convergence)");
        assertEquals(2, plan.getSubtasks("B").size(),
                "zero-scope build still produces the full plan structure");
    }

    // ==================== wiring-misuse guard ====================

    @Test
    void readingAnUnsubscribedChannelFailsFastTyped() throws Exception {
        // D1 misuse guard: a scope-converged (construct-only) channel must fail
        // fast on read with a typed error — never block on an empty queue.
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, "nop-stream.job-x.edge-x.0.0", 1L, 4, 0L, 10_000L, false);
        assertEquals(false, channel.isSubscriptionActive());
        assertThrows(io.nop.stream.core.exceptions.StreamException.class, channel::read,
                "read() on an unsubscribed channel must throw a typed wiring error");
        assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> channel.read(10, java.util.concurrent.TimeUnit.MILLISECONDS),
                "read(timeout) on an unsubscribed channel must throw a typed wiring error");
        // close() must tolerate the absent subscription (no NPE)
        channel.close();
        assertTrue(channel.isFinished(), "close() still marks an unsubscribed channel finished");
    }
}
