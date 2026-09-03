/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.source.CollectionReplayableSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Item 32 (Phase 2 focused tests): the channel queue-depth gauge
 * {@code nop.stream.io.channel.queue.size} — registration scope (D2b),
 * value-follows-real-queue-operations, re-registration/recovery semantics and
 * close-release semantics (D2c). All gauge reads go through the REAL process
 * composite registry (never a direct {@code queueSize()} call) so the
 * assertions prove the meter wiring, not the queue implementation.
 */
class TestChannelQueueGauges {

    private LocalMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
    }

    private static Gauge gaugeOf(String jobId, String edgeId, int s, int t) {
        return StreamMetricsRegistries.registry()
                .find(ChannelQueueGauges.METRIC_NAME)
                .tag(ChannelQueueGauges.TAG_JOB_ID, jobId)
                .tag(ChannelQueueGauges.TAG_EDGE_ID, edgeId)
                .tag(ChannelQueueGauges.TAG_SOURCE_SUBTASK, String.valueOf(s))
                .tag(ChannelQueueGauges.TAG_TARGET_SUBTASK, String.valueOf(t))
                .gauge();
    }

    /** Polls until the gauge reports the expected value (async LocalMessageService dispatch). */
    private static void awaitGaugeValue(Gauge gauge, double expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (gauge != null && gauge.value() == expected) {
                return;
            }
            Thread.sleep(20L);
        }
        assertEquals(expected, gauge == null ? Double.NaN : gauge.value(),
                "gauge must report " + expected + " within " + timeoutMs + "ms");
    }

    private RemoteResultPartition producerOf(String jobId, String edgeId, int s, int t) {
        String topic = StreamTopicNaming.buildTopic(jobId, edgeId, s, t);
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register(edgeId, String.class.getName());
        return new RemoteResultPartition(messageService, topic, typeRegistry, edgeId, 1L);
    }

    // ==================== registration + value follows real operations ====================

    @Test
    void gaugeFollowsRealEnqueueAndDequeue() throws Exception {
        String jobId = "gauge-follow-" + System.nanoTime();
        String edgeId = "src->tgt";
        RemoteResultPartition producer = producerOf(jobId, edgeId, 0, 0);
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, StreamTopicNaming.buildTopic(jobId, edgeId, 0, 0), 1L);
        try {
            ChannelQueueGauges.bind(channel, jobId, edgeId, 0, 0);
            Gauge gauge = gaugeOf(jobId, edgeId, 0, 0);
            assertNotNull(gauge, "bind() must register the gauge in the process registry");

            // Real enqueue path: producer writes → dispatch → channel queue.
            producer.write(new StreamRecord<>("a"));
            producer.write(new StreamRecord<>("b"));
            producer.write(new StreamRecord<>("c"));
            awaitGaugeValue(gauge, 3.0, 5000L);

            // Real dequeue path: read() consumes from the queue.
            StreamElement first = channel.read(2, TimeUnit.SECONDS);
            assertNotNull(first);
            awaitGaugeValue(gauge, 2.0, 5000L);
        } finally {
            channel.close();
        }
    }

    // ==================== D2c: re-registration (recovery rebuild) + close release ====================

    @Test
    void recoveryRebindFollowsNewChannelAndCloseReleases() throws Exception {
        String jobId = "gauge-rebind-" + System.nanoTime();
        String edgeId = "src->tgt";
        String topic = StreamTopicNaming.buildTopic(jobId, edgeId, 0, 0);

        // Generation 1 channel (recovery's old channel).
        RemoteResultPartition producer = producerOf(jobId, edgeId, 0, 0);
        RemoteInputChannel first = new RemoteInputChannel(messageService, topic, 1L);
        ChannelQueueGauges.bind(first, jobId, edgeId, 0, 0);
        Gauge gauge = gaugeOf(jobId, edgeId, 0, 0);
        assertNotNull(gauge);
        producer.write(new StreamRecord<>("x"));
        producer.write(new StreamRecord<>("y"));
        awaitGaugeValue(gauge, 2.0, 5000L);

        // Recovery: the OLD channel closes (holder released → 0, no frozen value).
        first.close();
        awaitGaugeValue(gauge, 0.0, 5000L);

        // Rebuild under the same identity: the SAME meter must follow the NEW
        // channel's live queue (micrometer would silently drop a second
        // registration — the mutable-holder shape prevents the stale binding).
        RemoteInputChannel second = new RemoteInputChannel(messageService, topic, 1L);
        try {
            ChannelQueueGauges.bind(second, jobId, edgeId, 0, 0);
            Gauge sameMeter = gaugeOf(jobId, edgeId, 0, 0);
            assertEquals(gauge, sameMeter, "rebind must NOT register a second meter id");
            awaitGaugeValue(sameMeter, 0.0, 5000L);

            producer.write(new StreamRecord<>("z"));
            awaitGaugeValue(sameMeter, 1.0, 5000L);
            StreamElement consumed = second.read(2, TimeUnit.SECONDS);
            assertNotNull(consumed);
            awaitGaugeValue(sameMeter, 0.0, 5000L);
        } finally {
            second.close();
        }
    }

    @Test
    void rebindWhileOldChannelStillQueuedFollowsNewestBinding() throws Exception {
        String jobId = "gauge-newest-" + System.nanoTime();
        String edgeId = "src->tgt";
        String topic = StreamTopicNaming.buildTopic(jobId, edgeId, 0, 0);

        RemoteResultPartition producer = producerOf(jobId, edgeId, 0, 0);
        RemoteInputChannel old = new RemoteInputChannel(messageService, topic, 1L);
        RemoteInputChannel fresh = null;
        try {
            ChannelQueueGauges.bind(old, jobId, edgeId, 0, 0);
            producer.write(new StreamRecord<>("stale"));
            Gauge gauge = gaugeOf(jobId, edgeId, 0, 0);
            assertNotNull(gauge);
            awaitGaugeValue(gauge, 1.0, 5000L);

            // Redeploy raced the old close: the old channel still holds its
            // element, and the replacement channel subscribes NOW (the
            // in-memory backend does not replay history to it).
            fresh = new RemoteInputChannel(messageService, topic, 1L);
            ChannelQueueGauges.bind(fresh, jobId, edgeId, 0, 0);
            assertEquals(gauge, gaugeOf(jobId, edgeId, 0, 0));
            awaitGaugeValue(gauge, 0.0, 5000L);

            producer.write(new StreamRecord<>("fresh"));
            awaitGaugeValue(gauge, 1.0, 5000L);
        } finally {
            old.close();
            if (fresh != null) {
                fresh.close();
            }
        }
    }

    // ==================== D2b: registration scope ====================

    @Test
    void builderRegistersGaugeOnlyForSubscribingChannels() {
        String jobId = "gauge-scope-" + System.nanoTime();
        JobGraph graph = trivialGraph(jobId);
        RecordingMessageService recording = new RecordingMessageService();

        // Coordinator form: zero-scope build (remoteDeployMode=true) — no channel
        // subscribes, therefore NO gauge (a construct-only channel's permanent 0
        // would be indistinguishable from a healthy empty consumer queue).
        new RemoteGraphExecutionPlanBuilder(recording, new TypeRegistry(), 1L)
                .buildRemoteOnly(graph, null, true, Collections.emptySet());
        assertNull(gaugeOf(jobId, "source->sink", 0, 0),
                "zero-scope (coordinator) build must not register channel gauges");

        // TM form: one deployed subtask ("sink/0") — exactly that subtask's input
        // channel subscribes and gets the gauge.
        RecordingMessageService recording2 = new RecordingMessageService();
        GraphExecutionPlan plan = new RemoteGraphExecutionPlanBuilder(recording2, new TypeRegistry(), 1L)
                .buildRemoteOnly(graph, null, true, Collections.singleton("sink/0"));
        assertNotNull(plan);
        Gauge gauge = gaugeOf(jobId, "source->sink", 0, 0);
        assertNotNull(gauge, "subscribed channel must have its queue-depth gauge");
        assertEquals(0.0, gauge.value(), "fresh channel reports an empty queue");
    }

    // ==================== fixtures ====================

    private static JobGraph trivialGraph(String jobId) {
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(
                new CollectionReplayableSource<>(Collections.emptyList()));
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(
                new io.nop.stream.core.common.functions.sink.PrintSinkFunction<>());
        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));
        JobGraph graph = new JobGraph(jobId);
        graph.addVertex(new JobVertex("source", "Source", 1,
                Collections.singletonList(sourceChain), new StreamTaskInvokable(sourceChain)));
        graph.addVertex(new JobVertex("sink", "Sink", 1,
                Collections.singletonList(sinkChain), new StreamTaskInvokable(sinkChain)));
        graph.addEdge(new JobEdge("source", "sink", ResultPartitionType.PIPELINED));
        return graph;
    }

    /** No-op message service (no data flows through the scope test). */
    static final class RecordingMessageService implements IMessageService {
        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                              MessageSubscribeOptions options) {
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isSuspended() {
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
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                                                                    MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
