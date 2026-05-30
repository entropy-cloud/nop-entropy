package io.nop.stream.fraud.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;

public class DemoKeyedStateStore implements KeyedStateStore {
    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        return new ValueState<T>() {
            private T value;

            @Override
            public T value() { return value; }

            @Override
            public void update(T value) { this.value = value; }

            @Override
            public void clear() { this.value = null; }
        };
    }

    @Override
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        return new ListState<T>() {
            private final List<T> list = new ArrayList<>();

            @Override
            public Iterable<T> get() { return list; }

            @Override
            public void add(T value) { list.add(value); }

            @Override
            public void addAll(Iterable<T> values) { for (T v : values) list.add(v); }

            @Override
            public void update(Iterable<T> values) { list.clear(); for (T v : values) list.add(v); }

            @Override
            public void clear() { list.clear(); }
        };
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        throw new UnsupportedOperationException("ReducingState not needed for demo");
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        throw new UnsupportedOperationException("AggregatingState not needed for demo");
    }

    @Override
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        return new MapState<>() {
            private Map<UK, UV> values;

            @Override
            public UV get(UK key) { return values == null ? null : values.get(key); }

            @Override
            public void put(UK key, UV value) {
                if (values == null) values = new HashMap<>();
                values.put(key, value);
            }

            @Override
            public void putAll(Map<UK, UV> map) {
                if (values == null) values = new HashMap<>();
                values.putAll(map);
            }

            @Override
            public void remove(UK key) { if (values != null) values.remove(key); }

            @Override
            public boolean contains(UK key) { return values != null && values.containsKey(key); }

            @Override
            public Iterable<Map.Entry<UK, UV>> entries() {
                return values == null ? Collections.emptyList() : values.entrySet();
            }

            @Override
            public Iterable<UK> keys() {
                return values == null ? Collections.emptyList() : values.keySet();
            }

            @Override
            public Iterable<UV> values() {
                return values == null ? Collections.emptyList() : values.values();
            }

            @Override
            public Iterator<Map.Entry<UK, UV>> iterator() {
                return values == null ? Collections.emptyIterator() : values.entrySet().iterator();
            }

            @Override
            public boolean isEmpty() { return values == null || values.isEmpty(); }

            @Override
            public void clear() { values = null; }
        };
    }
}
