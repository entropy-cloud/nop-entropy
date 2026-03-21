/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators;

import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.Triggerable;
import jakarta.annotation.Nonnull;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.BiConsumer;

/**
 * Timer service implementation for WindowOperator.
 * Uses priority queues for efficient timer management.
 */
public class WindowOperatorTimerService<K, N> implements InternalTimerService<N> {
    private final PriorityQueue<InternalTimer<K, N>> eventTimeTimers = new PriorityQueue<>(
            Comparator.comparingLong(InternalTimer::getTimestamp)
    );
    private final PriorityQueue<InternalTimer<K, N>> processingTimeTimers = new PriorityQueue<>(
            Comparator.comparingLong(InternalTimer::getTimestamp)
    );
    private final Triggerable<K, N> triggerable;
    private long currentWatermark = Long.MIN_VALUE;
    private long currentProcessingTime = Long.MIN_VALUE;
    public WindowOperatorTimerService(Triggerable<K, N> triggerable) {
        this.triggerable = triggerable;
    }
    @Override
    public long currentProcessingTime() {
        if (currentProcessingTime == Long.MIN_VALUE) {
            currentProcessingTime = System.currentTimeMillis();
        }
        return currentProcessingTime;
    }
    @Override
    public long currentWatermark() {
        return currentWatermark;
    }
    public void setCurrentWatermark(long watermark) {
        this.currentWatermark = watermark;
    }
    public void setCurrentProcessingTime(long time) {
        this.currentProcessingTime = time;
    }
    @Override
    public void registerEventTimeTimer(N namespace, long time) {
        InternalTimer<K, N> timer = new SimpleInternalTimer<>(time, null, namespace);
        if (!eventTimeTimers.contains(timer)) {
            eventTimeTimers.add(timer);
        }
    }
    @Override
    public void registerProcessingTimeTimer(N namespace, long time) {
        InternalTimer<K, N> timer = new SimpleInternalTimer<>(time, null, namespace);
        if (!processingTimeTimers.contains(timer)) {
            processingTimeTimers.add(timer);
        }
    }
    @Override
    public void deleteEventTimeTimer(N namespace, long time) {
        eventTimeTimers.removeIf(timer ->
                timer.getNamespace().equals(namespace) && timer.getTimestamp() == time);
    }
    @Override
    public void deleteProcessingTimeTimer(N namespace, long time) {
        processingTimeTimers.removeIf(timer ->
                timer.getNamespace().equals(namespace) && timer.getTimestamp() == time);
    }
    @Override
    public void forEachEventTimeTimer(BiConsumer<N, Long> consumer) throws Exception {
        for (InternalTimer<K, N> timer : eventTimeTimers) {
            consumer.accept(timer.getNamespace(), timer.getTimestamp());
        }
    }
    @Override
    public void forEachProcessingTimeTimer(BiConsumer<N, Long> consumer) throws Exception {
        for (InternalTimer<K, N> timer : processingTimeTimers) {
            consumer.accept(timer.getNamespace(), timer.getTimestamp());
        }
    }
    /**
     * Advances watermark and fires event-time timers
     */
    public void advanceWatermark(long watermark) throws Exception {
        this.currentWatermark = watermark;
        while (!eventTimeTimers.isEmpty()) {
            InternalTimer<K, N> timer = eventTimeTimers.peek();
            if (timer.getTimestamp() <= watermark) {
                eventTimeTimers.poll();
                if (triggerable != null) {
                    triggerable.onEventTime(timer);
                }
            } else {
                break;
            }
        }
    }
    /**
     * Advances processing time and fires processing-time timers
     */
    public void advanceProcessingTime(long timestamp) throws Exception {
        this.currentProcessingTime = timestamp;
        while (!processingTimeTimers.isEmpty()) {
            InternalTimer<K, N> timer = processingTimeTimers.peek();
            if (timer.getTimestamp() <= timestamp) {
                processingTimeTimers.poll();
                if (triggerable != null) {
                    triggerable.onProcessingTime(timer);
                }
            } else {
                break;
            }
        }
    }
    /**
     * Simple timer implementation
     */
    private static class SimpleInternalTimer<K, N> implements InternalTimer<K, N> {
        private final long timestamp;
        private final K key;
        private final N namespace;

        SimpleInternalTimer(long timestamp, K key, N namespace) {
            this.timestamp = timestamp;
            this.key = key;
            this.namespace = namespace;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }
        @Override
        public K getKey() {
            return key;
        }
        @Override
        public N getNamespace() {
            return namespace;
        }

        @Override
        public int comparePriorityTo(@Nonnull InternalTimer<?, ?> other) {
            return Long.compare(this.timestamp, other.getTimestamp());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SimpleInternalTimer)) {
                return false;
            }
            SimpleInternalTimer<?, ?> other = (SimpleInternalTimer<?, ?>) o;
            return this.timestamp == other.timestamp && 
                   (this.key == null ? other.key == null : this.key.equals(other.key)) && 
                   this.namespace.equals(other.namespace);
        }
        @Override
        public int hashCode() {
            int result = Long.hashCode(timestamp);
            result = 31 * result + (key != null ? key.hashCode() : 0);
            result = 31 * result + namespace.hashCode();
            return result;
        }
    }
}
