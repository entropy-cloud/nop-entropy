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
}
