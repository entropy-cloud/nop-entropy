# 任务队列

示例：

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

* nop\_job\_task表中保存了所有执行的异步任务
* 任务状态：已创建、待执行、执行中、已暂停、已结束、已失败、已取消
* 任务可以被分布式执行，因此需要记录执行者地址，执行者端口，执行参数，错误信息
* 任务执行者提供 start, cancel, getState这三个api，同时应该主动向管理端上传执行状态。
* 为了支持并行处理，任务具有partitionSet属性，用于标记当前所处理的数据分区集合
