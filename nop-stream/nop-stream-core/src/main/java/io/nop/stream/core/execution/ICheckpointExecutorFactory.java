/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.jobgraph.JobGraph;

/**
 * Factory interface for creating checkpoint-aware execution.
 *
 * <p>Since the core module cannot depend on the runtime module, this factory
 * allows the runtime module to register a checkpoint executor that the core
 * module can delegate to when checkpointing is enabled.
 */
@Internal
public interface ICheckpointExecutorFactory {

    StreamExecutionResult executeWithCheckpoint(
            JobGraph jobGraph,
            String jobName,
            CheckpointConfig checkpointConfig) throws Exception;

    /**
     * Triggers a savepoint for a running or completed job identified by the given job graph.
     * The savepoint is persisted via the configured {@link io.nop.stream.core.checkpoint.storage.ICheckpointStorage}.
     *
     * @param jobGraph       the job graph describing the pipeline
     * @param checkpointConfig checkpoint configuration including storage path
     * @param targetPath     hint for where to store the savepoint (may be used by storage impl)
     * @return the path where the savepoint was stored
     * @throws Exception if the savepoint could not be triggered or persisted
     */
    String triggerSavepoint(JobGraph jobGraph,
                            CheckpointConfig checkpointConfig,
                            String targetPath) throws Exception;

    /**
     * Executes a job graph, restoring operator states from a previously taken savepoint.
     *
     * @param jobGraph       the job graph describing the pipeline
     * @param jobName        human-readable job name
     * @param checkpointConfig checkpoint configuration
     * @param savepointPath  path to the savepoint to restore from (as returned by {@link #triggerSavepoint})
     * @return the execution result
     * @throws Exception if execution or state restoration fails
     */
    StreamExecutionResult executeWithSavepoint(JobGraph jobGraph,
                                               String jobName,
                                               CheckpointConfig checkpointConfig,
                                               String savepointPath) throws Exception;
}
