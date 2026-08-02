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
import io.nop.message.pulsar.PulsarClientConfig;
import io.nop.message.pulsar.PulsarMessageService;
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
 * Stage 40 Phase 2 — data-plane cross-JVM E2E over the real Apache Pulsar backend.
 *
 * <p><strong>Gated integration test.</strong> Pulsar requires a running broker that this
 * module does not embed (no testcontainers / embedded-broker dependency to keep the
 * module lightweight, and Pulsar 2.8.0 broker deps are heavy). CI provides a broker and
 * enables this test with {@code -Dnop.stream.test.pulsar.enabled=true} and
 * {@code -Dnop.stream.test.pulsar.serviceUrl=pulsar://host:6650}. Without the flag the
 * test is skipped — this is the CI-provided-service + gate pattern explicitly allowed by
 * the plan ("CI 提供的 Pulsar 实例 + @EnabledIfSystemProperty 门禁"), automated and
 * CI-repeatable, NOT a manual "run once" check.
 *
 * <p>What it proves when enabled: a {@link StreamMessageEnvelope} genuinely traverses a
 * real Pulsar topic — the producer's JSON-encoded wire string is carried by the broker
 * and reconstructed on the subscriber side, with record/barrier/watermark all crossing
 * the backend (plan guide #22/#23). The {@link PulsarStringWireCodec} round-trip logic
 * itself is pinned by {@code TestPulsarStringWireCodec} (runs always, no broker needed).
 *
 * <p>Back-pressure contract: Pulsar's built-in producer flow control (pending-message
 * queue full → send back-pressure) provides cross-JVM back-pressure; nop-stream builds no
 * credit-based / ACK_WINDOW layer (vision §三 constraint 7). A producer send failure
 * propagates through {@code IMessageService.send} as an exception (no silent swallow).
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "nop.stream.test.pulsar.enabled", matches = "true")
class TestDataPlanePulsarBackendE2E {

    private static final String JOB_ID = "pulsar-dataplane-job";
    private static final String EDGE_ID = "src->tgt";
    private static final long EPOCH = 9L;

    private PulsarMessageService backend;
    private IMessageService dataPlane;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        String serviceUrl = System.getProperty("nop.stream.test.pulsar.serviceUrl",
                "pulsar://localhost:6650");
        backend = new PulsarMessageService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl(serviceUrl);
        backend.setConfig(config);
        backend.init();
        dataPlane = new DataPlaneMessageServiceAdapter(backend, PulsarStringWireCodec.INSTANCE);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (backend != null) {
            backend.destroy();
        }
    }

    @org.junit.jupiter.api.Test
    void recordBarrierWatermarkTraversePulsarTopic() throws Exception {
        String topic = StreamTopicNaming.buildTopic(JOB_ID, EDGE_ID, 0, 0);
        // Unique topic per run (Pulsar topics may retain data across CI runs).
        topic = topic + "-" + Long.toHexString(System.nanoTime());

        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register(EDGE_ID, String.class.getName());

        RemoteResultPartition producer = new RemoteResultPartition(
                dataPlane, topic, typeRegistry, EDGE_ID, EPOCH);
        RemoteInputChannel consumer = new RemoteInputChannel(dataPlane, topic, EPOCH);

        try {
            // Give the subscription a moment to register with the broker before sending.
            Thread.sleep(500);

            producer.write(new StreamRecord<>("pulsar-record"));
            producer.write(new Watermark(1234L));
            CheckpointBarrier barrier = new CheckpointBarrier(7L, 2000L, CheckpointType.CHECKPOINT);
            producer.write(barrier);

            StreamElement recordElem = consumer.read(15, TimeUnit.SECONDS);
            assertNotNull(recordElem, "record must arrive over the Pulsar broker");
            assertTrue(recordElem.isRecord());
            assertEquals("pulsar-record", recordElem.asRecord().getValue());

            StreamElement watermarkElem = consumer.read(15, TimeUnit.SECONDS);
            assertNotNull(watermarkElem);
            assertTrue(watermarkElem.isWatermark());
            assertEquals(1234L, watermarkElem.asWatermark().getTimestamp());

            StreamElement barrierElem = consumer.read(15, TimeUnit.SECONDS);
            assertNotNull(barrierElem);
            assertTrue(barrierElem.isCheckpointBarrier());
            assertEquals(7L, barrierElem.asCheckpointBarrier().getId());
        } finally {
            consumer.close();
        }
    }
}
