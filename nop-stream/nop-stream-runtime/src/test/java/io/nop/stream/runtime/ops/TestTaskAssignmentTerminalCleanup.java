/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.ops;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.cluster.ClusterRegistry;
import io.nop.stream.runtime.cluster.JdbcClusterRegistry;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.coordinator.JobCoordinator;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2510 C1-006 / C10-020 (plan 2510 裁定⑦): the terminal transition point
 * ({@code OpsJobManager.stop} after {@code terminalAt.put}) must remove the
 * terminal job's task assignment ledger entries (job-scoped, InMemory prefix
 * removal) while ACTIVE jobs keep the append-only attempt semantics intact —
 * the +1 pin / monotonic pin of {@code TestJobCoordinatorPerTaskFailure} and
 * {@code TestInMemoryClusterRegistryAttemptHistory} must stay green.
 */
class TestTaskAssignmentTerminalCleanup {

    public static final class EndlessSource
            implements io.nop.stream.core.common.functions.source.SourceFunction<Long> {
        private static final long serialVersionUID = 1L;
        private volatile boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            long i = 0;
            while (running) {
                ctx.collect(i++);
                Thread.sleep(100);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** Trivial source → sink pipeline (same shape as the ops E2E factory). */
    public static final class TrivialPipelineFactory implements ClusterPipelineFactory {
        @Override
        public PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) {
            StreamSourceOperator<Long> sourceOp = new StreamSourceOperator<>(new EndlessSource());
            StreamSinkOperator<Long> sinkOp = new StreamSinkOperator<>(
                    new io.nop.stream.core.common.functions.sink.PrintSinkFunction<>());
            OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
            OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));
            StreamTaskInvokable sourceInv = new StreamTaskInvokable(sourceChain);
            StreamTaskInvokable sinkInv = new StreamTaskInvokable(sinkChain);
            JobVertex sourceVertex = new JobVertex("source", "Source", 1,
                    Collections.singletonList(sourceChain), sourceInv);
            JobVertex sinkVertex = new JobVertex("sink", "Sink", 1,
                    Collections.singletonList(sinkChain), sinkInv);
            JobGraph jobGraph = new JobGraph(jobId);
            jobGraph.addVertex(sourceVertex);
            jobGraph.addVertex(sinkVertex);
            jobGraph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));
            Map<String, PartitionedPlan.VertexPlan> vertexPlans = new java.util.LinkedHashMap<>();
            vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
            vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
            PartitionedPlan partitionedPlan = new PartitionedPlan(jobId, "pipeline-0", vertexPlans,
                    List.of(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD)),
                    null, null);
            DeploymentPlan deploymentPlan = new DeploymentPlan(
                    jobId, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);
            return new PipelineArtifacts(jobGraph, deploymentPlan, 500L, 10_000L, 3);
        }
    }

    private static final String FACTORY_FQCN =
            "io.nop.stream.runtime.ops.TestTaskAssignmentTerminalCleanup$TrivialPipelineFactory";

    @TempDir
    Path tempDir;

    private OpsJobManager manager;
    private InMemoryClusterRegistry registry;

    @BeforeEach
    void setUp() {
        InMemoryClusterRegistry inMemory = new InMemoryClusterRegistry();
        inMemory.registerNode("node-1", "localhost:8081", 4);
        registry = inMemory;
        manager = new OpsJobManager(new LocalMessageService(), registry,
                new LocalFileCheckpointStorage(tempDir.toString()),
                Map.of("node-1", new io.nop.stream.runtime.coordinator.CoordinatorTestSupport.RecordingTaskRpcService()));
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    private JobSubmissionSpec spec(String jobId) {
        JobSubmissionSpec spec = new JobSubmissionSpec();
        spec.setJobId(jobId);
        spec.setPipelineFactoryClass(FACTORY_FQCN);
        return spec;
    }

    @Test
    void terminalTransitionRemovesAssignmentLedgerEntriesJobScoped() {
        JobCoordinator terminalJob = manager.submit(spec("c1-006-terminal"));
        assertTrue(registry.getAttemptHistory("c1-006-terminal", "source", 0).size() >= 1,
                "submit must record assignment history (precondition)");

        JobCoordinator activeJob = manager.submit(spec("c1-006-active"));
        int activeHistoryBefore = registry.getAttemptHistory("c1-006-active", "source", 0).size();
        assertTrue(activeHistoryBefore >= 1, "active job must have assignment history (precondition)");

        // Terminal transition: stop(CANCEL) — the C1-006 cleanup anchor fires.
        manager.stop("c1-006-terminal", io.nop.stream.core.checkpoint.JobTerminationMode.CANCEL);

        assertEquals(0, registry.getAttemptHistory("c1-006-terminal", "source", 0).size(),
                "terminal job's assignment ledger entries must be removed at the terminal "
                        + "transition (C1-006 removeTaskAssignment wiring)");
        assertEquals(activeHistoryBefore, registry.getAttemptHistory("c1-006-active", "source", 0).size(),
                "active job's attempt history must be untouched (append semantics zero-change pin)");

        // Monotonic pin guard: further attempts on the active job still append.
        registry.assignTask("c1-006-active", "source", 0, "node-1", "attempt-x", 1L,
                activeHistoryBefore + 1);
        assertEquals(activeHistoryBefore + 1,
                registry.getAttemptHistory("c1-006-active", "source", 0).size(),
                "active job attempt history must stay append-only (monotonic pin)");
        assertNotEquals(terminalJob, activeJob);
    }
}
