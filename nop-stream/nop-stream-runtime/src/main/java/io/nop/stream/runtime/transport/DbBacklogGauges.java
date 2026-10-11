/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.stream.runtime.metrics.RuntimeMetrics;

import io.micrometer.core.instrument.Gauge;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WI6 (plan 2331): per-topic DB channel backlog gauge ({@code nop.stream.io.db.backlog}).
 * Registered ONLY when the producer gate is enabled (probe + positive limit) — no
 * permanently-zero gauges for un-gated channels (D2b rationale from
 * {@link ChannelQueueGauges}). Same mutable-holder anti-trap as ChannelQueueGauges:
 * re-deploys re-point the existing meter instead of re-registering (micrometer would
 * silently drop the second registration).
 *
 * <p>Distinct from {@code nop.stream.io.channel.queue.size}: that gauge measures the
 * consumer-side local queue; this one measures the persistence-backed channel table
 * (rows the producer deposited and consumers have not finished).
 */
public final class DbBacklogGauges {

    /** io-layer gauge name (Prometheus wire: nop_stream_io_db_backlog). */
    public static final String METRIC_NAME = "nop.stream.io.db.backlog";

    public static final String TAG_TOPIC = "topic";

    private static final ConcurrentMap<String, AtomicReference<RemoteResultPartition>> HOLDERS =
            new ConcurrentHashMap<>();

    private DbBacklogGauges() {
    }

    /** Binds (or re-binds) the backlog gauge of one gated topic to the partition. */
    public static void bind(RemoteResultPartition partition, String topic) {
        if (partition == null || topic == null) {
            return;
        }
        AtomicReference<RemoteResultPartition> holder = HOLDERS.computeIfAbsent(topic, k -> {
            AtomicReference<RemoteResultPartition> ref = new AtomicReference<>(partition);
            Gauge.builder(METRIC_NAME, ref, DbBacklogGauges::backlogOf)
                    .tag(TAG_TOPIC, topic)
                    .register(RuntimeMetrics.registry());
            return ref;
        });
        holder.set(partition);
    }

    private static double backlogOf(AtomicReference<RemoteResultPartition> holder) {
        RemoteResultPartition partition = holder.get();
        return partition == null ? 0.0 : partition.readBacklogCached();
    }
}
