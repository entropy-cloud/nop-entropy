package io.nop.job.api.alarm;

public interface IJobAlarmHandler {
    void onFireFailed(JobAlarmEvent event);

    void onFireTimeout(JobAlarmEvent event);
}
