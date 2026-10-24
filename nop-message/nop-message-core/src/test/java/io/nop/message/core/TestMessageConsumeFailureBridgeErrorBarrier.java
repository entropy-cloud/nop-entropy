package io.nop.message.core;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.retry.api.IRetryEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C3-009 barrier ⑦ regression (plan 2496 Phase 4, bridge half): the bridge is declared
 * never-throw (auxiliary operations channel) — an Error thrown by the retry-engine
 * submission path must be contained by the submit barrier and logged, not escaped to the
 * consume thread's failure-report call site.
 */
class TestMessageConsumeFailureBridgeErrorBarrier {

    @Test
    void engineErrorIsContainedAndNotThrown() {
        IRetryEngine engine = mock(IRetryEngine.class);
        when(engine.newRetryTask(anyString(), anyString()))
                .thenThrow(new LinkageError("injected engine Error"));

        MessageConsumeFailureBridge bridge = new MessageConsumeFailureBridge();
        bridge.setRetryEngine(engine);
        bridge.setServiceName("svc");
        bridge.setServiceMethod("m");

        ApiMessage message = mock(ApiMessage.class);
        ConsumeFailure failure = new ConsumeFailure("orders", "orders-0@42", message, 1,
                new IllegalStateException("handler-boom"));

        assertDoesNotThrow(() -> bridge.submit(failure),
                "bridge.submit must never throw, even on an injected Error");
    }
}
