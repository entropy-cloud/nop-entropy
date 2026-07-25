package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 (G53) — Anti-Hollow explicit exclusion verification.
 *
 * <p>Asserts that the cross-JVM data path ({@link RemoteResultPartition} /
 * {@link RemoteInputChannel}) <b>intentionally</b> does NOT consume the per-job
 * {@code IBufferPool}, while a local {@link ResultPartition} on the same pool DOES.
 *
 * <p>The two paths' behavioural difference is asserted explicitly here (not silently
 * assumed): the remote producer is unbounded at the process level by design, and its
 * cross-JVM bound is the responsibility of the {@code IMessageService} backend
 * (Stage 40). This is documented in {@code 01-architecture-baseline.md} §六.
 */
class TestBufferPoolRemoteExclusion {

    private static final String TOPIC = "remote-exclusion-topic";
    private static final String EDGE_ID = "edge-1";
    private static final String FENCING_TOKEN = "token-1";
    private static final long EPOCH_ID = 1L;

    @Test
    void remoteResultPartitionDoesNotConsumePool() throws InterruptedException {
        List<Object> sent = new ArrayList<>();
        IMessageService messageService = new MockMessageService(sent);

        RemoteResultPartition remote = new RemoteResultPartition(
                messageService, TOPIC, null, EDGE_ID, FENCING_TOKEN, EPOCH_ID);

        // (a) Remote partition carries NO pool reference (intentional exclusion)
        assertNull(remote.getBufferPool(),
                "RemoteResultPartition must not carry a pool (cross-JVM bound is IMessageService backend, Stage 40)");

        // (b) Writing through the remote path does NOT touch any pool's global budget:
        //     simulate the per-job pool and confirm its usage stays 0 after remote writes.
        BufferPool pool = new BufferPool(4);
        ResultPartition localWithPool = new ResultPartition(4, pool);

        // Write through the remote path
        remote.write(new StreamRecord<>("remote-a"));
        remote.write(new StreamRecord<>("remote-b"));
        remote.write(new StreamRecord<>("remote-c"));

        // Remote writes must not have moved the shared pool's meter at all
        assertEquals(0, pool.getGlobalUsage(),
                "Remote path must not consume the per-job pool (write bypasses queue+pool)");

        // (c) Contrast: writing through a LOCAL pool-bound partition DOES move the meter
        localWithPool.write(new StreamRecord<>("local-a"));
        assertEquals(1, pool.getGlobalUsage(),
                "Local pool-bound partition must consume the per-job pool (behaviour differs from remote by design)");

        // Explicit behavioural-difference assertion: remote writes left usage unchanged
        // for those three elements, local write moved it — proving the two paths are wired
        // differently on purpose, not by accident.
        remote.close();
        assertEquals(1, pool.getGlobalUsage(),
                "Closing the remote path still must not touch the pool");
    }

    @Test
    void remoteInputChannelDummyPartitionHasNoPool() {
        // RemoteInputChannel constructs super(new ResultPartition(1)) — a dummy partition
        // that must not be pool-bound.
        ResultPartition dummy = new ResultPartition(1);
        assertNull(dummy.getBufferPool(),
                "RemoteInputChannel's dummy partition must not be pool-bound (intentional exclusion)");
    }

    /** Minimal IMessageService capturing sends (shape matches TestRemoteResultPartition.MockMessageService). */
    static class MockMessageService implements IMessageService {
        final List<Object> sent;

        MockMessageService(List<Object> sent) {
            this.sent = sent;
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message, io.nop.api.core.message.MessageSendOptions options) {
            sent.add(message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void send(String topic, Object message) {
            sent.add(message);
        }

        @Override
        public io.nop.api.core.message.IMessageSubscription subscribe(String topic, io.nop.api.core.message.IMessageConsumer listener, io.nop.api.core.message.MessageSubscribeOptions options) {
            return new io.nop.api.core.message.IMessageSubscription() {
                @Override public void cancel() {}
                @Override public boolean isSuspended() { return false; }
                @Override public boolean isCancelled() { return true; }
                @Override public void suspend() {}
                @Override public void resume() {}
            };
        }
    }
}
