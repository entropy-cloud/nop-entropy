/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.runtime.operators.windowing;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.operators.InternalTimerService;
import io.nop.stream.core.operators.Triggerable;
import io.nop.stream.core.state.Keyed;
import io.nop.stream.core.state.KeyExtractorFunction;
import io.nop.stream.core.state.PriorityComparable;
import io.nop.stream.core.state.PriorityComparator;
import io.nop.stream.core.windowing.windows.Window;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Simple implementation of {@link InternalTimerService} that uses priority queues for timer
 * management.
 *
 * <p>This implementation maintains two priority queues: one for event-time timers and one for
 * processing-time timers. Timers are ordered by their timestamp, and timers are fired when the
 * watermark or processing time advances past the timer's timestamp.
 *
 * @param <K> Type of the keys to which timers are scoped.
 * @param <W> Type of the namespace to which timers are scoped.
 */
@Internal
public class SimpleInternalTimerService<K, W extends Window> implements InternalTimerService<W> {

    /** Priority queue for event-time timers, ordered by timestamp. */
    private final PriorityQueue<SimpleInternalTimer<K, W>> eventTimeTimers;

    /** Deduplication set for event-time timers. */
    private final Set<SimpleInternalTimer<K, W>> eventTimeTimerSet;

    /** Priority queue for processing-time timers, ordered by timestamp. */
    private final PriorityQueue<SimpleInternalTimer<K, W>> processingTimeTimers;

    /** Deduplication set for processing-time timers. */
    private final Set<SimpleInternalTimer<K, W>> processingTimeTimerSet;

    /** The current event-time watermark. */
    private long currentWatermark = Long.MIN_VALUE;

    /** The current processing time. */
    private long currentProcessingTime;

    /** The triggerable target to call when timers fire. */
    private final Triggerable<K, W> triggerTarget;

    /** The current key for timer registration. */
    private K currentKey;

    /**
     * Creates a new {@link SimpleInternalTimerService}.
     *
     * @param triggerTarget The triggerable target to call when timers fire.
     */
    public SimpleInternalTimerService(Triggerable<K, W> triggerTarget) {
        this.triggerTarget = triggerTarget;
        this.eventTimeTimers =
                new PriorityQueue<>(new InternalTimerComparator<>());
        this.eventTimeTimerSet = new HashSet<>();
        this.processingTimeTimers =
                new PriorityQueue<>(new InternalTimerComparator<>());
        this.processingTimeTimerSet = new HashSet<>();
        this.currentProcessingTime = System.currentTimeMillis();
    }

