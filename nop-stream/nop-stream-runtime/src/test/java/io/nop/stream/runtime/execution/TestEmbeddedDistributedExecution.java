package io.nop.stream.runtime.execution;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.message.*;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.DeploymentMode;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TestEmbeddedDistributedExecution {

    @Test
    void testDistributed_sourceMapSink() throws Exception {
        List<String> results = new CopyOnWriteArrayList<>();
        IMessageService messageService = new InProcessMessageService();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(new EmbeddedDistributedExecutor(messageService, 2));

        env.fromElements("a", "b", "c", "d").map(String::toUpperCase).sink(results::add);
        env.execute("distributed-test");

        assertTrue(results.size() >= 4,
                "Expected at least 4 results, got " + results.size() + ": " + results);
        assertTrue(results.containsAll(Arrays.asList("A", "B", "C", "D")));
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
