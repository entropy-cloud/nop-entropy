/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
/**
 * Data-exchange and control-plane runtime components for stream processing:
 * result partitions / record writers / input gates (data plane), mailbox and
 * processing-time driver (control plane), graph execution planning, and
 * checkpoint barrier tracking. The Task execution family (Task, SubtaskTask,
 * TaskExecutor, StreamTaskInvokable, Subtask, TaskStateTransition) lives in
 * the {@code io.nop.stream.core.execution.task} subpackage.
 */
package io.nop.stream.core.execution;
