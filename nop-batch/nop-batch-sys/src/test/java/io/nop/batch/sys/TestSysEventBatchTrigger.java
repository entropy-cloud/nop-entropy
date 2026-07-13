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
