package io.nop.job.dao.store;

import io.nop.api.core.beans.ErrorBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.type.utils.JavaGenericTypeBuilder;
import io.nop.job.api.JobDetail;
import io.nop.job.api.JobInstanceState;
import io.nop.job.api.spec.CalendarSpec;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.job.dao.entity.NopJobDefinition;
import io.nop.job.dao.entity.NopJobInstance;

import java.util.List;

public class JobDaoHelper {
    public static JobDetail toJobDetail(NopJobInstance entity) {
        if (entity == null) {
            return null;
        }

        JobDetail job = new JobDetail();

        // 设置JobSpec
        if (entity.getJobDefinition() != null) {
            job.setJobSpec(toJobSpec(entity.getJobDefinition()));
        }

        // 设置JobInstanceState
        JobInstanceState state = new JobInstanceState();
        state.setInstanceId(entity.getJobInstanceId());
        state.setJobDefId(entity.getJobDefId());
        state.setJobGroup(entity.getJobGroup());
        state.setJobName(entity.getJobName());
        state.setJobVersion(entity.getVersion() != null ? entity.getVersion() : 0L);

        // 转换jobParams
        if (entity.getJobParams() != null) {
            // 假设jobParams是JSON字符串，需要转换为Map
            // 这里简化处理，实际应根据具体格式解析
            state.setJobParams(entity.getJobParamsComponent().get_jsonMap());
        }

        // 转换时间字段
        state.setScheduledExecTime(entity.getScheduledExecTime() != null ?
                entity.getScheduledExecTime().getTime() : 0L);
        state.setExecBeginTime(entity.getExecBeginTime() != null ?
                entity.getExecBeginTime().getTime() : 0L);
        state.setExecEndTime(entity.getExecEndTime() != null ?
                entity.getExecEndTime().getTime() : 0L);

        state.setExecCount(entity.getExecCount() != null ? entity.getExecCount() : 0L);
        state.setExecFailCount(entity.getTotalFailCount() != null ? entity.getTotalFailCount() : 0L);

        // 转换错误信息
        if (entity.getErrCode() != null || entity.getErrMsg() != null) {
            ErrorBean error = new ErrorBean();
            error.setErrorCode(entity.getErrCode());
            error.setDescription(entity.getErrMsg());
            state.setExecError(error);
        }

        state.setInstanceStatus(entity.getStatus() != null ? entity.getStatus() : 0);
        state.setLastInstanceId(entity.getLastJobInstanceId());
        state.setOnceTask(entity.getOnceTask() != null ? entity.getOnceTask() : false);
        state.setManualFire(entity.getManualFire() != null ? entity.getManualFire() : false);
        state.setFiredBy(entity.getFiredBy());
        state.setChangeVersion(entity.getVersion() != null ? entity.getVersion() : 0L);

        job.setInstanceState(state);

        return job;
    }

    public static JobSpec toJobSpec(NopJobDefinition jobDef) {
        if (jobDef == null) {
            return null;
        }

        JobSpec jobSpec = new JobSpec();
        jobSpec.setJobName(jobDef.getJobName());
        jobSpec.setJobGroup(jobDef.getJobGroup());

        // 转换jobParams
        if (jobDef.getJobParams() != null) {
            jobSpec.setJobParams(jobDef.getJobParamsComponent().get_jsonMap());
        }

        jobSpec.setJobInvoker(jobDef.getJobInvoker());
        jobSpec.setDescription(jobDef.getDescription());

        jobSpec.setTriggerSpec(createTriggerSpec(jobDef));

        return jobSpec;
    }

    public static TriggerSpec createTriggerSpec(NopJobDefinition jobDef) {
        if (jobDef == null) {
            return null;
        }

        TriggerSpec triggerSpec = new TriggerSpec();
        triggerSpec.setCronExpr(jobDef.getCronExpr());
        triggerSpec.setRepeatInterval(jobDef.getRepeatInterval() != null ? jobDef.getRepeatInterval() : 0L);
        triggerSpec.setRepeatFixedDelay(jobDef.getIsFixedDelay() != null && jobDef.getIsFixedDelay() == 1);
        triggerSpec.setMaxExecutionCount(jobDef.getMaxExecutionCount() != null ? jobDef.getMaxExecutionCount() : 0L);

        // 转换时间字段
        triggerSpec.setMinScheduleTime(jobDef.getMinScheduleTime() != null ?
                jobDef.getMinScheduleTime().getTime() : 0L);
        triggerSpec.setMaxScheduleTime(jobDef.getMaxScheduleTime() != null ?
                jobDef.getMaxScheduleTime().getTime() : 0L);

        triggerSpec.setMisfireThreshold(jobDef.getMisfireThreshold() != null ? jobDef.getMisfireThreshold() : 0L);
        triggerSpec.setUseDefaultCalendar(jobDef.getIsUseDefaultCalendar() != null && jobDef.getIsUseDefaultCalendar() == 1);
        triggerSpec.setMaxFailedCount(jobDef.getMaxFailedCount() != null ? jobDef.getMaxFailedCount() : 0);

        // 注意：pauseCalendars需要从字符串解析为List<CalendarSpec>
        triggerSpec.setPauseCalendars(parseCalendars(jobDef.getPauseCalendars()));

        return triggerSpec;
    }

    public static List<CalendarSpec> parseCalendars(String text) {
        if (text == null || text.isEmpty())
            return null;
        return JsonTool.parseBeanFromText(text, JavaGenericTypeBuilder.buildListType(CalendarSpec.class));
    }
}