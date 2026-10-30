/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.kafka;

import io.nop.stream.core.exceptions.StreamException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2511 C8-006 / C10-014 (adjudication ⑦): transaction-margin guard — the
 * producer carries an explicit {@code transaction.timeout.ms} default (900000ms, 15x
 * the default checkpoint interval) and the constructor rejects the zero-margin
 * coupling {@code checkpointInterval × 3 > transaction.timeout.ms} typed and
 * fail-fast (拒配，不告警放行).
 *
 * <p>Red ⑦ carrier: {@link #zeroMarginCouplingFailsFast} — pre-fix the constructor
 * accepted a transaction timeout SHORTER than three checkpoint intervals silently
 * (sustained load then tripped the broker timeout into a typed-failure/restart/FAIL
 * loop).
 */
class TestKafkaTwoPhaseCommitSinkTransactionMargin {

    private static final String PREFIX = "tx-margin";

    private static Map<String, Object> props(String key, Object value) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(key, value);
        return props;
    }

    /** The explicit default: 900000ms, zero broker reconfiguration required. */
    @Test
    void defaultTransactionTimeoutIsExplicitAndFifteenFoldOverDefaultInterval() {
        KafkaTwoPhaseCommitSink<String> sink = new KafkaTwoPhaseCommitSink<String>(
                "localhost:9092", "out-topic", PREFIX, null,
                v -> v.getBytes(), v -> v, PREFIX);
        assertEquals(KafkaTwoPhaseCommitSink.DEFAULT_TRANSACTION_TIMEOUT_MS,
                sink.getTransactionTimeoutMs(),
                "default transaction.timeout.ms must be 900000");
        assertEquals(900000L, sink.getTransactionTimeoutMs());
    }

    /**
     * White-box producer-props assertion (C10-014): the built producer configuration
     * always carries transaction.timeout.ms — user-explicit value wins, default fills
     * the absence.
     */
    @Test
    void producerPropsAlwaysCarryTransactionTimeout() {
        KafkaTwoPhaseCommitSink<String> sink = new KafkaTwoPhaseCommitSink<String>(
                "localhost:9092", "out-topic", PREFIX, null,
                v -> v.getBytes(), v -> v, PREFIX);
        // the Kafka client types transaction.timeout.ms as a 32-bit INT config: the prop
        // must be an Integer or ConfigDef rejects the whole producer config
        assertEquals(900000L, asLong(sink.buildProducerProps().get(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)),
                "built producer props must carry the explicit default timeout as a 32-bit INT");

        KafkaTwoPhaseCommitSink<String> explicit = new KafkaTwoPhaseCommitSink<String>(
                "localhost:9092", "out-topic", PREFIX,
                props(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 600000L),
                v -> v.getBytes(), v -> v, PREFIX);
        assertEquals(600000L, asLong(explicit.buildProducerProps().get(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG)),
                "user-explicit timeout must win over the default");
    }

    /**
     * Red ⑦ carrier (see class javadoc): interval × 3 > timeout is rejected typed at
     * construction — 拒配，不告警放行. Uses the deployment-default interval (60000ms)
     * with a user timeout (30000ms) that cannot outlive three intervals.
     */
    @Test
    void zeroMarginCouplingFailsFast() {
        StreamException ex = assertThrows(StreamException.class,
                () -> new KafkaTwoPhaseCommitSink<String>("localhost:9092", "out-topic", PREFIX,
                        props(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 30000L),
                        v -> v.getBytes(), v -> v, PREFIX),
                "zero-margin coupling must be rejected at construction");
        assertTrue(String.valueOf(ex.getParam(ARG_DETAIL)).contains("zero-margin"),
                "message must explain the coupling guard, got: " + ex.getParam(ARG_DETAIL));
    }

    /** Explicit deployment interval participates in the guard. */
    @Test
    void explicitIntervalParticipatesInCouplingGuard() {
        // 400000 × 3 > 900000 → rejected even with the default timeout
        assertThrows(StreamException.class,
                () -> new KafkaTwoPhaseCommitSink<String>("localhost:9092", "out-topic", PREFIX,
                        null, v -> v.getBytes(), v -> v, PREFIX, 400000L));
        // boundary: interval × 3 == timeout is accepted (guard is strictly-greater)
        KafkaTwoPhaseCommitSink<String> boundary = new KafkaTwoPhaseCommitSink<String>(
                "localhost:9092", "out-topic", PREFIX,
                props(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 300000L),
                v -> v.getBytes(), v -> v, PREFIX, 100000L);
        assertEquals(300000L, boundary.getTransactionTimeoutMs());
    }

    /** Non-positive timeout / interval are rejected at assembly (≤0 fail-fast). */
    @Test
    void nonPositiveTimeoutOrIntervalFailFast() {
        assertThrows(StreamException.class,
                () -> new KafkaTwoPhaseCommitSink<String>("localhost:9092", "out-topic", PREFIX,
                        props(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 0L),
                        v -> v.getBytes(), v -> v, PREFIX));
        assertThrows(StreamException.class,
                () -> new KafkaTwoPhaseCommitSink<String>("localhost:9092", "out-topic", PREFIX,
                        props(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, -5L),
                        v -> v.getBytes(), v -> v, PREFIX));
        assertThrows(StreamException.class,
                () -> new KafkaTwoPhaseCommitSink<String>("localhost:9092", "out-topic", PREFIX,
                        null, v -> v.getBytes(), v -> v, PREFIX, 0L));
    }

    /** A garbage (non-numeric) timeout value is a typed config error, not silent default. */
    @Test
    void nonNumericTimeoutFailsTyped() {
        assertThrows(StreamException.class,
                () -> new KafkaTwoPhaseCommitSink<String>("localhost:9092", "out-topic", PREFIX,
                        props(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "soon"),
                        v -> v.getBytes(), v -> v, PREFIX));
    }

    private static long asLong(Object v) {
        assertTrue(v instanceof Integer,
                "transaction.timeout.ms prop must be an Integer (32-bit INT config), got: "
                        + (v == null ? "null" : v.getClass().getName()));
        return ((Number) v).longValue();
    }
}
