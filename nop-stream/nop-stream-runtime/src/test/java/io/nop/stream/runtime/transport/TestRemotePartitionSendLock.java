/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.PartitionRouter;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression proofs for the send-lock narrowing (plan 2279 Q2): only the backend
 * send serializes on {@code sendLock}; encode runs outside it; and the EOS
 * ordering contract (no heartbeat or data message past the terminal message) is
 * enforced by in-lock {@code isFinished()} re-checks.
 */
class TestRemotePartitionSendLock {

    /** Backend that records the wire form of every send, with an optional park hook. */
    static class RecordingBackend implements IMessageService {
        final List<String> sentMessages = new CopyOnWriteArrayList<>();
        /** When non-null, every send parks until it is counted down (one-shot per send). */
        volatile CountDownLatch blockSend;
        final AtomicInteger encodeCount = new AtomicInteger();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                              MessageSubscribeOptions options) {
            return new IMessageSubscription() {
                @Override
                public void cancel() {
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
            sentMessages.add(String.valueOf(message));
            CountDownLatch latch = blockSend;
            if (latch != null) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void inFlightHeartbeatLandsBeforeEos() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        TypeRegistry registry = new TypeRegistry();
        RemoteResultPartition partition = new RemoteResultPartition(
                backend, "topic", registry, "edge", 1L, 1L);

        // Make the partition idle so a heartbeat passes the idle check.
        Thread.sleep(50);

        CountDownLatch heartbeatSendStarted = new CountDownLatch(1);
        CountDownLatch releaseHeartbeat = new CountDownLatch(1);
        backend.blockSend = releaseHeartbeat;

        Thread heartbeatThread = new Thread(() -> {
            heartbeatSendStarted.countDown();
            partition.sendHeartbeatIfIdle();
        });
        heartbeatThread.start();
        assertTrue(heartbeatSendStarted.await(5, TimeUnit.SECONDS));
        // Wait until the heartbeat is parked inside the backend (message recorded,
        // send not completed): close() must wait for it under sendLock.
        long deadline = System.currentTimeMillis() + 5000;
        while (backend.sentMessages.size() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        partition.close(); // blocks in sendLock until the in-flight heartbeat send returns

        releaseHeartbeat.countDown();
        heartbeatThread.join(5000);
        assertFalse(heartbeatThread.isAlive());

        assertEquals(2, backend.sentMessages.size(), "heartbeat then EOS, no other sends");
        assertTrue(backend.sentMessages.get(0).contains("HEARTBEAT"),
                "in-flight heartbeat must land BEFORE the terminal message, got: "
                        + backend.sentMessages);
        assertTrue(backend.sentMessages.get(1).contains("END_OF_STREAM"),
                "EOS must be the last message, got: " + backend.sentMessages);
    }

    @Test
    void heartbeatAfterCloseIsRejectedByTheInLockRecheck() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        TypeRegistry registry = new TypeRegistry();
        RemoteResultPartition partition = new RemoteResultPartition(
                backend, "topic", registry, "edge", 1L, 1L);

        partition.close();
        int sendsAtClose = backend.sentMessages.size();
        assertEquals(1, sendsAtClose, "close sends exactly the EOS message");

        assertFalse(partition.sendHeartbeatIfIdle(),
                "a heartbeat after close must be rejected (volatile + in-lock recheck)");
        assertEquals(sendsAtClose, backend.sentMessages.size(),
                "no heartbeat may land after EOS");
    }

    @Test
    void writeAfterCloseIsRejected() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        TypeRegistry registry = new TypeRegistry();
        RemoteResultPartition partition = new RemoteResultPartition(
                backend, "topic", registry, "edge", 1L, 0L);
        partition.close();

        assertThrows(Exception.class, () -> partition.write(new StreamRecord<>(1L, 1L)),
                "data write after close must be rejected (in-lock recheck)");
        assertEquals(1, backend.sentMessages.size(), "no data message after EOS");
    }

}
