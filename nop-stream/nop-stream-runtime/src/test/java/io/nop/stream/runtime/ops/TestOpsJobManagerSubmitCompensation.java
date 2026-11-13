/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.runtime.ops;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.common.functions.sink.PrintSinkFunction;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2510 C4-009 / C10-004 (plan 2510 裁定④): a failure inside the submit window
 * (after the coordinator is started and tasks assigned, before the jobs
 * registration) must be compensated — the orphan coordinator is terminalized
 * (failure detector / periodic checkpoint scheduler / checkpoint commit
 * fan-out pool group all shut down), the registry keeps zero residue, and a
 * re-submit of the same jobId is safe (no second live coordinator).
 */
class TestOpsJobManagerSubmitCompensation {

    /** Endless long source (never actually runs in this test — submit aborts first). */
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
            StreamSourceOperator<Long> sourceOp = new StreamSourceOperator<>(
                    new EndlessSource());
            StreamSinkOperator<Long> sinkOp = new StreamSinkOperator<>(new PrintSinkFunction<>());
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
                    java.util.List.of(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD)),
                    null, null);
            DeploymentPlan deploymentPlan = new DeploymentPlan(
                    jobId, "pipeline-0", partitionedPlan, "local", "memory", "local", null, null);
            return new PipelineArtifacts(jobGraph, deploymentPlan, 500L, 10_000L, 3);
        }
    }

    /**
     * Fault-injection seam (裁定⑪ 缝4): throws once from the submit window after
     * start()/assignTasks() to simulate a mid-submit failure.
     */
    static final class FaultInjectingManager extends OpsJobManager {
        final AtomicBoolean failOnce = new AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicReference<JobCoordinator> lastCoordinator =
                new java.util.concurrent.atomic.AtomicReference<>();

        FaultInjectingManager(IMessageService messageService, InMemoryClusterRegistry registry,
                              LocalFileCheckpointStorage storage) {
            super(messageService, registry, storage, Map.of());
        }

        @Override
        protected void postAssignTasks(JobCoordinator coordinator, ClusterPipelineFactory.PipelineArtifacts artifacts) {
            lastCoordinator.set(coordinator);
            if (failOnce.compareAndSet(true, false)) {
                throw new IllegalStateException("injected submit-window failure (C4-009 fault seam)");
            }
        }
    }

    private static final String FACTORY_FQCN =
            "io.nop.stream.runtime.ops.TestOpsJobManagerSubmitCompensation$TrivialPipelineFactory";

    @TempDir
    Path tempDir;

    private FaultInjectingManager manager;
    private InMemoryClusterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryClusterRegistry();
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    private JobSubmissionSpec spec(String jobId) {
        JobSubmissionSpec spec = new JobSubmissionSpec();
        spec.setJobId(jobId);
        spec.setPipelineFactoryClass(FACTORY_FQCN);
        return spec;
    }

    @Test
    void submitWindowFailureLeavesZeroOrphansAndResubmitIsSafe() {
        manager = new FaultInjectingManager(new LocalMessageService(), registry,
                new LocalFileCheckpointStorage(tempDir.toString()));

        // The first submit fails inside the window (after start/assignTasks).
        assertThrows(IllegalStateException.class, () -> manager.submit(spec("c4-009-job")));

        // The orphan coordinator started inside the failed submit must have been
        // terminalized by the compensation (failure detector / scheduler pools
        // shut down) — captured via the fault seam.
        JobCoordinator orphan = manager.lastCoordinator.get();
        assertNotNull(orphan);
        assertFalse(orphan.isRunning(),
                "the orphan coordinator must be terminalized by the submit-window compensation "
                        + "(C4-009: live pool leak = second-live-coordinator risk)");

        // C4-009: zero orphans — the coordinator started inside the failed submit
        // must have been terminalized by the compensation (no live failure
        // detector / scheduler pools) and no registry entry may remain.
        assertTrue(manager.jobIds().isEmpty(), "no job registration may remain after a failed submit");
        assertTrue(manager.getTerminalAt().isEmpty(), "no terminal record may remain after a failed submit");

        // Re-submit of the same jobId must be safe (the orphan was terminalized,
        // so no second live coordinator exists).
        JobCoordinator resubmitted = manager.submit(spec("c4-009-job"));
        assertNotNull(resubmitted);
        assertTrue(resubmitted.isRunning(), "re-submit after a compensated failure must host a live coordinator");
        assertEquals(1, manager.jobIds().size());
    }
}
