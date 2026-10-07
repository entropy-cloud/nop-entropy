/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.channel.ChannelBacklogProbe;
import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 (plan 2331): producer-side DB channel backpressure gate — fake-probe unit
 * contract (no DB). Covers the five paths (pass-through / wait / typed timeout /
 * gate off / probe null never queried), plus the lock-outside ruling: a gated writer
 * waiting on backlog must NOT hold the send lock, so close() can still deliver EOS.
 */
public class TestRemoteResultPartitionBackpressureGate {

    private static StreamRecord<String> element(String value) {
        return new StreamRecord<>(value, 1L);
    }

    @Test
    public void passThroughWhenBacklogUnderLimit() throws InterruptedException {
        List<Object> sent = new ArrayList<>();
        IMessageService service = recordingService(sent);
        RemoteResultPartition partition = new RemoteResultPartition(service, "t",
                null, "e", 1L, probeOf(2L), 5L);
        partition.write(element("a"));
        partition.write(element("b"));
        assertEquals(2, sent.size());
    }

    @Test
    public void gateWaitsUntilBacklogDropsThenSends() throws Exception {
        List<Object> sent = new ArrayList<>();
        AtomicInteger backlog = new AtomicInteger(10);
        ChannelBacklogProbe probe = topic -> backlog.get();
        IMessageService service = recordingService(sent);
        RemoteResultPartition partition = new RemoteResultPartition(service, "t",
                null, "e", 1L, probe, 5L);

        Thread producer = new Thread(() -> {
            try {
                partition.write(element("a"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();
        Thread.sleep(300);
        assertEquals(0, sent.size(), "gated writer must not send while over limit");
        backlog.set(1);
        producer.join(5_000);
        assertEquals(1, sent.size(), "producer must send once backlog drops");
    }

    @Test
    public void sustainedOverloadFailsTypedWithDetails() {
        IMessageService service = recordingService(new ArrayList<>());
        RemoteResultPartition partition = new RemoteResultPartition(service, "t",
                null, "e", 1L, probeOf(99L), 5L);
        long start = System.nanoTime();
        try {
            partition.write(element("a"));
            throw new AssertionError("expected typed gate timeout");
        } catch (NopException e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            assertTrue(elapsedMs >= 9_000, () -> "bounded wait must run its course: " + elapsedMs);
            String text = String.valueOf(e);
            assertTrue(text.contains("t"), "must name the topic: " + text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void gateOffSendsWithoutQueryingProbe() throws InterruptedException {
        List<Object> sent = new ArrayList<>();
        AtomicBoolean queried = new AtomicBoolean(false);
        ChannelBacklogProbe probe = topic -> {
            queried.set(true);
            return 999L;
        };
        // limit = 0 -> gate off even though a probe is present
        RemoteResultPartition partition = new RemoteResultPartition(
                recordingService(sent), "t", null, "e", 1L, probe, 0L);
        partition.write(element("a"));
        assertEquals(1, sent.size());
        assertEquals(false, queried.get(), "gate-off must never query the probe");
        assertEquals(0L, partition.readBacklogCached());
    }

    @Test
    public void probeNullNeverQueriedAndLegacyConstructorUnaffected() throws InterruptedException {
        List<Object> sent = new ArrayList<>();
        RemoteResultPartition partition = new RemoteResultPartition(
                recordingService(sent), "t", null, "e", 1L);
        partition.write(element("a"));
        assertEquals(1, sent.size());
        assertNotNull(partition);
    }

    @Test
    public void gatedWaitDoesNotHoldTheSendLock() throws Exception {
        // Ruling (plan 2331 M1): the gate waits OUTSIDE the send lock — close() must be
        // able to acquire the lock and deliver EOS while a producer is gated.
        AtomicBoolean backlogHigh = new AtomicBoolean(true);
        ChannelBacklogProbe probe = topic -> backlogHigh.get() ? 99L : 0L;
        List<Object> sent = new ArrayList<>();
        IMessageService service = recordingService(sent);
        RemoteResultPartition partition = new RemoteResultPartition(service, "t",
                null, "e", 1L, probe, 5L);

        Thread producer = new Thread(() -> {
            try {
                partition.write(element("a"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();
        Thread.sleep(300);
        long start = System.nanoTime();
        // close() must acquire sendLock promptly (no gated writer holds it)
        partition.close();
        long closeMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(closeMs < 2_000, () -> "close() was blocked by a gated writer for " + closeMs + "ms");
        backlogHigh.set(false);
        producer.join(5_000);
        // EOS ordering contract: the write started before close but reached the lock
        // after EOS — it must be rejected, not land after the terminal message.
        assertEquals(1, sent.size(), "only the EOS control message may be sent");
    }

    private static IMessageService recordingService(List<Object> sink) {
        return new IMessageService() {
            @Override
            public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                                                                        io.nop.api.core.message.MessageSendOptions options) {
                sink.add(message);
                return java.util.concurrent.CompletableFuture.completedStage(null);
            }

            @Override
            public io.nop.api.core.message.IMessageSubscription subscribe(String topic,
                                                                         io.nop.api.core.message.IMessageConsumer consumer,
                                                                         io.nop.api.core.message.MessageSubscribeOptions options) {
                throw new UnsupportedOperationException("not used in gate tests");
            }
        };
    }

    private static ChannelBacklogProbe probeOf(long value) {
        return topic -> value;
    }
}
