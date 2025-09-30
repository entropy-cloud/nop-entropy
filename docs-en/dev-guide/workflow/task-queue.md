# Task Queue

Example:

```java
class NopJobTask {
    String taskId;
    String jobGroup;
    String jobName;
    int status;
    Timestamp startTime;
    Timestamp finishTime;

    Timestamp cancelTime;
    Timestamp restartTime;

    Map<String, Object> jobParams;
    ErrorBean errorInfo;
    String executorHost;
    int executorPort;
    IntRangeSet partitionSet;
}

interface IJobTaskExecutor {

    void startTask(JobTaskRequest request);

    void cancelTask(String taskId);

    JobTaskState getTaskState(String taskId);
}
```

* The nop_job_task table stores all asynchronous tasks
* Task statuses: Created, Pending Execution, Running, Paused, Completed, Failed, Canceled
* Tasks can be executed in a distributed manner; therefore the executor address, executor port, execution parameters, and error information need to be recorded
* The task executor provides three APIs: start, cancel, and getState, and should also proactively push execution status to the management service
* To support parallel processing, a task has the partitionSet attribute, which marks the set of data partitions currently being processed

<!-- SOURCE_MD5:d4bcba3edb3cdd6edcb22dd958a9b3df-->
