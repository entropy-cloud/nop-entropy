package io.nop.job.coordinator.alarm;

import io.nop.job.api.alarm.IJobAlarmHandler;
import io.nop.job.api.alarm.JobAlarmEvent;

public class NoOpJobAlarmHandler implements IJobAlarmHandler {
    @Override
    public void onFireFailed(JobAlarmEvent event) {
    }

    @Override
    public void onFireTimeout(JobAlarmEvent event) {
    }
}
