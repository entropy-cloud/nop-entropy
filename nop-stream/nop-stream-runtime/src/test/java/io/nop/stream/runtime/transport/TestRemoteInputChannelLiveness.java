package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer-side channel liveness (inactivity) timeout detection on
 * {@link RemoteInputChannel}: liveness is carried by accepted
 * data/barrier/watermark/control traffic alone — there is no dedicated
 * producer idle-heartbeat protocol message.
 *
 * <p>Wires {@link RemoteResultPartition} (producer) and {@link RemoteInputChannel}
 * (consumer) together on the same {@link LocalMessageService} topic (plan guide
 * #23 接线验证: both sides exercised in the same test, not standalone unit tests).
 *
 * <p>时间确定化（plan 2510）：channel liveness 判定读取经 {@code CoreMetrics}，测试注册
 * {@link ManualClock} 显式推进虚拟时间越过 channelTimeout 触发超时（推进前/推进后双断言）；
 * 错误 epoch 记录不刷新活性的负向断言以 lastReceivedTime 不变的结构性事实替代固定间隔
 * 发送。时钟为 JVM 全局量：逐测试注册、{@code @AfterEach} 恢复默认时钟。
 */
class TestRemoteInputChannelLiveness {

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
        return new RemoteResultPartition(messageService, topic, types, "edge-1", EPOCH);
    }

    /**
     * Consumer detects producer failure when neither data nor EOS arrives within
     * channelTimeout. read() throws ERR_STREAM_CHANNEL_TIMEOUT.
     */
    @Test
    void testConsumerTimesOutWhenSilent() throws Exception {
        String topic = "job.hb.silent";
        long channelTimeout = 120L;
        // Consumer subscribes; producer exists but never sends anything.
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // Within the window the consumer simply polls empty (no timeout yet).
        long birth = consumer.getLastReceivedTime();
        assertTrue(birth > 0);
        // 推进前断言：虚拟时钟未越过窗口 → 不超时（读空轮询返回 null，read 内部的
        // queue poll 为生产内部真实等待，允许形态）。
        assertNull(consumer.read(50, TimeUnit.MILLISECONDS), "empty queue within the window");
        assertFalse(consumer.isChannelTimedOut(), "within the window the channel is alive");

        // 推进虚拟时间越过 channelTimeout：下一次 read 顶部重评即抛超时。
        clock.advance(channelTimeout + 1L);
        StreamException thrown = assertThrows(StreamException.class,
                () -> consumer.read(50, TimeUnit.MILLISECONDS));
        assertEquals("nop.err.stream.channel-timeout", thrown.getErrorCode().toString(),
                "Expected channel timeout error: " + thrown.getMessage());
    }

    /**
     * Normal end-of-stream is NOT mistaken for a timeout. EOS sets
     * finished=true and read() returns null.
     */
    @Test
    void testEndOfStreamIsNotTimeout() throws Exception {
        String topic = "job.hb.eos";
        long channelTimeout = 1000L; // long window
        RemoteResultPartition partition = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // Producer finishes (sends END_OF_STREAM).
        partition.close();

        // Drain until EOS returns null — must NOT throw channel timeout even if
        // we wait past channelTimeout (finished suppresses timeout).
        StreamElement el = consumer.read(500, TimeUnit.MILLISECONDS);
        assertNull(el, "EOS should return null, not throw");
        assertTrue(consumer.isFinished());
        assertFalse(consumer.isChannelTimedOut(),
                "Finished channel must never report timeout");
    }

    /**
     * Fencing invariant: a message carrying the wrong epoch is discarded and
     * does NOT count as liveness — the consumer still times out.
     */
    @Test
    void testWrongEpochMessagesDoNotRefreshLiveness() throws Exception {
        String topic = "job.hb.fence";
        long channelTimeout = 150L;
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);
        long birth = consumer.getLastReceivedTime();

        // A stale producer with the wrong epoch keeps sending data records.
        TypeRegistry types = new TypeRegistry();
        types.register("edge-1", String.class.getName());
        long wrongEpoch = EPOCH + 99L;
        RemoteResultPartition staleProducer = new RemoteResultPartition(
                messageService, topic, types, "edge-1", wrongEpoch);

        // Repeatedly push wrong-epoch records. 时间确定化（plan 2510）：不再以真实
        // 间隔铺开发送 —— 「错误 epoch 不刷新活性」的负向断言以结构性事实表达：
        // lastReceivedTime 保持出生值不变（正确 epoch 的数据流会刷新它，见
        // testDataFlowKeepsConsumerAlive 的正例对照），消息未被接受（队列可读为空）。
        for (int i = 0; i < 5; i++) {
            staleProducer.write(new StreamRecord<>("stale-" + i));
        }
        assertEquals(birth, consumer.getLastReceivedTime(),
                "wrong-epoch traffic must not refresh the liveness stamp");
        assertNull(consumer.read(50, TimeUnit.MILLISECONDS),
                "wrong-epoch records must never surface as accepted data");

        // No correct-epoch message ever arrived → consumer must still time out.
        clock.advance(channelTimeout + 1L);
        StreamException thrown = assertThrows(StreamException.class,
                () -> consumer.read(50, TimeUnit.MILLISECONDS));
        assertEquals("nop.err.stream.channel-timeout", thrown.getErrorCode().toString(),
                "Wrong-epoch messages must not refresh liveness: " + thrown.getMessage());
    }

    /**
     * Data flow refreshes liveness — a producer that keeps sending records
     * keeps the consumer alive.
     */
    @Test
    void testDataFlowKeepsConsumerAlive() throws Exception {
        String topic = "job.hb.data";
        long channelTimeout = 200L;
        RemoteResultPartition partition = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // Send a record and read it — liveness refreshed by the record.
        partition.write(new StreamRecord<>("d"));
        StreamElement el = consumer.read(500, TimeUnit.MILLISECONDS);
        assertNotNull(el, "Should read the data record");
        assertTrue(el.isRecord());
        assertFalse(consumer.isChannelTimedOut(),
                "A recently-accepted record must keep the channel alive");

        partition.close();
    }
}
