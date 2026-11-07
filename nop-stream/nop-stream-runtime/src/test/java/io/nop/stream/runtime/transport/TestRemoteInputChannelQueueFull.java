/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.testsupport.TestAwait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Items 28+31 (D2, Phase 2 focused tests): bounded-enqueue queue-full
 * semantics of {@link RemoteInputChannel}.
 *
 * <p>Anchors (from the design decision, {@code dataplane-transport-design.md} §三):
 * <ul>
 *   <li>no reader + full queue → the dispatch thread (here: the synchronous
 *       sender of LocalMessageService, which plays the dispatch role) is NOT
 *       blocked forever; the channel flags a typed overflow failure that the
 *       reader surfaces as {@link NopStreamErrors#ERR_STREAM_CHANNEL_OVERFLOW}
 *       — observable, never a clean end-of-stream (silent truncation);</li>
 *   <li>healthy reader → in-order delivery, no loss, no duplication;</li>
 *   <li>healthy SLOW reader (slot freed continuously) → bounded waits succeed,
 *       no overflow false-positive (dispatch-level backpressure preserved —
 *       the BP-1 500ms-throttle regime);</li>
 *   <li>overflow observability: {@code isOverflowed()} is queryable even with
 *       no reader ever calling read().</li>
 * </ul>
 */
class TestRemoteInputChannelQueueFull {

    private static final String TOPIC = "nop-stream.job-q.edge-q.0.0";
    private static final long EPOCH = 1L;

    private LocalMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
    }

    /** Delivers a STREAM_RECORD envelope synchronously through the message service (the dispatch path). */
    private void deliver(RemoteInputChannel channel, String value) {
        StreamRecord<String> record = new StreamRecord<>(value);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(record, String.class.getName(), EPOCH);
        long start = System.nanoTime();
        messageService.send(TOPIC, envelope);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // Guard assertion helper: the sender (dispatch role) must never block
        // unbounded — a hard bound here would be the enqueue timeout itself;
        // individual tests assert the concrete expected bounds.
        assertTrue(elapsedMs < 60_000L, "dispatch must not block unbounded (took " + elapsedMs + "ms)");
    }

    @Test
    void fullQueueWithNoReaderFailsTypedInsteadOfBlockingForever() throws Exception {
        // Small capacity + short bounded window keeps the test fast while
        // exercising the exact production semantics.
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, TOPIC, EPOCH, 2, 0L, 300L, true);

        // Fill the queue (2 slots) with no reader, then keep delivering.
        deliver(channel, "a");
        deliver(channel, "b");
        assertFalse(channel.isOverflowed(), "within-capacity deliveries must not flag overflow");

        // Queue full, no consumer progress → the dispatch must return at its bounded
        // offer window, not hang. NAMED RESIDUAL（plan 2510 batch B，逐处裁定）：offer
        // 的 300ms 等待是 JDK BlockingQueue.offer 的真实时长语义（生产内部未走
        // CoreMetrics，虚拟时钟不可达），测试不再断言「耗时 >= 250ms」阈值（禁止形态
        // 第 4 类），改为结构性终态断言：dispatch 返回（未挂死）+ 类型化 overflow
        // 置位 + 读者面到 TYPED 错误 —— 与 TestTaskManager R-15 真实时长语义先例同口径。
        deliver(channel, "c");
        assertTrue(channel.isOverflowed(),
                "channel must flag the typed overflow failure (observable without a reader)");
        assertTrue(channel.isFinished(), "overflow failure finishes the channel");

        // A reader arriving later surfaces the TYPED error — never a clean EOS
        // (a null return here would silently acknowledge truncated data).
        StreamException ex = assertThrows(StreamException.class, channel::read);
        assertEquals(NopStreamErrors.ERR_STREAM_CHANNEL_OVERFLOW.getErrorCode(), ex.getErrorCode(),
                "reader must surface ERR_STREAM_CHANNEL_OVERFLOW, got: " + ex.getMessage());
        assertEquals(300L, ex.getParam(NopStreamErrors.ARG_TIMEOUT_MS));
        assertEquals(TOPIC, ex.getParam(NopStreamErrors.ARG_TOPIC));

        channel.close();
    }

    @Test
    void healthyReaderReceivesEverythingInOrderNoLossNoDuplication() throws Exception {
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, TOPIC, EPOCH, 4, 0L, 300L, true);

        int total = 50;
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < total; i++) {
                    StreamElement e = channel.read(5, TimeUnit.SECONDS);
                    if (e == null) {
                        break;
                    }
                    received.add(((StreamRecord<String>) e).getValue());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
        reader.start();

        for (int i = 0; i < total; i++) {
            deliver(channel, "v" + i);
        }
        reader.join(10_000L);
        assertFalse(reader.isAlive(), "reader must drain and exit");
        assertEquals(total, received.size(), "no loss");
        for (int i = 0; i < total; i++) {
            assertEquals("v" + i, received.get(i), "in-order delivery at index " + i);
        }
        assertFalse(channel.isOverflowed(), "a draining consumer must never trip the overflow failure");
        channel.close();
    }

    @Test
    void healthySlowReaderDoesNotTripOverflow_boundedWaitsSucceedAsSlotsFree() throws Exception {
        // BP-1 regime: consumer drains slower than the producer fills, but it
        // DOES make continuous progress — every offer succeeds within the
        // window (dispatch-level backpressure), no typed failure.
        //
        // 时间确定化（plan 2510）：读者「慢」的节拍不再用固定 sleep（与机器负载相关），
        // 而是结构化确定性握手 —— 读者只在队列满（delivered - received == capacity，
        // 即生产者必然正被阻塞在 bounded offer 上）时才消费一条。bounded offer 等待
        // 因此必然发生且必然在窗口内成功（读者的消费在上限内及时释放槽位）。
        RemoteInputChannel channel = new RemoteInputChannel(
                messageService, TOPIC, EPOCH, 2, 0L, 2_000L, true);

        int total = 12;
        int capacity = 2;
        AtomicInteger delivered = new AtomicInteger();
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        AtomicReference<Throwable> producerError = new AtomicReference<>();
        Thread slowReader = new Thread(() -> {
            try {
                for (int i = 0; i < total; i++) {
                    if (i < total - 1) {
                        // 确定性节拍：等队列重新装满（生产者停在 bounded offer 上）。
                        // 最后一条无需等待（生产者已全部送达，不再有 pending offer）。
                        TestAwait.until("queue full again (producer parked in its bounded offer)",
                                () -> delivered.get() - received.size() >= capacity);
                    }
                    StreamElement e = channel.read(10, TimeUnit.SECONDS);
                    if (e == null) {
                        return;
                    }
                    received.add(((StreamRecord<String>) e).getValue());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                readerError.set(t);
            }
        });
        slowReader.start();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < total; i++) {
                    deliver(channel, "s" + i); // producer faster than consumer → queue repeatedly full
                    delivered.incrementAndGet();
                }
            } catch (Throwable t) {
                producerError.set(t);
            }
        });
        producer.start();
        producer.join(30_000L);
        slowReader.join(30_000L);
        assertFalse(producer.isAlive(), "producer must finish delivering all messages");
        assertFalse(slowReader.isAlive(), "reader must drain and exit");

        assertNull(producerError.get(), "producer must not observe a dispatch failure: " + producerError.get());
        assertNull(readerError.get(), "slow-but-healthy reader must not observe a typed failure: " + readerError.get());
        assertFalse(channel.isOverflowed(), "continuous progress within the window must never trip overflow");
        assertEquals(total, received.size(), "no loss under sustained backpressure");
        for (int i = 0; i < total; i++) {
            assertEquals("s" + i, received.get(i), "in-order delivery at index " + i);
        }
        channel.close();
    }

    private final List<String> received = new ArrayList<>();
}
