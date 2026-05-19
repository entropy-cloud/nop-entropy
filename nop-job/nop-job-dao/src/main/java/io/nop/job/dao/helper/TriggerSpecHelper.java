package io.nop.job.dao.helper;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.type.utils.JavaGenericTypeBuilder;
import io.nop.job.api.spec.CalendarSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.core.ITriggerEvalContext;
import io.nop.job.core._NopJobCoreConstants;
import io.nop.job.dao.entity.NopJobSchedule;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

/**
 * Shared utility for converting a {@link NopJobSchedule} entity into
 * {@link TriggerSpec} and {@link ITriggerEvalContext} instances used by the
 * trigger calculation engine.
 */
public class TriggerSpecHelper {

    public static TriggerSpec toTriggerSpec(NopJobSchedule schedule) {
        TriggerSpec spec = new TriggerSpec();
        spec.setCronExpr(schedule.getCronExpr());
        spec.setRepeatInterval(defaultLong(schedule.getRepeatIntervalMs()));
        spec.setRepeatFixedDelay(schedule.getTriggerType() != null
                && schedule.getTriggerType() == _NopJobCoreConstants.TRIGGER_TYPE_FIXED_DELAY);
        spec.setMaxExecutionCount(defaultInt(schedule.getMaxExecutionCount()));
        spec.setMinScheduleTime(toTime(schedule.getMinScheduleTime()));
        spec.setMaxScheduleTime(toTime(schedule.getMaxScheduleTime()));
        spec.setMisfireThreshold(defaultInt(schedule.getMisfireThresholdMs()));
        spec.setUseDefaultCalendar(schedule.getUseDefaultCalendar() != null && schedule.getUseDefaultCalendar() == 1);
        spec.setPauseCalendars(parsePauseCalendars(schedule.getPauseCalendarSpec()));
        spec.setMaxFailedCount(0);
        return spec;
    }

    public static ITriggerEvalContext toEvalContext(NopJobSchedule schedule) {
        return new ITriggerEvalContext() {
            @Override
            public long getFireCount() {
                return defaultLong(schedule.getTotalFireCount());
            }

            @Override
            public long getLastScheduledTime() {
                return toTime(schedule.getLastFireTime());
            }

            @Override
            public long getLastEndTime() {
                return toTime(schedule.getLastEndTime());
            }

            @Override
            public long getMinScheduleTime() {
                return toTime(schedule.getMinScheduleTime());
            }

            @Override
            public long getMaxScheduleTime() {
                return toTime(schedule.getMaxScheduleTime());
            }

            @Override
            public long getMaxExecutionCount() {
                return defaultInt(schedule.getMaxExecutionCount());
            }

            @Override
            public boolean isScheduleCompleted() {
                return schedule.getScheduleStatus() != null
                        && schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED;
            }
        };
    }

    private static List<CalendarSpec> parsePauseCalendars(String json) {
        if (json == null || json.isEmpty())
            return Collections.emptyList();
        return JsonTool.parseBeanFromText(json,
                JavaGenericTypeBuilder.buildListType(CalendarSpec.class));
    }

    private static long toTime(Timestamp value) {
        return value == null ? 0L : value.getTime();
    }

    private static long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
