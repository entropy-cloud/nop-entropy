
class NopJobDefinition{

  String sid; //SID

  String displayName; //显示名

  String jobName; //任务名

  String jobGroup; //任务组

  String jobParams; //任务参数

  String jobInvoker; //任务执行函数

  String description; //任务描述

  Integer status; //任务状态

  String cronExpr; //定时表达式

  Integer repeatInterval; //定时执行间隔

  Byte isFixedDelay; //是否固定延时

  Integer maxExecutionCount; //最多执行次数

  Timestamp minScheduleTime; //最近调度时间

  Timestamp maxScheduleTime; //最大调度时间

  Integer misfireThreshold; //超时阈值

  Integer maxFailedCount; //最大允许失败次数

  Integer maxConsecFailedCount; //最大允许连续失败次数

  Byte isUseDefaultCalendar; //使用系统内置日历

  String pauseCalendars; //暂停日历

  Short partitionIndex; //分区索引

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopJobInstance{

  String jobInstanceId; //任务实例ID

  String jobDefId; //任务定义ID

  String jobName; //任务名

  String jobGroup; //任务组

  String jobParams; //任务参数

  String jobInvoker; //任务执行函数

  Integer status; //任务状态

  Timestamp scheduledExecTime; //调度执行时间

  Long execCount; //执行次数

  Timestamp execBeginTime; //本次执行开始时间

  Timestamp execEndTime; //本次执行完成时间

  Boolean onceTask; //是否只执行一次

  Boolean manualFire; //是否手工触发

  String firedBy; //触发执行的用户

  Integer consecutiveFailCount; //连续失败次数

  Integer totalFailCount; //总失败次数

  String errCode; //错误码

  String errMsg; //错误消息

  String lastJobInstanceId; //上次任务实例ID

  Short partitionIndex; //分区索引

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopJobDefinition jobDefinition; //作业计划

  NopJobInstanceHis lastJobInstance; //上次执行实例

}

class NopJobInstanceHis{

  String jobInstanceId; //任务实例ID

  String jobDefId; //任务定义ID

  String jobName; //任务名

  String jobGroup; //任务组

  String jobParams; //任务参数

  String jobInvoker; //任务执行函数

  Integer status; //任务状态

  Timestamp scheduledExecTime; //调度执行时间

  Long execCount; //执行次数

  Timestamp execBeginTime; //本次执行开始时间

  Timestamp execEndTime; //本次执行完成时间

  Boolean onceTask; //是否只执行一次

  Boolean manualFire; //是否手工触发

  String firedBy; //触发执行的用户

  Integer consecutiveFailCount; //连续失败次数

  Integer totalFailCount; //总失败次数

  String errCode; //错误码

  String errMsg; //错误消息

  String lastJobInstanceId; //上次任务实例ID

  Short partitionIndex; //分区索引

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopJobDefinition jobDefinition; //作业计划

  NopJobInstanceHis lastJobInstance; //上次执行实例

}

class NopJobAssignment{

  String serverId; //服务实例ID

  String assignment; //任务分配

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}
