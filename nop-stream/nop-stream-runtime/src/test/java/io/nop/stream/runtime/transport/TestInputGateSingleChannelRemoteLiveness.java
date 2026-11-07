package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.testsupport.TestAwait;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-1 (audit {@code nop-stream-independent-audit}, P1): proves the
 * {@link InputGate#read() single-channel remote-read} path surfaces producer
 * death via the channel liveness (inactivity) timeout within a bounded window,
 * instead of hanging forever.
 *
 * <p>The legacy {@code InputGate.readSingleChannel()} called the unbounded
 * blocking {@code read()} overload, which parked in {@code queue.take()}.
 * Once a single-input consumer parked inside {@code take()}, a remote producer
 * that subsequently died (crash / partition / stopped emitting any traffic)
 * left the consumer blocked indefinitely — the liveness-timeout
 * check at the top of {@code RemoteInputChannel.read()} could never re-fire
 * because the thread never returned to {@code checkChannelTimeout()}. This
 * silently disabled the documented fast-fail-on-producer-death safety feature
 * for the most common streaming topology (one upstream → one operator → one
 * sink) in the cross-JVM lane.
 *
 * <p>The fix rewrites {@code readSingleChannel} to loop on the bounded
 * {@code read(50, MILLISECONDS)} overload (mirroring the already-correct
 * {@code readMultiChannel} path), so {@code checkChannelTimeout()} re-fires
 * every ~50 ms even while the consumer is effectively parked. These tests lift
 * the existing {@code TestRemoteInputChannelLiveness} channel-level proof to
 * the {@link InputGate} layer — the gap the prior tests did not cover.
 *
 * <p>Plan guide coverage:
 * <ul>
 *   <li><b>#22 End-to-End / Anti-Hollow</b>: the liveness test exercises the
 *       full path {@code InputGate.readSingleChannel} →
 *       {@code RemoteInputChannel.read(long, TimeUnit)} → {@code queue.poll}
 *       → {@code checkChannelTimeout} re-firing each loop.</li>
 *   <li><b>#23 Wiring Verification</b>: the timeout fires during a <em>parked</em>
 *       read (loop re-enters {@code checkChannelTimeout} each round), not just
 *       once at the top — the core AR-1 fix.</li>
 *   <li><b>#25 Test-Mandated Feature</b>: tests assert the RESULT (timeout within
 *       a bounded window for a silent producer; elements returned for a live
 *       producer; prompt empty return on EOS), not merely absence of a hang.</li>
 * </ul>
 */
class TestInputGateSingleChannelRemoteLiveness {

    private static final long EPOCH = 7L;

    private LocalMessageService messageService;
    private ManualClock clock;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
        // 时钟是 JVM 全局量：逐测试恢复默认时钟，避免冻结同 fork 后续所有类。
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    private RemoteResultPartition producer(String topic) {
        TypeRegistry types = new TypeRegistry();
        types.register("edge-1", String.class.getName());
        // The producer stays fully silent to simulate death (no liveness traffic).
        return new RemoteResultPartition(messageService, topic, types, "edge-1", EPOCH);
    }

    /**
     * Liveness case (core AR-1 proof + revert guard + wiring #23): a
     * single-channel {@link InputGate} over a {@link RemoteInputChannel} fails
     * fast with {@code ERR_STREAM_CHANNEL_TIMEOUT} within a bounded window when
     * the producer goes silent, instead of hanging forever.
     *
     * <p>The consumer drains a seed element first so its parking read ENTERS the
     * poll loop while liveness is fresh. This is the revert guard: with the old
     * unbounded {@code read()} the consumer parks in {@code queue.take()} BEFORE
     * the timeout elapses, so the single top-level {@code checkChannelTimeout()}
     * does not fire and the read hangs (the bounded-window assertion then
     * fails). Only the bounded-poll loop re-fires {@code checkChannelTimeout()}
     * each ~50 ms and throws — proving the wiring (#23) is live.
     */
    @Test
    void testSingleChannelGateTimesOutOnProducerDeath() throws Exception {
        String topic = "job.ar1.liveness";
        long channelTimeout = 150L;
        RemoteResultPartition producer = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);
        InputGate gate = new InputGate(Collections.singletonList(consumer));

