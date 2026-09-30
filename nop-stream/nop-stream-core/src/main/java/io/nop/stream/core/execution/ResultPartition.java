/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.execution.buffer.IBufferPool;
import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_MATERIALIZE_WRITE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * A bounded buffer that holds {@link StreamElement} instances for inter-task data exchange.
 *
 * <p>ResultPartition is the producer side of the data exchange channel. It wraps a
 * {@link LinkedBlockingQueue} and provides thread-safe read/write operations for
 * single-producer single-consumer scenarios.
 *
 * <p>When the producer is done, it calls {@link #close()} to signal end-of-stream.
 * The consumer detects this via {@link #isFinished()} or by receiving an empty
 * result from {@link #read()} after the partition is closed and drained.
 */
public class ResultPartition implements IWriteStatus {

    private static final Logger LOG = LoggerFactory.getLogger(ResultPartition.class);

    /** Sentinel object placed into the queue to signal end-of-stream. */
    private static final StreamElement END_OF_STREAM = new StreamElement() {};

    /** Default bounded capacity for the internal queue. */
    public static final int DEFAULT_CAPACITY = 1024;

    private final LinkedBlockingQueue<StreamElement> queue;
    private final IBufferPool bufferPool;
    private volatile boolean finished;

    /**
     * Replay elements to deliver BEFORE any queue content. Attached by recovery
     * paths (materialization region restart via
     * {@link InputChannel#activateMaterializationReplay(long)}, unaligned channel
     * state restore via {@link InputChannel#injectElements(List)}) and consumed
     * by the read paths. Attaching is O(1) and never blocks — the recovery
     * thread (supervision loop) must not depend on a consumer draining the
     * bounded queue, and the replay set can exceed the queue capacity.
     *
     * <p>Ordering contract: pending replay → queue residual → EOS. The queue
     * sentinel is never placed in the deque; a finished partition yields EOS
     * only after the deque is drained, so the gate cannot mistake a timeout-null
     * for end-of-stream while replay data remains.
     *
     * <p>Permit accounting: replay elements hold NO buffer-pool permit (they
     * bypass the queue), so delivering them releases nothing and capturing them
     * (via {@link #drainBufferedElements()}) re-attaches them without acquire.
     * Queue elements keep the existing acquire-on-write / release-on-read flow.
     *
     * <p>Concurrency: attach happens-before the first consumer read (the
     * partition is handed to the consumer only after attachment); the queue is
     * lock-free and safe for the concurrent capture
     * ({@code drainBufferedElements} runs on the checkpoint thread while the
     * consumer reads). Not counted by {@link #size()}/{@link #isBackpressured()}
     * (observability-only gap, bounded by capture size).
     */
    private volatile java.util.concurrent.ConcurrentLinkedQueue<StreamElement> pendingReplay;

    /**
     * Attaches replay elements to be delivered ahead of all queue content.
     * O(1), never blocks, takes ownership of the collection. May be called
     * before the consumer starts (region restart rebuild) or concurrently with
     * reads (channel-state restore).
     *
     * <p>Single-active-segment contract (REG-07/AR-05): the attachment REPLACES
     * the {@link #pendingReplay} field. A second attach while a previous
     * segment still holds unconsumed elements would silently orphan them, so
     * that case fails fast. Callers must guarantee the previous segment is
     * exhausted or first captured via {@link #drainBufferedElements()} (both
     * production entries — region-restart rebuild and unaligned channel-state
     * restore — drain before attaching). Attaching over an exhausted (empty)
     * or never-attached field is the normal path and remains legal.
     *
     * @throws StreamException if a previous replay segment is still pending
     *         (not yet fully consumed)
     */
    public void attachPendingReplay(List<StreamElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        java.util.concurrent.ConcurrentLinkedQueue<StreamElement> existing = this.pendingReplay;
        if (existing != null && !existing.isEmpty()) {
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL,
                    "attachPendingReplay called while a previous replay segment with "
                            + existing.size() + " unconsumed element(s) is still pending; "
                            + "a second attach would silently orphan them. Drain or capture "
                            + "the pending segment first (see single-active-segment contract).");
        }
        this.pendingReplay = new java.util.concurrent.ConcurrentLinkedQueue<>(elements);
    }

    /**
     * Polls one pending replay element, or {@code null} when none remain.
     * Deliberately does NOT null out the field when drained: a concurrent
     * attach (channel-state restore while the consumer reads) could swap in a
     * fresh queue between our poll and the null-out, and clearing the field
     * would orphan its elements. An empty {@code ConcurrentLinkedQueue#poll}
     * costs a single volatile read, so the steady-state overhead is negligible.
     */
    private StreamElement pollPendingReplay() {
        java.util.concurrent.ConcurrentLinkedQueue<StreamElement> pending = this.pendingReplay;
        if (pending == null) {
            return null;
        }
        StreamElement element = pending.poll();
        if (element == null || element == END_OF_STREAM) {
            // The sentinel never enters the replay path (drain stops at it);
            // treat it defensively as end-of-stream rather than data.
            return null;
        }
        return element;
    }

    /**
     * Stage 44 successor 1 (materialization point mechanism, option B): optional
     * materialization bypass point. When non-null, {@link #write(StreamElement)}
     * dual-writes every element into the main queue <em>and</em> into this point
     * (tagged with {@link #currentMaterializationEpoch}). When {@code null}, the
     * partition follows the original by-reference path (default; zero regression
     * for existing jobs).
     *
     * <p>This field is attached by {@code GraphExecutionPlan.build(...)} when the
     * owning {@code JobEdge} is explicitly marked materialization-enabled. It is
     * the producer-side handle; the consumer side reads it via
     * {@link #getMaterializationPoint()} to perform recovery replay.
     */
    private volatile IMaterializationPoint materializationPoint;

    /**
     * The producer epoch used to tag materialized elements on the dual-write
     * bypass. Stage 44 successor 4 (consistent-cut epoch alignment): advanced to
     * {@code barrier.getId()} whenever a {@link CheckpointBarrier} flows through
     * {@link #write(StreamElement)}, so each materialized record's epoch equals
     * the checkpoint id of the most recent barrier that preceded it. This lets
     * the consumer-side replay select a checkpoint-aligned replay start point.
     * Explicit {@link #setCurrentMaterializationEpoch(long)} is still available
     * for tests and producers that manage their own epoch policy.
     */
    private volatile long currentMaterializationEpoch = 0L;

    /**
     * Creates a ResultPartition with the default capacity (1024) and no global pool.
     */
    public ResultPartition() {
        this(DEFAULT_CAPACITY, null);
    }

    /**
     * Creates a ResultPartition with the specified capacity and no global pool.
     *
     * <p>When no pool is attached ({@code bufferPool == null}), only the per-partition
     * bounded queue limits in-flight elements (legacy behavior). Direct-construction
     * callers (tests, {@code RemoteResultPartition}, {@code RemoteInputChannel}) use this path.
     *
     * @param capacity the bounded queue capacity (must be positive)
     * @throws IllegalArgumentException if capacity is not positive
     */
    public ResultPartition(int capacity) {
        this(capacity, null);
    }

    /**
     * Creates a ResultPartition bound to a global {@link IBufferPool}.
     *
     * <p>When {@code bufferPool != null}, each {@link #write(StreamElement)} acquires one
     * permit from the pool before enqueueing (blocking if the global pool is exhausted),
     * and each consumed element releases one permit back. The per-partition queue capacity
     * remains in force: write blocks on the queue when full (per-partition backpressure),
     * and blocks on the pool when the aggregate cross-partition budget is exhausted
     * (global backpressure). This is the production {@code GraphExecutionPlan.build()}
     * path.
     *
     * <p>When {@code bufferPool == null}, this behaves identically to
     * {@link #ResultPartition(int)} (no global aggregation constraint).
     *
     * @param capacity   the bounded queue capacity (must be positive)
     * @param bufferPool optional global buffer pool (nullable)
     * @throws IllegalArgumentException if capacity is not positive
     */
    public ResultPartition(int capacity, IBufferPool bufferPool) {
        if (capacity <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "capacity").param(ARG_DETAIL, "must be positive, got: " + capacity);
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.bufferPool = bufferPool;
        this.finished = false;
    }

    /**
     * Writes a stream element into the partition, blocking if the queue is full.
     *
     * <p>Stage 44 successor 1 (materialization point mechanism): when a
     * {@link IMaterializationPoint materialization point} is attached, this
     * method <em>dual-writes</em> — the element is written to the main queue
     * (by-reference in-flight path, unchanged) <em>and</em> to the bypass
     * materialization store, tagged with the current
     * {@link #setCurrentMaterializationEpoch(long) producer epoch}. When no point
     * is attached, the original by-reference path is followed (default behavior;
     * zero regression for existing jobs).
     *
     * <p>Stage 44 successor 4 (consistent-cut epoch alignment + control-event
     * filtering):
     * <ul>
     *   <li>When the element is a {@link CheckpointBarrier}, the producer epoch is
     *       advanced to {@code barrier.getId()} (checkpoint-id alignment) so that
     *       subsequent data records are tagged with the checkpoint id. This lets
     *       the consumer-side replay select a checkpoint-aligned replay start
     *       ({@code replay(epoch >= ckpId)} returns precisely post-checkpoint
     *       records).</li>
     *   <li>Control events (barrier, watermark, watermark-status, latency marker)
     *       are <strong>not</strong> dual-written to the materialization store —
     *       only data records are. Persisting control events would pollute the
     *       store and inject spurious barriers/watermarks on replay.</li>
     * </ul>
     *
     * <p>The bypass write fails fast (throws) if the materialization store
     * rejects the element; the producer does not silently continue with a
     * divergent main-queue/materialization-store pair (No-Silent-No-Op).
     *
     * <p>Overflow-bypass (deadlock release): when a
     * materialization point is attached, the main-queue write is
     * <strong>non-blocking</strong> ({@code queue.offer}). If the queue is full,
     * the element is not enqueued — but it has already been dual-written to the
     * materialization store, so no data is lost and the producer does not block
     * indefinitely on a dead/slow consumer. When no point is attached, the
     * original blocking {@code queue.put} path is preserved (zero regression for
     * existing jobs and non-materialization edges).
     *
     * @param element the element to write (must not be null)
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws IllegalStateException if the partition is already finished
     */
    public void write(StreamElement element) throws InterruptedException {
        if (element == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "element");
        }
        if (finished) {
            if (element.isCheckpointBarrier()) {
                // A checkpoint barrier written to an already-finished partition is
                // redundant by construction: the executor injects the barrier into
                // EVERY task of the job (triggerBarrierOnAllInvokables), so the
                // consuming vertex received its own injection, and the producing
                // task already finished (its data stream is complete). This happens
                // on the best-effort CANCEL-mode final checkpoint, which races the
                // natural termination of bounded jobs — throwing here turned a
                // benign terminal checkpoint into a job failure. The barrier is
                // dropped with a log so the race stays observable; data records on
                // finished partitions still fail fast below.
                LOG.debug("Dropped checkpoint barrier {} written to finished partition",
                        ((CheckpointBarrier) element).getId());
                return;
            }
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Cannot write to a finished ResultPartition");
        }
        // Stage 44 successor 4 (consistent-cut epoch alignment): when a checkpoint
        // barrier flows through write(), advance the materialization epoch to the
        // barrier's checkpoint id. All subsequent data elements are tagged with
        // this epoch on the dual-write bypass, so the consumer-side replay can
        // select a checkpoint-aligned replay start point (replay(epoch >= ckpId)
        // returns precisely the post-checkpoint records). The barrier itself is
        // NOT a data element and is filtered from the materialization store.
        if (element.isCheckpointBarrier()) {
            long barrierId = ((CheckpointBarrier) element).getId();
            this.currentMaterializationEpoch = barrierId;
        }
        // Stage 44 successor 1: dual-write bypass. Snapshot the point locally so a
        // concurrent detach does not split the dual-write into a partial state.
        IMaterializationPoint point = this.materializationPoint;
        dualWriteToMaterialization(point, element);
        enqueueWithBackpressure(point, element);
    }

    /**
     * Stage 44 successor 1/4: dual-writes a data record into the attached
     * materialization bypass point, tagged with the current producer epoch.
     * Control events (CheckpointBarrier, Watermark, WatermarkStatus,
     * LatencyMarker) are NOT dual-written — only data records are. Persisting
     * control events would pollute the store and inject spurious barriers/
     * watermarks on replay; the epoch bump for barriers still happens in
     * {@link #write(StreamElement)}. SideOutputElement is likewise NOT
     * dual-written (isRecord() is false, so the gate excludes it naturally;
     * HG-01 Phase 1 decision D6 — best-effort delivery semantics). The bypass
     * write fails fast (throws) if the materialization store rejects the
     * element; the producer does not silently continue with a divergent
     * main-queue/materialization-store pair (No-Silent-No-Op).
     */
    private void dualWriteToMaterialization(IMaterializationPoint point,
                                            StreamElement element) throws InterruptedException {
        if (point == null || !element.isRecord()) {
            return;
        }
        try {
            point.write(element, currentMaterializationEpoch);
        } catch (InterruptedException ie) {
            throw ie;
        } catch (RuntimeException rex) {
            throw new StreamException(ERR_STREAM_MATERIALIZE_WRITE_FAILED, rex)
                    .param(NopStreamErrors.ARG_POINT_ID, point.getPointId())
                    .param(ARG_DETAIL, rex.getMessage());
        }
    }

    /**
     * Enqueues the element on the main queue with the quadrant-specific
     * backpressure policy (materialization attached or not × pool attached or
     * not), preserving the ordering and guards of the pre-extraction write path:
     * <ul>
     *   <li>pool + materialization: non-blocking offer under an optimistically
     *       acquired permit; on queue-full the permit is released and the record
     *       overflows to the materialization store only (Stage 44 successor 4
     *       Phase 2 overflow-bypass — the producer never blocks indefinitely on
     *       a dead/slow consumer — failover-design.md §9.4 deadlock case)</li>
     *   <li>pool, no materialization: blocking put; the permit is returned when
     *       the put is interrupted so it cannot leak</li>
     *   <li>no pool + materialization: non-blocking offer, no permit accounting</li>
     *   <li>no pool, no materialization: legacy blocking put (zero regression)</li>
     * </ul>
     */
    private void enqueueWithBackpressure(IMaterializationPoint point,
                                         StreamElement element) throws InterruptedException {
        if (bufferPool != null) {
            if (point != null) {
                bufferPool.acquire();
                if (!queue.offer(element)) {
                    // Queue full — overflow to the materialization store only
                    // (already written above). Release the permit and continue.
                    bufferPool.release();
                }
            } else {
                bufferPool.acquire();
                try {
                    queue.put(element);
                } catch (InterruptedException e) {
                    // Acquired a permit but never enqueued; return it to avoid a leak.
                    bufferPool.release();
                    throw e;
                }
            }
        } else {
            if (point != null) {
                // Overflow-bypass without a pool: non-blocking offer, no permit
                // accounting. Data already in the materialization store.
                queue.offer(element);
            } else {
                // Legacy blocking path (no materialization → zero regression).
                queue.put(element);
            }
        }
    }

    /**
     * Reads the next stream element from the partition, blocking until one is available.
     *
     * <p>Pending replay elements (attached by recovery paths) are delivered
     * ahead of all queue content. Returns {@code null} if the partition is
     * finished and neither replay nor queue elements remain.
     *
     * @return the next element, or null if end-of-stream
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public StreamElement read() throws InterruptedException {
        StreamElement pending = pollPendingReplay();
        if (pending != null) {
            return pending;
        }
        StreamElement element = queue.take();
        if (element == END_OF_STREAM) {
            return null;
        }
        if (bufferPool != null) {
            bufferPool.release();
        }
        return element;
    }

    /**
     * Reads the next stream element with a timeout.
     *
     * <p>Pending replay elements (attached by recovery paths) are delivered
     * ahead of all queue content and without waiting; the timeout applies only
     * to the queue wait. The gate loop relies on this overload — a finished
     * partition with undelivered replay elements must keep returning data here
     * instead of a null that the gate would misread as end-of-stream.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout
     * @return the next element, or null if timeout elapsed or end-of-stream reached
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public StreamElement read(long timeout, TimeUnit unit) throws InterruptedException {
        StreamElement pending = pollPendingReplay();
        if (pending != null) {
            return pending;
        }
        StreamElement element = queue.poll(timeout, unit);
        if (element == null) {
            // Timeout - return null but don't mark as finished
            return null;
        }
        if (element == END_OF_STREAM) {
            return null;
        }
        if (bufferPool != null) {
            bufferPool.release();
        }
        return element;
    }

    /**
     * Signals that the producer has finished writing.
     *
     * <p>This method places a sentinel value into the queue so that any consumer
     * currently blocked on {@link #read()} will wake up and detect end-of-stream.
     *
     * <p>P1-10: when the queue is full, the prior implementation called
     * {@code queue.clear()} to make room for the sentinel — silently discarding
     * every in-flight record the consumer had not yet seen. That broke at-least-once
     * delivery for the bounded-source EOS path. The fix uses a blocking
     * {@code queue.put(END_OF_STREAM)}: the producer thread blocks on natural
     * backpressure until the consumer drains enough room, then enqueues the
     * sentinel. No data is lost; an {@code InterruptedException} is propagated
     * to the caller so cancel/abort still unblocks the producer.
     */
    public void close() throws InterruptedException {
        finished = true;
        try {
            // Blocking put: respects backpressure and never drops in-flight data.
            queue.put(END_OF_STREAM);
        } catch (InterruptedException ie) {
            // Restore interrupt status and rethrow so callers (cancel/abort) can
            // still tear down the producer. The sentinel was never placed, so the
            // consumer must rely on its own cancel path; we explicitly do NOT
            // discard pending elements here.
            Thread.currentThread().interrupt();
            throw ie;
        }
    }

    /**
     * Returns whether this partition has been closed by the producer.
     * Note that elements may still remain in the queue after close.
     *
     * @return true if the producer has called close()
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Marks this partition as finished. Used by remote partition implementations
     * that do not use the internal queue.
     */
    protected void markFinished() {
        this.finished = true;
    }

    /**
     * Returns the current number of elements waiting in the queue.
     *
     * @return approximate queue size
     */
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isBackpressured() {
        return queue.remainingCapacity() < (queue.size() * 0.2 + 1);
    }

    @Override
    public int getAvailableCapacity() {
        return queue.remainingCapacity();
    }

    @Override
    public int getTotalCapacity() {
        return queue.size() + queue.remainingCapacity();
    }

    /**
     * Returns the global buffer pool attached to this partition, or {@code null}
     * if this partition operates in legacy (per-partition-only) mode.
     *
     * <p>Used by wiring verification tests to assert that production-built partitions
     * share a single per-job pool instance.
     *
     * @return the attached pool, or {@code null}
     */
    public IBufferPool getBufferPool() {
        return bufferPool;
    }

    /**
     * Stage 43 (unaligned checkpoint): drains and returns all currently buffered
     * elements WITHOUT blocking, excluding the end-of-stream sentinel. Used by
     * {@link InputChannel#captureInFlightData(boolean)} to snapshot in-flight data
     * at unaligned-checkpoint time. Elements are <em>moved</em> (removed from the
     * queue), not copied — each removed element releases one buffer-pool permit
     * when a pool is attached, so global backpressure accounting stays consistent.
     *
     * <p>This method does NOT mark the partition finished and does NOT inject the
     * EOS sentinel; the producer may continue writing after capture.
     *
     * @return a list of the buffered elements (possibly empty); never null
     */
    public List<StreamElement> drainBufferedElements() {
        List<StreamElement> drained = new ArrayList<>();
        // Pending replay first (preserves the pending → queue delivery order in
        // the captured list). Replay elements hold no permit, so draining them
        // releases nothing.
        StreamElement pendingElement;
        while ((pendingElement = pollPendingReplay()) != null) {
            drained.add(pendingElement);
        }
        StreamElement e;
        while ((e = queue.poll()) != null) {
            if (e == END_OF_STREAM) {
                // Do not hand out the sentinel; it is a terminal signal, not data.
                // Stop draining once the sentinel is observed.
                break;
            }
            if (bufferPool != null) {
                bufferPool.release();
            }
            drained.add(e);
        }
        return drained;
    }

    // ----------------------------------------------------------------------
    // Stage 44 successor 1 — materialization point mechanism (option B)
    // ----------------------------------------------------------------------

    /**
     * Attaches a materialization bypass point to this partition. Once attached,
     * {@link #write(StreamElement)} dual-writes every element into the main queue
     * <em>and</em> into the bypass point (tagged with
     * {@link #setCurrentMaterializationEpoch(long) the current producer epoch}).
     *
     * <p>This is the producer-side handle. The consumer side reads it via
     * {@link #getMaterializationPoint()} to perform recovery replay.
     *
     * <p>Passing {@code null} detaches the point (disables dual-write); this is
     * the default state for existing jobs (zero regression).
     *
     * @param point the materialization point, or {@code null} to disable bypass
     */
    public void setMaterializationPoint(IMaterializationPoint point) {
        this.materializationPoint = point;
    }

    /**
     * @return the attached materialization bypass point, or {@code null} if this
     *         partition operates in legacy (by-reference-only) mode
     */
    public IMaterializationPoint getMaterializationPoint() {
        return materializationPoint;
    }

    /**
     * @return {@code true} if a materialization bypass point is attached (i.e.
     *         the owning {@code JobEdge} is materialization-enabled and the
     *         producer dual-writes)
     */
    public boolean isMaterializationEnabled() {
        return materializationPoint != null;
    }

    /**
     * Sets the producer epoch used to tag elements on the dual-write bypass.
     * Advanced by the producer (typically aligned with checkpoint id). All
     * subsequent {@link #write(StreamElement)} calls tag the bypass-written
     * element with this epoch.
     *
     * @param epoch the new producer epoch
     */
    public void setCurrentMaterializationEpoch(long epoch) {
        this.currentMaterializationEpoch = epoch;
    }

    /**
     * @return the producer epoch used to tag the next bypass-written element
     */
    public long getCurrentMaterializationEpoch() {
        return currentMaterializationEpoch;
    }
}
