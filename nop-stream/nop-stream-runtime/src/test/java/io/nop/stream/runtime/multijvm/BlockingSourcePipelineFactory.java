/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.multijvm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.functions.sink.PrintSinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.StreamTaskInvokable;
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
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;

/**
 * Item 14 (composite-scenario distributed): a {@link ClusterPipelineFactory} whose
 * source NEVER completes on its own — it emits a heartbeat record every
 * {@code blockingEmitIntervalMs} until cancelled. Used by the legacy gated
 * kill/recover/fencing multi-JVM tests to make the kill <strong>deterministic</strong>:
 * with the previous trivial empty-collection source both tasks reached COMPLETED
 * within ~1s of deployment, so a {@code killTaskManager} that landed afterwards had
 * no RUNNING task to interrupt — no FAILED report, no recovery, no fencing rotation,
 * and the test degraded into a 90s timeout race against task completion. With this
 * factory the source task is guaranteed RUNNING at the kill, the SIGTERM interrupt
 * produces the FAILED report, and global recovery + fencing rotation fire
 * deterministically (the same observable path the fencing contract requires).
 *
 * <p>Checkpoints run periodically (default 1000ms, {@code blockingCheckpointIntervalMs})
 * so a kill typically lands with a checkpoint pending or recently completed —
 * exercising the recovery-time pending-abort path
 * ({@code CheckpointCoordinator.abortAllPendingCheckpoints} via globalRecovery) and
 * the post-recovery checkpoint restart.
 */
public class BlockingSourcePipelineFactory implements ClusterPipelineFactory {

    /** A source that emits incrementing heartbeat records forever until cancelled. */
    public static final class NeverEndingHeartbeatSource
            implements SourceFunction<Long>, java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private final long emitIntervalMs;
        private volatile boolean running = true;

        public NeverEndingHeartbeatSource(long emitIntervalMs) {
            this.emitIntervalMs = emitIntervalMs;
        }

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            long n = 0;
            while (running) {
                ctx.collect(n++);
                Thread.sleep(emitIntervalMs);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    @Override
    public PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) throws Exception {
        long emitIntervalMs = config.getLong("blockingEmitIntervalMs", 200L);
        long checkpointIntervalMs = config.getLong("blockingCheckpointIntervalMs", 1000L);

        StreamSourceOperator<Long> sourceOp = new StreamSourceOperator<>(
                new NeverEndingHeartbeatSource(emitIntervalMs));
        StreamSinkOperator<Long> sinkOp = new StreamSinkOperator<>(new PrintSinkFunction<>());

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));
        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(new JobVertex("source", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable));
        jobGraph.addVertex(new JobVertex("sink", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable));
        jobGraph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));

        Map<String, PartitionedPlan.VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new PartitionedPlan.VertexPlan("source", 1, null));
        vertexPlans.put("sink", new PartitionedPlan.VertexPlan("sink", 1, null));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                jobId, "pipeline-0", vertexPlans,
                List.of(new PartitionedPlan.EdgePlan("source", "sink", PartitionPolicy.FORWARD)),
                null, null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                jobId, "pipeline-0", partitionedPlan,
                "local", "memory", "local", null, null);

        return new PipelineArtifacts(jobGraph, deploymentPlan, checkpointIntervalMs, 30_000L, 3, null);
    }
}
