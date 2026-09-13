/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.launch;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.runtime.rpc.RemotePipelineSpec;

/**
 * Item 14 (composite-scenario distributed): pluggable pipeline source for the
 * standalone {@link JobCoordinatorMain} launch path. Before this seam the
 * coordinator hardcoded the trivial empty-source pipeline
 * ({@code buildTrivialSourceSinkJobGraph}) — zero records, no scenario data
 * flow, no checkpoints. A factory installed via the {@code pipelineFactoryClass}
 * launch parameter supplies the REAL pipeline artifacts (scenario JobGraph +
 * DeploymentPlan + checkpoint tuning), which are then deployed to the
 * TaskManagers through the same remote-deploy {@code deployTask} RPC path.
 *
 * <p>Implementations live on the test classpath shared by every spawned JVM
 * (the harness spawns child JVMs with {@code java.class.path} of the test JVM),
 * so the factory itself only runs in the coordinator JVM; the produced
 * {@link JobGraph} is Java-serialized to each TaskManager inside the
 * {@code TaskDeploymentDescriptor}.
 *
 * <p>Scenario factories typically build the pipeline from the XDSL declaration
 * (parse → DSL builder → env → graph artifacts), honoring design D9: the
 * declaration is the XDSL; execution/recovery orchestration stays on the
 * runtime side.
 */
public interface ClusterPipelineFactory {

    /**
     * Builds the deployable pipeline artifacts for one job.
     *
     * @param jobId   the job id assigned by the launch config (also the TaskLocation
     *                family and the checkpoint storage key — must be used as the
     *                graph's identity, not a cosmetic name)
     * @param config  the full launch config (scenario parameters such as stream
     *                path, fixture paths, parallelism, and sink endpoints are read
     *                from here)
     * @return the artifacts; never null
     * @throws Exception on any construction failure (fails the coordinator fast)
     */
    PipelineArtifacts buildPipeline(String jobId, ClusterLaunchConfig config) throws Exception;

    /**
     * The deployable pipeline plus checkpoint tuning. All checkpoint fields are
     * optional ({@code null}/non-positive keeps the launch default).
     */
    final class PipelineArtifacts {
        private final JobGraph jobGraph;
        private final DeploymentPlan deploymentPlan;
        private final Long checkpointIntervalMs;
        private final Long checkpointTimeoutMs;
        private final Integer maxRetainedCheckpoints;
        /**
         * Item 14: optional serializable pipeline DECLARATION. When non-null the
         * launch ships this in the deployment descriptors instead of the compiled
         * graph (XDSL pipelines embed non-serializable compiled expressions; each
         * TaskManager rebuilds an identical graph locally — see
         * {@link RemotePipelineSpec}).
         */
        private final RemotePipelineSpec pipelineSpec;

        public PipelineArtifacts(JobGraph jobGraph,
                                 DeploymentPlan deploymentPlan,
                                 Long checkpointIntervalMs,
                                 Long checkpointTimeoutMs,
                                 Integer maxRetainedCheckpoints) {
            this(jobGraph, deploymentPlan, checkpointIntervalMs, checkpointTimeoutMs,
                    maxRetainedCheckpoints, null);
        }

        public PipelineArtifacts(JobGraph jobGraph,
                                 DeploymentPlan deploymentPlan,
                                 Long checkpointIntervalMs,
                                 Long checkpointTimeoutMs,
                                 Integer maxRetainedCheckpoints,
                                 RemotePipelineSpec pipelineSpec) {
            if (jobGraph == null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "jobGraph must not be null");
            }
            if (deploymentPlan == null) {
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "deploymentPlan must not be null");
            }
            this.jobGraph = jobGraph;
            this.deploymentPlan = deploymentPlan;
            this.checkpointIntervalMs = checkpointIntervalMs;
            this.checkpointTimeoutMs = checkpointTimeoutMs;
            this.maxRetainedCheckpoints = maxRetainedCheckpoints;
            this.pipelineSpec = pipelineSpec;
        }

        public JobGraph getJobGraph() {
            return jobGraph;
        }

        public DeploymentPlan getDeploymentPlan() {
            return deploymentPlan;
        }

        public Long getCheckpointIntervalMs() {
            return checkpointIntervalMs;
        }

        public Long getCheckpointTimeoutMs() {
            return checkpointTimeoutMs;
        }

        public Integer getMaxRetainedCheckpoints() {
            return maxRetainedCheckpoints;
        }

        public RemotePipelineSpec getPipelineSpec() {
            return pipelineSpec;
        }
    }
}
