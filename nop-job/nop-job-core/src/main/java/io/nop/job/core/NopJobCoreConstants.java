package io.nop.job.core;

public interface NopJobCoreConstants extends _NopJobCoreConstants {
    String CANCEL_REASON_KILL = "kill";

    String CANCEL_REASON_SUSPEND = "suspend";

    String CANCEL_REASON_CANCEL = "cancel";

    int JOB_INSTANCE_STATUS_UNKNOWN = -1;

    // Keep the old scheduler/trigger runtime stable while Phase 2 extracts pure calculation APIs.
    int JOB_INSTANCE_STATUS_CREATED = 0;
    int JOB_INSTANCE_STATUS_SUSPENDED = 10;
    int JOB_INSTANCE_STATUS_WAITING = 20;
    int JOB_INSTANCE_STATUS_SCHEDULED = 30;
    int JOB_INSTANCE_STATUS_RUNNING = 40;
    int JOB_INSTANCE_STATUS_EXEC_SUCCESS = 50;
    int JOB_INSTANCE_STATUS_EXEC_FAILED = 60;
    int JOB_INSTANCE_STATUS_EXEC_CANCELLED = 70;
    int JOB_INSTANCE_STATUS_EXEC_TIMEOUT = 80;
    int JOB_INSTANCE_STATUS_JOB_FINISHED = 90;
    int JOB_INSTANCE_STATUS_JOB_FAILED = 100;
    int JOB_INSTANCE_STATUS_JOB_KILLED = 110;
}
