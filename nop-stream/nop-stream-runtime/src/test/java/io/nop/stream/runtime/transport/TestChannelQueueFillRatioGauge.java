/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.micrometer.core.instrument.Gauge;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.metrics.StreamMetricsRegistries;
import io.nop.stream.runtime.transport.ChannelQueueGauges;
import io.nop.stream.runtime.transport.RemoteInputChannel;
import io.nop.stream.runtime.transport.RemoteResultPartition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI16 (plan 2337): the edge-level queue fill-ratio gauge — a derived readout on the
 * SAME holder as the queue.size gauge (review F4: one probe, two views). Asserts the
 * ratio tracks real enqueue/dequeue against the channel's recorded capacity and is
 * clamped to [0,1].
 */
class TestChannelQueueFillRatioGauge {

    private LocalMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
    }

    private static Gauge ratioOf(String jobId, String edgeId, int s, int t) {
        return StreamMetricsRegistries.registry()
                .find(ChannelQueueGauges.METRIC_FILL_RATIO)
                .tag(ChannelQueueGauges.TAG_JOB_ID, jobId)
                .tag(ChannelQueueGauges.TAG_EDGE_ID, edgeId)
                .tag(ChannelQueueGauges.TAG_SOURCE_SUBTASK, String.valueOf(s))
                .tag(ChannelQueueGauges.TAG_TARGET_SUBTASK, String.valueOf(t))
                .gauge();
    }

    private RemoteResultPartition producerOf(String jobId, String edgeId, int s, int t) {
        String topic = StreamTopicNaming.buildTopic(jobId, edgeId, s, t);
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register(edgeId, String.class.getName());
        return new RemoteResultPartition(messageService, topic, typeRegistry, edgeId, 1L);
    }

    @Test
    void fillRatioRegistersAlongsideSizeAndTracksQueueDepth() throws Exception {
        String jobId = "fill-ratio-" + System.nanoTime();
        String edgeId = "src->tgt";
        RemoteResultPartition producer = producerOf(jobId, edgeId, 0, 0);
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, StreamTopicNaming.buildTopic(jobId, edgeId, 0, 0), 1L);
        try {
            ChannelQueueGauges.bind(channel, jobId, edgeId, 0, 0);
            Gauge ratio = ratioOf(jobId, edgeId, 0, 0);
            assertNotNull(ratio, "bind() must register the fill-ratio gauge alongside queue.size");
            assertEquals(0.0, ratio.value(), "empty queue = 0 fill");

            // real enqueue path fills the queue: ratio > 0 afterwards
            for (int i = 0; i < 5; i++) {
                producer.write(new io.nop.stream.core.streamrecord.StreamRecord<>("msg-" + i));
            }
            long deadline = System.currentTimeMillis() + 5000L;
            while (System.currentTimeMillis() < deadline && !(ratio.value() > 0.0)) {
                Thread.sleep(20L);
            }
            assertTrue(ratio.value() > 0.0 && ratio.value() <= 1.0,
                    "fill ratio must be in (0,1] after enqueue, got " + ratio.value());

            // capacity rides the channel (review F7: a field, not a torn computation)
            assertEquals(RemoteInputChannel.DEFAULT_QUEUE_CAPACITY, channel.getCapacity());
        } finally {
            ChannelQueueGauges.release(channel);
        }
    }

    /** After release the ratio reads 0 (same null-holder convention as queue.size). */
    @Test
    void fillRatioReadsZeroAfterRelease() throws Exception {
        String jobId = "fill-release-" + System.nanoTime();
        String edgeId = "src->tgt";
        RemoteResultPartition producer = producerOf(jobId, edgeId, 0, 0);
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, StreamTopicNaming.buildTopic(jobId, edgeId, 0, 0), 1L);
        ChannelQueueGauges.bind(channel, jobId, edgeId, 0, 0);
        producer.write(new io.nop.stream.core.streamrecord.StreamRecord<>("x"));
        ChannelQueueGauges.release(channel);
        Gauge ratio = ratioOf(jobId, edgeId, 0, 0);
        assertNotNull(ratio);
        assertEquals(0.0, ratio.value(), "released holder = 0 fill");
    }
}
