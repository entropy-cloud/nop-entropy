/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.execution.IDeploymentPlanProvider;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;

/**
 * Runtime implementation of {@link IDeploymentPlanProvider} that
 * delegates to {@link DeploymentPlanGenerator}.
 */
@Internal
public class DeploymentPlanProviderImpl implements IDeploymentPlanProvider {

    private final DeploymentPlanGenerator generator = new DeploymentPlanGenerator();

    @Override
    public DeploymentPlan generateLocal(PartitionedPlan partitionedPlan) {
        return generator.generateLocal(partitionedPlan);
    }
}
