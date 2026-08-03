/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
/**
 * Region concept and identification for region-based failover (Stage 44
 * successor plan 2, option B: streaming + materialization point).
 *
 * <p>This package delivers the <em>identification</em> half of region-based
 * failover: decomposing a {@link io.nop.stream.core.jobgraph.JobGraph} into
 * pipelined connected components (regions) by treating
 * materialization-enabled {@link io.nop.stream.core.jobgraph.JobEdge} instances
 * as region cut points. Non-materialization edges connect vertices inside the
 * same region; materialization-enabled edges cross region boundaries.
 *
 * <p><b>In scope (this mechanism)</b>:
 * <ul>
 *   <li>{@link io.nop.stream.core.jobgraph.region.RegionId} — typed region
 *       identifier;</li>
 *   <li>{@link io.nop.stream.core.jobgraph.region.Region} — a single
 *       pipelined connected component (region ID + member vertex IDs);</li>
 *   <li>{@link io.nop.stream.core.jobgraph.region.RegionDecomposition} — the
 *       full decomposition result (region list + vertex→region map);</li>
 *   <li>{@link io.nop.stream.core.jobgraph.region.RegionDecomposer} — the
 *       connected-component decomposition algorithm that consumes successor 1's
 *       {@code JobEdge.isMaterializationEnabled()} marker.</li>
 * </ul>
 *
 * <p><b>Zero regression</b>: an existing job whose edges have no materialization
 * marker decomposes into a single region (the entire graph = one pipelined
 * connected component), matching the pre-existing all-pipelined invariant.
 *
 * <p><b>Out of scope (successor plans)</b>:
 * <ul>
 *   <li>supervision loop / mid-execution restart — successor 3;</li>
 *   <li>drain/reconnect — successor 4;</li>
 *   <li>per-region restart counter — successor 5;</li>
 *   <li>region-aware scheduling (G55);</li>
 *   <li>cross-JVM region scheduling;</li>
 *   <li>region boundary dynamic adjustment (rescale interaction).</li>
 * </ul>
 *
 * @see io.nop.stream.core.jobgraph.region.RegionId
 * @see io.nop.stream.core.jobgraph.region.Region
 * @see io.nop.stream.core.jobgraph.region.RegionDecomposition
 * @see io.nop.stream.core.jobgraph.region.RegionDecomposer
 */
package io.nop.stream.core.jobgraph.region;
