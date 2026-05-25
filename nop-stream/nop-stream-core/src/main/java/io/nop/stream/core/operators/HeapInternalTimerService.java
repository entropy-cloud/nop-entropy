/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Heap-based implementation of {@link InternalTimerService} that stores event-time timers
 * in a {@link TreeMap} ordered by timestamp.
 *
 * <p>When {@link #advanceWatermark(long)} is called, all timers with timestamp <= the
 * new watermark are fired via the registered {@link Triggerable} callback.
 *
 * @param <N> the namespace type for timers
 */
public class HeapInternalTimerService<N> implements InternalTimerService<N> {

    private final TreeMap<Long, Set<TimerEntry<N>>> eventTimeTimers = new TreeMap<>();
    private final Triggerable<Object, N> triggerable;
    private final Supplier<Object> currentKeySupplier;
    private long currentWatermark = Long.MIN_VALUE;

    public HeapInternalTimerService(Triggerable<Object, N> triggerable) {
        this(triggerable, null);
    }

    public HeapInternalTimerService(Triggerable<Object, N> triggerable, Supplier<Object> currentKeySupplier) {
        this.triggerable = triggerable;
        this.currentKeySupplier = currentKeySupplier;
    }

    @Override
    public long currentProcessingTime() {
        return System.currentTimeMillis();
    }

    @Override
    public long currentWatermark() {
        return currentWatermark;
    }

    @Override
    public void registerProcessingTimeTimer(N namespace, long time) {
        // Processing time semantics not implemented per task constraints
    }

    @Override
    public void deleteProcessingTimeTimer(N namespace, long time) {
        // Processing time semantics not implemented per task constraints
    }

    @Override
    public void registerEventTimeTimer(N namespace, long time) {
        Object key = currentKeySupplier != null ? currentKeySupplier.get() : null;
        eventTimeTimers.computeIfAbsent(time, k -> new java.util.HashSet<>())
                .add(new TimerEntry<>(key, namespace, time));
    }

    @Override
    public void deleteEventTimeTimer(N namespace, long time) {
        Object key = currentKeySupplier != null ? currentKeySupplier.get() : null;
        Set<TimerEntry<N>> timers = eventTimeTimers.get(time);
        if (timers != null) {
            timers.remove(new TimerEntry<>(key, namespace, time));
            if (timers.isEmpty()) {
                eventTimeTimers.remove(time);
            }
        }
    }

    @Override
    public void forEachEventTimeTimer(BiConsumer<N, Long> consumer) throws Exception {
        for (Map.Entry<Long, Set<TimerEntry<N>>> entry : eventTimeTimers.entrySet()) {
            for (TimerEntry<N> timer : entry.getValue()) {
                consumer.accept(timer.namespace, timer.timestamp);
            }
        }
    }

    @Override
    public void forEachProcessingTimeTimer(BiConsumer<N, Long> consumer) throws Exception {
        // Processing time not implemented
    }

    /**
     * Advances the watermark and fires all event-time timers with timestamp <= newWatermark.
     *
     * @param newWatermark the new watermark to advance to
     */
    public void advanceWatermark(long newWatermark) throws Exception {
        if (newWatermark <= currentWatermark) {
            return;
        }
        currentWatermark = newWatermark;

        // Collect timers to fire (those with timestamp <= newWatermark)
        List<Map.Entry<Long, Set<TimerEntry<N>>>> toFire = new ArrayList<>();
        for (Map.Entry<Long, Set<TimerEntry<N>>> entry : eventTimeTimers.headMap(newWatermark, true).entrySet()) {
            toFire.add(entry);
        }

        // Fire timers and remove them
        for (Map.Entry<Long, Set<TimerEntry<N>>> entry : toFire) {
            List<TimerEntry<N>> timersToFire = new ArrayList<>(entry.getValue());
            for (TimerEntry<N> timer : timersToFire) {
                triggerable.onEventTime(new HeapInternalTimer<>(timer.key, timer.timestamp, timer.namespace));
            }
            eventTimeTimers.remove(entry.getKey());
        }
    }

    public int numEventTimeTimers() {
        return eventTimeTimers.values().stream().mapToInt(Set::size).sum();
    }

    private static class TimerEntry<N> {
        final Object key;
        final N namespace;
        final long timestamp;

        TimerEntry(Object key, N namespace, long timestamp) {
            this.key = key;
            this.namespace = namespace;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TimerEntry<?> that = (TimerEntry<?>) o;
            return timestamp == that.timestamp
                    && java.util.Objects.equals(key, that.key)
                    && java.util.Objects.equals(namespace, that.namespace);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(key, namespace, timestamp);
        }
    }

    private static class HeapInternalTimer<N> implements InternalTimer<Object, N> {
        private final Object key;
        private final long timestamp;
        private final N namespace;

        HeapInternalTimer(Object key, long timestamp, N namespace) {
            this.key = key;
            this.timestamp = timestamp;
            this.namespace = namespace;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public N getNamespace() {
            return namespace;
        }

        @Override
        public int comparePriorityTo(InternalTimer<?, ?> other) {
            return Long.compare(this.timestamp, other.getTimestamp());
        }
    }
}
