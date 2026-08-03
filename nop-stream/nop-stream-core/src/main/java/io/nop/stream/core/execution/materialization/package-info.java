/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
/**
 * Materialization point mechanism (option B: streaming + materialization point)
 * for region-based failover.
 *
 * <p>This package delivers the data-plane foundation that decouples producer and
 * consumer lifecycles at region boundaries: when a {@code JobEdge} is explicitly
 * marked as materialization-enabled, the producer side dual-writes every stream
 * element into the main in-flight queue <em>and</em> into an
 * {@link io.nop.stream.core.execution.materialization.IMaterializationPoint}
 * (bypass), tagged with the producer's current epoch. On recovery, the consumer
 * side can replay the materialized content.
 *
 * <p><b>In scope (this mechanism)</b>:
 * <ul>
 *   <li>materialization point SPI + in-memory implementation;</li>
 *   <li>epoch tagging on materialized data;</li>
 *   <li>{@code ResultPartition} dual-write bypass path;</li>
 *   <li>{@code InputChannel} replay path;</li>
 *   <li>{@code JobEdge} materialization-enabled marker (opt-in).</li>
 * </ul>
 *
 * <p><b>Out of scope (successor plans)</b>:
 * <ul>
 *   <li>consistent-cut alignment protocol (replay-start epoch selection) —
 *       successor 4 drain/reconnect;</li>
 *   <li>supervision loop / mid-execution restart activation — successor 3;</li>
 *   <li>region-boundary automatic identification — successor 2;</li>
 *   <li>per-region restart counter — successor 5;</li>
 *   <li>RocksDB/disk or cross-JVM materialization.</li>
 * </ul>
 *
 * @see io.nop.stream.core.execution.materialization.IMaterializationPoint
 * @see io.nop.stream.core.execution.materialization.InMemoryMaterializationPoint
 * @see io.nop.stream.core.execution.materialization.MaterializedElement
 */
package io.nop.stream.core.execution.materialization;
