package io.nop.stream.core.operators;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.triggers.TriggerResult;
import io.nop.stream.core.windowing.windows.Window;

import java.util.*;

public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
        extends AbstractStreamOperator<OUT>
        implements OneInputStreamOperator<IN, OUT>, KeyContext {

    private static final long serialVersionUID = 1L;

    private final WindowAssigner<? super IN, W> windowAssigner;
    private final Trigger<? super IN, ? super W> trigger;
    private final WindowAggregationFunction<IN, ACC, OUT, K, W> aggregationFunction;
    private final KeySelector<IN, K> keySelector;

    private transient Map<WindowKey<K, W>, ACC> windowState;
    private transient TreeMap<Long, Set<WindowKey<K, W>>> eventTimeTimers;
    private transient Map<WindowKey<K, W>, Set<Long>> windowTimerLookup;
    private transient Map<String, SimpleAccumulator<?>> triggerState;
    private transient long currentWatermark;
    private transient Object currentKeyField;
    private transient WindowAssigner.WindowAssignerContext assignerContext;

    public WindowAggregationOperator(
            WindowAssigner<? super IN, W> windowAssigner,
            Trigger<? super IN, ? super W> trigger,
            WindowAggregationFunction<IN, ACC, OUT, K, W> aggregationFunction,
            KeySelector<IN, K> keySelector) {
        this.windowAssigner = windowAssigner;
        this.trigger = trigger;
        this.aggregationFunction = aggregationFunction;
        this.keySelector = keySelector;
    }

    @Override
    public void open() throws Exception {
        super.open();
        this.windowState = new LinkedHashMap<>();
        this.eventTimeTimers = new TreeMap<>();
        this.windowTimerLookup = new HashMap<>();
        this.triggerState = new HashMap<>();
        this.currentWatermark = Long.MIN_VALUE;
        this.currentKeyField = null;
        this.assignerContext = new WindowAssigner.WindowAssignerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return System.currentTimeMillis();
            }
        };
    }

    @Override
    public void close() throws Exception {
        super.close();
        windowState = null;
        eventTimeTimers = null;
        windowTimerLookup = null;
        triggerState = null;
    }

    @Override
    public void setCurrentKey(Object key) {
        this.currentKeyField = key;
    }

    @Override
    public Object getCurrentKey() {
        return currentKeyField;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void processElement(StreamRecord<IN> element) throws Exception {
        IN value = element.getValue();
        long timestamp = element.getTimestamp();

        K key = resolveKey(value);

        Collection<W> windows = windowAssigner.assignWindows(value, timestamp, assignerContext);

        for (W window : windows) {
            WindowKey<K, W> wk = new WindowKey<>(key, window);

            ACC acc = windowState.get(wk);
            if (acc == null) {
                acc = aggregationFunction.createAccumulator();
            }
            acc = aggregationFunction.add(value, acc);
            windowState.put(wk, acc);

            TriggerContextImpl triggerCtx = new TriggerContextImpl(key, window);
            TriggerResult result = trigger.onElement(value, timestamp, window, triggerCtx);

            if (result.isFire()) {
                emitWindowResult(key, window, acc);
            }

            if (result.isPurge()) {
                purgeWindow(key, window, wk, triggerCtx);
            }
        }
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        long newWatermark = mark.getTimestamp();
        if (newWatermark <= currentWatermark) {
            output.emitWatermark(mark);
            return;
        }
        currentWatermark = newWatermark;

        while (!eventTimeTimers.isEmpty() && eventTimeTimers.firstKey() <= newWatermark) {
            Map.Entry<Long, Set<WindowKey<K, W>>> entry = eventTimeTimers.pollFirstEntry();
            long timerTimestamp = entry.getKey();

            for (WindowKey<K, W> wk : entry.getValue()) {
                windowTimerLookup.remove(wk);

                ACC acc = windowState.get(wk);
                if (acc == null) {
                    continue;
                }

                K key = wk.key;
                W window = wk.window;
                TriggerContextImpl triggerCtx = new TriggerContextImpl(key, window);
                TriggerResult result = trigger.onEventTime(timerTimestamp, window, triggerCtx);

                if (result.isFire()) {
                    emitWindowResult(key, window, acc);
                }

                if (result.isPurge()) {
                    purgeWindow(key, window, wk, triggerCtx);
                }
            }
        }

        output.emitWatermark(mark);
    }

    private void emitWindowResult(K key, W window, ACC acc) throws Exception {
        if (acc == null) {
            return;
        }
        Collector<OUT> collector = new Collector<OUT>() {
            @Override
            public void collect(OUT record) {
                output.collect(new StreamRecord<>(record, window.maxTimestamp()));
            }

            @Override
            public void close() {
            }
        };
        aggregationFunction.emitResult(key, window, acc, collector);
    }

    private void purgeWindow(K key, W window, WindowKey<K, W> wk,
                             TriggerContextImpl triggerCtx) throws Exception {
        windowState.remove(wk);

        Set<Long> timers = windowTimerLookup.remove(wk);
        if (timers != null) {
            for (Long time : timers) {
                Set<WindowKey<K, W>> keysAtTime = eventTimeTimers.get(time);
                if (keysAtTime != null) {
                    keysAtTime.remove(wk);
                    if (keysAtTime.isEmpty()) {
                        eventTimeTimers.remove(time);
                    }
                }
            }
        }

        trigger.clear(window, triggerCtx);

        String prefix = String.valueOf(key) + "#" + window + "#";
        triggerState.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @SuppressWarnings("unchecked")
    private K resolveKey(IN value) throws Exception {
        if (currentKeyField != null) {
            return (K) currentKeyField;
        }
        if (keySelector != null) {
            return keySelector.getKey(value);
        }
        throw new IllegalStateException("No key available: setCurrentKey() not called and no keySelector provided");
    }

    static final class WindowKey<K, W extends Window> {
        final K key;
        final W window;

        WindowKey(K key, W window) {
            this.key = key;
            this.window = window;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WindowKey<?, ?> that = (WindowKey<?, ?>) o;
            return Objects.equals(key, that.key) && Objects.equals(window, that.window);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, window);
        }
    }

    private class TriggerContextImpl implements Trigger.TriggerContext {
        private final K key;
        private final W window;

        TriggerContextImpl(K key, W window) {
            this.key = key;
            this.window = window;
        }

        @Override
        public long getCurrentProcessingTime() {
            return System.currentTimeMillis();
        }

        @Override
        public long getCurrentWatermark() {
            return currentWatermark;
        }

        @Override
        public void registerEventTimeTimer(long time) {
            WindowKey<K, W> wk = new WindowKey<>(key, window);
            eventTimeTimers.computeIfAbsent(time, t -> new LinkedHashSet<>()).add(wk);
            windowTimerLookup.computeIfAbsent(wk, w -> new LinkedHashSet<>()).add(time);
        }

        @Override
        public void deleteEventTimeTimer(long time) {
            WindowKey<K, W> wk = new WindowKey<>(key, window);
            Set<WindowKey<K, W>> keysAtTime = eventTimeTimers.get(time);
            if (keysAtTime != null) {
                keysAtTime.remove(wk);
                if (keysAtTime.isEmpty()) {
                    eventTimeTimers.remove(time);
                }
            }
            Set<Long> timersForWindow = windowTimerLookup.get(wk);
            if (timersForWindow != null) {
                timersForWindow.remove(time);
                if (timersForWindow.isEmpty()) {
                    windowTimerLookup.remove(wk);
                }
            }
        }

        @Override
        public void registerProcessingTimeTimer(long time) {
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            String stateKey = String.valueOf(key) + "#" + window + "#" + descriptor.getName();
            SimpleAccumulator<T> existing = (SimpleAccumulator<T>) triggerState.get(stateKey);
            if (existing != null) {
                return existing;
            }
            if (descriptor instanceof ReducingStateDescriptor) {
                ReducingStateDescriptor<T> rsd = (ReducingStateDescriptor<T>) descriptor;
                try {
                    SimpleAccumulator<T> acc = rsd.getAccumulatorType().getDeclaredConstructor().newInstance();
                    triggerState.put(stateKey, acc);
                    return acc;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create trigger state accumulator", e);
                }
            }
            throw new UnsupportedOperationException(
                    "getSimpleAccumulator not supported for descriptor: " + descriptor.getName());
        }
    }
}
