package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for the 2026-09-01 cep module audit fixes on
 * {@link SharedBuffer}/{@link SharedBufferAccessor}:
 *
 * <ul>
 *   <li>EventId counter overflow: when the per-timestamp counter already sits at
 *       {@code Integer.MAX_VALUE} and that slot is free, {@code registerEvent} must fail fast
 *       (previously the guard only fired inside the collision loop, so the counter write
 *       silently overflowed to {@code Integer.MIN_VALUE}).</li>
 *   <li>{@code lockEvent} on a missing event must throw a typed {@code StreamException} with an
 *       interpolated eventId (previously a bare {@code IllegalStateException} via
 *       {@code Guard.checkState} with a literal "%s" in the message).</li>
 * </ul>
 */
public class TestSharedBufferAuditFixes {

    /** State name constants mirrored from SharedBuffer (private there). */
    private static final String EVENTS_STATE_NAME = "sharedBuffer-events";
    private static final String EVENTS_COUNT_STATE_NAME = "sharedBuffer-events-count";

    private MemoryKeyedStateBackend<Object> backend() {
        return new MemoryKeyedStateBackend<>(Object.class);
    }

    @Test
    void testEventIdCounterOverflowFailsFast() throws Exception {
        MemoryKeyedStateBackend<Object> backend = backend();
        // Drive the per-timestamp counter to the overflow boundary; the (MAX_VALUE, ts) slot
        // itself stays free, so the collision loop is skipped entirely.
        backend.getMapState(new MapStateDescriptor<>(EVENTS_COUNT_STATE_NAME, Long.class, Integer.class))
                .put(0L, Integer.MAX_VALUE);

        SharedBuffer<Event> buffer = new SharedBuffer<>(backend, null, new SharedBufferCacheConfig());
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            StreamException ex = assertThrows(StreamException.class,
                    () -> accessor.registerEvent(new Event(1, "x"), 0L));
            String detail = (String) ex.getParam(ARG_DETAIL);
            assertTrue(detail != null && detail.contains("overflow"),
                    "detail should report the overflow but was: " + detail);
        }
    }

    @Test
    void testLockEventMissingEventFailsFastTyped() throws Exception {
        MemoryKeyedStateBackend<Object> backend = backend();

        // Register an event through a first buffer instance.
        EventId eventId;
        SharedBuffer<Event> first = new SharedBuffer<>(backend, null, new SharedBufferCacheConfig());
        try (SharedBufferAccessor<Event> accessor = first.getAccessor()) {
            eventId = accessor.registerEvent(new Event(1, "x"), 1L);
        }

        // Corrupt the state behind the buffer's back: drop the event entry from the backing
        // keyed state (simulates inconsistent buffer state).
        backend.getMapState(new MapStateDescriptor<>(EVENTS_STATE_NAME, EventId.class, (Class) Lockable.class))
                .remove(eventId);

        // A fresh buffer has fresh caches, so getEvent misses both cache and state and
        // lockEvent hits its missing-event guard while putting a new node for that event.
        SharedBuffer<Event> second = new SharedBuffer<>(backend, null, new SharedBufferCacheConfig());
        try (SharedBufferAccessor<Event> accessor = second.getAccessor()) {
            StreamException ex = assertThrows(StreamException.class,
                    () -> accessor.put("a1", eventId, null, DeweyNumber.fromString("1")));
            String detail = (String) ex.getParam(ARG_DETAIL);
            assertTrue(detail != null && detail.contains("non-existent event") && detail.contains(String.valueOf(eventId)),
                    "detail should carry the interpolated eventId but was: " + detail);
        }
    }
}