        // Send ONE seed element first so the consumer's parking read enters the
        // poll loop while liveness is fresh (lastReceivedTime just reset).
        producer.write(new StreamRecord<>("seed"));

        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean drainedSeed = new AtomicBoolean(false);
        Thread reader = new Thread(() -> {
            try {
                // First read drains the seed element (liveness fresh at this
                // instant, so no top-level checkChannelTimeout fires).
                Optional<StreamElement> first = gate.read();
                if (first.isPresent()) {
                    drainedSeed.set(true);
                }
                // Second read parks in the bounded-poll loop; the producer is
                // now silent → checkChannelTimeout re-fires each ~50ms and
                // throws ERR_STREAM_CHANNEL_TIMEOUT once channelTimeoutMs
                // elapses. With the old unbounded read() this call would hang
                // in queue.take() (revert guard).
                gate.read();
            } catch (Throwable t) {
                error.set(t);
            }
        }, "ar1-liveness-reader");
        reader.setDaemon(true);
        reader.start();

        // 结构证据：读者已消费 seed 并进入 bounded-poll 循环（revert guard 的前提）。
        // 若代码回归为无界 take()，读者在此前就永久停靠 —— 由下方的 join 上限兜底变红。
        TestAwait.until("reader drained the seed element and parked in the bounded-poll loop",
                () -> drainedSeed.get());

        // 时间确定化（plan 2510）：channelTimeout 判定读取 CoreMetrics —— 推进虚拟
        // 时间越过窗口，读者的下一次 poll 轮回（生产内部 ~50ms 真实轮询，允许形态）
        // 即抛 ERR_STREAM_CHANNEL_TIMEOUT。join 上限仅挂死保护（回归代码会停靠在
        // take() 里，时钟推进救不了它）。
        clock.advance(channelTimeout + 1L);
        long boundedWindowMs = 2L * 50L + channelTimeout + 2000L;
        reader.join(boundedWindowMs);
        assertFalse(reader.isAlive(),
                "single-channel read must surface producer death within bounded window ("
                        + boundedWindowMs + "ms), not hang");
        assertTrue(drainedSeed.get(),
                "reader should have drained the seed element before parking");
        Throwable t = error.get();
        assertNotNull(t, "parking read should have thrown channel-timeout, not return normally");
        assertTrue(t instanceof StreamException,
                "expected StreamException, got " + (t == null ? "null" : t.getClass()));
        assertEquals("nop.err.stream.channel-timeout",
                ((StreamException) t).getErrorCode().toString(),
                "Expected channel timeout: " + t.getMessage());

