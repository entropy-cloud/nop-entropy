/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.materialization;

import java.util.List;

import io.nop.stream.core.streamrecord.StreamElement;

/**
 * Materialization point SPI (option B: streaming + materialization point).
 *
 * <p>A materialization point is an independently addressable, epoch-tagged bypass
 * buffer attached to a region-boundary {@code ResultPartition}. When a
 * {@code JobEdge} is explicitly marked as materialization-enabled, the producer
 * side dual-writes every {@link StreamElement} into the main in-flight queue
 * <em>and</em> into this point (bypass), tagged with the producer's current
 * epoch. On recovery, the consumer side can replay the materialized content to
 * rebuild state without re-running the upstream producer.
 *
 * <p><b>Scope</b>: this SPI only governs the data-plane contract of a single
 * materialization point (write / replay / epoch query / seal). It deliberately
 * does not specify:
 * <ul>
 *   <li>the consistent-cut alignment protocol (replay-start epoch selection) —
 *       that belongs to drain/reconnect (successor plan 4);</li>
 *   <li>when replay is activated — that belongs to the supervision loop
 *       (successor plan 3);</li>
 *   <li>cross-JVM materialization — in-process correctness first;</li>
 *   <li>persistence (RocksDB/disk) — in-memory correctness first.</li>
 * </ul>
 *
 * <p><b>Thread-safety</b>: implementations must be safe for concurrent use by
 * one producer thread (write / seal) and replay callers (typically on the
 * recovery path, after the producer has been drained or sealed).
 *
 * <p><b>Epoch semantics</b>: each materialized element carries the epoch tag
 * supplied at write time. {@link #replay(long)} returns elements whose epoch is
 * {@code >= fromEpoch}, in write order. The epoch is opaque to this SPI; the
 * producer chooses its meaning (typically the current checkpoint id).
 *
 * @see MaterializedElement
 */
public interface IMaterializationPoint {

    /**
     * @return the independently addressable id of this point (stable across
     *         producer/consumer restarts within one job execution)
     */
    String getPointId();

    /**
     * Materializes a stream element with the given epoch tag (producer bypass
     * write). Called by {@code ResultPartition.write(...)} on the dual-write
     * path when a materialization point is attached.
     *
     * <p>Implementations must not silently drop the element. If the underlying
     * store is unavailable or sealed, this method fails fast (throws).
     *
     * @param element the element to materialize (must not be null)
     * @param epoch   the producer epoch to tag the element with
     * @throws InterruptedException if the current thread is interrupted while
     *                              waiting for store capacity
     */
    void write(StreamElement element, long epoch) throws InterruptedException;

    /**
     * Replays all materialized elements whose epoch is {@code >= fromEpoch},
     * in write order. Used by the consumer recovery path.
     *
     * @param fromEpoch the inclusive lower-bound epoch (consistent-cut marker)
     * @return the matching materialized elements in write order (never null;
     *         possibly empty)
     */
    List<MaterializedElement> replay(long fromEpoch);

    /**
     * Replays every materialized element regardless of epoch, in write order.
     *
     * @return all materialized elements in write order (never null; possibly empty)
     */
    List<MaterializedElement> replayAll();

    /**
     * Seals this point: the producer has finished writing. After seal, further
     * {@link #write(StreamElement, long)} calls fail fast. Idempotent.
     */
    void seal();

    /**
     * @return {@code true} if {@link #seal()} has been called
     */
    boolean isSealed();

    /**
     * @return the number of materialized elements currently stored
     */
    int size();

    /**
     * @return the epoch of the most recently written element, or {@code -1} if
     *         the point holds no elements
     */
    long getLastEpoch();
}
