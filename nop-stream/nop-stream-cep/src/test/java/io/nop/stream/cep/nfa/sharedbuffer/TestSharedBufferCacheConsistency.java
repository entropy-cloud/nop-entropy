package io.nop.stream.cep.nfa.sharedbuffer;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSharedBufferCacheConsistency {

    @Test
    void testCacheContainsEventAfterRegister() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId = accessor.registerEvent(new Event(1, "a"), 1L);
            assertNotNull(eventId);
            assertEquals(1, buffer.getEventsBufferCacheSize());
        }
    }

    @Test
    void testEventIdCounterNoOverflow() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId id0 = accessor.registerEvent(new Event(1, "a"), 100L);
            EventId id1 = accessor.registerEvent(new Event(2, "b"), 100L);
            EventId id2 = accessor.registerEvent(new Event(3, "c"), 100L);

            assertEquals(0, id0.getId());
            assertEquals(1, id1.getId());
            assertEquals(2, id2.getId());
            assertEquals(100L, id0.getTimestamp());
        }
    }

    @Test
    void testEventIdCounterIndependentPerTimestamp() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId idA1 = accessor.registerEvent(new Event(1, "a"), 1L);
            EventId idB1 = accessor.registerEvent(new Event(2, "b"), 2L);
            EventId idA2 = accessor.registerEvent(new Event(3, "c"), 1L);

            assertEquals(0, idA1.getId());
            assertEquals(0, idB1.getId());
            assertEquals(1, idA2.getId());
        }
    }

    @Test
    void testCacheRollbackOnStateFailure() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new FailingPutStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            try {
                accessor.registerEvent(new Event(1, "a"), 1L);
            } catch (Exception e) {
                // expected - state store fails on put
            }

            assertEquals(0, buffer.getEventsBufferCacheSize());
        }
    }

    @Test
    void testCacheSizeAfterMultipleRegistrations() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());

        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            for (int i = 0; i < 5; i++) {
                accessor.registerEvent(new Event(i, "e" + i), i + 1);
            }
            assertEquals(5, buffer.getEventsBufferCacheSize());
        }
    }

    private static class FailingPutStateStore extends SimpleKeyedStateStore {
        @Override
        public <K, V> MapState<K, V> getMapState(MapStateDescriptor<K, V> stateProperties) {
            return new FailingPutMapState<>();
        }
    }

    private static class FailingPutMapState<K, V> implements MapState<K, V> {
        @Override
        public V get(K key) { return null; }

        @Override
        public void put(K key, V value) {
            throw new StreamException("Simulated state store failure");
        }

        @Override
        public void putAll(Map<K, V> map) {
            throw new StreamException("Simulated state store failure");
        }

        @Override
        public void remove(K key) {}

        @Override
        public boolean contains(K key) { return false; }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() { return Collections.emptyIterator(); }

        @Override
        public Iterable<K> keys() { return Collections.emptyList(); }

        @Override
        public Iterable<V> values() { return Collections.emptyList(); }

        @Override
        public Set<Map.Entry<K, V>> entries() { return Collections.emptySet(); }

        @Override
        public boolean isEmpty() { return true; }

        @Override
        public void clear() {}
    }
}
