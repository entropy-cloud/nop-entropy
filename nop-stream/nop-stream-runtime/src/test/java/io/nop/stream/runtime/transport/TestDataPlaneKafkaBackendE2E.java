/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.concurrent.TimeUnit;

import io.nop.api.core.message.IMessageService;
import io.nop.message.kafka.KafkaClientConfig;
import io.nop.message.kafka.KafkaConsumerConfig;
import io.nop.message.kafka.KafkaMessageService;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 48 — data-plane cross-JVM E2E over the real Apache Kafka backend.
 *
 * <p><strong>Gated integration test.</strong> Kafka requires a running broker that this
 * module does not embed (no testcontainers / embedded-broker dependency to keep the
 * module lightweight). CI provides a broker and enables this test with
 * {@code -Dnop.stream.test.kafka.enabled=true} and
 * {@code -Dnop.stream.test.kafka.brokers=host:9092}. Without the flag the test is
 * skipped — this is the CI-provided-service + gate pattern explicitly allowed by the
 * plan (mirrors {@code TestDataPlanePulsarBackendE2E}).
 *
 * <p>What it proves when enabled: a {@link StreamMessageEnvelope} genuinely traverses a
 * real Kafka topic — the producer's JSON-encoded wire string is carried by the broker
 * and reconstructed on the subscriber side, with record/barrier/watermark all crossing
 * the backend (plan guide #22/#23). The {@link KafkaStringWireCodec} round-trip logic
 * itself is pinned by {@code TestKafkaStringWireCodec} (runs always, no broker needed).
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "nop.stream.test.kafka.enabled", matches = "true")
class TestDataPlaneKafkaBackendE2E {

    private static final String JOB_ID = "kafka-dataplane-job";
    private static final String EDGE_ID = "src->tgt";
    private static final long EPOCH = 9L;

    private KafkaMessageService backend;
    private IMessageService dataPlane;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        String brokers = System.getProperty("nop.stream.test.kafka.brokers",
                "localhost:9092");
        backend = new KafkaMessageService();
        KafkaClientConfig config = new KafkaClientConfig();
        config.setBootstrapServers(brokers);
        backend.setConfig(config);
        KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();
        backend.setDefaultConsumerConfig(consumerConfig);
        backend.init();
        dataPlane = new DataPlaneMessageServiceAdapter(backend, KafkaStringWireCodec.INSTANCE);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (backend != null) {
            backend.destroy();
        }
    }

    @org.junit.jupiter.api.Test
    void recordBarrierWatermarkTraverseKafkaTopic() throws Exception {
        String topic = StreamTopicNaming.buildTopic(JOB_ID, EDGE_ID, 0, 0);
        // Unique topic per run (Kafka topics retain data across CI runs).
        topic = topic + "-" + Long.toHexString(System.nanoTime());

        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register(EDGE_ID, String.class.getName());

        RemoteResultPartition producer = new RemoteResultPartition(
                dataPlane, topic, typeRegistry, EDGE_ID, EPOCH);
        RemoteInputChannel consumer = new RemoteInputChannel(dataPlane, topic, EPOCH);

        try {
            // Give the subscription a moment to register with the broker before sending.
            Thread.sleep(2000);

            producer.write(new StreamRecord<>("kafka-record"));
            producer.write(new Watermark(1234L));
            CheckpointBarrier barrier = new CheckpointBarrier(7L, 2000L, CheckpointType.CHECKPOINT);
            producer.write(barrier);

            StreamElement recordElem = consumer.read(30, TimeUnit.SECONDS);
            assertNotNull(recordElem, "record must arrive over the Kafka broker");
            assertTrue(recordElem.isRecord());
            assertEquals("kafka-record", recordElem.asRecord().getValue());

            StreamElement watermarkElem = consumer.read(30, TimeUnit.SECONDS);
            assertNotNull(watermarkElem);
            assertTrue(watermarkElem.isWatermark());
            assertEquals(1234L, watermarkElem.asWatermark().getTimestamp());

            StreamElement barrierElem = consumer.read(30, TimeUnit.SECONDS);
            assertNotNull(barrierElem);
            assertTrue(barrierElem.isCheckpointBarrier());
            assertEquals(7L, barrierElem.asCheckpointBarrier().getId());
        } finally {
            consumer.close();
        }
    }
}
