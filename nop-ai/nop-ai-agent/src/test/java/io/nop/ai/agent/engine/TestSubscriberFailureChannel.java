package io.nop.ai.agent.engine;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2291 (WI10): subscriber failure reporting channel — per-subscriber
 * failure counters, LOG_ONLY default (failure never blocks the fan-out),
 * and REMOVE_AFTER_THRESHOLD auto-removal.
 */
public class TestSubscriberFailureChannel {

    static class FailingSubscriber implements IAgentEventSubscriber {
        int calls;

        @Override
        public void onEvent(AgentEvent event) {
            calls++;
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL)
                    .param(ARG_DETAIL, "subscriber boom");
        }
    }

    static class RecordingSubscriber implements IAgentEventSubscriber {
        int events;

        @Override
        public void onEvent(AgentEvent event) {
            events++;
        }
    }

    private static AgentEvent event() {
        return AgentEvent.create(AgentEventType.EXECUTION_STARTED, "s1", "agent", null);
    }

    @Test
    void logOnlyDefaultCountsFailuresWithoutBlockingFanOut() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        FailingSubscriber failing = new FailingSubscriber();
        RecordingSubscriber healthy = new RecordingSubscriber();
        publisher.addSubscriber(failing);
        publisher.addSubscriber(healthy);

        publisher.publish(event());

        assertEquals(1, failing.calls, "failing subscriber was invoked");
        assertEquals(1, healthy.events, "healthy subscriber still receives events");
        assertEquals(1, publisher.getSubscriberFailureCount(failing));
        assertEquals(SubscriberFailurePolicy.LOG_ONLY, publisher.getSubscriberFailurePolicy());
    }

    @Test
    void removeAfterThresholdAutoRemovesFailingSubscriber() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        publisher.setSubscriberFailurePolicy(SubscriberFailurePolicy.REMOVE_AFTER_THRESHOLD);
        publisher.setFailureRemovalThreshold(3);
        FailingSubscriber failing = new FailingSubscriber();
        RecordingSubscriber healthy = new RecordingSubscriber();
        publisher.addSubscriber(failing);
        publisher.addSubscriber(healthy);

        for (int i = 0; i < 3; i++) {
            publisher.publish(event());
        }
        assertEquals(3, publisher.getSubscriberFailureCount(failing));
        // after the threshold the subscriber is removed — later events skip it
        publisher.publish(event());
        assertEquals(3, failing.calls, "removed subscriber no longer invoked");
        assertEquals(4, healthy.events, "healthy subscriber unaffected");
    }

    @Test
    void zeroFailuresReportedAsZero() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        RecordingSubscriber healthy = new RecordingSubscriber();
        publisher.addSubscriber(healthy);
        publisher.publish(event());
        assertEquals(0, publisher.getSubscriberFailureCount(healthy));
    }
}
