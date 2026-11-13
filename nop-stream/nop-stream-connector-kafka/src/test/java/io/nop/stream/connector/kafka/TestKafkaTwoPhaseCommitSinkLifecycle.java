/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.kafka;

import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.exceptions.StreamException;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 (plan 2335): epoch lifecycle of the commit-at-barrier Kafka sink over a scripted
 * fake producer (MockProducer subclass recording the call sequence) — initTransactions
 * exactly once, commit-per-barrier, idempotent re-commit via the high-water mark, typed
 * contract-violation beyond it, recovery fencing, and the identity namespace.
 */
class TestKafkaTwoPhaseCommitSinkLifecycle {

    /** MockProducer with a transaction-lifecycle call log. */
    static final class FakeProducer extends MockProducer<String, byte[]> {
        final List<String> calls = new ArrayList<>();

        FakeProducer() {
            super(false, new org.apache.kafka.clients.producer.RoundRobinPartitioner(),
                    new StringSerializer(), new ByteArraySerializer());
        }

        @Override
        public void initTransactions() {
            calls.add("init");
            super.initTransactions();
        }

        @Override
        public void beginTransaction() {
            calls.add("begin");
            super.beginTransaction();
        }

        @Override
        public void commitTransaction() {
            calls.add("commit");
            super.commitTransaction();
        }

        @Override
        public void abortTransaction() {
            calls.add("abort");
            super.abortTransaction();
        }

        @Override
        public void flush() {
            calls.add("flush");
            super.flush();
        }

        @Override
        public java.util.concurrent.Future<org.apache.kafka.clients.producer.RecordMetadata> send(
                ProducerRecord<String, byte[]> record) {
            calls.add("send:" + record.key());
            return super.send(record);
        }

        @Override
        public void close(Duration timeout) {
            calls.add("close");
            super.close(timeout);
        }
    }

    private static KafkaTwoPhaseCommitSink<String> newSink(FakeProducer producer) {
        return new KafkaTwoPhaseCommitSink<>("localhost:9092", "out-topic",
                "tx-prefix", null, v -> v.getBytes(), v -> v, "tx-prefix") {
            @Override
            protected Producer<String, byte[]> newProducer() {
                return producer;
            }
        };
    }

    /**
     * Happy path: send buffers in the open transaction; the barrier saveState commits
     * and opens the next epoch; the coordinator's commit is an idempotent no-op for the
     * marked epoch.
     */
    @Test
    void epochCommitsAtBarrierAndCoordinatorCommitIsNoop() throws Exception {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);

        sink.beginTransaction();
        sink.invoke("a");
        sink.invoke("b");
        sink.preCommit(1L); // flush before the barrier state persists
        sink.saveState(1L); // the barrier: commit + begin(next) + marker

        // note: MockProducer's own beginTransaction() flushes internally, hence the second
        // "flush" — it belongs to the next-epoch begin, not to an extra sink call
        assertEquals(List.of("init", "begin", "send:a", "send:b", "flush", "commit", "flush", "begin"),
                producer.calls, "initTransactions exactly once; commit-at-barrier; next epoch open");
        assertEquals(2, producer.history().size(), "both records in the committed history");

