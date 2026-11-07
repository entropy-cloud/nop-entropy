/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.message.core.channel.ChannelBacklogProbe;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.testsupport.TestAwait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 (plan 2331): producer-side DB channel backpressure gate — fake-probe unit
 * contract (no DB). Covers the five paths (pass-through / wait / typed timeout /
 * gate off / probe null never queried), plus the lock-outside ruling: a gated writer
 * waiting on backlog must NOT hold the send lock, so close() can still deliver EOS.
 *
 * <p>时间确定化（plan 2510）：gate 的 deadline 与 backlog cache TTL 判定读取经
 * {@code CoreMetrics}，测试注册 {@link ManualClock} 显式推进虚拟时间越过门控截止点；
 * 「仍未发生」类断言先以探测调用计数 / 时钟读取计数（生产门循环每片重评一次）取得
 * 对方已重新求值的结构证据，再用真实 join / 条件等待观测结果。时钟为 JVM 全局量：
 * 逐测试注册、{@code @AfterEach} 恢复默认时钟。
 */
public class TestRemoteResultPartitionBackpressureGate {

    private ManualClock clock;

    @BeforeEach
    void registerManualClock() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    void restoreDefaultClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

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
        AtomicInteger probeQueries = new AtomicInteger();
        ChannelBacklogProbe probe = topic -> {
            probeQueries.incrementAndGet();
            return backlog.get();
        };
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

        // 「门控期间未发送」的结构证据：门循环每片 force-refresh 探测一次 —— 探测
        // 计数增长即生产者已在超限状态下重新求值过门（而非还没进到门）。
        TestAwait.until("gated writer re-evaluated the gate while over the backlog limit",
                () -> probeQueries.get() >= 2);
        assertEquals(0, sent.size(), "gated writer must not send while over limit");

        backlog.set(1);
        producer.join(5_000);
        assertEquals(1, sent.size(), "producer must send once backlog drops");
    }

    /**
     * Sustained overload fails typed once the gate deadline passes. 推进前/推进后双断言
     * （接线验证）：预算内推进不触发失败（门在虚拟时间线上继续等待），越过 10s deadline
     * 后下一次重评抛类型化超时 —— 替代旧的「真实阻塞 ~10s + elapsed >= 9000 阈值断言」。
     */
    @Test
    public void sustainedOverloadFailsTypedWithDetails() throws Exception {
        List<Object> sent = new ArrayList<>();
        AtomicInteger probeQueries = new AtomicInteger();
        ChannelBacklogProbe probe = topic -> {
            probeQueries.incrementAndGet();
            return 99L;
        };
        IMessageService service = recordingService(sent);
        RemoteResultPartition partition = new RemoteResultPartition(service, "t",
                null, "e", 1L, probe, 5L);

        AtomicReference<Throwable> outcome = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            try {
                partition.write(element("a"));
            } catch (Throwable t) {
                outcome.set(t);
            } finally {
                done.countDown();
            }
        }, "gated-writer");
        writer.setDaemon(true);
        writer.start();

        // 门进入证据：初始 TTL 探测 + deadline 读取（时钟读取计数增长）后，门循环
        // 已至少 force-refresh 一片 —— writer 确实在门控等待而非尚未进入。
        TestAwait.until("gated writer entered the backlog gate (deadline computed + probe re-queried)",
                () -> probeQueries.get() >= 2 && clock.readsBy(writer) >= 2);
        assertNull(outcome.get(), "no typed failure before the virtual deadline passes");

        // 推进前半程（仍在 10s 预算内）：门循环再重评一片，仍不得失败。
        clock.advance(5_000L);
        TestAwait.until("gate loop re-evaluated the deadline inside the budget",
                () -> probeQueries.get() >= 3);
        assertNull(outcome.get(),
                "the gate must keep waiting while the virtual clock is inside the deadline");

        // 推进越过 10s deadline：下一次重评 now >= deadline → 类型化失败。
        clock.advance(5_001L);
        assertTrue(done.await(10, TimeUnit.SECONDS),
                "typed gate timeout must fire once the virtual clock passes the deadline");
        Throwable t = outcome.get();
        assertTrue(t instanceof StreamException,
                "expected the typed send-gate timeout, got: " + t);
        StreamException se = (StreamException) t;
        assertEquals(NopStreamErrors.ERR_STREAM_SEND_GATE_TIMEOUT.getErrorCode(), se.getErrorCode(),
                "bounded wait must fail typed with the send-gate-timeout error");
        assertEquals("t", se.getParam(NopStreamErrors.ARG_TOPIC), "must name the topic");
        assertEquals(5L, se.getParam(NopStreamErrors.ARG_BACKLOG_LIMIT), "must carry the backlog limit");
        assertEquals(99L, se.getParam(NopStreamErrors.ARG_CURRENT_BACKLOG),
                "must carry the observed backlog");
        assertEquals(0, sent.size(), "the gated write must not have sent anything");
        writer.join(5_000);
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
        AtomicInteger probeQueries = new AtomicInteger();
        ChannelBacklogProbe probe = topic -> {
            probeQueries.incrementAndGet();
            return backlogHigh.get() ? 99L : 0L;
        };
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

        // 结构证据：生产者已进入门控等待（门循环 force-refresh 过探测），此时它必然
        // 不在 sendLock 的临界区内。
        TestAwait.until("producer entered the gated wait (probe re-queried while over limit)",
                () -> probeQueries.get() >= 2);

        // close() 必须能即刻拿锁投递 EOS —— 结构性替代旧的 closeMs < 2000 阈值断言：
        // close 在辅助线程执行，条件等待其完成（上限 5s 仅挂死保护；若回归为门控写者
        // 持锁，close 将阻塞到门超时，在冻结的虚拟时钟下永不返回 → 上限处确定性变红）。
        AtomicBoolean closed = new AtomicBoolean(false);
        Thread closer = new Thread(() -> {
            partition.close();
            closed.set(true);
        }, "eos-closer");
        closer.setDaemon(true);
        closer.start();
        TestAwait.until("close() must not be blocked by the gated writer (the gate waits outside the send lock)",
                () -> closed.get(), 5_000L);
        assertTrue(closed.get(), "close() was blocked by a gated writer holding the send lock");
        assertTrue(producer.isAlive(),
                "producer must still be gated when close() completed (backlog still over limit)");

        backlogHigh.set(false);
        producer.join(5_000);
        // EOS ordering contract: the write started before close but reached the lock
        // after EOS — it must be rejected, not land after the terminal message.
        assertEquals(1, sent.size(), "only the EOS control message may be sent");
        closer.join(5_000);
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
