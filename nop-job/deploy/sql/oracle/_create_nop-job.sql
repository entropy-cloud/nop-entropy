
CREATE TABLE nop_job_schedule(
  JOB_SCHEDULE_ID VARCHAR2(32) NOT NULL ,
  NAMESPACE_ID VARCHAR2(50)  ,
  GROUP_ID VARCHAR2(100)  ,
  JOB_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  DESCRIPTION VARCHAR2(4000)  ,
  SCHEDULE_STATUS INTEGER NOT NULL ,
  EXECUTOR_KIND VARCHAR2(50)  ,
  JOB_PARAMS VARCHAR2(4000)  ,
  TRIGGER_TYPE INTEGER  ,
  CRON_EXPR VARCHAR2(100)  ,
  REPEAT_INTERVAL_MS NUMBER(20)  ,
  MAX_EXECUTION_COUNT INTEGER  ,
  MIN_SCHEDULE_TIME TIMESTAMP  ,
  MAX_SCHEDULE_TIME TIMESTAMP  ,
  MISFIRE_THRESHOLD_MS INTEGER  ,
  USE_DEFAULT_CALENDAR SMALLINT default 0   ,
  PAUSE_CALENDAR_SPEC VARCHAR2(4000)  ,
  BLOCK_STRATEGY INTEGER  ,
  TIMEOUT_SECONDS INTEGER  ,
  RETRY_POLICY_ID VARCHAR2(32)  ,
  PARTITION_INDEX SMALLINT NOT NULL ,
  FIRE_COUNT NUMBER(20) default 0  NOT NULL ,
  ACTIVE_FIRE_COUNT INTEGER default 0  NOT NULL ,
  LAST_FIRE_TIME TIMESTAMP  ,
  LAST_END_TIME TIMESTAMP  ,
  NEXT_FIRE_TIME TIMESTAMP  ,
  LAST_FIRE_STATUS INTEGER  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  LAST_DURATION_MS NUMBER(20)  ,
  TOTAL_FIRE_COUNT NUMBER(20) default 0   ,
  SUCCESS_FIRE_COUNT NUMBER(20) default 0   ,
  FAIL_FIRE_COUNT NUMBER(20) default 0   ,
  constraint PK_nop_job_schedule primary key (JOB_SCHEDULE_ID)
);

CREATE TABLE nop_job_fire(
  JOB_FIRE_ID VARCHAR2(32) NOT NULL ,
  JOB_SCHEDULE_ID VARCHAR2(32) NOT NULL ,
  NAMESPACE_ID VARCHAR2(50)  ,
  GROUP_ID VARCHAR2(100)  ,
  JOB_NAME VARCHAR2(100)  ,
  TRIGGER_SOURCE INTEGER  ,
  SCHEDULED_FIRE_TIME TIMESTAMP NOT NULL ,
  TRIGGERED_BY VARCHAR2(50)  ,
  FIRE_STATUS INTEGER NOT NULL ,
  PLANNER_INSTANCE_ID VARCHAR2(100)  ,
  DISPATCH_INSTANCE_ID VARCHAR2(100)  ,
  START_TIME TIMESTAMP  ,
  END_TIME TIMESTAMP  ,
  DURATION_MS NUMBER(20)  ,
  JOB_PARAMS_SNAPSHOT VARCHAR2(4000)  ,
  EXECUTOR_KIND VARCHAR2(50)  ,
  RETRY_POLICY_ID VARCHAR2(32)  ,
  RETRY_RECORD_ID VARCHAR2(32)  ,
  ERROR_CODE VARCHAR2(200)  ,
  ERROR_MESSAGE VARCHAR2(1000)  ,
  PARTITION_INDEX SMALLINT NOT NULL ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_job_fire primary key (JOB_FIRE_ID)
);

