package io.nop.stream.core.common.state.simple;

import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
//
//    @Override
//    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
//        throw new UnsupportedOperationException();
//    }
//
//    @Override
//    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
//        throw new UnsupportedOperationException();
//    }
//
//    @Override
//    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
//            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
//        throw new UnsupportedOperationException();
//    }

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