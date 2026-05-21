package io.nop.message.pulsar;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.pulsar.client.api.Consumer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestPulsarConsumeTask {

    @Test
    void testStart_setsActive() {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("test-topic", msgConsumer, options);

        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);

        assertFalse(task.isActive());

        task.start();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(task.isActive());

        task.stop();
        executor.shutdown();
    }

    @Test
    void testStop_deactivates() {
        PulsarMessageService service = mock(PulsarMessageService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Object> consumer = mock(Consumer.class);
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("test-topic", msgConsumer, options);

        PulsarConsumeTask task = new PulsarConsumeTask(service, executor, consumer, config);

        assertFalse(task.isActive());

        task.start();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(task.isActive());

        task.stop();
        assertFalse(task.isActive());

        executor.shutdown();
    }
}
