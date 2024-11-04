
CREATE TABLE nop_job_definition(
  SID VARCHAR2(32) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  JOB_NAME VARCHAR2(100) NOT NULL ,
  JOB_GROUP VARCHAR2(100) NOT NULL ,
  JOB_PARAMS VARCHAR2(4000)  ,
  JOB_INVOKER VARCHAR2(200) NOT NULL ,
  DESCRIPTION VARCHAR2(4000)  ,
  STATUS INTEGER NOT NULL ,
  CRON_EXPR VARCHAR2(100)  ,
  REPEAT_INTERVAL INTEGER  ,
  IS_FIXED_DELAY SMALLINT default 0   ,
  MAX_EXECUTION_COUNT INTEGER  ,
  MIN_SCHEDULE_TIME TIMESTAMP  ,
  MAX_SCHEDULE_TIME TIMESTAMP  ,
  MISFIRE_THRESHOLD INTEGER  ,
  MAX_FAILED_COUNT INTEGER  ,
  IS_USE_DEFAULT_CALENDAR SMALLINT default 0   ,
  PAUSE_CALENDARS VARCHAR2(4000)  ,
  SCHEDULER_GROUP VARCHAR2(50) NOT NULL ,
  SCHEDULER_ID VARCHAR2(32) NOT NULL ,
  SCHEDULER_EPOCH NUMBER(20) NOT NULL ,
  SCHEDULER_LOAD_TIME TIMESTAMP  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_job_definition primary key (SID)
);

CREATE TABLE nop_job_instance(
  JOB_ID VARCHAR2(32) NOT NULL ,
  JOB_DEF_ID VARCHAR2(32)  ,
  JOB_NAME VARCHAR2(100) NOT NULL ,
  JOB_GROUP VARCHAR2(100) NOT NULL ,
  JOB_PARAMS VARCHAR2(4000)  ,
  JOB_INVOKER VARCHAR2(200) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  SCHEDULED_EXEC_TIME TIMESTAMP  ,
  EXEC_TIME TIMESTAMP  ,
  ONCE_TASK SMALLINT default 0   ,
  ERR_CODE VARCHAR2(200)  ,
  ERR_MSG VARCHAR2(500)  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_job_instance primary key (JOB_ID)
);


      COMMENT ON TABLE nop_job_definition IS '作业定义';
                
      COMMENT ON COLUMN nop_job_definition.SID IS 'SID';
                    
      COMMENT ON COLUMN nop_job_definition.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_job_definition.JOB_NAME IS '任务名';
                    
      COMMENT ON COLUMN nop_job_definition.JOB_GROUP IS '任务组';
                    
      COMMENT ON COLUMN nop_job_definition.JOB_PARAMS IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_definition.JOB_INVOKER IS '任务执行函数';
                    
      COMMENT ON COLUMN nop_job_definition.DESCRIPTION IS '任务描述';
                    
      COMMENT ON COLUMN nop_job_definition.STATUS IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_definition.CRON_EXPR IS '定时表达式';
                    
      COMMENT ON COLUMN nop_job_definition.REPEAT_INTERVAL IS '定时执行间隔';
                    
      COMMENT ON COLUMN nop_job_definition.IS_FIXED_DELAY IS '是否固定延时';
                    
      COMMENT ON COLUMN nop_job_definition.MAX_EXECUTION_COUNT IS '最多执行次数';
                    
      COMMENT ON COLUMN nop_job_definition.MIN_SCHEDULE_TIME IS '最近调度时间';
                    
      COMMENT ON COLUMN nop_job_definition.MAX_SCHEDULE_TIME IS '最大调度时间';
                    
      COMMENT ON COLUMN nop_job_definition.MISFIRE_THRESHOLD IS '超时阈值';
                    
      COMMENT ON COLUMN nop_job_definition.MAX_FAILED_COUNT IS '最大允许失败次数';
                    
      COMMENT ON COLUMN nop_job_definition.IS_USE_DEFAULT_CALENDAR IS '使用系统内置日历';
                    
      COMMENT ON COLUMN nop_job_definition.PAUSE_CALENDARS IS '暂停日历';
                    
      COMMENT ON COLUMN nop_job_definition.SCHEDULER_GROUP IS '调度器分组';
                    
      COMMENT ON COLUMN nop_job_definition.SCHEDULER_ID IS '调度器ID';
                    
      COMMENT ON COLUMN nop_job_definition.SCHEDULER_EPOCH IS '调度器世代';
                    
      COMMENT ON COLUMN nop_job_definition.SCHEDULER_LOAD_TIME IS '调度器加载时间';
                    
      COMMENT ON COLUMN nop_job_definition.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_definition.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_job_definition.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_definition.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_job_definition.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_definition.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_job_instance IS '任务实例';
                
      COMMENT ON COLUMN nop_job_instance.JOB_ID IS 'Job ID';
                    
      COMMENT ON COLUMN nop_job_instance.JOB_DEF_ID IS '任务定义ID';
                    
      COMMENT ON COLUMN nop_job_instance.JOB_NAME IS '任务名';
                    
      COMMENT ON COLUMN nop_job_instance.JOB_GROUP IS '任务组';
                    
      COMMENT ON COLUMN nop_job_instance.JOB_PARAMS IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_instance.JOB_INVOKER IS '任务执行函数';
                    
      COMMENT ON COLUMN nop_job_instance.STATUS IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_instance.SCHEDULED_EXEC_TIME IS '调度执行时间';
                    
      COMMENT ON COLUMN nop_job_instance.EXEC_TIME IS '实际执行时间';
                    
      COMMENT ON COLUMN nop_job_instance.ONCE_TASK IS '是否只执行一次';
                    
      COMMENT ON COLUMN nop_job_instance.ERR_CODE IS '错误码';
                    
      COMMENT ON COLUMN nop_job_instance.ERR_MSG IS '错误消息';
                    
      COMMENT ON COLUMN nop_job_instance.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_instance.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_job_instance.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_instance.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_job_instance.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_instance.REMARK IS '备注';
                    
