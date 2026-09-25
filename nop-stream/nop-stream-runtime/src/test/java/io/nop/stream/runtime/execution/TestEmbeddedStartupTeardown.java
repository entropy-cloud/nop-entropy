/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.cluster.naming.INamingService;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.DeploymentMode;
import io.nop.stream.runtime.taskmanager.TaskManager;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 358 Fix-2 (startup teardown): when embedded assembly fails AFTER the
 * TaskManagers were started (here: platform discovery registration fails
 * fail-loud), the finally teardown must stop every already-started node.
 * Before the fix the {@code tm.start()} loop ran before the try block, so a
 * registration/assembly failure leaked each node's heartbeat and task-executor
 * threads.
 */
class TestEmbeddedStartupTeardown {

    @Test
    void startupFailureStopsStartedTaskManagers() throws Exception {
        EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(
                new InProcessMessageService(), 2, 120L, new FailingNamingService());

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(executor);
        env.fromElements("a", "b").map(x -> x).sink(x -> {
        });

        // Discovery registration fails fail-loud AFTER both TaskManagers started.
        assertThrows(Exception.class, () -> env.execute("p358-startup-fail"),
                "a startup failure must propagate (fail-loud registration contract)");

        List<TaskManager> created = executor.lastCreatedTaskManagers;
        assertNotNull(created, "the executor must expose the nodes it created (test hook)");
        assertFalse(created.isEmpty());
        for (TaskManager tm : created) {
            assertFalse(tm.isRunning(),
                    "startup-failure teardown must stop already-started TaskManager " + tm.getNodeId());
        }
    }

    @Test
    void healthyEmbeddedRunStillStopsTaskManagers() throws Exception {
        EmbeddedDistributedExecutor executor = new EmbeddedDistributedExecutor(
                new InProcessMessageService(), 2, 120L);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.setParallelism(2);
        env.setDeploymentMode(DeploymentMode.DISTRIBUTED);
        env.setExecutionDispatcher(executor);
        List<String> results = new java.util.concurrent.CopyOnWriteArrayList<>();
        env.fromElements("a", "b", "c", "d").map(String::toUpperCase).sink(results::add);
        env.execute("p358-healthy-run");

        assertTrue(results.size() >= 4, "the healthy path must keep working after the restructure");
        for (TaskManager tm : executor.lastCreatedTaskManagers) {
            assertFalse(tm.isRunning(), "normal completion must also stop every TaskManager");
        }
    }

    // ---------------- stubs ----------------

    static class FailingNamingService implements INamingService {
        @Override
        public void registerInstance(io.nop.cluster.discovery.ServiceInstance instance) {
            throw new IllegalStateException("naming service unavailable (plan 358 Fix-2 test)");
        }

        @Override
        public void unregisterInstance(io.nop.cluster.discovery.ServiceInstance instance) {
        }

        @Override
        public List<String> getServices() {
            return Collections.emptyList();
        }

        @Override
        public List<io.nop.cluster.discovery.ServiceInstance> getInstances(String serviceName) {
            return Collections.emptyList();
        }
    }

    static class InProcessMessageService implements IMessageService {
        private final Map<String, List<IMessageConsumer>> subscribers = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            subscribers.computeIfAbsent(topic, k -> new java.util.ArrayList<>()).add(listener);
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                    subscribers.getOrDefault(topic, Collections.emptyList()).remove(listener);
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }
            };
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            List<IMessageConsumer> consumers = subscribers.get(topic);
            if (consumers != null) {
                for (IMessageConsumer consumer : new java.util.ArrayList<>(consumers)) {
                    consumer.onMessage(topic, message, null);
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