        producer.close();
        consumer.close();
    }

    /**
     * Negative control (live producer): a single-channel {@link InputGate} does
     * NOT time out and keeps returning elements while the producer keeps
     * emitting within the channel-timeout window — even across a total duration
     * well beyond a single {@code channelTimeoutMs}. This directly contrasts
     * with the liveness case and proves the loop does not spuriously fire.
     *
     * <p>Also covers the momentarily-idle-but-live case: between two emits the
     * consumer's read parks briefly via {@code poll(50ms)} (no busy-spin) yet
     * stays alive because liveness is refreshed before the window elapses.
     */
    @Test
    void testSingleChannelGateStaysAliveWhileProducerEmits() throws Exception {
        String topic = "job.ar1.live";
        long channelTimeout = 200L;
        RemoteResultPartition producer = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);
        InputGate gate = new InputGate(Collections.singletonList(consumer));

        // Drive producer + consumer in lockstep (LocalMessageService dispatch is
        // synchronous, so write() enqueues into the consumer before returning).
        // Total duration > channelTimeout proves the consumer would have timed
        // out if the producer were silent, but does not because liveness is
        // refreshed by each emit.
        //
        // 时间确定化（plan 2510）：总时长与「brief idle」都在虚拟时间线上表达 ——
        // 每次发射之间推进 channelTimeout/4（替代真实 sleep），累计推进越过
        // 2×channelTimeout（替代墙钟窗口）。活性判定读取 CoreMetrics：每次发射
        // 刷新 lastReceivedTime，推进间隔 < channelTimeout → 永不超时。
        long start = clock.peekMillis();
        int received = 0;
        while (clock.peekMillis() - start <= channelTimeout * 2L) {
            producer.write(new StreamRecord<>("d-" + received));
            Optional<StreamElement> el = gate.read();
            assertTrue(el.isPresent(),
                    "live producer must keep the consumer alive (no timeout); iteration " + received);
            assertTrue(el.get().isRecord());
            received++;
            // Brief idle (< channelTimeout) between emits, on the virtual line:
            // the next read stays within the refreshed liveness window, so no
            // timeout fires.
            clock.advance(channelTimeout / 4);
        }
        assertTrue(clock.peekMillis() - start > channelTimeout * 2L,
                "the lockstep exchange must span well beyond a single channelTimeout "
                        + "(virtual elapsed=" + (clock.peekMillis() - start) + "ms)");
        assertTrue(received > 1,
                "should have received multiple records over the window without timing out");
        assertFalse(consumer.isChannelTimedOut(),
                "consumer must stay alive while the producer keeps emitting");

        producer.close();
        consumer.close();
    }

    /**
     * EOS control (review B1 — {@code isFinished()} disambiguation): a producer
     * that sends {@code CONTROL_END_OF_STREAM} makes the single-channel read
     * return promptly (empty), with no busy-spin and no timeout. This directly
     * verifies the {@code null}-via-{@code isFinished()} branch: the bounded
     * overload returns {@code null} for BOTH poll-timeout and EOS, and only
     * {@code isFinished()} distinguishes them. Treating all null as "continue"
     * would busy-spin here; treating all null as "empty" would silently
     * terminate the liveness case's parking read on its first idle poll.
     */
    @Test
    void testSingleChannelGateReturnsEmptyOnEndOfStream() throws Exception {
        String topic = "job.ar1.eos";
        long channelTimeout = 1000L; // long window so timeout never fires
        RemoteResultPartition producer = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);
        InputGate gate = new InputGate(Collections.singletonList(consumer));

        // Producer finishes (synchronously dispatches CONTROL_END_OF_STREAM).
        producer.close();

        // 时间确定化（plan 2510）：替代旧的 elapsed < 500ms 阈值断言 —— EOS 读在辅助
        // 线程执行，条件等待其完成（上限 5s 仅挂死保护）。若实现把 EOS-null 当
        // 「continue」忙碌空转或停靠在 take()，读永不返回 → 上限处确定性变红；
        // 正常路径毫秒级返回。返回后无 busy-spin 由「线程已退出」结构性证明。
        AtomicBoolean eosReadReturned = new AtomicBoolean(false);
        AtomicReference<Optional<StreamElement>> eosResult = new AtomicReference<>();
        Thread eosReader = new Thread(() -> {
            try {
                eosResult.set(gate.read());
                eosReadReturned.set(true);
            } catch (Throwable t) {
                eosResult.set(Optional.empty());
            }
        }, "eos-reader");
        eosReader.setDaemon(true);
        eosReader.start();
        TestAwait.until("EOS read returns promptly (no hang, no busy-spin on the finished channel)",
                () -> eosReadReturned.get(), 5_000L);
        assertTrue(eosReadReturned.get(),
                "EOS read should return promptly (a hang or busy-spin would exceed the cap)");
        Optional<StreamElement> el = eosResult.get();
        eosReader.join(5_000L);

        assertFalse(el.isPresent(), "EOS should return empty promptly, not hang or busy-spin");
        assertTrue(consumer.isFinished(),
                "channel must be marked finished on END_OF_STREAM");
        assertFalse(consumer.isChannelTimedOut(),
                "finished channel must never report liveness timeout");

        consumer.close();
    }
}
