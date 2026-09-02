/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.runtime.rpc.RemotePipelineResolver;
import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * Stage 42 Phase 0: builds a single assigned subtask's
 * {@link StreamTaskInvokable} on the TaskManager side from a
 * {@link TaskDeploymentDescriptor}.
 *
 * <p><strong>Design rationale</strong>: the descriptor carries the full
 * {@link io.nop.stream.core.jobgraph.JobGraph} + {@link io.nop.stream.core.execution.plan.DeploymentPlan}
 * (both {@link java.io.Serializable}). Edge wiring (data-plane topics) is
 * derived <em>deterministically</em> from {@code jobId + edgeId + sourceIndex +
 * targetIndex} via {@link StreamTopicNaming#buildTopic}. Therefore a TaskManager
 * that runs {@link RemoteGraphExecutionPlanBuilder#buildRemoteOnly} locally —
 * with its own {@link IMessageService} instance connected to the shared backend
 * — produces the <strong>same topic names</strong> the coordinator would have
 * produced. The TaskManager then picks out the subtask assigned to it and
 * installs the resulting invokable.
 *
 * <p>This avoids (a) duplicating the complex edge-wiring / InputGate /
 * RecordWriter construction logic, and (b) attempting to serialize live runtime
 * objects ({@code StreamTaskInvokable} holds non-serializable
 * {@code RecordWriter}/{@code InputGate} referencing the message service). The
 * TaskManager rebuilds everything locally from serializable model metadata,
 * consistent with the model-first architecture.
 *
 * <p>Failure mode: if the assigned subtask cannot be found in the built plan
 * (vertex/parallelism mismatch), this builder fails fast with a
 * {@link StreamException} (plan guide #24 — no silent skip).
 */
@Internal
public final class SubtaskPlanBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(SubtaskPlanBuilder.class);

    private final IMessageService messageService;
    private final TypeRegistry typeRegistry;

    public SubtaskPlanBuilder(IMessageService messageService, TypeRegistry typeRegistry) {
        this.messageService = messageService;
        this.typeRegistry = typeRegistry != null ? typeRegistry : new TypeRegistry();
    }

    /**
     * Build the {@link StreamTaskInvokable} for the subtask identified by
     * {@code descriptor.getVertexId()} / {@code descriptor.getSubtaskIndex()}.
     *
     * <p>The invokable is wired with this TaskManager's {@link IMessageService}
     * instance, but connects to the same deterministic topics the coordinator's
     * view would have produced, so cross-JVM data exchange works.
     *
     * @param descriptor the deployment descriptor (carries the JobGraph + plan)
     * @return the locally-built invokable for the assigned subtask
     * @throws StreamException if the assigned subtask cannot be resolved
     */
    public StreamTaskInvokable buildSubtaskInvokable(TaskDeploymentDescriptor descriptor) {
        return buildSubtaskPlan(descriptor).getInvokable();
    }

    /**
     * Item 14 (composite-scenario distributed): builds the locally-reconstructed
     * full {@link GraphExecutionPlan} AND resolves the assigned subtask's
     * invokable. The full plan is returned because the TM-side checkpoint wiring
     * ({@code RemoteTaskDeploySupport}) needs it to build the checkpoint plan
     * and to run the rescale-aware restore (its subtask set mirrors the global
     * topology, including parallelism taken from the deployment plan).
     */
    /**
     * Item 14 (composite-scenario distributed): resolves the JobGraph for a
     * descriptor — either the carried graph (legacy/trivial path) or the locally
     * rebuilt one when the descriptor carries a {@link RemotePipelineSpec}
     * (XDSL-declared pipeline; the compiled graph is not Java-serializable, so the
     * declaration crosses the JVM boundary and every TaskManager rebuilds an
     * identical graph locally — stable transformation ids guarantee identity).
     *
     * <p>Spec resolution goes through {@link RemotePipelineResolver} discovered via
     * {@link java.util.ServiceLoader}; the runtime module cannot depend on the
     * flow/XDSL modules that own the declaration format. A spec without a
     * registered resolver fails fast (no silent fallback to an empty graph).
     */
    public DeployedSubtaskPlan buildSubtaskPlan(TaskDeploymentDescriptor descriptor) {
        if (descriptor == null) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, "descriptor");
        }

        io.nop.stream.core.jobgraph.JobGraph jobGraph = descriptor.getJobGraph();
        if (jobGraph == null && descriptor.getPipelineSpec() != null) {
            jobGraph = resolvePipelineSpec(descriptor.getPipelineSpec());
        }
        if (jobGraph == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "TaskDeploymentDescriptor carries neither a JobGraph nor a pipeline spec; "
                            + "cannot build subtask invokable for "
                            + descriptor.getVertexId() + "/" + descriptor.getSubtaskIndex());
        }

        long epoch = descriptor.getFencingEpoch();
        RemoteGraphExecutionPlanBuilder planBuilder = new RemoteGraphExecutionPlanBuilder(
                messageService, typeRegistry, epoch);

        GraphExecutionPlan plan = planBuilder.buildRemoteOnly(
                jobGraph, descriptor.getDeploymentPlan(), true);

        java.util.List<Subtask> subtasks = plan.getSubtasks(descriptor.getVertexId());
        if (subtasks == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "Vertex " + descriptor.getVertexId()
                            + " not found in the locally-built execution plan for job "
                            + descriptor.getJobId()
                            + ". Reconstructed vertices: " + plan.getSortedVertexIds());
        }
        for (Subtask subtask : subtasks) {
            if (subtask.getTaskIndex() == descriptor.getSubtaskIndex()) {
                LOG.info("SubtaskPlanBuilder built invokable for {}/{} (attempt={}) on local TaskManager",
                        descriptor.getVertexId(), descriptor.getSubtaskIndex(), descriptor.getAttemptId());
                return new DeployedSubtaskPlan(jobGraph, plan, subtask.getInvokable());
            }
        }
        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                "Subtask " + descriptor.getSubtaskIndex() + " of vertex " + descriptor.getVertexId()
                        + " not found. Reconstructed subtask indices for this vertex: "
                        + subtasks.stream().mapToInt(Subtask::getTaskIndex).boxed().collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Item 14: resolves a {@link RemotePipelineSpec} to a JobGraph via the
     * ServiceLoader-discovered {@link RemotePipelineResolver}. Fail-fast when no
     * resolver is on the classpath (rule #24 — never silently deploy an empty
     * graph).
     */
    private static io.nop.stream.core.jobgraph.JobGraph resolvePipelineSpec(
            io.nop.stream.runtime.rpc.RemotePipelineSpec spec) {
        RemotePipelineResolver resolver = null;
        for (RemotePipelineResolver candidate : java.util.ServiceLoader.load(RemotePipelineResolver.class)) {
            resolver = candidate;
            break;
        }
        if (resolver == null) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "TaskDeploymentDescriptor carries a RemotePipelineSpec but no "
                            + RemotePipelineResolver.class.getName()
                            + " is registered on the classpath (META-INF/services). "
                            + "The scenario module owning the XDSL declaration must provide one. Spec: " + spec);
        }
        try {
            io.nop.stream.core.jobgraph.JobGraph graph = resolver.buildJobGraph(spec);
            if (graph == null) {
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                        "RemotePipelineResolver returned a null JobGraph for spec: " + spec);
            }
            return graph;
        } catch (StreamException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL,
                    "RemotePipelineResolver failed to rebuild the JobGraph for spec: " + spec);
        }
    }

    /**
     * Item 14: the (carried or spec-rebuilt) JobGraph, the locally-built full plan,
     * and the assigned subtask's invokable. The graph is needed by the TM-side
     * checkpoint wiring for the StreamModel fingerprint (restore-time
     * compatibility check).
     */
    public static final class DeployedSubtaskPlan {
        private final io.nop.stream.core.jobgraph.JobGraph jobGraph;
        private final GraphExecutionPlan plan;
        private final StreamTaskInvokable invokable;

        DeployedSubtaskPlan(io.nop.stream.core.jobgraph.JobGraph jobGraph,
                            GraphExecutionPlan plan, StreamTaskInvokable invokable) {
            this.jobGraph = jobGraph;
            this.plan = plan;
            this.invokable = invokable;
        }

        public io.nop.stream.core.jobgraph.JobGraph getJobGraph() {
            return jobGraph;
        }

        public GraphExecutionPlan getPlan() {
            return plan;
        }

        public StreamTaskInvokable getInvokable() {
            return invokable;
        }
    }
}
