/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.debezium.engine;

import io.debezium.engine.DebeziumEngine;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * rule 22 entry-to-exit assertion (plan 2503 Phase 2, MA-02 pairing C3-002+C8-004): on the
 * dedicated engine carrier, a REAL wrapper drives
 * {@code start → handleDebeziumEvent → poison-event endpoint → failure listener 必达} in one
 * pass (extends the {@code TestDebeziumEngineDedicatedCarrier_C4_004} probe shape).
 *
 * <p>D12 §3.3.3 two polarities in one chain:
 * <ul>
 *   <li>survival (C3-002): the malformed event must not kill the stream — the good event
 *       AFTER it is still delivered and the poison never escapes into engine.run();</li>
 *   <li>delivery (C8-004): the poison is not silently swallowed — the ConsumeFailure
 *       reaches the wired reporter with attempt fixed 0 (M-1 convert attribution) and the
 *       poison-event WARN channel fires.</li>
 * </ul>
 * The terminal engine death at the end of the body proves the endpoint and the death
 * notification are distinct channels (endpoint ≠ engine failure masking).
 */
@Timeout(60)
public class TestDebeziumEngineWrapperPoisonEndpoint_C3_002_C8_004 {

    private static final String CONNECTOR = "poison-endpoint";
    private static final String DESTINATION = "poison-topic";

    static DebeziumConfig newConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(CONNECTOR);
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        config.setDatabasePort(3306);
        config.setDatabaseUser("repl");
        config.setDatabasePassword("secret");
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath("_tmp/cdc-" + CONNECTOR + "/schema-history.dat");
        return config;
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Minimal raw ChangeEvent: destination + key + value (debezium-api 3.7 shape). */
    record RawEvent(String destination, byte[] key, byte[] value)
            implements io.debezium.engine.ChangeEvent<byte[], byte[]> {
        @Override
        public byte[] key() {
            return key;
        }

        @Override
        public byte[] value() {
            return value;
        }

        @Override
        public String destination() {
            return destination;
        }

        @Override
        public Integer partition() {
            return 0;
        }
    }

    /**
     * Engine body: delivers a good event, a malformed event (convert throws), a good event
     * again (stream must have survived), then dies terminally.
     */
    static class PoisonDrivingEngine
            implements DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> {
        final EndpointProbeWrapper wrapper;
        final AtomicReference<String> carrierThreadName = new AtomicReference<>();

        PoisonDrivingEngine(EndpointProbeWrapper wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public void run() {
            carrierThreadName.set(Thread.currentThread().getName());
            wrapper.handleDebeziumEvent(new RawEvent(DESTINATION, bytes("{\"id\":1}"),
                    bytes("{\"payload\":{\"op\":\"c\","
                            + "\"source\":{\"connector\":\"mysql\",\"name\":\"srv\",\"db\":\"db\",\"table\":\"t\"},"
                            + "\"before\":null,\"after\":{\"id\":1}}}")));
            wrapper.handleDebeziumEvent(new RawEvent(DESTINATION, null, bytes("not-json{{{")));
            wrapper.handleDebeziumEvent(new RawEvent(DESTINATION, bytes("{\"id\":2}"),
                    bytes("{\"payload\":{\"op\":\"u\","
                            + "\"source\":{\"connector\":\"mysql\",\"name\":\"srv\",\"db\":\"db\",\"table\":\"t\"},"
                            + "\"before\":null,\"after\":{\"id\":2}}}")));
            throw new LinkageError("injected engine terminal death");
        }

        @Override
        public void close() {
        }
    }

    /** Real wrapper whose worker body runs the injected engine on the dedicated carrier. */
    static class EndpointProbeWrapper extends DebeziumEngineWrapper {
        final CountDownLatch bodyDone = new CountDownLatch(1);
        private volatile DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> injected;

        EndpointProbeWrapper(DebeziumConfig config,
                             Consumer<ChangeEvent> consumer,
                             Consumer<ConsumeFailure> poisonReporter,
                             Consumer<Throwable> terminalListener) {
            super(config, consumer, null, poisonReporter, terminalListener);
        }

        void inject(DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine) {
            this.injected = engine;
        }

        @Override
        protected void runEngineWorker(
                DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
            try {
                super.runEngineWorker(injected);
            } finally {
                bodyDone.countDown();
            }
        }
    }

    @Test
    public void poisonEventDoesNotKillStreamAndReachesReporterViaRealWrapper() throws Exception {
        List<ChangeEvent> delivered = new CopyOnWriteArrayList<>();
        List<ConsumeFailure> poison = new CopyOnWriteArrayList<>();
        List<Throwable> terminalFailures = new CopyOnWriteArrayList<>();
        CountDownLatch terminal = new CountDownLatch(1);

        EndpointProbeWrapper wrapper = new EndpointProbeWrapper(newConfig(),
                delivered::add, poison::add,
                error -> {
                    terminalFailures.add(error);
                    terminal.countDown();
                });
        wrapper.inject(new PoisonDrivingEngine(wrapper));

        try {
            // real start(): engine built from config + submitted to the dedicated carrier
            wrapper.start();

            assertTrue(wrapper.bodyDone.await(30, TimeUnit.SECONDS),
                    "the engine body must run to completion on the carrier");
            assertTrue(terminal.await(5, TimeUnit.SECONDS),
                    "the injected terminal death must reach the failure listener (listener 必达)");

            // rule 23: the body really ran on the dedicated debezium-engine- carrier
            String carrierThread = ((PoisonDrivingEngine) wrapper.injected).carrierThreadName.get();
            assertTrue(carrierThread.startsWith("debezium-engine-" + CONNECTOR),
                    "entry-to-exit must run on the dedicated carrier, got: " + carrierThread);

            // survival polarity (C3-002): good events before AND after the poison delivered
            assertEquals(2, delivered.size(),
                    "the malformed event must not kill the stream — the good event after it "
                            + "is still delivered");
            assertEquals("u", delivered.get(1).getOperation(),
                    "the post-poison event is the second good event");

            // delivery polarity (C8-004): the poison entered the endpoint, not silence
            assertEquals(1, poison.size(),
                    "the malformed event must reach the poison-event endpoint (pre-fix: "
                            + "exception escaped into engine.run() and killed the stream)");
            ConsumeFailure failure = poison.get(0);
            assertEquals(0, failure.getAttempt(),
                    "convert-stage attempt is fixed 0 (M-1 attribution ruling)");
            assertEquals(DESTINATION, failure.getTopic(),
                    "topic = raw event destination (D12 field mapping)");
            assertTrue(failure.getLocation().startsWith(CONNECTOR + "@"),
                    "location = connector name + offset key (D12 field mapping)");
            assertTrue(failure.getThrowable() instanceof Exception,
                    "the reported throwable carries the original convert failure");

            // engine terminal death is a distinct channel from the poison endpoint
            assertTrue(terminalFailures.get(0) instanceof LinkageError);
        } finally {
            wrapper.stop();
        }
        assertFalse(wrapper.isRunning(), "engine must not be running after the body exited + stop");
    }
}
