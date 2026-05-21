package io.nop.message.core;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(testBeansFile = "/nop/message/beans/test.beans.xml")
class TestLocalMessageServiceAsync extends JunitBaseTestCase {

    @Inject
    IMessageService messageService;

    @Test
    void testAsyncCompletionStageResolvesValue() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> received = new AtomicReference<>();

        messageService.subscribe("async-test", (IMessageConsumer) (topic, message, context) -> {
            return CompletableFuture.completedFuture(new Acknowledge("ack-result"));
        });

        messageService.subscribe("ack-async-test", (IMessageConsumer) (topic, message, context) -> {
            received.set(message);
            latch.countDown();
            return null;
        });

        messageService.send("async-test", "test-message");
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("ack-result", received.get());
    }

    @Test
    void testExceptionIsolation() {
        AtomicInteger count = new AtomicInteger(0);

        messageService.subscribe("isolation-test", (IMessageConsumer) (topic, message, context) -> {
            throw new RuntimeException("consumer-1-fail");
        });

        messageService.subscribe("isolation-test", (IMessageConsumer) (topic, message, context) -> {
            count.incrementAndGet();
            return null;
        });

        messageService.send("isolation-test", "test-message");
        assertEquals(1, count.get());
    }
}
