package io.nop.sys.dao.message;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I-05（C8-001，plan 2525 裁定⑩）：invokeConsumer 消费等待必须有界——消费者返回永不完成的
 * future 时，无界 syncGet 使轮询线程永久阻塞（scheduleWithFixedDelay 后续轮询永不开始，
 * 全部事件消费静默停摆）。修复形态：分片等待 + 总预算 consume-wait-timeout，耗尽返回
 * ConsumeLater(0) 回 WAITING 重排（at-least-once）。挂死类失败模式=超时：缺陷态下本钉
 * 永久悬挂（assertTimeoutPreemptibly 判红），修复态 200ms 预算内按 ConsumeLater 路径返回。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestConsumeWaitTimeout_I05 extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    private SysDaoMessageService service;

    @BeforeEach
    public void setUp() {
        service = new SysDaoMessageService();
        service.setDaoProvider(daoProvider);
        service.setAssignedPartitions(IntRangeSet.parse("0,32767"));
        service.setFetchSize(20);
        service.setMinProcessDelay(1);
        service.setLeaseTimeout(1000);
        service.setConsumeWaitTimeout(200);
    }

    private static ApiRequest<Map<String, Object>> request(String id) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", id));
        return request;
    }

    /**
     * 红⑩（挂死超时钉）：永不完成的消费者 future 必须在预算内按 ConsumeLater 路径放弃——
     * 事件回 WAITING 且 retryTimes 递增，轮询线程不被永久阻塞。缺陷态（无界 syncGet）下
     * 本用例悬挂至 assertTimeoutPreemptibly 判红——注释即失败模式语义（挂死=超时判红）。
     */
    @Test
    public void testHungConsumerFutureTimesOutAsConsumeLater() throws Exception {
        service.subscribe("i05-hung-topic", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                // 消费者挂死：返回永不完成的 future（确定性悬挂构造，非竞态红）
                return new CompletableFuture<>();
            }
        }, null);

        service.send("i05-hung-topic", request("evt-i05"), null);

        long begin = System.currentTimeMillis();
        // C8-001：挂死消费者不得永久阻塞轮询线程——等待必须有界并按 ConsumeLater 路径返回。
        // 缺陷态（无界 syncGet）下 worker 悬挂于 syncGet → join 超时判红——挂死类失败模式=超时
        Thread worker = new Thread(() -> service.processNonBroadcastEvent(), "i05-hung-worker");
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive(),
                "C8-001: a hung consumer future must not block the polling thread forever —"
                        + " the wait must be bounded and return via the ConsumeLater path");
        long elapsed = System.currentTimeMillis() - begin;
        assertTrue(elapsed < 4000, "bounded wait must return promptly, elapsed=" + elapsed + "ms");

        IEntityDao<NopSysEvent> dao = daoProvider.daoFor(NopSysEvent.class);
        List<NopSysEvent> events = dao.findAll();
        assertEquals(1, events.size());
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, events.get(0).getEventStatus(),
                "timed-out consumption must reschedule the event as WAITING (at-least-once), not drop it");
        assertEquals(1, events.get(0).getRetryTimes());
        org.junit.jupiter.api.Assertions.assertNull(events.get(0).getLeaseOwner(),
                "ConsumeLater path must clear the lease owner");
    }

    /**
     * 正常消费者（同步返回）不受有界等待改造影响——零回归钉。
     */
    @Test
    public void testNormalConsumerStillProcessed() {
        service.subscribe("i05-normal-topic", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return null;
            }
        }, null);

        service.send("i05-normal-topic", request("evt-normal"), null);
        service.processNonBroadcastEvent();

        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        assertEquals(1, events.size());
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED, events.get(0).getEventStatus());
    }

    /**
     * 慢消费者在预算内完成：等待循环按切片推进并最终取到结果（等待机制不吞正常完成）。
     */
    @Test
    public void testSlowConsumerCompletingWithinBudgetIsProcessed() {
        service.subscribe("i05-slow-topic", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> future.complete("done"));
                return future;
            }
        }, null);

        service.send("i05-slow-topic", request("evt-slow"), null);
        service.processNonBroadcastEvent();

        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        NopSysEvent target = events.stream()
                .filter(e -> "i05-slow-topic".equals(e.getEventTopic())).findFirst().orElseThrow();
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED, target.getEventStatus(),
                "consumer completing within budget must be processed normally");
    }
}
