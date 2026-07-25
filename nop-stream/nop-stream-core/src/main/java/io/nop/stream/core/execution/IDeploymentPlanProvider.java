/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.List;
import java.util.ServiceLoader;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.execution.plan.DeploymentAssignment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;

/**
 * SPI interface for generating DeploymentPlan from PartitionedPlan.
 *
 * <p>The core module defines this interface, and the runtime module
 * provides the implementation via {@link ServiceLoader}. This avoids
 * a direct dependency from core to runtime.
 */
@Internal
public interface IDeploymentPlanProvider {

    /**
     * Generate a local DeploymentPlan from the given PartitionedPlan.
     *
     * <p>The generated plan has no physical subtask→node assignment
     * ({@link DeploymentPlan#getAssignment()} returns null); the runtime
     * performs round-robin assignment at execution time.
     *
     * @param partitionedPlan the partitioned execution plan
     * @return the deployment plan for local execution
     */
    DeploymentPlan generateLocal(PartitionedPlan partitionedPlan);

    /**
     * Generate a distributed DeploymentPlan that carries a materialized
     * subtask→node assignment (round-robin over the given active node set).
     *
     * <p>The generated plan's {@link DeploymentPlan#getAssignment()} will
     * contain the complete {@link DeploymentAssignment} so that
     * {@code JobCoordinator.assignTasks()} can consume it directly without
     * re-computing the mapping.
     *
     * @param partitionedPlan the partitioned execution plan
     * @param activeNodeIds   the ordered list of active node identifiers
     * @return the deployment plan for distributed execution
     * @throws io.nop.stream.core.exceptions.StreamException if {@code activeNodeIds} is empty
     */
    DeploymentPlan generateDistributed(PartitionedPlan partitionedPlan, List<String> activeNodeIds);

    /**
     * Load the IDeploymentPlanProvider via ServiceLoader.
     * Returns a no-op provider that creates a minimal DeploymentPlan
     * if no implementation is found on the classpath.
     */
    static IDeploymentPlanProvider getProvider() {
        for (IDeploymentPlanProvider provider : ServiceLoader.load(IDeploymentPlanProvider.class)) {
            return provider;
        }
        return DefaultDeploymentPlanProvider.INSTANCE;
    }
}
