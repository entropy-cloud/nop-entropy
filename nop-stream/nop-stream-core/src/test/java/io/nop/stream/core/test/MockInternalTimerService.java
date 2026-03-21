/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.test;

import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.Triggerable;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Mock implementation of InternalTimerService for testing.
 * 
 * <p>Allows controlling time advancement and timer firing in tests.
 *
 * @param <N> The type of namespace
 */
public class MockInternalTimerService<N> implements InternalTimerService<N> {

    private long currentProcessingTime = 0;
    private long currentWatermark = Long.MIN_VALUE;

    // Processing time timers: namespace -> sorted times
    private final Map<N, TreeMap<Long, TimerEntry>> processingTimeTimers = new HashMap<>();

    // Event time timers: namespace -> sorted times
    private final Map<N, TreeMap<Long, TimerEntry>> eventTimeTimers = new HashMap<>();

    // Triggerable to call when timer fires
    private Triggerable<N, ?> triggerable;

    // Track fired timers for verification
    private final PriorityQueue<TimerEntry> processingTimeQueue = new PriorityQueue<>();
    private final PriorityQueue<TimerEntry> eventTimeQueue = new PriorityQueue<>();

    public void setTriggerable(Triggerable<N, ?> triggerable) {
        this.triggerable = triggerable;
    }

    /**
     * Sets the current processing time.
     */
    public void setCurrentProcessingTime(long time) {
        this.currentProcessingTime = time;
    }

    /**
     * Sets the current watermark.
     */
    public void setCurrentWatermark(long watermark) {
        this.currentWatermark = watermark;
    }

    /**
     * Advances processing time and fires any registered timers.
     */
    public void advanceProcessingTime(long targetTime) throws Exception {
        if (targetTime < currentProcessingTime) {
            throw new IllegalArgumentException("Cannot advance backwards in time");
        }

        while (!processingTimeQueue.isEmpty()) {
            TimerEntry entry = processingTimeQueue.peek();
            if (entry.timestamp > targetTime) {
                break;
            }
            processingTimeQueue.poll();
            currentProcessingTime = entry.timestamp;
            fireTimer(entry);
        }

        currentProcessingTime = targetTime;
    }

    /**
     * Advances watermark and fires any registered event time timers.
     */
    public void advanceWatermark(long watermark) throws Exception {
        if (watermark < currentWatermark) {
            throw new IllegalArgumentException("Cannot advance watermark backwards");
        }

        while (!eventTimeQueue.isEmpty()) {
            TimerEntry entry = eventTimeQueue.peek();
            if (entry.timestamp > watermark) {
                break;
            }
            eventTimeQueue.poll();
            fireTimer(entry);
        }

        currentWatermark = watermark;
    }

    @SuppressWarnings("unchecked")
    private void fireTimer(TimerEntry entry) throws Exception {
        if (triggerable != null) {
            N namespace = (N) entry.namespace;
            if (entry.isProcessingTime) {
                ((Triggerable<N, Object>) triggerable).onProcessingTime(
                        new MockInternalTimer<>(entry.timestamp, (N) entry.key, namespace));
            } else {
                ((Triggerable<N, Object>) triggerable).onEventTime(
                        new MockInternalTimer<>(entry.timestamp, (N) entry.key, namespace));
            }
        }
    }

    @Override
    public long currentProcessingTime() {
        return currentProcessingTime;
    }

    @Override
    public long currentWatermark() {
        return currentWatermark;
    }

    @Override
    public void registerProcessingTimeTimer(N namespace, long time) {
        TimerEntry entry = new TimerEntry(time, null, (N) namespace, true);
        processingTimeQueue.add(entry);
        processingTimeTimers.computeIfAbsent(namespace, k -> new TreeMap<>())
                .put(time, entry);
    }

    @Override
    public void deleteProcessingTimeTimer(N namespace, long time) {
        TreeMap<Long, TimerEntry> timers = processingTimeTimers.get(namespace);
        if (timers != null) {
            TimerEntry entry = timers.remove(time);
            if (entry != null) {
                processingTimeQueue.remove(entry);
            }
        }
    }

    @Override
    public void registerEventTimeTimer(N namespace, long time) {
        TimerEntry entry = new TimerEntry(time, null, namespace, false);
        eventTimeQueue.add(entry);
        eventTimeTimers.computeIfAbsent(namespace, k -> new TreeMap<>())
                .put(time, entry);
    }

    @Override
    public void deleteEventTimeTimer(N namespace, long time) {
        TreeMap<Long, TimerEntry> timers = eventTimeTimers.get(namespace);
        if (timers != null) {
            TimerEntry entry = timers.remove(time);
            if (entry != null) {
                eventTimeQueue.remove(entry);
            }
        }
    }

    @Override
    public void forEachEventTimeTimer(BiConsumer<N, Long> consumer) {
        for (Map.Entry<N, TreeMap<Long, TimerEntry>> entry : eventTimeTimers.entrySet()) {
            for (Long time : entry.getValue().keySet()) {
                consumer.accept(entry.getKey(), time);
            }
        }
    }

    @Override
    public void forEachProcessingTimeTimer(BiConsumer<N, Long> consumer) {
        for (Map.Entry<N, TreeMap<Long, TimerEntry>> entry : processingTimeTimers.entrySet()) {
            for (Long time : entry.getValue().keySet()) {
                consumer.accept(entry.getKey(), time);
            }
        }
    }

    /**
     * Gets the number of registered processing time timers.
     */
    public int getNumProcessingTimeTimers() {
        return processingTimeQueue.size();
    }

    /**
     * Gets the number of registered event time timers.
     */
    public int getNumEventTimeTimers() {
        return eventTimeQueue.size();
    }

    /**
     * Checks if a processing time timer is registered for the given namespace and time.
     */
    public boolean hasProcessingTimeTimer(N namespace, long time) {
        TreeMap<Long, TimerEntry> timers = processingTimeTimers.get(namespace);
        return timers != null && timers.containsKey(time);
    }

    /**
     * Checks if an event time timer is registered for the given namespace and time.
     */
    public boolean hasEventTimeTimer(N namespace, long time) {
        TreeMap<Long, TimerEntry> timers = eventTimeTimers.get(namespace);
        return timers != null && timers.containsKey(time);
    }

    /**
     * Clears all timers.
     */
    public void clear() {
        processingTimeQueue.clear();
        eventTimeQueue.clear();
        processingTimeTimers.clear();
        eventTimeTimers.clear();
    }

    private static class TimerEntry implements Comparable<TimerEntry> {
        final long timestamp;
        final Object key;
        final Object namespace;
        final boolean isProcessingTime;

        TimerEntry(long timestamp, Object key, Object namespace, boolean isProcessingTime) {
            this.timestamp = timestamp;
            this.key = key;
            this.namespace = namespace;
            this.isProcessingTime = isProcessingTime;
        }

        @Override
        public int compareTo(TimerEntry o) {
            return Long.compare(this.timestamp, o.timestamp);
        }
    }
}
