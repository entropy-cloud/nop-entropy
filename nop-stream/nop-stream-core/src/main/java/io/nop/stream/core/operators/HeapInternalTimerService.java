/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Heap-based implementation of {@link InternalTimerService} that stores event-time timers
 * in a {@link TreeMap} ordered by timestamp.
 *
 * <p>When {@link #advanceWatermark(long)} is called, all timers with timestamp <= the
 * new watermark are fired via the registered {@link Triggerable} callback.
 *
 * <p>This is the unified timer service used by both {@code WindowOperator} and {@code ProcessOperator}.
 *
 * @param <K> the key type to which timers are scoped
 * @param <N> the namespace type for timers
 */
public class HeapInternalTimerService<K, N> implements InternalTimerService<N> {

    private final TreeMap<Long, Set<TimerEntry<K, N>>> eventTimeTimers = new TreeMap<>();
    private final TreeMap<Long, Set<TimerEntry<K, N>>> processingTimeTimers = new TreeMap<>();
    private final Triggerable<K, N> triggerable;
    private final Supplier<K> currentKeySupplier;
    private long currentWatermark = Long.MIN_VALUE;

    /**
     * Earliest registered processing-time timer, or {@link Long#MAX_VALUE} when none is
     * registered. Written by the owning task thread (register / delete / fire / restore),
     * read by the {@code ProcessingTimeServiceDriver} scheduler thread — a single volatile
     * read, so the scheduler never touches the timer map itself.
     */
    private volatile long nextProcessingTimeTimer = Long.MAX_VALUE;

    public HeapInternalTimerService(Triggerable<K, N> triggerable) {
        this(triggerable, null);
    }

    public HeapInternalTimerService(Triggerable<K, N> triggerable, Supplier<K> currentKeySupplier) {
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
        K key = currentKeySupplier != null ? currentKeySupplier.get() : null;
        processingTimeTimers.computeIfAbsent(time, k -> new HashSet<>())
                .add(new TimerEntry<>(key, namespace, time));
        if (time < nextProcessingTimeTimer) {
            nextProcessingTimeTimer = time;
        }
    }

    @Override
    public void deleteProcessingTimeTimer(N namespace, long time) {
        K key = currentKeySupplier != null ? currentKeySupplier.get() : null;
        Set<TimerEntry<K, N>> timers = processingTimeTimers.get(time);
        if (timers != null) {
            timers.remove(new TimerEntry<>(key, namespace, time));
            if (timers.isEmpty()) {
                processingTimeTimers.remove(time);
                recomputeNextProcessingTimeTimer();
            }
        }
    }

    @Override
    public void registerEventTimeTimer(N namespace, long time) {
        K key = currentKeySupplier != null ? currentKeySupplier.get() : null;
        eventTimeTimers.computeIfAbsent(time, k -> new HashSet<>())
                .add(new TimerEntry<>(key, namespace, time));
    }

    @Override
    public void deleteEventTimeTimer(N namespace, long time) {
        K key = currentKeySupplier != null ? currentKeySupplier.get() : null;
        Set<TimerEntry<K, N>> timers = eventTimeTimers.get(time);
        if (timers != null) {
            timers.remove(new TimerEntry<>(key, namespace, time));
            if (timers.isEmpty()) {
                eventTimeTimers.remove(time);
            }
        }
    }

    @Override
    public void forEachEventTimeTimer(BiConsumer<N, Long> consumer) throws Exception {
        for (Map.Entry<Long, Set<TimerEntry<K, N>>> entry : eventTimeTimers.entrySet()) {
            for (TimerEntry<K, N> timer : entry.getValue()) {
                consumer.accept(timer.namespace, timer.timestamp);
            }
        }
    }

    @Override
    public void forEachProcessingTimeTimer(BiConsumer<N, Long> consumer) throws Exception {
        for (Map.Entry<Long, Set<TimerEntry<K, N>>> entry : processingTimeTimers.entrySet()) {
            for (TimerEntry<K, N> timer : entry.getValue()) {
                consumer.accept(timer.namespace, timer.timestamp);
            }
        }
    }

    /**
     * Fires all processing-time timers with timestamp <= the given time.
     *
     * @param timestamp the processing time to fire timers up to
     */
    public void fireProcessingTimeTimers(long timestamp) throws Exception {
        List<Map.Entry<Long, Set<TimerEntry<K, N>>>> toFire = new ArrayList<>();
        while (true) {
            Map.Entry<Long, Set<TimerEntry<K, N>>> entry = processingTimeTimers.firstEntry();
            if (entry == null || entry.getKey() > timestamp) {
                break;
            }
            processingTimeTimers.pollFirstEntry();
            toFire.add(entry);
        }
        if (!toFire.isEmpty()) {
            recomputeNextProcessingTimeTimer();
        }
        for (Map.Entry<Long, Set<TimerEntry<K, N>>> entry : toFire) {
            List<TimerEntry<K, N>> timersToFire = new ArrayList<>(entry.getValue());
            for (TimerEntry<K, N> timer : timersToFire) {
                triggerable.onProcessingTime(new HeapInternalTimer<>(timer.key, timer.timestamp, timer.namespace));
            }
        }
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

        List<Map.Entry<Long, Set<TimerEntry<K, N>>>> toFire = new ArrayList<>();
        while (true) {
            Map.Entry<Long, Set<TimerEntry<K, N>>> entry = eventTimeTimers.firstEntry();
            if (entry == null || entry.getKey() > newWatermark) {
                break;
            }
            eventTimeTimers.pollFirstEntry();
            toFire.add(entry);
        }
        for (Map.Entry<Long, Set<TimerEntry<K, N>>> entry : toFire) {
            List<TimerEntry<K, N>> timersToFire = new ArrayList<>(entry.getValue());
            for (TimerEntry<K, N> timer : timersToFire) {
                triggerable.onEventTime(new HeapInternalTimer<>(timer.key, timer.timestamp, timer.namespace));
            }
        }
    }

    public int numEventTimeTimers() {
        return eventTimeTimers.values().stream().mapToInt(Set::size).sum();
    }

    public int numProcessingTimeTimers() {
        return processingTimeTimers.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * @return the earliest registered processing-time timer timestamp, or
     *         {@link Long#MAX_VALUE} when no processing-time timer is registered.
     *         Safe to call from the scheduler thread (single volatile read).
     */
    public long nextProcessingTimeTimer() {
        return nextProcessingTimeTimer;
    }

    /**
     * @return {@code true} if at least one processing-time timer is due at or before
     *         {@code now}. Safe to call from the scheduler thread.
     */
    public boolean hasProcessingTimeTimersDue(long now) {
        return nextProcessingTimeTimer <= now;
    }

    private void recomputeNextProcessingTimeTimer() {
        Map.Entry<Long, Set<TimerEntry<K, N>>> first = processingTimeTimers.firstEntry();
        nextProcessingTimeTimer = first != null ? first.getKey() : Long.MAX_VALUE;
    }

    // ------------------------------------------------------------------------
    //  Snapshot / Restore (G2: timer state survives checkpoint/restore)
    // ------------------------------------------------------------------------

    /**
     * Snapshots all currently registered event-time and processing-time timers into a
     * serializable {@link TimerSnapshot} DTO. The returned snapshot is safe to store in
     * {@code MemoryStateBackend} checkpoints: keys and namespaces are stored directly,
     * so they must be {@link Serializable} (or transitively serializable) for cross-JVM
     * restore. Within a single JVM (current scope) any object reference is acceptable.
     *
     * <p>The snapshot includes only timers that have not yet fired. Already-fired timers
     * are removed from the internal data structures when they fire, so they are naturally
     * excluded — this guarantees no double-fire after restore.
     *
     * @return a non-null {@link TimerSnapshot} (possibly empty if no timers are registered)
     */
    public TimerSnapshot<K, N> snapshotTimers() {
        List<TimerEntry<K, N>> eventTimeSnapshot = new ArrayList<>();
        for (Set<TimerEntry<K, N>> bucket : eventTimeTimers.values()) {
            eventTimeSnapshot.addAll(bucket);
        }
        List<TimerEntry<K, N>> processingTimeSnapshot = new ArrayList<>();
        for (Set<TimerEntry<K, N>> bucket : processingTimeTimers.values()) {
            processingTimeSnapshot.addAll(bucket);
        }
        return new TimerSnapshot<>(eventTimeSnapshot, processingTimeSnapshot, currentWatermark);
    }

    /**
     * Restores previously-snapshotted timers. Directly inserts {@link TimerEntry} objects
     * (carrying the snapshot's stored key) into the internal {@link TreeMap} structures,
     * bypassing {@code currentKeySupplier}. This is critical during restore: the supplier
     * returns the currently-active processing key which may be null or stale at restore
     * time, while the snapshot's stored key is the authoritative key captured at
     * checkpoint time.
     *
     * <p>This method is idempotent for empty snapshots (a no-op) and never throws on
     * duplicate entries — the underlying {@link Set} deduplicates them.
     *
     * @param snapshot the snapshot to restore; {@code null} is treated as an empty snapshot
     */
    public void restoreTimers(TimerSnapshot<K, N> snapshot) {
        if (snapshot == null) {
            return;
        }
        for (TimerEntry<K, N> entry : snapshot.getEventTimeTimers()) {
            eventTimeTimers.computeIfAbsent(entry.timestamp, k -> new HashSet<>()).add(entry);
        }
        for (TimerEntry<K, N> entry : snapshot.getProcessingTimeTimers()) {
            processingTimeTimers.computeIfAbsent(entry.timestamp, k -> new HashSet<>()).add(entry);
        }
        if (!snapshot.getProcessingTimeTimers().isEmpty()) {
            recomputeNextProcessingTimeTimer();
        }
        // Restore the watermark as well so subsequent advanceWatermark() calls do not
        // re-fire timers that were already fired before the checkpoint (those timers
        // are not in the snapshot, but restoring the watermark keeps the invariant
        // "currentWatermark only moves forward" consistent across restore).
        if (snapshot.getCurrentWatermark() > currentWatermark) {
            currentWatermark = snapshot.getCurrentWatermark();
        }
    }

    // ------------------------------------------------------------------------
    //  Internal DTOs
    // ------------------------------------------------------------------------

    /**
     * Immutable entry capturing a single registered timer: (key, namespace, timestamp).
     * Used both as the live internal storage entry and as a serializable element of
     * {@link TimerSnapshot}.
     */
    public static final class TimerEntry<K, N> implements Serializable {
        private static final long serialVersionUID = 1L;

        private final K key;
        private final N namespace;
        private final long timestamp;

        public TimerEntry(K key, N namespace, long timestamp) {
            this.key = key;
            this.namespace = namespace;
            this.timestamp = timestamp;
        }

        public K getKey() {
            return key;
        }

        public N getNamespace() {
            return namespace;
        }

        public long getTimestamp() {
            return timestamp;
        }

        /**
         * JSON-safe map form for the checkpoint persist path (see {@link TimerSnapshot#toSerializableForm()}).
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> toSerializableForm() {
            java.util.LinkedHashMap<String, Object> form = new java.util.LinkedHashMap<>();
            form.put("key", key);
            form.put("namespace", TimerSnapshot.serializeNamespace(namespace));
            form.put("timestamp", timestamp);
            return form;
        }

        /**
         * Rebuilds a {@link TimerEntry} from the JSON-safe form produced by
         * {@link #toSerializableForm()}.
         */
        @SuppressWarnings("unchecked")
        public static <K, N> TimerEntry<K, N> fromSerializableForm(Map<String, Object> form) {
            return new TimerEntry<>(
                    (K) form.get("key"),
                    (N) TimerSnapshot.deserializeNamespace(form.get("namespace")),
                    form.get("timestamp") instanceof Number ? ((Number) form.get("timestamp")).longValue() : 0L);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TimerEntry<?, ?> that = (TimerEntry<?, ?>) o;
            return timestamp == that.timestamp
                    && Objects.equals(key, that.key)
                    && Objects.equals(namespace, that.namespace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, namespace, timestamp);
        }
    }

    /**
     * Serializable snapshot DTO of all in-flight timers in a {@link HeapInternalTimerService}.
     *
     * <p>For {@code MemoryStateBackend}, the {@code K} and {@code N} objects are stored
     * directly inside {@link TimerEntry}. They must be {@link Serializable} when the
     * checkpoint needs to survive JVM restart; within a single JVM they may be any
     * object reference.
     *
     * <p>The JSON checkpoint persist path ({@code CheckpointSerDe}) converts this DTO
     * to a plain JSON-safe map form via {@link #toSerializableForm()} / {@link
     * #fromSerializableForm(Map)} — the DTO itself is NOT a Nop {@code @DataBean}
     * (generic type parameters conflict with the annotation processor, see
     * {@code PaneState}).
     */
    public static final class TimerSnapshot<K, N> implements Serializable {
        private static final long serialVersionUID = 1L;

        private static final String FORM_TYPE_TIMER_SNAPSHOT = "TimerSnapshot";
        private static final String FORM_TYPE_TIME_WINDOW = "TimeWindow";
        private static final String FORM_GLOBAL_WINDOW = "GlobalWindow";

        private final List<TimerEntry<K, N>> eventTimeTimers;
        private final List<TimerEntry<K, N>> processingTimeTimers;
        private final long currentWatermark;

        public TimerSnapshot(List<TimerEntry<K, N>> eventTimeTimers,
                             List<TimerEntry<K, N>> processingTimeTimers,
                             long currentWatermark) {
            this.eventTimeTimers = eventTimeTimers != null ? eventTimeTimers : new ArrayList<>();
            this.processingTimeTimers = processingTimeTimers != null ? processingTimeTimers : new ArrayList<>();
            this.currentWatermark = currentWatermark;
        }

        public List<TimerEntry<K, N>> getEventTimeTimers() {
            return eventTimeTimers;
        }

        public List<TimerEntry<K, N>> getProcessingTimeTimers() {
            return processingTimeTimers;
        }

        public long getCurrentWatermark() {
            return currentWatermark;
        }

        public boolean isEmpty() {
            return eventTimeTimers.isEmpty() && processingTimeTimers.isEmpty();
        }

        public int size() {
            return eventTimeTimers.size() + processingTimeTimers.size();
        }

        /**
         * JSON-safe map form used by the checkpoint persist path (the generic DTO
         * itself cannot be serialized by {@code JsonTool} — not a {@code @DataBean}).
         * Self-describing via {@code "@type"}. Namespaces are window types only
         * (TimeWindow / GlobalWindow / null); the key is JSON-safe in all current uses.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> toSerializableForm() {
            java.util.LinkedHashMap<String, Object> form = new java.util.LinkedHashMap<>();
            form.put("@type", FORM_TYPE_TIMER_SNAPSHOT);
            form.put("currentWatermark", currentWatermark);
            List<Object> eventForms = new ArrayList<>();
            for (TimerEntry<K, N> entry : eventTimeTimers) {
                eventForms.add(entry.toSerializableForm());
            }
            List<Object> processingForms = new ArrayList<>();
            for (TimerEntry<K, N> entry : processingTimeTimers) {
                processingForms.add(entry.toSerializableForm());
            }
            form.put("eventTimeTimers", eventForms);
            form.put("processingTimeTimers", processingForms);
            return form;
        }

        /**
         * Rebuilds a {@link TimerSnapshot} from the JSON-safe form produced by
         * {@link #toSerializableForm()}. Returns {@code null} for non-timer maps.
         */
        @SuppressWarnings("unchecked")
        public static <K, N> TimerSnapshot<K, N> fromSerializableForm(Map<String, Object> form) {
            if (form == null || !FORM_TYPE_TIMER_SNAPSHOT.equals(form.get("@type"))) {
                return null;
            }
            List<TimerEntry<K, N>> eventTimers = new ArrayList<>();
            List<Object> eventForms = (List<Object>) form.get("eventTimeTimers");
            if (eventForms != null) {
                for (Object f : eventForms) {
                    if (f instanceof Map) {
                        eventTimers.add(TimerEntry.fromSerializableForm((Map<String, Object>) f));
                    }
                }
            }
            List<TimerEntry<K, N>> processingTimers = new ArrayList<>();
            List<Object> processingForms = (List<Object>) form.get("processingTimeTimers");
            if (processingForms != null) {
                for (Object f : processingForms) {
                    if (f instanceof Map) {
                        processingTimers.add(TimerEntry.fromSerializableForm((Map<String, Object>) f));
                    }
                }
            }
            long watermark = form.get("currentWatermark") instanceof Number
                    ? ((Number) form.get("currentWatermark")).longValue() : Long.MIN_VALUE;
            return new TimerSnapshot<>(eventTimers, processingTimers, watermark);
        }

        private static Object serializeNamespace(Object namespace) {
            if (namespace instanceof io.nop.stream.core.windowing.windows.TimeWindow) {
                io.nop.stream.core.windowing.windows.TimeWindow w =
                        (io.nop.stream.core.windowing.windows.TimeWindow) namespace;
                java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("@type", FORM_TYPE_TIME_WINDOW);
                m.put("start", w.getStart());
                m.put("end", w.getEnd());
                return m;
            }
            if (namespace instanceof io.nop.stream.core.windowing.windows.GlobalWindow) {
                return FORM_GLOBAL_WINDOW;
            }
            return namespace;
        }

        @SuppressWarnings("unchecked")
        private static Object deserializeNamespace(Object obj) {
            if (obj instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) obj;
                if (FORM_TYPE_TIME_WINDOW.equals(m.get("@type"))) {
                    return new io.nop.stream.core.windowing.windows.TimeWindow(
                            ((Number) m.get("start")).longValue(),
                            ((Number) m.get("end")).longValue());
                }
            }
            if (FORM_GLOBAL_WINDOW.equals(obj)) {
                return io.nop.stream.core.windowing.windows.GlobalWindow.get();
            }
            return obj;
        }
    }

    private static class HeapInternalTimer<K, N> implements InternalTimer<K, N> {
        private final K key;
        private final long timestamp;
        private final N namespace;

        HeapInternalTimer(K key, long timestamp, N namespace) {
            this.key = key;
            this.timestamp = timestamp;
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
        public int comparePriorityTo(InternalTimer<?, ?> other) {
            return Long.compare(this.timestamp, other.getTimestamp());
        }
    }
}
