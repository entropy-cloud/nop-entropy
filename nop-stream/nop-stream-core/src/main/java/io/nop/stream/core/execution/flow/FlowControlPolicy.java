/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.flow;

/**
 * Flow control strategy for in-process data exchange between
 * {@link io.nop.stream.core.execution.ResultPartition} instances.
 *
 * <p>Stage 28 (G27 decision): the Flink Netty credit-based flow control policies
 * were permanently removed from this enum — they are never needed in nop-stream.
 * In-process backpressure is provided by {@code BLOCKING_QUEUE} (per-partition
 * queue) + {@link io.nop.stream.core.execution.buffer.IBufferPool} (per-job
 * global quota). Cross-JVM backpressure is provided by the
 * {@code IMessageService} backend (Stage 40), not by this enum. See
 * {@code ai-dev/design/nop-stream/01-architecture-baseline.md} §五 G27.
 */
public enum FlowControlPolicy {

    BLOCKING_QUEUE
}
