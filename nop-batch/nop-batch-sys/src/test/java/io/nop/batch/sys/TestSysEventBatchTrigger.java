package io.nop.batch.sys;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.batch.dsl.runner.IBatchTaskRunner;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysEvent;
import io.nop.sys.dao.message.SysDaoMessageService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testConfigFile = "classpath:test.properties")
public class TestSysEventBatchTrigger extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    SysDaoMessageService nopSysDaoMessageService;

    @Inject
    IBatchTaskRunner batchTaskRunner;

    private SysDaoMessageService service;

    @BeforeEach
    public void setUp() {
        service = nopSysDaoMessageService;
        service.setAssignedPartitions(IntRangeSet.parse("0,32767"));
        service.setFetchSize(20);
        service.setMinProcessDelay(1);
        service.setLeaseTimeout(1000);
    }

    @Test
    public void testBatchConsumerProcessesDifferentPartitions() {
        List<String> processed = new ArrayList<>();
        service.subscribe("order-created", recordingConsumer(processed), null);

        service.send("order-created", request("Order", "A-100", "evt-a1"), null);
        service.send("order-created", request("Order", "B-200", "evt-b1"), null);

        batchTaskRunner.execute("/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml");

        assertEquals(Set.of("evt-a1", "evt-b1"), Set.copyOf(processed));
    }

    @Test
    public void testBatchConsumerRequeuesConsumeLater() {
        service.subscribe("order-created", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return new ConsumeLater(50);
            }
        }, null);

        service.send("order-created", request("Order", "A-100", "evt-1"), null);

        batchTaskRunner.execute("/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml");

        IEntityDao<NopSysEvent> eventDao = daoProvider.daoFor(NopSysEvent.class);
        NopSysEvent event = eventDao.findAll().get(0);
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, event.getEventStatus());
        assertEquals(1, event.getRetryTimes());
        assertNull(event.getLeaseOwner());
    }

    /**
     * C7-011（plan 2516 裁定⑧；C10-002 半；红⑧）：sys-event 触发链守卫接线断言。
     * 缺陷态（红）：non-broadcast-consumer.batch.xml 无 saveState → stateStore 不接线 →
     * (taskName,taskKey) 唯一键与启动闸门结构性旁路，nop_batch_task 零任务行（UUID 键死数据增长面）。
     * 修复后（绿）：saveState=true + 固定常量 taskKeyExpr 接线后，每次执行真实经过启动闸门/
     * 状态机写者——任务行以固定 taskKey 落库；allowStartIfComplete=true 保证 cron 重复触发可重入，
     * 且每次执行复用同一任务行（不再每执行一行死数据，增长面由 C7-003 保留策略承接）。
     */
    @Test
    public void testSysEventConsumerGuardWired_fixedTaskKeyRowWritten() {
        service.subscribe("order-created", recordingConsumer(new ArrayList<>()), null);
        service.send("order-created", request("Order", "A-100", "evt-g1"), null);

        batchTaskRunner.execute("/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml");

        // 守卫真实经过（接线验证 rule 23）：stateStore 写出固定 (taskName, taskKey) 任务行。
        // 缺陷态：无任务行（saveState 未接线）→ 断言失败即红。
        IEntityDao<io.nop.batch.dao.entity.NopBatchTask> taskDao =
                daoProvider.daoFor(io.nop.batch.dao.entity.NopBatchTask.class);
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("taskName", "sys-event.non-broadcast-consumer"));
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("taskKey", "non-broadcast"));
        List<io.nop.batch.dao.entity.NopBatchTask> rows = taskDao.findAllByQuery(query);
        assertEquals(1, rows.size(),
                "saveState wiring must persist exactly one task row with the fixed taskKey");
        assertEquals(io.nop.batch.dao.NopBatchDaoConstants.TASK_STATUS_COMPLETED, rows.get(0).getTaskStatus());

        // 重复触发（cron 0/5 形态）：allowStartIfComplete=true 允许重入且仍单活跃（同一行，无 UUID 死数据）
        service.send("order-created", request("Order", "B-200", "evt-g2"), null);
        batchTaskRunner.execute("/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml");
        assertEquals(1, taskDao.findAllByQuery(query).size(),
                "repeated cron triggers must reuse the single fixed-key task row");
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

    private ApiRequest<Map<String, Object>> request(String bizObjName, String bizKey, String id) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", id));
        io.nop.api.core.util.ApiHeaders.setSvcName(request, bizObjName);
        io.nop.api.core.util.ApiHeaders.setBizKey(request, bizKey);
        return request;
    }
}