    /**
     * Sets the current key for timer registration.
     *
     * @param key The current key.
     */
    public void setCurrentKey(K key) {
        this.currentKey = key;
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
    public void registerProcessingTimeTimer(W namespace, long time) {
        SimpleInternalTimer<K, W> timer = new SimpleInternalTimer<>(time, currentKey, namespace);
        if (processingTimeTimerSet.add(timer)) {
            processingTimeTimers.add(timer);
        }
    }

    @Override
    public void deleteProcessingTimeTimer(W namespace, long time) {
        SimpleInternalTimer<K, W> timer = new SimpleInternalTimer<>(time, currentKey, namespace);
        if (processingTimeTimerSet.remove(timer)) {
            processingTimeTimers.remove(timer);
        }
    }

    @Override
    public void registerEventTimeTimer(W namespace, long time) {
        SimpleInternalTimer<K, W> timer = new SimpleInternalTimer<>(time, currentKey, namespace);
        if (eventTimeTimerSet.add(timer)) {
            eventTimeTimers.add(timer);
        }
    }

    @Override
    public void deleteEventTimeTimer(W namespace, long time) {
        SimpleInternalTimer<K, W> timer = new SimpleInternalTimer<>(time, currentKey, namespace);
        if (eventTimeTimerSet.remove(timer)) {
            eventTimeTimers.remove(timer);
        }
    }

    @Override
    public void forEachEventTimeTimer(BiConsumer<W, Long> consumer) throws Exception {
        for (SimpleInternalTimer<K, W> timer : eventTimeTimers) {
            consumer.accept(timer.getNamespace(), timer.getTimestamp());
        }
    }

    @Override
    public void forEachProcessingTimeTimer(BiConsumer<W, Long> consumer) throws Exception {
        for (SimpleInternalTimer<K, W> timer : processingTimeTimers) {
            consumer.accept(timer.getNamespace(), timer.getTimestamp());
        }
    }

    /**
     * Advances the event-time watermark to the given value and fires all event-time timers whose
     * timestamp is less than or equal to the new watermark.
     *
     * @param watermark The new watermark.
     * @throws Exception If an error occurs while firing timers.
     */
    public void advanceWatermark(long watermark) throws Exception {
        this.currentWatermark = watermark;
        while (!eventTimeTimers.isEmpty()) {
            SimpleInternalTimer<K, W> timer = eventTimeTimers.peek();
            if (timer.getTimestamp() <= watermark) {
                eventTimeTimers.poll();
                eventTimeTimerSet.remove(timer);
                triggerTarget.onEventTime(timer);
            } else {
                break;
            }
        }
    }

    /**
     * Advances the processing time to the given value and fires all processing-time timers whose
     * timestamp is less than or equal to the new time.
     *
     * @param time The new processing time.
     * @throws Exception If an error occurs while firing timers.
     */
    public void advanceProcessingTime(long time) throws Exception {
        this.currentProcessingTime = time;
        while (!processingTimeTimers.isEmpty()) {
            SimpleInternalTimer<K, W> timer = processingTimeTimers.peek();
            if (timer.getTimestamp() <= time) {
                processingTimeTimers.poll();
                processingTimeTimerSet.remove(timer);
                triggerTarget.onProcessingTime(timer);
            } else {
                break;
            }
        }
    }

    /**
     * Returns the number of event-time timers currently registered.
     *
     * @return The number of event-time timers.
     */
    public int numEventTimeTimers() {
        return eventTimeTimers.size();
    }

    /**
     * Returns the number of processing-time timers currently registered.
     *
     * @return The number of processing-time timers.
     */
    public int numProcessingTimeTimers() {
        return processingTimeTimers.size();
    }

    /**
     * Simple implementation of {@link InternalTimer} for use with {@link
     * SimpleInternalTimerService}.
     *
     * @param <K> Type of the key.
     * @param <W> Type of the namespace (window).
     */
    @Internal
    public static class SimpleInternalTimer<K, W extends Window>
            implements InternalTimer<K, W>, PriorityComparable<InternalTimer<?, ?>>, Keyed<K> {

        private final long timestamp;
        private final K key;
        private final W namespace;

        public SimpleInternalTimer(long timestamp, K key, W namespace) {
            this.timestamp = timestamp;
            this.key = key;
            this.namespace = namespace;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Nonnull
        @Override
        public K getKey() {
            return key;
        }

        @Nonnull
        @Override
        public W getNamespace() {
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
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SimpleInternalTimer<?, ?> timer = (SimpleInternalTimer<?, ?>) o;

            if (timestamp != timer.timestamp) {
                return false;
            }
            if (!key.equals(timer.key)) {
                return false;
            }
            return namespace.equals(timer.namespace);
        }

        @Override
        public int hashCode() {
            int result = (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + key.hashCode();
            result = 31 * result + namespace.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "SimpleInternalTimer{"
                    + "timestamp="
                    + timestamp
                    + ", key="
                    + key
                    + ", window="
                    + namespace
                    + '}';
        }
    }

    /**
     * Comparator for comparing {@link InternalTimer} instances by priority (timestamp).
     *
     * @param <K> Type of the key.
     * @param <W> Type of the namespace.
     */
    private static class InternalTimerComparator<K, W extends Window>
            implements PriorityComparator<InternalTimer<?, ?>>, java.util.Comparator<InternalTimer<?, ?>> {

        @Override
        public int comparePriority(InternalTimer<?, ?> left, InternalTimer<?, ?> right) {
            return Long.compare(left.getTimestamp(), right.getTimestamp());
        }

        @Override
        public int compare(InternalTimer<?, ?> left, InternalTimer<?, ?> right) {
            return comparePriority(left, right);
        }
    }
}
