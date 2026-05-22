/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.simple;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SimpleKeyedStateStore implements KeyedStateStore {

    private long stateWrites = 0;
    private long stateReads = 0;

    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        return new ValueState<T>() {

            private T value;

            @Override
            public T value() throws IOException {
                stateReads++;
                return value;
            }

            @Override
            public void update(T value) throws IOException {
                stateWrites++;
                this.value = value;
            }

            @Override
            public void clear() {
                this.value = null;
            }
        };
    }
    @Override
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        return new ListState<T>() {
            private final List<T> list = new ArrayList<>();

            @Override
            public Iterable<T> get() throws IOException {
                stateReads++;
                return list;
            }

            @Override
            public void add(T value) throws IOException {
                stateWrites++;
                list.add(value);
            }

            @Override
            public void addAll(Iterable<T> values) throws IOException {
                stateWrites++;
                for (T v : values) {
                    list.add(v);
                }
            }

            @Override
            public void update(Iterable<T> values) throws IOException {
                stateWrites++;
                list.clear();
                for (T v : values) {
                    list.add(v);
                }
            }

            @Override
            public void clear() {
                stateWrites++;
                list.clear();
            }
        };
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        return new ReducingState<T>() {
            private SimpleAccumulator<T> accumulator;

            private SimpleAccumulator<T> getOrCreate() throws Exception {
                if (accumulator == null) {
                    accumulator = stateProperties.getAccumulatorType().getDeclaredConstructor().newInstance();
                }
                return accumulator;
            }

            @Override
            public T get() throws Exception {
                stateReads++;
                if (accumulator == null) {
                    return null;
                }
                return accumulator.get();
            }

            @Override
            public void add(T value) throws Exception {
                stateWrites++;
                getOrCreate().add(value);
            }

            @Override
            public void clear() {
                stateWrites++;
                accumulator = null;
            }
        };
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        return new AggregatingState<IN, OUT>() {
            private ACC accumulator;

            @Override
            public OUT get() throws Exception {
                stateReads++;
                if (accumulator == null) {
                    return null;
                }
                return stateProperties.getAggregateFunction().getResult(accumulator);
            }

            @Override
            public void add(IN value) throws Exception {
                stateWrites++;
                if (accumulator == null) {
                    accumulator = stateProperties.getAggregateFunction().createAccumulator();
                }
                accumulator = stateProperties.getAggregateFunction().add(value, accumulator);
            }

            @Override
            public void clear() {
                stateWrites++;
                accumulator = null;
            }
        };
    }

    protected <UK, UV> Map<UK, UV> newMap() {
        return new HashMap<>();
    }

    @Override
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        return new MapState<>() {

            private Map<UK, UV> values;

            private Map<UK, UV> getOrSetMap() {
                if (values == null) {
                    this.values = newMap();
                }
                return values;
            }

            @Override
            public UV get(UK key) {
                stateReads++;
                if (values == null) {
                    return null;
                }

                return values.get(key);
            }

            @Override
            public void put(UK key, UV value) {
                stateWrites++;
                getOrSetMap().put(key, value);
            }

            @Override
            public void putAll(Map<UK, UV> map) {
                stateWrites++;
                getOrSetMap().putAll(map);
            }

            @Override
            public void remove(UK key) {
                if (values == null) {
                    return;
                }

                stateWrites++;
                values.remove(key);
            }

            @Override
            public boolean contains(UK key) {
                if (values == null) {
                    return false;
                }

                stateReads++;
                return values.containsKey(key);
            }

            @Override
            public Iterable<Map.Entry<UK, UV>> entries() {
                if (values == null) {
                    return Collections.emptyList();
                }

                return () -> new CountingIterator<>(values.entrySet().iterator());
            }

            @Override
            public Iterable<UK> keys() {
                if (values == null) {
                    return Collections.emptyList();
                }

                return () -> new CountingIterator<>(values.keySet().iterator());
            }

            @Override
            public Iterable<UV> values() {
                if (values == null) {
                    return Collections.emptyList();
                }

                return () -> new CountingIterator<>(values.values().iterator());
            }

            @Override
            public Iterator<Map.Entry<UK, UV>> iterator() {
                if (values == null) {
                    return Collections.emptyIterator();
                }

                return new CountingIterator<>(values.entrySet().iterator());
            }

            @Override
            public boolean isEmpty() {
                if (values == null) {
                    return true;
                }

                return values.isEmpty();
            }

            @Override
            public void clear() {
                stateWrites++;
                this.values = null;
            }
        };
    }

    private class CountingIterator<T> implements Iterator<T> {

        private final Iterator<T> iterator;

        CountingIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            stateReads++;
            return iterator.next();
        }

        @Override
        public void remove() {
            stateWrites++;
            iterator.remove();
        }
    }
}