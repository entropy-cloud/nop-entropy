package io.nop.job.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.job.biz.INopJobScheduleBiz;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_ALREADY_ARCHIVED;
import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION;
import static io.nop.job.service.NopJobErrors.ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopJobScheduleBizModel extends JunitBaseTestCase {
    private static final int SCHEDULE_STATUS_DISABLED = 0;
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int SCHEDULE_STATUS_PAUSED = 20;
    private static final int SCHEDULE_STATUS_ARCHIVED = 40;
    private static final int TRIGGER_TYPE_FIXED_RATE = 2;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopJobScheduleBiz scheduleBiz;

    @Test
    public void testDisablePauseAndArchiveSchedule() {
        NopJobSchedule disabled = saveSchedule(newSchedule("schedule-disable", "job-disable"));
        scheduleBiz.disableSchedule(disabled.getJobScheduleId(), newContext());
        assertEquals(SCHEDULE_STATUS_DISABLED, loadSchedule(disabled.getJobScheduleId()).getScheduleStatus());

        NopJobSchedule paused = saveSchedule(newSchedule("schedule-pause", "job-pause"));
        scheduleBiz.pauseSchedule(paused.getJobScheduleId(), newContext());
        assertEquals(SCHEDULE_STATUS_PAUSED, loadSchedule(paused.getJobScheduleId()).getScheduleStatus());

        NopJobSchedule archived = saveSchedule(newSchedule("schedule-archive", "job-archive"));
        scheduleBiz.archiveSchedule(archived.getJobScheduleId(), newContext());

        NopJobSchedule savedArchived = loadSchedule(archived.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_ARCHIVED, savedArchived.getScheduleStatus());
        assertNull(savedArchived.getNextFireTime());
    }

    @Test
    public void testDisableScheduleAllowsPausedAndIsIdempotentForDisabled() {
        NopJobSchedule paused = newSchedule("schedule-disable-paused", "job-disable-paused");
        paused.setScheduleStatus(SCHEDULE_STATUS_PAUSED);
        Timestamp pausedNextFireTime = new Timestamp(System.currentTimeMillis() + 20_000L);
        paused.setNextFireTime(pausedNextFireTime);
        saveSchedule(paused);

        scheduleBiz.disableSchedule(paused.getJobScheduleId(), newContext());

        NopJobSchedule savedPaused = loadSchedule(paused.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_DISABLED, savedPaused.getScheduleStatus());
        assertEquals(pausedNextFireTime, savedPaused.getNextFireTime());

        NopJobSchedule disabled = newSchedule("schedule-disable-idempotent", "job-disable-idempotent");
        disabled.setScheduleStatus(SCHEDULE_STATUS_DISABLED);
        Timestamp disabledNextFireTime = new Timestamp(System.currentTimeMillis() + 30_000L);
        disabled.setNextFireTime(disabledNextFireTime);
        saveSchedule(disabled);

        scheduleBiz.disableSchedule(disabled.getJobScheduleId(), newContext());

        NopJobSchedule savedDisabled = loadSchedule(disabled.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_DISABLED, savedDisabled.getScheduleStatus());
        assertEquals(disabledNextFireTime, savedDisabled.getNextFireTime());
    }

    @Test
    public void testDisableScheduleRejectedForCompletedAndArchived() {
        NopJobSchedule completed = newSchedule("schedule-disable-completed", "job-disable-completed");
        completed.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        saveSchedule(completed);

        NopException completedError = assertThrows(NopException.class,
                () -> scheduleBiz.disableSchedule(completed.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), completedError.getErrorCode());

        NopJobSchedule archived = newSchedule("schedule-disable-archived", "job-disable-archived");
        archived.setScheduleStatus(SCHEDULE_STATUS_ARCHIVED);
        saveSchedule(archived);

        NopException archivedError = assertThrows(NopException.class,
                () -> scheduleBiz.disableSchedule(archived.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_ALREADY_ARCHIVED.getErrorCode(), archivedError.getErrorCode());
    }

    @Test
    public void testPauseScheduleIsIdempotentAndRejectedForDisabledCompletedArchived() {
        NopJobSchedule paused = newSchedule("schedule-pause-idempotent", "job-pause-idempotent");
        paused.setScheduleStatus(SCHEDULE_STATUS_PAUSED);
        Timestamp pausedNextFireTime = new Timestamp(System.currentTimeMillis() + 25_000L);
        paused.setNextFireTime(pausedNextFireTime);
        saveSchedule(paused);

        scheduleBiz.pauseSchedule(paused.getJobScheduleId(), newContext());

        NopJobSchedule savedPaused = loadSchedule(paused.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_PAUSED, savedPaused.getScheduleStatus());
        assertEquals(pausedNextFireTime, savedPaused.getNextFireTime());

        NopJobSchedule disabled = newSchedule("schedule-pause-disabled", "job-pause-disabled");
        disabled.setScheduleStatus(SCHEDULE_STATUS_DISABLED);
        saveSchedule(disabled);

        NopException disabledError = assertThrows(NopException.class,
                () -> scheduleBiz.pauseSchedule(disabled.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), disabledError.getErrorCode());

        NopJobSchedule completed = newSchedule("schedule-pause-completed", "job-pause-completed");
        completed.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        saveSchedule(completed);

        NopException completedError = assertThrows(NopException.class,
                () -> scheduleBiz.pauseSchedule(completed.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), completedError.getErrorCode());

        NopJobSchedule archived = newSchedule("schedule-pause-archived", "job-pause-archived");
        archived.setScheduleStatus(SCHEDULE_STATUS_ARCHIVED);
        saveSchedule(archived);

        NopException archivedError = assertThrows(NopException.class,
                () -> scheduleBiz.pauseSchedule(archived.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_ALREADY_ARCHIVED.getErrorCode(), archivedError.getErrorCode());
    }

    @Test
    public void testArchiveScheduleAllowsCompletedAndIsIdempotent() {
        NopJobSchedule completed = newSchedule("schedule-archive-completed", "job-archive-completed");
        completed.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        completed.setNextFireTime(new Timestamp(System.currentTimeMillis() + 40_000L));
        saveSchedule(completed);

        scheduleBiz.archiveSchedule(completed.getJobScheduleId(), newContext());

        NopJobSchedule savedCompleted = loadSchedule(completed.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_ARCHIVED, savedCompleted.getScheduleStatus());
        assertNull(savedCompleted.getNextFireTime());

        scheduleBiz.archiveSchedule(savedCompleted.getJobScheduleId(), newContext());

        NopJobSchedule archivedAgain = loadSchedule(savedCompleted.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_ARCHIVED, archivedAgain.getScheduleStatus());
        assertNull(archivedAgain.getNextFireTime());
    }

    @Test
    public void testEnableScheduleRecalculatesNextFireTimeWhenMissing() {
        NopJobSchedule schedule = newSchedule("schedule-enable", "job-enable");
        schedule.setScheduleStatus(SCHEDULE_STATUS_DISABLED);
        schedule.setNextFireTime(null);
        saveSchedule(schedule);

        long before = System.currentTimeMillis();
        scheduleBiz.enableSchedule(schedule.getJobScheduleId(), newContext());
        long after = System.currentTimeMillis();

        NopJobSchedule saved = loadSchedule(schedule.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_ENABLED, saved.getScheduleStatus());
        assertNotNull(saved.getNextFireTime());
        assertTrue(saved.getNextFireTime().getTime() > before);
        assertTrue(saved.getNextFireTime().getTime() <= after + schedule.getRepeatIntervalMs() + 2000L);
    }

    @Test
    public void testResumeScheduleRecalculatesNextFireTime() {
        NopJobSchedule schedule = newSchedule("schedule-resume", "job-resume");
        schedule.setScheduleStatus(SCHEDULE_STATUS_PAUSED);
        schedule.setLastFireTime(new Timestamp(System.currentTimeMillis() - 60_000));
        schedule.setNextFireTime(new Timestamp(System.currentTimeMillis() - 10_000));
        saveSchedule(schedule);

        long before = System.currentTimeMillis();
        scheduleBiz.resumeSchedule(schedule.getJobScheduleId(), newContext());
        long after = System.currentTimeMillis();

        NopJobSchedule saved = loadSchedule(schedule.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_ENABLED, saved.getScheduleStatus());
        assertNotNull(saved.getNextFireTime());
        assertTrue(saved.getNextFireTime().getTime() > before);
        assertTrue(saved.getNextFireTime().getTime() <= after + schedule.getRepeatIntervalMs() + 2000L);
    }

    @Test
    public void testArchivedScheduleCannotBeEnabledOrResumed() {
        NopJobSchedule schedule = newSchedule("schedule-archived-guard", "job-archived-guard");
        schedule.setScheduleStatus(SCHEDULE_STATUS_ARCHIVED);
        schedule.setNextFireTime(null);
        saveSchedule(schedule);

        NopException enableError = assertThrows(NopException.class,
                () -> scheduleBiz.enableSchedule(schedule.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_ALREADY_ARCHIVED.getErrorCode(), enableError.getErrorCode());

        NopException resumeError = assertThrows(NopException.class,
                () -> scheduleBiz.resumeSchedule(schedule.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_ALREADY_ARCHIVED.getErrorCode(), resumeError.getErrorCode());
    }

    @Test
    public void testEnableScheduleRejectedUnlessDisabled() {
        NopJobSchedule enabled = saveSchedule(newSchedule("schedule-enable-enabled", "job-enable-enabled"));

        NopException enabledError = assertThrows(NopException.class,
                () -> scheduleBiz.enableSchedule(enabled.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), enabledError.getErrorCode());

        NopJobSchedule paused = newSchedule("schedule-enable-paused", "job-enable-paused");
        paused.setScheduleStatus(SCHEDULE_STATUS_PAUSED);
        saveSchedule(paused);

        NopException pausedError = assertThrows(NopException.class,
                () -> scheduleBiz.enableSchedule(paused.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), pausedError.getErrorCode());

        NopJobSchedule completed = newSchedule("schedule-enable-completed", "job-enable-completed");
        completed.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        saveSchedule(completed);

        NopException completedError = assertThrows(NopException.class,
                () -> scheduleBiz.enableSchedule(completed.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), completedError.getErrorCode());
    }

    @Test
    public void testResumeScheduleRejectedUnlessPaused() {
        NopJobSchedule enabled = saveSchedule(newSchedule("schedule-resume-enabled", "job-resume-enabled"));

        NopException enabledError = assertThrows(NopException.class,
                () -> scheduleBiz.resumeSchedule(enabled.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), enabledError.getErrorCode());

        NopJobSchedule disabled = newSchedule("schedule-resume-disabled", "job-resume-disabled");
        disabled.setScheduleStatus(SCHEDULE_STATUS_DISABLED);
        saveSchedule(disabled);

        NopException disabledError = assertThrows(NopException.class,
                () -> scheduleBiz.resumeSchedule(disabled.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), disabledError.getErrorCode());

        NopJobSchedule completed = newSchedule("schedule-resume-completed", "job-resume-completed");
        completed.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        saveSchedule(completed);

        NopException completedError = assertThrows(NopException.class,
                () -> scheduleBiz.resumeSchedule(completed.getJobScheduleId(), newContext()));
        assertEquals(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION.getErrorCode(), completedError.getErrorCode());
    }

    @Test
    public void testTriggerNowCreatesManualFireWithoutMovingScheduleCursor() {
        long now = System.currentTimeMillis();
        NopJobSchedule schedule = newSchedule("schedule-trigger-now", "job-trigger-now");
        schedule.setNextFireTime(new Timestamp(now + 60_000L));
        schedule.setLastFireTime(new Timestamp(now - 60_000L));
        schedule.setFireCount(2L);
        saveSchedule(schedule);

        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName("alice");

        scheduleBiz.triggerNow(schedule.getJobScheduleId(), Map.of("k", "override", "x", 1), context);

        NopJobSchedule saved = loadSchedule(schedule.getJobScheduleId());
        assertEquals(3L, saved.getFireCount());
        assertEquals(1, saved.getActiveFireCount());
        assertEquals(schedule.getNextFireTime(), saved.getNextFireTime());
        assertEquals(schedule.getLastFireTime(), saved.getLastFireTime());

        NopJobFire fire = findOnlyFire(schedule.getJobScheduleId());
        Map<String, Object> params = JsonTool.parseMap(fire.getJobParamsSnapshot());
        assertEquals(_NopJobCoreConstants.TRIGGER_SOURCE_MANUAL, fire.getTriggerSource());
        assertEquals(_NopJobCoreConstants.FIRE_STATUS_WAITING, fire.getFireStatus());
        assertEquals("alice", fire.getTriggeredBy());
        assertEquals("override", params.get("k"));
        assertEquals(1, params.get("x"));
    }

    @Test
    public void testTriggerNowUsesScheduleParamsWhenOverrideMissing() {
        NopJobSchedule schedule = saveSchedule(newSchedule("schedule-trigger-default", "job-trigger-default"));

        scheduleBiz.triggerNow(schedule.getJobScheduleId(), null, newContext());

        NopJobFire fire = findOnlyFire(schedule.getJobScheduleId());
        assertEquals("v", JsonTool.parseMap(fire.getJobParamsSnapshot()).get("k"));
    }

    @Test
    public void testTriggerNowRejectedForArchivedAndCompletedSchedule() {
        NopJobSchedule archived = newSchedule("schedule-trigger-archived", "job-trigger-archived");
        archived.setScheduleStatus(SCHEDULE_STATUS_ARCHIVED);
        saveSchedule(archived);

        NopException archivedError = assertThrows(NopException.class,
                () -> scheduleBiz.triggerNow(archived.getJobScheduleId(), null, newContext()));
        assertEquals(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED.getErrorCode(), archivedError.getErrorCode());

        NopJobSchedule completed = newSchedule("schedule-trigger-completed", "job-trigger-completed");
        completed.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
        saveSchedule(completed);

        NopException completedError = assertThrows(NopException.class,
                () -> scheduleBiz.triggerNow(completed.getJobScheduleId(), null, newContext()));
        assertEquals(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED.getErrorCode(), completedError.getErrorCode());
    }

    private IServiceContext newContext() {
        return new ServiceContextImpl();
    }

    private NopJobSchedule saveSchedule(NopJobSchedule schedule) {
        if (schedule.getJobParams() == null) {
            schedule.setJobParams(JsonTool.stringify(Map.of("k", "v")));
        }
        daoProvider.daoFor(NopJobSchedule.class).saveEntityDirectly(schedule);
        return schedule;
    }

    private NopJobSchedule loadSchedule(String id) {
        return daoProvider.daoFor(NopJobSchedule.class).getEntityById(id);
    }

    private NopJobFire findOnlyFire(String scheduleId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("jobScheduleId", scheduleId));
        NopJobFire fire = daoProvider.daoFor(NopJobFire.class).findFirstByQuery(query);
        assertNotNull(fire);
        return fire;
    }

    private NopJobSchedule newSchedule(String id, String jobName) {
        long now = System.currentTimeMillis();

        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobScheduleId(id);
        schedule.setNamespaceId("default");
        schedule.setGroupId("default");
        schedule.setJobName(jobName);
        schedule.setDisplayName(jobName);
        schedule.setScheduleStatus(SCHEDULE_STATUS_ENABLED);
        schedule.setExecutorKind("testInvoker");
        schedule.setJobParams(JsonTool.stringify(Map.of("k", "v")));
        schedule.setTriggerType(TRIGGER_TYPE_FIXED_RATE);
        schedule.setRepeatIntervalMs(1000L);
        schedule.setPartitionIndex((short) 1);
        schedule.setFireCount(0L);
        schedule.setActiveFireCount(0);
        schedule.setNextFireTime(new Timestamp(now + 1000L));
        schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_SUCCESS);
        schedule.setVersion(0L);
        schedule.setCreatedBy("test");
        schedule.setCreateTime(new Timestamp(now));
        schedule.setUpdatedBy("test");
        schedule.setUpdateTime(new Timestamp(now));
        return schedule;
    }

    @Test
    public void testDisableSchedulePreservesEngineCountersOnVersionConflict() {
        NopJobSchedule schedule = newSchedule("schedule-ar22", "job-ar22");
        schedule.setActiveFireCount(3);
        schedule.setFireCount(10L);
        schedule.setTotalFireCount(8L);
        schedule.setSuccessFireCount(5L);
        schedule.setFailFireCount(3L);
        saveSchedule(schedule);

        @SuppressWarnings("unchecked")
        IOrmEntityDao<NopJobSchedule> schedDao =
                (IOrmEntityDao<NopJobSchedule>) daoProvider.daoFor(NopJobSchedule.class);

        NopJobSchedule concurrent = schedDao.requireEntityById(schedule.getJobScheduleId());
        concurrent.setActiveFireCount(5);
        concurrent.setFireCount(15L);
        concurrent.setTotalFireCount(12L);
        concurrent.setSuccessFireCount(8L);
        concurrent.setFailFireCount(4L);
        schedDao.updateEntityDirectly(concurrent);

        scheduleBiz.disableSchedule(schedule.getJobScheduleId(), newContext());

        NopJobSchedule saved = loadSchedule(schedule.getJobScheduleId());
        assertEquals(SCHEDULE_STATUS_DISABLED, saved.getScheduleStatus());
        assertEquals(5, saved.getActiveFireCount(),
                "Engine field activeFireCount should be preserved from concurrent update");
        assertEquals(15L, saved.getFireCount(),
                "Engine field fireCount should be preserved from concurrent update");
        assertEquals(12L, saved.getTotalFireCount(),
                "Engine field totalFireCount should be preserved from concurrent update");
    }
}