CREATE TABLE nop_job_task(
  JOB_TASK_ID VARCHAR2(32) NOT NULL ,
  JOB_FIRE_ID VARCHAR2(32) NOT NULL ,
  TASK_NO INTEGER default 1  NOT NULL ,
  TASK_STATUS INTEGER NOT NULL ,
  WORKER_INSTANCE_ID VARCHAR2(100)  ,
  WORKER_ADDRESS VARCHAR2(200)  ,
  TASK_PAYLOAD VARCHAR2(4000)  ,
  START_TIME TIMESTAMP  ,
  END_TIME TIMESTAMP  ,
  DURATION_MS NUMBER(20)  ,
  RESULT_PAYLOAD VARCHAR2(4000)  ,
  ERROR_CODE VARCHAR2(200)  ,
  ERROR_MESSAGE VARCHAR2(1000)  ,
  PARTITION_INDEX SMALLINT NOT NULL ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  PROGRESS INTEGER  ,
  PROGRESS_MESSAGE VARCHAR2(500)  ,
  constraint PK_nop_job_task primary key (JOB_TASK_ID)
);


      COMMENT ON TABLE nop_job_schedule IS '调度定义';
                
      COMMENT ON COLUMN nop_job_schedule.JOB_SCHEDULE_ID IS '调度ID';
                    
      COMMENT ON COLUMN nop_job_schedule.NAMESPACE_ID IS '命名空间';
                    
      COMMENT ON COLUMN nop_job_schedule.GROUP_ID IS '分组';
                    
      COMMENT ON COLUMN nop_job_schedule.JOB_NAME IS '作业名';
                    
      COMMENT ON COLUMN nop_job_schedule.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_job_schedule.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_job_schedule.SCHEDULE_STATUS IS '调度状态';
                    
      COMMENT ON COLUMN nop_job_schedule.EXECUTOR_KIND IS '执行器类型';
                    
      COMMENT ON COLUMN nop_job_schedule.JOB_PARAMS IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_schedule.TRIGGER_TYPE IS '触发器类型';
                    
      COMMENT ON COLUMN nop_job_schedule.CRON_EXPR IS 'CRON表达式';
                    
      COMMENT ON COLUMN nop_job_schedule.REPEAT_INTERVAL_MS IS '重复间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.MAX_EXECUTION_COUNT IS '最大执行次数';
                    
      COMMENT ON COLUMN nop_job_schedule.MIN_SCHEDULE_TIME IS '最早调度时间';
                    
      COMMENT ON COLUMN nop_job_schedule.MAX_SCHEDULE_TIME IS '最晚调度时间';
                    
      COMMENT ON COLUMN nop_job_schedule.MISFIRE_THRESHOLD_MS IS 'Misfire阈值(毫秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.USE_DEFAULT_CALENDAR IS '使用默认日历';
                    
      COMMENT ON COLUMN nop_job_schedule.PAUSE_CALENDAR_SPEC IS '暂停日历配置';
                    
      COMMENT ON COLUMN nop_job_schedule.BLOCK_STRATEGY IS '阻塞策略';
                    
      COMMENT ON COLUMN nop_job_schedule.TIMEOUT_SECONDS IS '超时时间(秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.RETRY_POLICY_ID IS '重试策略ID';
                    
      COMMENT ON COLUMN nop_job_schedule.PARTITION_INDEX IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_schedule.FIRE_COUNT IS '已触发次数';
                    
      COMMENT ON COLUMN nop_job_schedule.ACTIVE_FIRE_COUNT IS '活跃触发数';
                    
      COMMENT ON COLUMN nop_job_schedule.LAST_FIRE_TIME IS '上次触发时间';
                    
      COMMENT ON COLUMN nop_job_schedule.LAST_END_TIME IS '上次结束时间';
                    
      COMMENT ON COLUMN nop_job_schedule.NEXT_FIRE_TIME IS '下次触发时间';
                    
      COMMENT ON COLUMN nop_job_schedule.LAST_FIRE_STATUS IS '上次触发状态';
                    
      COMMENT ON COLUMN nop_job_schedule.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_schedule.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_job_schedule.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_schedule.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_job_schedule.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_schedule.REMARK IS '备注';
                    
      COMMENT ON COLUMN nop_job_schedule.LAST_DURATION_MS IS '上次执行耗时(毫秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.TOTAL_FIRE_COUNT IS '总触发次数';
                    
      COMMENT ON COLUMN nop_job_schedule.SUCCESS_FIRE_COUNT IS '成功触发次数';
                    
      COMMENT ON COLUMN nop_job_schedule.FAIL_FIRE_COUNT IS '失败触发次数';
                    
      COMMENT ON TABLE nop_job_fire IS '触发批次';
                
      COMMENT ON COLUMN nop_job_fire.JOB_FIRE_ID IS '触发批次ID';
                    
      COMMENT ON COLUMN nop_job_fire.JOB_SCHEDULE_ID IS '调度ID';
                    
      COMMENT ON COLUMN nop_job_fire.NAMESPACE_ID IS '命名空间';
                    
      COMMENT ON COLUMN nop_job_fire.GROUP_ID IS '分组';
                    
      COMMENT ON COLUMN nop_job_fire.JOB_NAME IS '作业名';
                    
      COMMENT ON COLUMN nop_job_fire.TRIGGER_SOURCE IS '触发来源';
                    
      COMMENT ON COLUMN nop_job_fire.SCHEDULED_FIRE_TIME IS '计划触发时间';
                    
      COMMENT ON COLUMN nop_job_fire.TRIGGERED_BY IS '触发人';
                    
      COMMENT ON COLUMN nop_job_fire.FIRE_STATUS IS '批次状态';
                    
      COMMENT ON COLUMN nop_job_fire.PLANNER_INSTANCE_ID IS '计划节点ID';
                    
      COMMENT ON COLUMN nop_job_fire.DISPATCH_INSTANCE_ID IS '分发节点ID';
                    
      COMMENT ON COLUMN nop_job_fire.START_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_job_fire.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_job_fire.DURATION_MS IS '执行时长(毫秒)';
                    
      COMMENT ON COLUMN nop_job_fire.JOB_PARAMS_SNAPSHOT IS '参数快照';
                    
      COMMENT ON COLUMN nop_job_fire.EXECUTOR_KIND IS '执行器类型';
                    
      COMMENT ON COLUMN nop_job_fire.RETRY_POLICY_ID IS '重试策略ID';
                    
      COMMENT ON COLUMN nop_job_fire.RETRY_RECORD_ID IS '重试记录ID';
                    
      COMMENT ON COLUMN nop_job_fire.ERROR_CODE IS '错误码';
                    
      COMMENT ON COLUMN nop_job_fire.ERROR_MESSAGE IS '错误消息';
                    
      COMMENT ON COLUMN nop_job_fire.PARTITION_INDEX IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_fire.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_fire.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_job_fire.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_fire.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_job_fire.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_fire.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_job_task IS '执行任务';
                
      COMMENT ON COLUMN nop_job_task.JOB_TASK_ID IS '任务ID';
                    
      COMMENT ON COLUMN nop_job_task.JOB_FIRE_ID IS '批次ID';
                    
      COMMENT ON COLUMN nop_job_task.TASK_NO IS '任务序号';
                    
      COMMENT ON COLUMN nop_job_task.TASK_STATUS IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_task.WORKER_INSTANCE_ID IS '执行节点ID';
                    
      COMMENT ON COLUMN nop_job_task.WORKER_ADDRESS IS '执行节点地址';
                    
      COMMENT ON COLUMN nop_job_task.TASK_PAYLOAD IS '投递参数';
                    
      COMMENT ON COLUMN nop_job_task.START_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_job_task.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_job_task.DURATION_MS IS '执行时长(毫秒)';
                    
      COMMENT ON COLUMN nop_job_task.RESULT_PAYLOAD IS '执行结果';
                    
      COMMENT ON COLUMN nop_job_task.ERROR_CODE IS '错误码';
                    
      COMMENT ON COLUMN nop_job_task.ERROR_MESSAGE IS '错误消息';
                    
      COMMENT ON COLUMN nop_job_task.PARTITION_INDEX IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_task.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_task.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_job_task.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_task.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_job_task.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_task.REMARK IS '备注';
                    
      COMMENT ON COLUMN nop_job_task.PROGRESS IS '执行进度';
                    
      COMMENT ON COLUMN nop_job_task.PROGRESS_MESSAGE IS '进度消息';
                    
