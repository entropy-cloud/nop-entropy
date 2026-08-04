package io.nop.batch.sys;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.batch.dsl.manager.BatchTaskManagerImpl;
import io.nop.batch.dsl.runner.BatchTaskRunner;
import io.nop.cluster.naming.PartitionResolver;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysEvent;
import io.nop.sys.dao.message.SysDaoMessageService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证：job 调度的 sys-event batch 扫描通过 BatchTaskRunner 注入的 PartitionResolver
 * 限制只处理本实例分区的事件。链路：resolver resolve → context.partitionRange → orm-reader
 * addPartitionFilter → 只有对应分区的事件被加载处理。
 * <p>
 * 用真实 IBatchTaskManager（加载真实 batch.xml + orm-reader + DB），BatchTaskRunner 为新建实例
 * 并设置受控 resolver，验证 executeAsync 的 resolve+注入+reader 过滤完整路径。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testConfigFile = "classpath:test.properties")
public class TestSysEventBatchPartitionE2E extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    BatchTaskManagerImpl batchTaskManager;

    @Inject
    SysDaoMessageService messageService;

    @BeforeEach
    public void setUp() {
        // batch.xml 的 reader 用 getNonBroadcastTopics() 过滤，必须先订阅才能读到事件
        messageService.subscribe("order-created", (topic, message, ctx) -> null, null);
    }

    @Test
    public void testBatchOnlyProcessesConfiguredPartition() {
        // resolver 只负责 partition 0
        PartitionResolver resolver = new PartitionResolver();
        resolver.setAssignedPartitions("0,1");
        BatchTaskRunner runner = newRunner(resolver);

        // 向 3 个分区各写一条 waiting 事件
        IEntityDao<NopSysEvent> dao = daoProvider.daoFor(NopSysEvent.class);
        Timestamp now = new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis());
        for (int p : new int[]{0, 1, 2}) {
            NopSysEvent event = newEvent(dao, "order-created", p, "k-" + p, now);
            dao.saveEntity(event);
        }

        runner.execute("/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml");

        // partition=0 被处理（状态变化）；partition=1、2 仍 waiting
        Set<Integer> processed = processedPartitions(dao);
        Set<Integer> waiting = waitingPartitions(dao);
        assertTrue(processed.contains(0), "partition 0 should be processed: " + processed);
        assertTrue(waiting.contains(1), "partition 1 should remain waiting: " + waiting);
        assertTrue(waiting.contains(2), "partition 2 should remain waiting: " + waiting);
    }

    @Test
    public void testNoPartitionConfigProcessesAll() {
        // resolver 不限制（assignedPartitions 清空 → resolve 返回 null → reader 不过滤）
        PartitionResolver resolver = new PartitionResolver();
        BatchTaskRunner runner = newRunner(resolver);

        IEntityDao<NopSysEvent> dao = daoProvider.daoFor(NopSysEvent.class);
        Timestamp now = new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis());
        for (int p : new int[]{0, 1, 2}) {
            NopSysEvent event = newEvent(dao, "order-created", p, "k2-" + p, now);
            dao.saveEntity(event);
        }

        runner.execute("/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml");

        assertEquals(Set.of(0, 1, 2), processedPartitions(dao));
    }

    @Test
    public void testMultiRangePartitionProcessesUnion() {
        // resolver 负责 partition 0 和 2（多区间，验证 OR 组合）
        PartitionResolver resolver = new PartitionResolver();
        resolver.setAssignedPartitions("0,1|2,1");
        BatchTaskRunner runner = newRunner(resolver);

        IEntityDao<NopSysEvent> dao = daoProvider.daoFor(NopSysEvent.class);
        Timestamp now = new Timestamp(dao.getDbEstimatedClock().getMaxCurrentTimeMillis());
        for (int p : new int[]{0, 1, 2}) {
            NopSysEvent event = newEvent(dao, "order-created", p, "k3-" + p, now);
            dao.saveEntity(event);
        }

        runner.execute("/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml");

        Set<Integer> processed = processedPartitions(dao);
        assertTrue(processed.contains(0) && processed.contains(2), "partition 0 and 2 processed: " + processed);
        assertTrue(waitingPartitions(dao).contains(1), "partition 1 remains waiting");
    }

    private BatchTaskRunner newRunner(PartitionResolver resolver) {
        BatchTaskRunner runner = new BatchTaskRunner();
        runner.setBatchTaskManager(batchTaskManager);
        runner.setPartitionResolver(resolver);
        return runner;
    }

    private NopSysEvent newEvent(IEntityDao<NopSysEvent> dao, String topic, int partition, String bizKey, Timestamp now) {
        NopSysEvent event = dao.newEntity();
        event.setEventTopic(topic);
        event.setEventName("evt-" + bizKey);
        event.setEventHeaders("{}");
        event.setEventData("{\"id\":\"" + bizKey + "\"}");
        event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING);
        event.setScheduleTime(now);
        event.setProcessTime(now);
        event.setEventTime(now);
        event.setBizDate(now.toLocalDateTime().toLocalDate());
        event.setPartitionIndex(partition);
        event.setBizObjName("Order");
        event.setBizKey(bizKey);
        event.setRetryTimes(0);
        return event;
    }

    private Set<Integer> processedPartitions(IEntityDao<NopSysEvent> dao) {
        Set<Integer> ret = new HashSet<>();
        for (NopSysEvent e : dao.findAll()) {
            if (e.getEventStatus() != NopSysDaoConstants.SYS_EVENT_STATUS_WAITING) {
                ret.add(e.getPartitionIndex());
            }
        }
        return ret;
    }

    private Set<Integer> waitingPartitions(IEntityDao<NopSysEvent> dao) {
        Set<Integer> ret = new HashSet<>();
        for (NopSysEvent e : dao.findAll()) {
            if (e.getEventStatus() == NopSysDaoConstants.SYS_EVENT_STATUS_WAITING) {
                ret.add(e.getPartitionIndex());
            }
        }
        return ret;
    }
}
