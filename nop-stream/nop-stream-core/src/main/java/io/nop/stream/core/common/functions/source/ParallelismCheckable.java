/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.source;

/**
 * Implemented by source functions that impose a deployment-time parallelism
 * constraint. The execution-plan builders ({@code GraphExecutionPlan} for the
 * embedded path and {@code RemoteGraphExecutionPlanBuilder} for the remote
 * path) call {@link #validateParallelism(int)} for every source vertex before
 * subtasks are created, so a function whose implementation cannot honor
 * {@code parallelism > 1} fails fast at deployment instead of producing
 * duplicated data at runtime (No-Silent-No-Op).
 *
 * <p>This is the deployment-time parallelism check adjudicated for the
 * batch-loader source (audit R5-CON-05): the function has no sharding
 * semantics, so {@code parallelism > 1} would deliver the full dataset once
 * per subtask copy.
 */
public interface ParallelismCheckable {

    /**
     * Validates that this source function supports deployment with the given
     * parallelism. Called once per vertex at execution-plan build time, before
     * any subtask copy exists.
     *
     * @param parallelism the effective parallelism of the source vertex
     * @throws StreamException if the function cannot run with this parallelism
     */
    void validateParallelism(int parallelism);
}
