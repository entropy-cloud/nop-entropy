package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestRemoteResultPartition {

    private static final String TOPIC = "test-topic";
    private static final String EDGE_ID = "edge-1";
    private static final String FENCING_TOKEN = "token-1";
    private static final long EPOCH_ID = 1L;

    @Test
    void testWriteSendsToTopic() throws InterruptedException {
        List<Object> sent = new ArrayList<>();
        IMessageService messageService = new MockMessageService(sent);

        RemoteResultPartition partition = new RemoteResultPartition(
                messageService, TOPIC, null, EDGE_ID, FENCING_TOKEN, EPOCH_ID);

        partition.write(new StreamRecord<>("hello", 100L));
        assertEquals(1, sent.size());

        partition.close();
        assertEquals(2, sent.size(), "close should send END_OF_STREAM");
    }

    @Test
    void testConcurrentWriteAndCloseNoDataAfterEos() throws Exception {
        CopyOnWriteArrayList<StreamMessageEnvelope> sent = new CopyOnWriteArrayList<>();
        IMessageService messageService = new ConcurrentMockMessageService(sent);

        RemoteResultPartition partition = new RemoteResultPartition(
                messageService, TOPIC, null, EDGE_ID, FENCING_TOKEN, EPOCH_ID);

        int writerCount = 4;
        int writesPerWriter = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writerCount);
        AtomicInteger writeErrors = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(writerCount + 1);
        for (int i = 0; i < writerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < writesPerWriter; j++) {
                        try {
                            partition.write(new StreamRecord<>("data-" + j, j));
                        } catch (Exception e) {
                            writeErrors.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        Thread.sleep(50);
        partition.close();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        int eosIndex = -1;
        for (int i = 0; i < sent.size(); i++) {
            if ("END_OF_STREAM".equals(sent.get(i).getPayload())) {
                eosIndex = i;
                break;
            }
        }

        if (eosIndex >= 0) {
            for (int i = eosIndex + 1; i < sent.size(); i++) {
                assertNotEquals("END_OF_STREAM", sent.get(i).getPayload(),
                        "No data should appear after END_OF_STREAM");
                assertNull(sent.get(i).getPayload(),
                        "No data messages should appear after END_OF_STREAM");
            }
        }
    }

    static class MockMessageService implements IMessageService {
        private final List<Object> sent;

        MockMessageService(List<Object> sent) {
            this.sent = sent;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, io.nop.api.core.message.MessageSendOptions options) {
            sent.add(message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void send(String topic, Object message) {
            sent.add(message);
        }

        @Override
        public io.nop.api.core.message.IMessageSubscription subscribe(String topic, io.nop.api.core.message.IMessageConsumer listener, io.nop.api.core.message.MessageSubscribeOptions options) {
            return new io.nop.api.core.message.IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return true; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }
    }

    static class ConcurrentMockMessageService implements IMessageService {
        private final CopyOnWriteArrayList<StreamMessageEnvelope> sent;

        ConcurrentMockMessageService(CopyOnWriteArrayList<StreamMessageEnvelope> sent) {
            this.sent = sent;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, io.nop.api.core.message.MessageSendOptions options) {
            send(topic, message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void send(String topic, Object message) {
            if (message instanceof StreamMessageEnvelope) {
                sent.add((StreamMessageEnvelope) message);
            }
        }

        @Override
        public io.nop.api.core.message.IMessageSubscription subscribe(String topic, io.nop.api.core.message.IMessageConsumer listener, io.nop.api.core.message.MessageSubscribeOptions options) {
            return new io.nop.api.core.message.IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return true; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }
    }
}
