/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.runtime.protocol.TaskDeploymentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 Phase 2 (plan 2332): the REMOTE deployment path activates side-output tag edges.
 * A {@code JobEdge} carrying {@code outputTagId} must
 * <ul>
 *   <li>register the target vertex's side-output consumer on EVERY subtask invokable
 *       (both the coordinator-side {@code buildRemoteOnly} full-subscription form and
 *       the TaskManager-side {@code SubtaskPlanBuilder.buildSubtaskPlan} scoped form),
 *       so tag records arriving on the input gate route into the operator chain;</li>
 *   <li>wire the producing vertex's tail output to the tag-aware edge writer
 *       ({@code EdgeWriterOutput}) so main records never cross the tag edge.</li>
 * </ul>
 * Wiring-level assertions ({@code hasSideOutputConsumer} / output class) mirror the
 * pinned-contract style of {@code TestSideOutputChainingE2E}; the behavioral delivery
 * is covered end-to-end by {@code TestSideOutputPipelineE2E} on the local path.
 */
public class TestRemoteSideOutputTagWiring {

    /**
     * Graph: {@code A → B} where the single edge is (optionally) a tag edge. A is the
     * tag producer (tap vertex), B the tag-fed consumer.
     */
    private static JobGraph buildGraph(String jobId, boolean tagEdge, boolean fanOutMainToo) {
        JobGraph graph = new JobGraph(jobId);
        for (String vertex : new String[]{"A", "B"}) {
            // Serializable identity map operator: survives both the plan build and the
            // per-subtask deepCopy (serialization-based copyForSubtask).
            OperatorChain chain = new OperatorChain(Collections.singletonList(
                    new StreamMap<>(new IdentityMap<>())));
            graph.addVertex(new JobVertex(vertex, vertex, 1,
                    Collections.singletonList(chain),
                    new StreamTaskInvokable(chain)));
        }
        if (fanOutMainToo || !tagEdge) {
            graph.addEdge(new JobEdge("A", "B", ResultPartitionType.PIPELINED));
        }
        if (tagEdge) {
            JobEdge tagEdgeJob = new JobEdge("A", "B", ResultPartitionType.PIPELINED);
            tagEdgeJob.setOutputTagId("late");
            graph.addEdge(tagEdgeJob);
        }
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

    @Test
    void remoteBuildRegistersTagConsumerOnTargetAndTagAwareWriterOnProducer() {
        RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                new TestSubscriptionScopeConvergence.RecordingMessageService(), null, 1L);
        GraphExecutionPlan plan = planBuilder.buildRemoteOnly(
                buildGraph("job-tag-wiring-1", true, false), null, false);

        for (Subtask subtask : plan.getSubtasks("B")) {
            StreamTaskInvokable invokable = subtask.getInvokable();
            assertTrue(invokable.hasSideOutputConsumer("late"),
                    "every target subtask must register the tag-edge consumer for 'late'");
        }
        StreamTaskInvokable producer = plan.getInvokables().get("A");
        Object output = ((AbstractStreamOperator<?>) producer.getOperatorChain().getOperators()
                .get(0)).getOutput();
        assertTrue(output.getClass().getName().endsWith("$EdgeWriterOutput"),
                "a tag-edge producer must wire the tag-aware edge writer, got: "
                        + output.getClass().getName());
    }

    @Test
    void fanOutProducerKeepsDistinctMainAndTagChannels() {
        RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                new TestSubscriptionScopeConvergence.RecordingMessageService(), null, 1L);
        GraphExecutionPlan plan = planBuilder.buildRemoteOnly(
                buildGraph("job-tag-wiring-2", true, true), null, false);

        // The tag registration is replace-by-id per consumer vertex — both channels
        // feed the same B vertex, one registration is expected (not one per edge).
        for (Subtask subtask : plan.getSubtasks("B")) {
            assertTrue(subtask.getInvokable().hasSideOutputConsumer("late"),
                    "the tag-fed target must have the tag consumer registered");
        }
        // The producer has a 2-writer fan-out (main + tag) — the tag-aware broadcasting
        // wiring is what keeps main records off the tag edge and vice versa.
        StreamTaskInvokable producer = plan.getInvokables().get("A");
        assertEquals(2, producer.getFanOutWriters().size(),
                "the fan-out producer must keep one writer per channel");
    }

    @Test
    void plainEdgesRegisterNoTagConsumer() {
        RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                new TestSubscriptionScopeConvergence.RecordingMessageService(), null, 1L);
        GraphExecutionPlan plan = planBuilder.buildRemoteOnly(
                buildGraph("job-tag-wiring-3", false, false), null, false);

        for (Subtask subtask : plan.getSubtasks("B")) {
            assertFalse(subtask.getInvokable().hasSideOutputConsumer("late"),
                    "a plain main-path edge must not register any tag consumer (zero regression)");
        }
    }

    @Test
    void tmDeployedSubtaskRegistersTagConsumer() {
        // TaskManager-side scoped form (SubtaskPlanBuilder): deploying B/0 must
        // register the tag consumer on the locally rebuilt invokable.
        SubtaskPlanBuilder builder = new SubtaskPlanBuilder(
                new TestSubscriptionScopeConvergence.RecordingMessageService(), null);
        TaskDeploymentDescriptor descriptor = new TaskDeploymentDescriptor(
                "job-tag-wiring-4", "B", 0, "tm-x", "attempt-b-0", 1, 1L,
                buildGraph("job-tag-wiring-4", true, false), null, null);
        SubtaskPlanBuilder.DeployedSubtaskPlan deployed = builder.buildSubtaskPlan(descriptor);
        assertTrue(deployed.getInvokable().hasSideOutputConsumer("late"),
                "the TM-deployed tag-fed subtask must register the tag consumer");
    }
}
