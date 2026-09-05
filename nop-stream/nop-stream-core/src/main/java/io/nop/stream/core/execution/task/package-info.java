/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
/**
 * Task execution family for stream processing jobs.
 * Contains Task, SubtaskTask, TaskExecutor, StreamTaskInvokable, Subtask, and
 * the shared TaskStateTransition state machine. These classes execute a
 * JobVertex's operator chains on task threads; the data-exchange and
 * control-plane components they use (RecordWriter, InputGate, MailboxExecutor,
 * TaskProcessingTimeService) remain in the parent
 * {@code io.nop.stream.core.execution} package.
 */
package io.nop.stream.core.execution.task;
