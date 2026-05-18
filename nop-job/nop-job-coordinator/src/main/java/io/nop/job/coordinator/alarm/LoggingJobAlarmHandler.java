package io.nop.job.coordinator.alarm;

import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingJobAlarmHandler implements IJobAlarmHandler {
    static final Logger LOG = LoggerFactory.getLogger(LoggingJobAlarmHandler.class);

    @Override
    public void onFireFailed(JobAlarmEvent event) {
        LOG.warn("nop.job.alarm.fire-failed:fireId={},scheduleId={},jobName={},errorCode={},durationMs={}",
                event.getJobFireId(), event.getJobScheduleId(), event.getJobName(),
                event.getErrorCode(), event.getDurationMs());
    }

    @Override
    public void onFireTimeout(JobAlarmEvent event) {
        LOG.warn("nop.job.alarm.fire-timeout:fireId={},scheduleId={},jobName={},durationMs={}",
                event.getJobFireId(), event.getJobScheduleId(), event.getJobName(),
                event.getDurationMs());
    }
}
