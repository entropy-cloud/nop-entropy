
class NopJobSchedule{

  String jobScheduleId; //调度ID

  String namespaceId; //命名空间

  String groupId; //分组

  String jobName; //作业名

  String displayName; //显示名

  String description; //描述

  Integer scheduleStatus; //调度状态

  String executorKind; //执行器类型

  String jobParams; //任务参数

  Integer triggerType; //触发器类型

  String cronExpr; //CRON表达式

  Long repeatIntervalMs; //重复间隔(毫秒)

  Integer maxExecutionCount; //最大执行次数

  Timestamp minScheduleTime; //最早调度时间

  Timestamp maxScheduleTime; //最晚调度时间

  Integer misfireThresholdMs; //Misfire阈值(毫秒)

  Byte useDefaultCalendar; //使用默认日历

  String pauseCalendarSpec; //暂停日历配置

  Integer blockStrategy; //阻塞策略

  Integer timeoutSeconds; //超时时间(秒)

  String retryPolicyId; //重试策略ID

  Short partitionIndex; //分区索引

  Long fireCount; //已触发次数

  Integer activeFireCount; //活跃触发数

  Timestamp lastFireTime; //上次触发时间

  Timestamp lastEndTime; //上次结束时间

  Timestamp nextFireTime; //下次触发时间

  Integer lastFireStatus; //上次触发状态

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  Long lastDurationMs; //上次执行耗时(毫秒)

  Long totalFireCount; //总触发次数

  Long successFireCount; //成功触发次数

  Long failFireCount; //失败触发次数

}

class NopJobFire{

  String jobFireId; //触发批次ID

  String jobScheduleId; //调度ID

  String namespaceId; //命名空间

  String groupId; //分组

  String jobName; //作业名

  Integer triggerSource; //触发来源

  Timestamp scheduledFireTime; //计划触发时间

  String triggeredBy; //触发人

  Integer fireStatus; //批次状态

  String plannerInstanceId; //计划节点ID

  String dispatchInstanceId; //分发节点ID

  Timestamp startTime; //开始时间

  Timestamp endTime; //结束时间

  Long durationMs; //执行时长(毫秒)

  String jobParamsSnapshot; //参数快照

  String executorKind; //执行器类型

  String retryPolicyId; //重试策略ID

  String retryRecordId; //异步重试提交后的重试记录ID（当前NopRetryJobRetryBridge使用异步提交，此字段暂不可用）

  String errorCode; //错误码

  String errorMessage; //错误消息

  Short partitionIndex; //分区索引

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopJobSchedule jobSchedule; //调度定义

}

class NopJobTask{

  String jobTaskId; //任务ID

  String jobFireId; //批次ID

  Integer taskNo; //任务序号

  Integer taskStatus; //任务状态

  String workerInstanceId; //执行节点ID

  String workerAddress; //执行节点地址

  String taskPayload; //投递参数

  String targetHost; //目标节点地址

  Integer shardingIndex; //分片索引

  Integer shardingTotal; //总分片数

  Timestamp startTime; //开始时间

  Timestamp endTime; //结束时间

  Long durationMs; //执行时长(毫秒)

  String resultPayload; //执行结果

  String errorCode; //错误码

  String errorMessage; //错误消息

  Short partitionIndex; //分区索引

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  Integer progress; //执行进度

  String progressMessage; //进度消息

  NopJobFire jobFire; //触发批次

}
