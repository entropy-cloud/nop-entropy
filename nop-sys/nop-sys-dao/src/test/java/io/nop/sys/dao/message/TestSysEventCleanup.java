package io.nop.sys.dao.message;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 审计 [P2]：nop_sys_event 只清理 PROCESSED——FAILED 行与发往无订阅者 topic 的
 * WAITING 行永久滞留，事件表无界增长拖垮轮询扫描。修复后清理覆盖 FAILED（保留期）与
 * 超长滞留的 WAITING/CLAIMED（nop.sys.event.cleanup-stale-waiting-days，默认30天）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysEventCleanup extends JunitBaseTestCase {
    private static final long DAY_MILLIS = 24L * 3600_000L;

    @Inject
    IDaoProvider daoProvider;

    private SysDaoMessageService service;

    @BeforeEach
    public void setUp() {
        service = new SysDaoMessageService();
        service.setDaoProvider(daoProvider);
        service.setAssignedPartitions(IntRangeSet.parse("0,32767"));
    }

    @Test
    public void testCleanupExpiredEventsCoversFailedAndStaleWaiting() {
        IEntityDao<NopSysEvent> dao = daoProvider.daoFor(NopSysEvent.class);

        send("cleanup-processed-old", "evt-processed-old");
        send("cleanup-failed-old", "evt-failed-old");
        send("cleanup-waiting-stale", "evt-waiting-stale");
        send("cleanup-waiting-fresh", "evt-waiting-fresh");

        long now = System.currentTimeMillis();
        updateStatus(dao, "cleanup-processed-old", NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED, now - 8 * DAY_MILLIS);
        updateStatus(dao, "cleanup-failed-old", NopSysDaoConstants.SYS_EVENT_STATUS_FAILED, now - 8 * DAY_MILLIS);
        updateStatus(dao, "cleanup-waiting-stale", NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, now - 40 * DAY_MILLIS);
        updateStatus(dao, "cleanup-waiting-fresh", NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, now);

        service.cleanupExpiredEvents();

        List<NopSysEvent> remaining = dao.findAll();
        assertEquals(1, remaining.size(), "only the fresh WAITING event should survive cleanup");
        assertEquals("cleanup-waiting-fresh", remaining.get(0).getEventTopic());
        assertEquals(NopSysDaoConstants.SYS_EVENT_STATUS_WAITING, remaining.get(0).getEventStatus());
    }

    @Test
    public void testFailedEventWithinRetentionIsKept() {
        IEntityDao<NopSysEvent> dao = daoProvider.daoFor(NopSysEvent.class);

        send("cleanup-failed-fresh", "evt-failed-fresh");
        updateStatus(dao, "cleanup-failed-fresh", NopSysDaoConstants.SYS_EVENT_STATUS_FAILED,
                System.currentTimeMillis() - DAY_MILLIS);

        service.cleanupExpiredEvents();

        List<NopSysEvent> remaining = dao.findAll();
        assertEquals(1, remaining.size(), "FAILED event within retention window must be kept for troubleshooting");
        assertNotNull(remaining.get(0));
    }

    private void send(String topic, String id) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", id));
        service.send(topic, request, null);
    }

    private void updateStatus(IEntityDao<NopSysEvent> dao, String topic, int status, long eventTime) {
        NopSysEvent event = findByTopic(dao, topic);
        assertTrue(event != null, "event for topic " + topic + " must exist before cleanup");
        event.setEventStatus(status);
        event.setEventTime(new Timestamp(eventTime));
        dao.updateEntityDirectly(event);
    }

    private NopSysEvent findByTopic(IEntityDao<NopSysEvent> dao, String topic) {
        for (NopSysEvent event : dao.findAll()) {
            if (topic.equals(event.getEventTopic()))
                return event;
        }
        return null;
    }
}
