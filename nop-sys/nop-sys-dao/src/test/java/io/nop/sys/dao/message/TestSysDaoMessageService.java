package io.nop.sys.dao.message;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.message.core.MessageCoreConstants;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysBroadcastEvent;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysDaoMessageService extends JunitBaseTestCase {
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
    }

    @Test
    public void testSendAsyncRoutesBroadcastAndQueueSeparately() {
        ApiRequest<Map<String, Object>> request = request("Order", "A-100");

        service.send(MessageCoreConstants.TOPIC_PREFIX_BROADCAST + "order-created", request, null);
        service.send("order-created", request, null);

        IEntityDao<NopSysBroadcastEvent> broadcastDao = daoProvider.daoFor(NopSysBroadcastEvent.class);
        IEntityDao<NopSysEvent> eventDao = daoProvider.daoFor(NopSysEvent.class);

        List<NopSysBroadcastEvent> broadcastEvents = broadcastDao.findAll();
        List<NopSysEvent> queueEvents = eventDao.findAll();

        assertEquals(1, broadcastEvents.size());
        assertEquals(1, queueEvents.size());
        assertEquals("order-created", queueEvents.get(0).getEventTopic());
        assertEquals(MessageCoreConstants.TOPIC_PREFIX_BROADCAST + "order-created", broadcastEvents.get(0).getEventTopic());
    }

    @Test
    public void testStablePartitionIndexForSameBusinessKey() {
        ApiRequest<Map<String, Object>> first = request("Order", "A-100");
        ApiRequest<Map<String, Object>> second = request("Order", "A-100");
        ApiRequest<Map<String, Object>> third = request("Order", "B-200");

        service.send("order-created", first, null);
        service.send("order-created", second, null);
        service.send("order-created", third, null);

        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        assertEquals(3, events.size());
        assertEquals(events.get(0).getPartitionIndex(), events.get(1).getPartitionIndex());
        assertTrue(events.get(0).getPartitionIndex() >= 0);
        assertTrue(events.get(2).getPartitionIndex() >= 0);
    }

    @Test
    public void testNonBroadcastWithoutOrderKeyFallsBackToTopicHash() {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", "evt-topic-hash"));

        service.send("topic-only", request, null);

        NopSysEvent event = daoProvider.daoFor(NopSysEvent.class).findAll().get(0);
        assertEquals((int) io.nop.commons.util.StringHelper.shortHash("topic-only"), event.getPartitionIndex());
    }

    @Test
    public void testNonBroadcastProcessClaimsPartitionHeadOnly() {
        List<String> processed = new ArrayList<>();
        service.subscribe("order-created", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                ApiRequest<Map<String, Object>> request = (ApiRequest<Map<String, Object>>) message;
                processed.add(String.valueOf(request.getData().get("id")));
                return null;
            }
        }, null);

        service.send("order-created", request("Order", "A-100", "evt-1"), null);
        service.send("order-created", request("Order", "A-100", "evt-2"), null);

        service.processNonBroadcastEvent();

        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        assertEquals(1, processed.size());
        assertEquals("evt-1", processed.get(0));
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED, events.get(0).getEventStatus());
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, events.get(1).getEventStatus());
    }

    @Test
    public void testNonBroadcastFailureBlocksPartitionUntilRetry() {
        AtomicInteger attempts = new AtomicInteger();
        service.subscribe("order-created", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                attempts.incrementAndGet();
                throw new IllegalStateException("boom");
            }
        }, null);

        service.send("order-created", request("Order", "A-100", "evt-1"), null);
        service.send("order-created", request("Order", "A-100", "evt-2"), null);

        service.processNonBroadcastEvent();

        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        assertEquals(1, attempts.get());
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, events.get(0).getEventStatus());
        assertEquals(1, events.get(0).getRetryTimes());
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, events.get(1).getEventStatus());
    }

    @Test
    public void testActiveClaimedHeadBlocksLaterEventInSamePartition() {
        service.send("order-created", request("Order", "A-100", "evt-1"), null);
        service.send("order-created", request("Order", "A-100", "evt-2"), null);

        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        events.get(0).setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED);
        events.get(0).setLeaseOwner("worker-A");
        events.get(0).setLeaseExpireTime(new java.sql.Timestamp(System.currentTimeMillis() + 60_000));
        daoProvider.daoFor(NopSysEvent.class).updateEntityDirectly(events.get(0));

        List<String> processed = new ArrayList<>();
        service.subscribe("order-created", recordingConsumer(processed), null);
        service.processNonBroadcastEvent();

        assertEquals(List.of(), processed);
    }

    @Test
    public void testBroadcastProcessesEventsByEventIdOrder() {
        List<String> processed = new ArrayList<>();
        service.subscribe("bro-order-created", recordingConsumer(processed), null);

        service.send("bro-order-created", request("Order", "A-100", "evt-1"), null);
        service.send("bro-order-created", request("Order", "A-100", "evt-2"), null);
        service.send("bro-order-created", request("Order", "A-100", "evt-3"), null);

        service.processBroadcastEvent();

        assertEquals(List.of("evt-1", "evt-2", "evt-3"), processed);
    }

    @Test
    public void testBroadcastInMemoryCursorPreventsReprocessing() {
        List<String> processed = new ArrayList<>();
        service.subscribe("bro-order-created", recordingConsumer(processed), null);

        service.send("bro-order-created", request("Order", "A-100", "evt-1"), null);
        service.send("bro-order-created", request("Order", "A-100", "evt-2"), null);

        service.processBroadcastEvent();
        service.processBroadcastEvent();

        assertEquals(List.of("evt-1", "evt-2"), processed);
    }

    @Test
    public void testBroadcastMultiSubscriberFanOut() {
        List<String> processedA = new ArrayList<>();
        List<String> processedB = new ArrayList<>();
        service.subscribe("bro-order-created", recordingConsumer(processedA), null);
        service.subscribe("bro-order-created", recordingConsumer(processedB), null);

        service.send("bro-order-created", request("Order", "A-100", "evt-1"), null);

        service.processBroadcastEvent();

        assertEquals(List.of("evt-1"), processedA);
        assertEquals(List.of("evt-1"), processedB);
    }

    @Test
    public void testBroadcastConsumeLaterIgnored() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> processed = new ArrayList<>();
        service.subscribe("bro-order-created", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                ApiRequest<Map<String, Object>> request = (ApiRequest<Map<String, Object>>) message;
                String id = String.valueOf(request.getData().get("id"));
                if (attempts.getAndIncrement() == 0) {
                    return new ConsumeLater(50);
                }
                processed.add(id);
                return null;
            }
        }, null);

        service.send("bro-order-created", request("Order", "A-100", "evt-1"), null);
        service.send("bro-order-created", request("Order", "A-100", "evt-2"), null);

        service.processBroadcastEvent();

        assertEquals(List.of("evt-2"), processed);
    }

    @Test
    public void testBroadcastFailureDoesNotBlockSubsequent() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> processed = new ArrayList<>();
        service.subscribe("bro-order-created", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                ApiRequest<Map<String, Object>> request = (ApiRequest<Map<String, Object>>) message;
                String id = String.valueOf(request.getData().get("id"));
                if (attempts.getAndIncrement() == 0) {
                    throw new IllegalStateException("boom");
                }
                processed.add(id);
                return null;
            }
        }, null);

        service.send("bro-order-created", request("Order", "A-100", "evt-1"), null);
        service.send("bro-order-created", request("Order", "A-100", "evt-2"), null);

        service.processBroadcastEvent();

        assertEquals(List.of("evt-2"), processed);
    }

    @Test
    public void testBroadcastRestartReplaysTimeWindow() {
        List<String> processed = new ArrayList<>();
        service.subscribe("bro-order-created", recordingConsumer(processed), null);

        service.send("bro-order-created", request("Order", "A-100", "evt-1"), null);
        service.processBroadcastEvent();

        TestableSysDaoMessageService restarted = newService("worker-B", "0,32767");
        restarted.subscribe("bro-order-created", recordingConsumer(processed), null);
        restarted.send("bro-order-created", request("Order", "A-100", "evt-2"), null);
        restarted.processBroadcastEvent();

        // evt-1 appears twice: once consumed by original service,
        // once re-consumed by restarted service (memory cursor lost, within time window)
        assertEquals(List.of("evt-1", "evt-1", "evt-2"), processed);
    }

    @Test
    public void testConsumeLaterRequeuesNonBroadcastEvent() {
        service.subscribe("order-created", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return new ConsumeLater(50);
            }
        }, null);

        service.send("order-created", request("Order", "A-100", "evt-1"), null);
        service.processNonBroadcastEvent();

        NopSysEvent event = daoProvider.daoFor(NopSysEvent.class).findAll().get(0);
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, event.getEventStatus());
        assertEquals(1, event.getRetryTimes());
        assertNull(event.getLeaseOwner());
    }

    @Test
    public void testDifferentPartitionsCanAdvanceIndependently() {
        List<String> processed = new ArrayList<>();
        service.subscribe("order-created", recordingConsumer(processed), null);

        service.send("order-created", request("Order", "A-100", "evt-a1"), null);
        service.send("order-created", request("Order", "B-200", "evt-b1"), null);

        service.processNonBroadcastEvent();

        assertEquals(Set.of("evt-a1", "evt-b1"), Set.copyOf(processed));
    }

    @Test
    public void testClaimedEventCanBeTakenOverAfterLeaseExpiry() {
        service.send("order-created", request("Order", "A-100", "evt-1"), null);

        NopSysEvent event = daoProvider.daoFor(NopSysEvent.class).findAll().get(0);
        event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_CLAIMED);
        event.setLeaseOwner("worker-A");
        event.setLeaseExpireTime(new java.sql.Timestamp(System.currentTimeMillis() - 1000));
        daoProvider.daoFor(NopSysEvent.class).updateEntityDirectly(event);

        TestableSysDaoMessageService workerB = newService("worker-B", "0,32767");
        List<String> processed = new ArrayList<>();
        workerB.subscribe("order-created", recordingConsumer(processed), null);

        workerB.processNonBroadcastEvent();

        assertEquals(List.of("evt-1"), processed);
    }

    /**
     * check2 审计 [P2]：持久订阅的 suspend/resume 原本只作用于 localService 内存订阅，
     * 持久消息投递路径从不检查挂起状态。修复后挂起的队列订阅不再投递，事件保持 WAITING
     * 待 resume 后重投（而非按无消费者丢弃成 PROCESSED）。
     */
    @Test
    public void testSuspendedDurableSubscriptionHoldsNonBroadcastEvent() {
        List<String> processed = new ArrayList<>();
        IMessageSubscription subscription = service.subscribe("order-created", recordingConsumer(processed), null);
        subscription.suspend();
        assertTrue(subscription.isSuspended());

        service.send("order-created", request("Order", "A-100", "evt-1"), null);
        service.processNonBroadcastEvent();

        assertEquals(List.of(), processed, "suspended subscription must not receive messages");
        List<NopSysEvent> events = daoProvider.daoFor(NopSysEvent.class).findAll();
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, events.get(0).getEventStatus(),
                "event must be held for the suspended subscriber instead of being marked PROCESSED");

        subscription.resume();
        assertFalse(subscription.isSuspended());
        service.processNonBroadcastEvent();
        assertEquals(List.of("evt-1"), processed);
    }

    /**
     * check2 审计 [P2]：广播投递路径同样需跳过挂起的订阅者。
     */
    @Test
    public void testSuspendedBroadcastSubscriberIsSkipped() {
        List<String> processedA = new ArrayList<>();
        List<String> processedB = new ArrayList<>();
        IMessageSubscription subscriptionA = service.subscribe("bro-order-created", recordingConsumer(processedA), null);
        service.subscribe("bro-order-created", recordingConsumer(processedB), null);

        subscriptionA.suspend();
        service.send("bro-order-created", request("Order", "A-100", "evt-1"), null);
        service.processBroadcastEvent();

        assertEquals(List.of(), processedA, "suspended broadcast subscriber must be skipped");
        assertEquals(List.of("evt-1"), processedB);
    }

    /**
     * check2 审计 [P3]：最后一个订阅者取消后 topic 残留在轮询集合中——无谓轮询持续，
     * 且取消期间到达的非广播事件被认领后直接标记 PROCESSED 丢弃。修复后空 topic
     * 从轮询集合移除。
     */
    @Test
    public void testCancelLastSubscriptionRemovesTopicFromPollSet() {
        IMessageSubscription subscription = service.subscribe("order-created",
                recordingConsumer(new ArrayList<>()), null);
        assertTrue(service.getNonBroadcastTopics().contains("order-created"));
        subscription.cancel();
        assertFalse(service.getNonBroadcastTopics().contains("order-created"),
                "empty topic must be removed from the polling set after the last subscriber cancels");

        IMessageSubscription broadcastSubscription = service.subscribe("bro-order-created",
                recordingConsumer(new ArrayList<>()), null);
        assertTrue(service.getBroadcastTopics().contains("bro-order-created"));
        broadcastSubscription.cancel();
        assertFalse(service.getBroadcastTopics().contains("bro-order-created"),
                "empty broadcast topic must be removed from the polling set after the last subscriber cancels");
    }

    private IMessageConsumer recordingConsumer(List<String> processed) {
        return new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                ApiRequest<Map<String, Object>> request = (ApiRequest<Map<String, Object>>) message;
                processed.add(String.valueOf(request.getData().get("id")));
                return null;
            }
        };
    }

    private TestableSysDaoMessageService newService(String hostId, String partitions) {
        TestableSysDaoMessageService svc = new TestableSysDaoMessageService(hostId);
        svc.setDaoProvider(daoProvider);
        svc.setAssignedPartitions(IntRangeSet.parse(partitions));
        svc.setFetchSize(20);
        svc.setMinProcessDelay(1);
        svc.setLeaseTimeout(1000);
        return svc;
    }

    private ApiRequest<Map<String, Object>> request(String bizObjName, String bizKey) {
        return request(bizObjName, bizKey, bizKey);
    }

    private ApiRequest<Map<String, Object>> request(String bizObjName, String bizKey, String id) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", id));
        io.nop.api.core.util.ApiHeaders.setSvcName(request, bizObjName);
        io.nop.api.core.util.ApiHeaders.setBizKey(request, bizKey);
        return request;
    }

    static class TestableSysDaoMessageService extends SysDaoMessageService {
        private final String hostId;

        private TestableSysDaoMessageService(String hostId) {
            this.hostId = hostId;
        }

        @Override
        protected String getHostId() {
            return hostId;
        }
    }
}
