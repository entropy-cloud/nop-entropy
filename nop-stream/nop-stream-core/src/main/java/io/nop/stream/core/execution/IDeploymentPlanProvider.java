/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ServiceLoader;

import io.nop.api.core.annotations.core.Internal;

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
     * @param partitionedPlan the partitioned execution plan
     * @return the deployment plan for local execution
     */
    DeploymentPlan generateLocal(PartitionedPlan partitionedPlan);

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
