/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.event;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Item 16 (P-REQ-2): event bus dispatch semantics — listener callbacks,
 * built-in logging listener, listener-failure isolation.
 */
class TestStreamJobEventBus {

    @Test
    void testListenersCalledBackWithEvent() {
        StreamJobEventBus bus = new StreamJobEventBus();
        List<StreamJobEvent> received = new ArrayList<>();
        bus.addListener(received::add);

        StreamJobEvent event = new StreamJobEvent("job-1",
                StreamJobEvent.EventType.CHECKPOINT_COMPLETED,
                123L, 5L, 200L, 1024L, null);
        bus.fire(event);

        assertEquals(1, received.size());
        assertSameEvent(event, received.get(0));
        assertEquals(1, bus.getListenerCount());
    }

    @Test
    void testFailingListenerDoesNotBreakDispatchOrOtherListeners() {
        StreamJobEventBus bus = new StreamJobEventBus();
        List<StreamJobEvent> received = new ArrayList<>();
        bus.addListener(e -> {
            throw new IllegalStateException("listener bug");
        });
        bus.addListener(received::add);

        bus.fire(StreamJobEvent.simple("job-1", StreamJobEvent.EventType.JOB_FAILED, "boom"));

        assertEquals(1, received.size());
        assertEquals(StreamJobEvent.EventType.JOB_FAILED, received.get(0).getType());
        assertEquals("boom", received.get(0).getCause());
    }

    @Test
    void testLoggingListenerDoesNotThrow() {
        // the built-in logging listener must accept every event type
        LoggingJobEventListener listener = new LoggingJobEventListener();
        for (StreamJobEvent.EventType type : StreamJobEvent.EventType.values()) {
            listener.onEvent(StreamJobEvent.simple("job-1", type, "unit"));
        }
    }

    @Test
    void testNullEventIgnored() {
        StreamJobEventBus bus = new StreamJobEventBus();
        // firing null must be a no-op, not an NPE
        bus.fire(null);
        assertEquals(0, bus.getListenerCount());
    }

    private void assertSameEvent(StreamJobEvent expected, StreamJobEvent actual) {
        assertEquals(expected.getJobId(), actual.getJobId());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
        assertEquals(expected.getCheckpointId(), actual.getCheckpointId());
        assertEquals(expected.getDurationMs(), actual.getDurationMs());
        assertEquals(expected.getSizeBytes(), actual.getSizeBytes());
    }
}
