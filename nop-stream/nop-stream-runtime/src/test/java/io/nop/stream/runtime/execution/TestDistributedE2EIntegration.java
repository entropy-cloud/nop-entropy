package io.nop.stream.runtime.execution;

import io.nop.api.core.message.*;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.DeploymentMode;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestDistributedE2EIntegration {

    @Test
    void testDistributed_parallelism2_allDataProcessed() throws Exception {
        List<String> results = new CopyOnWriteArrayList<>();
        IMessageService messageService = new InProcessMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(new EmbeddedDistributedExecutor(messageService, 2));

        env.fromElements("a", "b", "c", "d", "e", "f")
                .map(String::toUpperCase)
                .sink(results::add);

        env.execute("distributed-all-data");

        assertTrue(results.size() >= 6,
                "Expected at least 6 results (parallelism=2), got " + results.size() + ": " + results);
        assertTrue(results.containsAll(Arrays.asList("A", "B", "C", "D", "E", "F")),
                "All mapped values should be present: " + results);
    }

    @Test
    void testDistributed_parallelism2_independentOperatorChains() throws Exception {
        AtomicInteger instanceCount = new AtomicInteger(0);
        IMessageService messageService = new InProcessMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(new EmbeddedDistributedExecutor(messageService, 2));

        env.fromElements("x", "y", "z")
                .map(s -> {
                    instanceCount.incrementAndGet();
                    return s + "-" + instanceCount.get();
                })
                .sink(v -> {});

        env.execute("distributed-independent-chains");

        assertTrue(instanceCount.get() >= 3,
                "Map function should be called at least 3 times, got " + instanceCount.get());
    }

    @Test
    void testDistributed_taskFailurePropagates() throws Exception {
        IMessageService messageService = new InProcessMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(new EmbeddedDistributedExecutor(messageService, 2));

        env.fromElements("a", "b", "c", "d")
                .map(s -> {
                    if ("c".equals(s)) throw new RuntimeException("intentional test failure");
                    return s.toUpperCase();
                })
                .sink(v -> {});

        assertThrows(Exception.class, () -> env.execute("distributed-failure-test"),
                "Task failure should propagate to execute()");
    }

    @Test
    void testTimestampPreservedThroughCodec() throws Exception {
        long expectedTimestamp = 123456789L;
        StreamRecord<String> original = new StreamRecord<>("test-value", expectedTimestamp);

        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, "java.lang.String", "fence-token", 100L);
        Object decoded = StreamElementCodec.decode(envelope);

        assertInstanceOf(StreamRecord.class, decoded, "Decoded should be StreamRecord");
        StreamRecord<?> record = (StreamRecord<?>) decoded;
        assertEquals("test-value", record.getValue());
        assertTrue(record.hasTimestamp(), "Timestamp should be preserved");
        assertEquals(expectedTimestamp, record.getTimestamp(),
                "Timestamp value should match after codec roundtrip");
    }

    @Test
    void testNoTimestampPreservedThroughCodec() throws Exception {
        StreamRecord<String> original = new StreamRecord<>("no-ts-value");

        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, "java.lang.String", "fence-token", 100L);
        Object decoded = StreamElementCodec.decode(envelope);

        assertInstanceOf(StreamRecord.class, decoded, "Decoded should be StreamRecord");
        StreamRecord<?> record = (StreamRecord<?>) decoded;
        assertEquals("no-ts-value", record.getValue());
        assertFalse(record.hasTimestamp(), "Record without timestamp should remain without timestamp");
    }

    private static class InProcessMessageService implements IMessageService {
        private final Map<String, List<IMessageConsumer>> subscribers = new HashMap<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
            return new IMessageSubscription() {
                @Override public void cancel() {
                    subscribers.getOrDefault(topic, Collections.emptyList()).remove(listener);
                }
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return false; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            List<IMessageConsumer> consumers = subscribers.get(topic);
            if (consumers != null) {
                for (IMessageConsumer consumer : new ArrayList<>(consumers)) {
                    consumer.onMessage(topic, message, null);
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
