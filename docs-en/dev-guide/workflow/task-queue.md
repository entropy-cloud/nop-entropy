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
```

```java
interface IJobTaskExecutor {

    void startTask(JobTaskRequest request);

    void cancelTask(String taskId);

    JobTaskState getTaskState(String taskId);
}
```

* The nop_job_task table stores all asynchronous tasks.
* Task states: created, pending, running, paused, completed, failed, canceled.
* Asynchronous tasks require recording the executor's address, port, parameters, and error information.
* The executor provides start, cancel, getState APIs simultaneously. These should be actively reported to the management endpoint.
* To support parallel processing, tasks have a partitionSet property for marking the current data partition set being processed.