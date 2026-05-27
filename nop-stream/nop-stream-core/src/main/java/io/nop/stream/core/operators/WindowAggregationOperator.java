package io.nop.stream.core.operators;

import java.util.*;

import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
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
import io.nop.stream.core.exceptions.StreamException;

public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
        extends AbstractStreamOperator<OUT>
        implements OneInputStreamOperator<IN, OUT>, KeyContext {

    private static final long serialVersionUID = 2L;
    private static final Logger LOG = LoggerFactory.getLogger(WindowAggregationOperator.class);

    private final WindowAssigner<? super IN, W> windowAssigner;
    Trigger<? super IN, ? super W> trigger;
    private final WindowAggregationFunction<IN, ACC, OUT, K, W> aggregationFunction;
    private final KeySelector<IN, K> keySelector;

    private transient Map<WindowKey<K, W>, ACC> windowState;
    private transient TreeMap<Long, Set<WindowKey<K, W>>> eventTimeTimers;
    private transient TreeMap<Long, Set<WindowKey<K, W>>> processingTimeTimers;
    private transient Map<WindowKey<K, W>, Set<Long>> windowTimerLookup;
    private transient Map<WindowKey<K, W>, Set<Long>> processingTimeTimerLookup;
    private transient Map<TriggerStateKey<K, W>, SimpleAccumulator<?>> triggerState;
    private transient long currentWatermark;
    private transient boolean watermarkInitialized;
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
        if (this.windowState == null) {
            this.windowState = new LinkedHashMap<>();
        }
        if (this.eventTimeTimers == null) {
            this.eventTimeTimers = new TreeMap<>();
        }
        if (this.processingTimeTimers == null) {
            this.processingTimeTimers = new TreeMap<>();
        }
        if (this.windowTimerLookup == null) {
            this.windowTimerLookup = new HashMap<>();
        }
        if (this.processingTimeTimerLookup == null) {
            this.processingTimeTimerLookup = new HashMap<>();
        }
        if (this.triggerState == null) {
            this.triggerState = new HashMap<>();
        }
        if (!this.watermarkInitialized) {
            this.currentWatermark = Long.MIN_VALUE;
        }
        this.watermarkInitialized = true;
        if (this.assignerContext == null) {
            this.assignerContext = new WindowAssigner.WindowAssignerContext() {
                @Override
                public long getCurrentProcessingTime() {
                    return System.currentTimeMillis();
                }
            };
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        windowState = null;
        eventTimeTimers = null;
        processingTimeTimers = null;
        windowTimerLookup = null;
        processingTimeTimerLookup = null;
        triggerState = null;
    }

    @Override
    public OperatorSnapshotResult snapshotState(StateSnapshotContext context) throws Exception {
        OperatorSnapshotResult result = new OperatorSnapshotResult();

        WindowAggregationState state = new WindowAggregationState();
        state.setVersion(WindowAggregationState.CURRENT_VERSION);

        if (!windowState.isEmpty()) {
            K firstKey = windowState.keySet().iterator().next().key;
            state.setKeyClassName(firstKey.getClass().getName());
        }
        if (!windowState.isEmpty()) {
            W firstWindow = windowState.keySet().iterator().next().window;
            state.setWindowClassName(firstWindow.getClass().getName());
        }

        state.setWindowState(serializeWindowState(windowState));
        state.setEventTimeTimers(serializeTimers(eventTimeTimers));
        state.setProcessingTimeTimers(serializeTimers(processingTimeTimers));
        state.setTriggerState(serializeTriggerState(triggerState));
        state.setCurrentWatermark(currentWatermark);

        result.putOperatorState("window-aggregation-state", state);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
        Object stateObj = snapshotResult.getOperatorState("window-aggregation-state");
        if (stateObj == null) {
            throw new IllegalStateException("No window-aggregation-state found in snapshot");
        }

        WindowAggregationState state;
        if (stateObj instanceof WindowAggregationState) {
            state = (WindowAggregationState) stateObj;
        } else if (stateObj instanceof Map) {
            state = JsonTool.parseBeanFromText(JsonTool.stringify(stateObj), WindowAggregationState.class);
        } else {
            throw new IllegalStateException("Unexpected state type: " + stateObj.getClass().getName());
        }

        if (state.getVersion() != WindowAggregationState.CURRENT_VERSION) {
            throw new IllegalStateException("Unsupported state version: " + state.getVersion());
        }

        String keyClassName = state.getKeyClassName();
        String windowClassName = state.getWindowClassName();

        Class<?> keyClass = keyClassName != null ? Class.forName(keyClassName) : null;
        Class<?> windowClass = windowClassName != null ? Class.forName(windowClassName) : null;

        this.windowState = new LinkedHashMap<>();
        deserializeWindowState(state.getWindowState(), keyClass, windowClass, this.windowState);

        this.eventTimeTimers = new TreeMap<>();
        deserializeTimers(state.getEventTimeTimers(), keyClass, windowClass, this.eventTimeTimers);

        this.processingTimeTimers = new TreeMap<>();
        deserializeTimers(state.getProcessingTimeTimers(), keyClass, windowClass, this.processingTimeTimers);

        this.triggerState = new HashMap<>();
        deserializeTriggerState(state.getTriggerState(), keyClass, windowClass, this.triggerState);

        this.currentWatermark = state.getCurrentWatermark();
        this.watermarkInitialized = true;

        rebuildTimerLookups();

        this.currentKeyField = null;
        this.assignerContext = new WindowAssigner.WindowAssignerContext() {
            @Override
            public long getCurrentProcessingTime() {
                return System.currentTimeMillis();
            }
        };
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

        // Late data handling: discard elements with valid timestamps below current watermark
        if (element.hasTimestamp() && timestamp < currentWatermark) {
            LOG.debug("Dropping late element with timestamp {} below current watermark {}", timestamp, currentWatermark);
            return;
        }

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
                // Only remove the current timestamp from the lookup, not the entire key
                Set<Long> eventTimersForWindow = windowTimerLookup.get(wk);
                if (eventTimersForWindow != null) {
                    eventTimersForWindow.remove(timerTimestamp);
                    if (eventTimersForWindow.isEmpty()) {
                        windowTimerLookup.remove(wk);
                    }
                }

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

    public void advanceProcessingTime(long timestamp) throws Exception {
        while (!processingTimeTimers.isEmpty() && processingTimeTimers.firstKey() <= timestamp) {
            Map.Entry<Long, Set<WindowKey<K, W>>> entry = processingTimeTimers.pollFirstEntry();
            long timerTimestamp = entry.getKey();

            for (WindowKey<K, W> wk : entry.getValue()) {
                // Only remove the current timestamp from the lookup, not the entire key
                Set<Long> ptTimersForWindow = processingTimeTimerLookup.get(wk);
                if (ptTimersForWindow != null) {
                    ptTimersForWindow.remove(timerTimestamp);
                    if (ptTimersForWindow.isEmpty()) {
                        processingTimeTimerLookup.remove(wk);
                    }
                }

                ACC acc = windowState.get(wk);
                if (acc == null) {
                    continue;
                }

                K key = wk.key;
                W window = wk.window;
                TriggerContextImpl triggerCtx = new TriggerContextImpl(key, window);
                TriggerResult result = trigger.onProcessingTime(timerTimestamp, window, triggerCtx);

                if (result.isFire()) {
                    emitWindowResult(key, window, acc);
                }

                if (result.isPurge()) {
                    purgeWindow(key, window, wk, triggerCtx);
                }
            }
        }
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

        Iterator<TriggerStateKey<K, W>> it = triggerState.keySet().iterator();
        while (it.hasNext()) {
            TriggerStateKey<K, W> k = it.next();
            if (k.windowKey.equals(wk)) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private K resolveKey(IN value) throws Exception {
        if (currentKeyField != null) {
            // Defensive: verify that the current key field is of the expected type;
            // after JSON deserialization the key may be a different type (e.g. Integer vs Long)
            if (keySelector != null) {
                K selectorKey = keySelector.getKey(value);
                if (selectorKey != null && !selectorKey.getClass().isInstance(currentKeyField)) {
                    LOG.warn("Key type mismatch: currentKeyField type={} but expected type={}. "
                            + "Falling back to keySelector result.",
                            currentKeyField.getClass().getName(), selectorKey.getClass().getName());
                    return selectorKey;
                }
            }
            return (K) currentKeyField;
        }
        if (keySelector != null) {
            return keySelector.getKey(value);
        }
        throw new IllegalStateException("No key available: setCurrentKey() not called and no keySelector provided");
    }

    private Map<String, Object> serializeWindowState(Map<WindowKey<K, W>, ACC> state) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<WindowKey<K, W>, ACC> entry : state.entrySet()) {
            WindowKey<K, W> wk = entry.getKey();
            String key = serializeWindowKey(wk);
            ACC acc = entry.getValue();
            if (acc instanceof SimpleAccumulator) {
                Map<String, Object> accMap = new LinkedHashMap<>();
                accMap.put("@type", acc.getClass().getName());
                accMap.put("value", ((SimpleAccumulator<?>) acc).getLocalValue());
                result.put(key, accMap);
            } else {
                result.put(key, acc);
            }
        }
        return result;
    }

    private String serializeWindowKey(WindowKey<K, W> wk) {
        return JsonTool.stringify(wk.key) + "#" + JsonTool.stringify(wk.window);
    }

    private Map<String, Object> serializeTimers(TreeMap<Long, Set<WindowKey<K, W>>> timers) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<WindowKey<K, W>>> entry : timers.entrySet()) {
            Set<String> keys = new LinkedHashSet<>();
            for (WindowKey<K, W> wk : entry.getValue()) {
                keys.add(serializeWindowKey(wk));
            }
            result.put(String.valueOf(entry.getKey()), keys);
        }
        return result;
    }

    private Map<String, Object> serializeTriggerState(Map<TriggerStateKey<K, W>, SimpleAccumulator<?>> state) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<TriggerStateKey<K, W>, SimpleAccumulator<?>> entry : state.entrySet()) {
            TriggerStateKey<K, W> tsk = entry.getKey();
            String key = serializeWindowKey(tsk.windowKey) + ":" + tsk.descriptorName;
            SimpleAccumulator<?> acc = entry.getValue();
            Map<String, Object> accMap = new LinkedHashMap<>();
            accMap.put("@type", acc.getClass().getName());
            accMap.put("value", acc.getLocalValue());
            result.put(key, accMap);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void deserializeWindowState(Map<String, Object> data, Class<?> keyClass, Class<?> windowClass,
                                        Map<WindowKey<K, W>, ACC> target) throws Exception {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String[] parts = entry.getKey().split("#", 2);
            K key = (K) JsonTool.parseBeanFromText(parts[0], keyClass);
            W window = (W) JsonTool.parseBeanFromText(parts[1], windowClass);
            WindowKey<K, W> wk = new WindowKey<>(key, window);

            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) value;
                if (map.containsKey("@type")) {
                    String accType = (String) map.get("@type");
                    Object accValue = map.get("value");
                    SimpleAccumulator<Object> acc = (SimpleAccumulator<Object>) Class.forName(accType).getDeclaredConstructor().newInstance();
                    acc.add(accValue);
                    target.put(wk, (ACC) acc);
                } else {
                    target.put(wk, (ACC) value);
                }
            } else {
                target.put(wk, (ACC) value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void deserializeTimers(Map<String, Object> data, Class<?> keyClass, Class<?> windowClass,
                                   TreeMap<Long, Set<WindowKey<K, W>>> target) throws Exception {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            long timestamp = Long.parseLong(entry.getKey());
            Set<WindowKey<K, W>> keys = new LinkedHashSet<>();
            Object value = entry.getValue();
            if (value instanceof Collection) {
                for (Object item : (Collection<?>) value) {
                    String[] parts = item.toString().split("#", 2);
                    K key = (K) JsonTool.parseBeanFromText(parts[0], keyClass);
                    W window = (W) JsonTool.parseBeanFromText(parts[1], windowClass);
                    keys.add(new WindowKey<>(key, window));
                }
            }
            target.put(timestamp, keys);
        }
    }

    @SuppressWarnings("unchecked")
    private void deserializeTriggerState(Map<String, Object> data, Class<?> keyClass, Class<?> windowClass,
                                         Map<TriggerStateKey<K, W>, SimpleAccumulator<?>> target) throws Exception {
        if (data == null) return;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String[] mainParts = entry.getKey().split(":", 2);
            String[] keyParts = mainParts[0].split("#", 2);
            K key = (K) JsonTool.parseBeanFromText(keyParts[0], keyClass);
            W window = (W) JsonTool.parseBeanFromText(keyParts[1], windowClass);
            WindowKey<K, W> wk = new WindowKey<>(key, window);
            String descriptorName = mainParts[1];

            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) value;
                String accType = (String) map.get("@type");
                Object accValue = map.get("value");
                SimpleAccumulator<Object> acc = (SimpleAccumulator<Object>) Class.forName(accType).getDeclaredConstructor().newInstance();
                acc.add(accValue);
                target.put(new TriggerStateKey<>(wk, descriptorName), acc);
            }
        }
    }

    private void rebuildTimerLookups() {
        this.windowTimerLookup = new HashMap<>();
        for (Map.Entry<Long, Set<WindowKey<K, W>>> entry : eventTimeTimers.entrySet()) {
            long timestamp = entry.getKey();
            for (WindowKey<K, W> wk : entry.getValue()) {
                windowTimerLookup.computeIfAbsent(wk, w -> new LinkedHashSet<>()).add(timestamp);
            }
        }

        this.processingTimeTimerLookup = new HashMap<>();
        for (Map.Entry<Long, Set<WindowKey<K, W>>> entry : processingTimeTimers.entrySet()) {
            long timestamp = entry.getKey();
            for (WindowKey<K, W> wk : entry.getValue()) {
                processingTimeTimerLookup.computeIfAbsent(wk, w -> new LinkedHashSet<>()).add(timestamp);
            }
        }
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

    static final class TriggerStateKey<K, W extends Window> {
        final WindowKey<K, W> windowKey;
        final String descriptorName;

        TriggerStateKey(WindowKey<K, W> windowKey, String descriptorName) {
            this.windowKey = windowKey;
            this.descriptorName = descriptorName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TriggerStateKey<?, ?> that = (TriggerStateKey<?, ?>) o;
            return windowKey.equals(that.windowKey) && descriptorName.equals(that.descriptorName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(windowKey, descriptorName);
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
            WindowKey<K, W> wk = new WindowKey<>(key, window);
            processingTimeTimers.computeIfAbsent(time, t -> new LinkedHashSet<>()).add(wk);
            processingTimeTimerLookup.computeIfAbsent(wk, w -> new LinkedHashSet<>()).add(time);
        }

        @Override
        public void deleteProcessingTimeTimer(long time) {
            WindowKey<K, W> wk = new WindowKey<>(key, window);
            Set<WindowKey<K, W>> keysAtTime = processingTimeTimers.get(time);
            if (keysAtTime != null) {
                keysAtTime.remove(wk);
                if (keysAtTime.isEmpty()) {
                    processingTimeTimers.remove(time);
                }
            }
            Set<Long> timersForWindow = processingTimeTimerLookup.get(wk);
            if (timersForWindow != null) {
                timersForWindow.remove(time);
                if (timersForWindow.isEmpty()) {
                    processingTimeTimerLookup.remove(wk);
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> SimpleAccumulator<T> getSimpleAccumulator(StateDescriptor<T> descriptor) {
            TriggerStateKey<K, W> stateKey = new TriggerStateKey<>(new WindowKey<>(key, window), descriptor.getName());
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
                    throw new StreamException("Failed to create trigger state accumulator", e);
                }
            }
            throw new UnsupportedOperationException(
                    "getSimpleAccumulator not supported for descriptor: " + descriptor.getName());
        }
    }
}
