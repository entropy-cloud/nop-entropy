/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.List;

import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.jobgraph.JobGraph;

/**
 * Deployment entry point for distributed stream execution.
 *
 * <p><b>G26 / Stage 28 decision — intentional minimization</b>: this interface is
 * deliberately limited to three deployment-concern methods
 * ({@link #supportsDeploymentMode}, {@link #getExpectedNodeIds}, {@link #execute}).
 * It is <b>not</b> a job-lifecycle manager. Job lifecycle management (terminate,
 * checkpoint abort, status query) lives on the coordinator RPC surface
 * ({@code IStreamCoordinatorRpcService}) and the {@code JobCoordinator} that owns
 * the long-lived execution state.
 *
 * <p>Rationale: in the current synchronous {@code execute()} model the
 * {@code JobCoordinator} is a method-local variable inside {@code execute()} and
 * is destroyed when {@code execute()} returns (see
 * {@code EmbeddedDistributedExecutor}). Therefore a dispatcher-level lifecycle
 * method would have no coordinator instance to delegate to. An asynchronous
 * submit + poll dispatcher form (which would own a long-lived coordinator) is
 * deferred to Stage 39 (cross-JVM RPC). Until then, the dispatcher remains a
 * thin deployment entry point and all lifecycle control goes through the
 * coordinator RPC.
 */
public interface IStreamExecutionDispatcher {

    boolean supportsDeploymentMode(DeploymentMode mode);

    /**
     * Returns the ordered list of node identifiers that this dispatcher will bring
     * online for the given partitioned plan.
     *
     * <p>Used by {@code StreamExecutionEnvironment.generateDeploymentPlan()} to
     * materialize a physical subtask→node assignment into the
     * {@link DeploymentPlan} before execution begins.
     *
     * @param partitionedPlan the partitioned execution plan
     * @return ordered list of expected node identifiers (never empty)
     */
    List<String> getExpectedNodeIds(PartitionedPlan partitionedPlan);

    StreamExecutionResult execute(JobGraph jobGraph, PartitionedPlan partitionedPlan,
                                  DeploymentPlan deploymentPlan) throws Exception;
}
