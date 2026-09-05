/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Gauge;

import io.nop.stream.core.metrics.StreamMetricsRegistries;

/**
 * Item 32 (D2/D2b/D2c): process-local gauge registration for
 * {@link RemoteInputChannel} queue depth — the data-plane channel backlog as a
 * DIRECT measurement (the {@code nop_stream_msg_queue} COUNT stays a proxy: the
 * JDBC transport table is INSERT-only, so its row count can only approximate
 * backlog).
 *
 * <p><strong>D2 (naming/ownership)</strong>: {@code nop.stream.io.channel.queue.size}
 * — io layer (the consumer-side input queue of the cross-task exchange; same
 * layer as {@code nop.stream.io.emit.time}). Prometheus wire name:
 * {@code nop_stream_io_channel_queue_size}. Tags: {@code jobId}/{@code edgeId}/
 * {@code sourceSubtask}/{@code targetSubtask} — the channel's full identity;
 * per-edge aggregation (max/sum) is a scrape-side concern (the exercise harness
 * takes max — the worst channel is the backpressure signal, sum would scale
 * with the channel-matrix size). No separate overflow gauge: the typed
 * {@code ERR_STREAM_CHANNEL_OVERFLOW} failure is already observable through
 * task failure reporting; the metric surface stays minimal.
 *
 * <p><strong>D2b (registration scope)</strong>: gauges are registered ONLY for
 * channels that activate their subscription ({@code subscribe==true}). The
 * coordinator form (zero-subscription) and construct-only mirror channels must
 * not produce permanently-zero gauges — that would be indistinguishable from a
 * healthy empty queue on the real consumers.
 *
 * <p><strong>D2c (re-registration / close semantics)</strong>: global recovery
 * re-assigns → the TM re-deploys → the plan is rebuilt → a NEW channel for the
 * same (jobId, edgeId, s, t) identity subscribes. Micrometer silently DROPS a
 * second registration of the same meter id (the old gauge object wins and —
 * holding a weak reference to a closed channel — would freeze or NaN). The
 * anti-trap shape here is a MUTABLE HOLDER: one holder per channel identity,
 * strongly referenced from a static map (no weak-reference loss), whose
 * {@code set} re-points the existing gauge at the new channel. Closing a
 * channel releases the binding (holder → null ⇒ gauge reads 0, never a frozen
 * stale value); a later rebuild rebinds the same gauge to the fresh channel.
 * Holder entries are intentionally never removed: removing + re-registering the
 * same meter id would resurrect the silent-drop trap. Growth is bounded by the
 * distinct channel identities ever active in the process (jobs × edges ×
 * subtask-pairs — a handful per job).
 */
public final class ChannelQueueGauges {

    /** D2: io-layer gauge name (Prometheus wire: nop_stream_io_channel_queue_size). */
    public static final String METRIC_NAME = "nop.stream.io.channel.queue.size";

    public static final String TAG_JOB_ID = "jobId";
    public static final String TAG_EDGE_ID = "edgeId";
    public static final String TAG_SOURCE_SUBTASK = "sourceSubtask";
    public static final String TAG_TARGET_SUBTASK = "targetSubtask";

    private static final ConcurrentMap<String, AtomicReference<RemoteInputChannel>> HOLDERS =
            new ConcurrentHashMap<>();

    private ChannelQueueGauges() {
    }

    /**
     * Binds (or re-binds, D2c) the queue-depth gauge of one channel identity to
     * the given channel. The first bind registers the meter; subsequent binds
     * for the same identity only re-point the existing gauge's holder.
     *
     * @param channel         the subscribing channel whose queue the gauge reports
     * @param jobId           job identity
     * @param edgeId          edge identity ("sourceVertex->targetVertex")
     * @param sourceSubtask   producer subtask index
     * @param targetSubtask   consumer subtask index
     */
    public static void bind(RemoteInputChannel channel, String jobId, String edgeId,
                            int sourceSubtask, int targetSubtask) {
        if (channel == null || jobId == null || edgeId == null) {
            return;
        }
        String key = gaugeKey(jobId, edgeId, sourceSubtask, targetSubtask);
        AtomicReference<RemoteInputChannel> holder = HOLDERS.computeIfAbsent(key, k -> {
            AtomicReference<RemoteInputChannel> ref = new AtomicReference<>(channel);
            Gauge.builder(METRIC_NAME, ref, ChannelQueueGauges::queueSizeOf)
                    .tag(TAG_JOB_ID, jobId)
                    .tag(TAG_EDGE_ID, edgeId)
                    .tag(TAG_SOURCE_SUBTASK, String.valueOf(sourceSubtask))
                    .tag(TAG_TARGET_SUBTASK, String.valueOf(targetSubtask))
                    .register(StreamMetricsRegistries.registry());
            return ref;
        });
        // Re-registration (recovery rebuild): the SAME meter now follows the new
        // channel — no second meter id is ever registered (micrometer would
        // silently drop it and keep the stale binding).
        holder.set(channel);
        channel.bindQueueGaugeHolder(holder);
    }

    /** Releases the gauge binding of a closing channel (holder → null ⇒ gauge reads 0). */
    static void release(RemoteInputChannel channel) {
        AtomicReference<RemoteInputChannel> holder = channel.queueGaugeHolder;
        if (holder != null) {
            holder.compareAndSet(channel, null);
        }
    }

    private static double queueSizeOf(AtomicReference<RemoteInputChannel> holder) {
        RemoteInputChannel channel = holder.get();
        return channel == null ? 0.0 : channel.queueSize();
    }

    private static String gaugeKey(String jobId, String edgeId, int sourceSubtask, int targetSubtask) {
        return jobId + '|' + edgeId + '|' + sourceSubtask + '|' + targetSubtask;
    }
}