        // coordinator bookkeeping arrives after the barrier: idempotent no-op
        sink.commit(1L);
        sink.finishCommit(1L, true);
        assertEquals(8, producer.calls.size(),
                "no extra producer calls after the barrier commit (8 = 7 sink calls + MockProducer's "
                        + "internal flush riding its own begin)");
        assertEquals(SinkConsistencyCapability.TWO_PHASE_COMMIT, sink.getSinkConsistency());
    }

    /** Contract violation: commit for an epoch beyond the high-water mark fails typed. */
    @Test
    void commitBeyondHighWaterFailsTyped() throws Exception {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);
        sink.beginTransaction();
        sink.saveState(3L);

        StreamException ex = assertThrows(StreamException.class, () -> sink.commit(5L));
        assertEquals(ERR_STREAM_STATE_ERROR.getErrorCode(), ex.getErrorCode());
    }

    /**
     * Crash between invoke and saveState: recover() drops the dangling transaction; the
     * next epoch runs normally. initTransactions runs exactly once per producer
     * generation (the second begin after recovery does NOT re-init).
     */
    @Test
    void recoverFencesDanglingTransaction() throws Exception {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);
        sink.beginTransaction();
        sink.invoke("x");
        sink.recover(2L); // rollback (abort dangling) + begin — producer already initialized

        // trailing "flush" = MockProducer-internal, emitted by the post-recovery begin
        assertEquals(List.of("init", "begin", "send:x", "abort", "flush", "begin"),
                producer.calls, "recovery = abort dangling tx + fresh begin, no re-init");

        // post-recovery epoch commits normally
        sink.invoke("a");
        sink.saveState(3L);
        sink.commit(3L);
        assertTrue(producer.history().stream().anyMatch(r -> "a".equals(r.key())),
                "post-recovery records reach the committed history");
    }

    /**
     * restoreFromEpoch replays the committed high-water from the restored pendingCommits
     * BEFORE the base class re-commits restored markers, so those re-commit requests hit
     * the idempotent branch (not the typed contract-violation branch).
     */
    @Test
    void restoreFromEpochReplaysHighWaterBeforeBaseRecommit() throws Exception {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);
        sink.beginTransaction();
        sink.saveState(4L); // barrier commit: marker 4, high-water 4

        // simulate restart: transient state gone; pendingCommits rebuilt from the
        // checkpoint (marker 4); then restoreFromEpoch(durable=4)
        sink.restoreFromEpoch(4L, null);

        assertEquals(4L, sink.committedHighWaterForTest(),
                "the high-water must be replayed from the restored markers");
        sink.commit(4L); // base's re-commit landed here: harmless no-op after restore
        StreamException ex = assertThrows(StreamException.class, () -> sink.commit(5L));
        assertEquals(ERR_STREAM_STATE_ERROR.getErrorCode(), ex.getErrorCode());
    }

    /** Task-identity namespace: the transactional.id gains job/vertex/task coordinates. */
    @Test
    void copyForSubtaskNamespacesTransactionalId() {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);
        TwoPhaseCommitSinkFunction<String> copy = sink.copyForSubtask(
                new TaskLocation("job-9", "pipe-0", "out-vertex", 2));
        assertEquals("tx-prefix|job-9|out-vertex|2",
                ((KafkaTwoPhaseCommitSink<String>) copy).getTransactionalId());
        // independent instances: the template keeps its own identity
        assertEquals("tx-prefix", sink.getTransactionalId());
    }

    /** producerProps must not override the transactional.id (guard fires before any network I/O). */
    @Test
    void transactionalIdOverrideFailsTyped() throws Exception {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("transactional.id", "hijack");
        KafkaTwoPhaseCommitSink<String> sink = new KafkaTwoPhaseCommitSink<>("localhost:9092", "out-topic",
                "tx-prefix", props, v -> v.getBytes(), null, "tx-prefix");
        StreamException ex = assertThrows(StreamException.class, sink::checkConnection,
                "the identity guard must fire when the producer is built");
        assertTrue(String.valueOf(ex).contains("transactional.id"),
                "error must name the guarded property: " + ex);
    }

    /**
     * CRA 1.2 C6-001 regression: the transactional producer must be released on task
     * termination. close() aborts the open (next-epoch) transaction left by the barrier,
     * closes the producer, and is idempotent. The runtime teardown reaches this via
     * {@code StreamSinkOperator.close} → {@code AutoCloseable} userFunction.
     */
    @Test
    void closeAbortsOpenTransactionAndClosesProducerIdempotently() throws Exception {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);
        sink.beginTransaction();
        sink.invoke("a");
        sink.saveState(1L); // barrier: commit + begin(next) — a transaction is open again

        sink.close();
        // trailing "flush" = MockProducer-internal, riding its own abortTransaction
        assertEquals(List.of("abort", "flush", "close"),
                producer.calls.subList(producer.calls.size() - 3, producer.calls.size()),
                "close must abort the open transaction, then close the producer");

        int callsAfterFirstClose = producer.calls.size();
        sink.close();
        assertEquals(callsAfterFirstClose, producer.calls.size(), "the second close must be a no-op");
    }

    /** close() before any record: no producer exists yet — a no-op, no NPE, no producer calls. */
    @Test
    void closeWithoutProducerIsNoop() {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);
        assertDoesNotThrow(sink::close);
        assertEquals(List.of(), producer.calls, "no producer, nothing to abort or close");
    }

    /**
     * close() after rollback: no transaction is open any more, so close must not abort a
     * second time — but the producer itself must still be closed.
     */
    @Test
    void closeAfterRollbackSkipsAbortButClosesProducer() throws Exception {
        FakeProducer producer = new FakeProducer();
        KafkaTwoPhaseCommitSink<String> sink = newSink(producer);
        sink.beginTransaction();
        sink.invoke("x");
        sink.rollback(); // abort happens here; no transaction open afterwards

        sink.close();
        // trailing "flush" = MockProducer-internal, riding the rollback's abortTransaction
        assertEquals(List.of("init", "begin", "send:x", "abort", "flush", "close"), producer.calls,
                "exactly one abort (from rollback); close still releases the producer");
    }
}
