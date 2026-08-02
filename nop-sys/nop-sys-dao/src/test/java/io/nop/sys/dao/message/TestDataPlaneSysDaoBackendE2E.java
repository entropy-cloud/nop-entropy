/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.message;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.message.IMessageService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.runtime.transport.DataPlaneMessageServiceAdapter;
import io.nop.stream.runtime.transport.RemoteInputChannel;
import io.nop.stream.runtime.transport.RemoteResultPartition;
import io.nop.stream.runtime.transport.StreamTopicNaming;
import io.nop.stream.runtime.transport.SysDaoWireCodec;
import io.nop.sys.dao.entity.NopSysEvent;
import io.nop.autotest.junit.JunitBaseTestCase;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 40 Phase 1 — data-plane cross-JVM E2E over the real DB backend
 * ({@link SysDaoMessageService} backed by H2). Proves the wiring is non-hollow: records
 * genuinely traverse the {@code NopSysEvent} table (NOT {@code LocalMessageService}
 * in-memory direct), fencing epoch filtering holds across the backend, and exactly-once
 * delivery is preserved.
 *
 * <p>This test lives in {@code nop-sys-dao} (not {@code nop-stream-runtime}) for the same
 * reason as {@code TestJobCoordinatorWithSysDaoLeaderElector}: the JDBC harness +
 * {@code SysDaoMessageService} live here, and {@code nop-stream-runtime} must not take a
 * hard dependency on {@code nop-sys-dao}. The {@code nop-stream-runtime} dependency is
 * test-scope only; the deploy-time wiring direction is
 * {@code SysDaoMessageService bean -> dispatcher.setDataPlaneWireCodec(SysDaoWireCodec)}.
 *
 * <p>Wiring exercised: {@code RemoteResultPartition} / {@code RemoteInputChannel} are
 * handed a {@link DataPlaneMessageServiceAdapter} wrapping the real
 * {@link SysDaoMessageService} with {@link SysDaoWireCodec}. Each envelope is converted
 * to {@code ApiRequest{data: map}} on send (so the backend persists its body) and
 * reconstructed on the polling delivery path.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDataPlaneSysDaoBackendE2E extends JunitBaseTestCase {

    private static final String JOB_ID = "sys-dao-dataplane-job";
    private static final String EDGE_ID = "src->tgt";
    private static final long EPOCH = 5L;

    @Inject
    IDaoProvider daoProvider;

    private SysDaoMessageService backend;
    private IMessageService dataPlane;

    @BeforeEach
    public void setUp() {
        backend = new SysDaoMessageService();
        backend.setDaoProvider(daoProvider);
        backend.setAssignedPartitions(IntRangeSet.parse("0,32767"));
        backend.setFetchSize(20);
        backend.setMinProcessDelay(1);
        backend.setLeaseTimeout(2000);
        // No start(): tests drain synchronously via processNonBroadcastEvent(), which is
        // deterministic and avoids poller timing flakiness.
        dataPlane = new DataPlaneMessageServiceAdapter(backend, SysDaoWireCodec.INSTANCE);
    }

    @AfterEach
    public void tearDown() {
        if (backend != null) {
            backend.stop();
        }
    }

    /**
     * Core Proof (plan guide #22/#23): records traverse the {@code NopSysEvent} table,
     * the consumer receives each exactly once, and the table rows carry the data-plane
     * topic — proving the backend was genuinely crossed (not LocalMessageService direct).
     */
    @Test
    public void recordsTraverseNopSysEventTableExactlyOnce() throws Exception {
        String topic = StreamTopicNaming.buildTopic(JOB_ID, EDGE_ID, 0, 0);
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register(EDGE_ID, String.class.getName());

        RemoteResultPartition producer = new RemoteResultPartition(
                dataPlane, topic, typeRegistry, EDGE_ID, EPOCH);
        RemoteInputChannel consumer = new RemoteInputChannel(dataPlane, topic, EPOCH);

        try {
            producer.write(new StreamRecord<>("r-1"));
            producer.write(new StreamRecord<>("r-2"));
            producer.write(new StreamRecord<>("r-3"));

            // Drain the 3 events through the DB-poll path (one event per partition per
            // processNonBroadcastEvent() call; the single topic maps to one partition).
            drainBackend(3);

            List<String> received = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                StreamElement elem = consumer.read(2, TimeUnit.SECONDS);
                assertNotNull(elem, "record " + i + " must arrive over the DB backend");
                assertTrue(elem.isRecord());
                received.add((String) elem.asRecord().getValue());
            }

            // Exactly-once: the three distinct values, no duplicates, no loss.
            assertEquals(List.of("r-1", "r-2", "r-3"), received,
                    "records must arrive in order, exactly once each");
            Set<String> unique = new LinkedHashSet<>(received);
            assertEquals(3, unique.size(), "no duplicate delivery (exactly-once)");

            // Wiring verification: the records were persisted to NopSysEvent under the
            // data-plane topic — they crossed the real DB backend, not memory.
            IEntityDao<NopSysEvent> eventDao = daoProvider.daoFor(NopSysEvent.class);
            List<NopSysEvent> rows = eventDao.findAll();
            assertFalse(rows.isEmpty(), "NopSysEvent table must contain the persisted envelopes");
            long dataPlaneRows = rows.stream()
                    .filter(e -> topic.equals(e.getEventTopic()))
                    .count();
            assertTrue(dataPlaneRows >= 3,
                    "at least 3 NopSysEvent rows for the data-plane topic expected, got " + dataPlaneRows);
        } finally {
            consumer.close();
        }
    }

    /**
     * Barrier and watermark control elements also traverse the backend (plan guide #22).
     */
    @Test
    public void barrierAndWatermarkTraverseBackend() throws Exception {
        String topic = StreamTopicNaming.buildTopic(JOB_ID, EDGE_ID, 0, 0);

        RemoteResultPartition producer = new RemoteResultPartition(
                dataPlane, topic, null, EDGE_ID, EPOCH);
        RemoteInputChannel consumer = new RemoteInputChannel(dataPlane, topic, EPOCH);

        try {
            CheckpointBarrier barrier = new CheckpointBarrier(42L, 1000L, CheckpointType.CHECKPOINT);
            producer.write(barrier);
            producer.write(new Watermark(77L));

            drainBackend(2);

            StreamElement barrierElem = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(barrierElem);
            assertTrue(barrierElem.isCheckpointBarrier());
            assertEquals(42L, barrierElem.asCheckpointBarrier().getId());

            StreamElement watermarkElem = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(watermarkElem);
            assertTrue(watermarkElem.isWatermark());
            assertEquals(77L, watermarkElem.asWatermark().getTimestamp());
        } finally {
            consumer.close();
        }
    }

    /**
     * Fencing holds across the backend: a stale-epoch envelope is discarded by the
     * consumer even though it was persisted and polled through the DB.
     */
    @Test
    public void fencingRejectsStaleEpochOverBackend() throws Exception {
        String topic = StreamTopicNaming.buildTopic(JOB_ID, EDGE_ID, 0, 0);
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register(EDGE_ID, String.class.getName());

        RemoteInputChannel consumer = new RemoteInputChannel(dataPlane, topic, EPOCH);
        // Stale producer: wrong (older) epoch — its envelopes must be discarded.
        RemoteResultPartition staleProducer = new RemoteResultPartition(
                dataPlane, topic, typeRegistry, EDGE_ID, EPOCH - 1);

        try {
            staleProducer.write(new StreamRecord<>("stale"));

            drainBackend(1);

            // The stale envelope crossed the backend (persisted + polled) but the
            // consumer's fencing filter discarded it.
            StreamElement elem = consumer.read(500, TimeUnit.MILLISECONDS);
            assertNull(elem, "stale-epoch record must be discarded by the consumer");
            assertEquals(0, consumer.queueSize());
        } finally {
            consumer.close();
        }
    }

    /**
     * END_OF_STREAM control signal propagates through the backend and finishes the
     * consumer channel.
     */
    @Test
    public void endOfStreamPropagatesThroughBackend() throws Exception {
        String topic = StreamTopicNaming.buildTopic(JOB_ID, EDGE_ID, 0, 0);

        RemoteResultPartition producer = new RemoteResultPartition(
                dataPlane, topic, null, EDGE_ID, EPOCH);
        RemoteInputChannel consumer = new RemoteInputChannel(dataPlane, topic, EPOCH);

        try {
            producer.write(new StreamRecord<>("last"));
            producer.close(); // sends END_OF_STREAM control envelope

            drainBackend(2);

            StreamElement record = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(record);
            assertEquals("last", record.asRecord().getValue());

            StreamElement eos = consumer.read(2, TimeUnit.SECONDS);
            assertNull(eos, "END_OF_STREAM must finish the channel");
            assertTrue(consumer.isFinished());
        } finally {
            consumer.close();
        }
    }

    /**
     * Drains {@code expectedEvents} envelopes through the DB-poll path. Each call to
     * {@code processNonBroadcastEvent()} processes the head of each subscribed partition;
     * for a single topic (one partition) that is one event per call.
     */
    private void drainBackend(int expectedEvents) {
        int maxIters = expectedEvents + 5;
        for (int i = 0; i < maxIters; i++) {
            backend.processNonBroadcastEvent();
        }
    }
}
