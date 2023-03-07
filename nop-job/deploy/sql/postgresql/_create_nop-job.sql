
CREATE TABLE nop_job_plan(
  SID VARCHAR(32) NOT NULL ,
  DISPLAY_NAME VARCHAR(200) NOT NULL ,
  JOB_NAME VARCHAR(100) NOT NULL ,
  JOB_GROUP VARCHAR(100) NOT NULL ,
  JOB_PARAMS VARCHAR(4000)  ,
  JOB_INVOKER VARCHAR(200) NOT NULL ,
  DESCRIPTION VARCHAR(4000)  ,
  STATUS INT4 NOT NULL ,
  CRON_EXPR VARCHAR(100)  ,
  REPEAT_INTERVAL INT4  ,
  IS_FIXED_DELAY INT4  ,
  MAX_EXECUTION_COUNT INT4  ,
  MIN_SCHEDULE_TIME TIMESTAMP  ,
  MAX_SCHEDULE_TIME TIMESTAMP  ,
  MISFIRE_THRESHOLD INT4  ,
  MAX_FAILED_COUNT INT4  ,
  IS_USE_DEFAULT_CALENDAR INT4  ,
  PAUSE_CALENDARS VARCHAR(4000)  ,
  VERSION INT8 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_job_plan primary key (SID)
);


      COMMENT ON TABLE nop_job_plan IS '任务调度计划';
                
      COMMENT ON COLUMN nop_job_plan.SID IS 'SID';
                    
      COMMENT ON COLUMN nop_job_plan.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_job_plan.JOB_NAME IS '任务名';
                    
      COMMENT ON COLUMN nop_job_plan.JOB_GROUP IS '任务组';
                    
      COMMENT ON COLUMN nop_job_plan.JOB_PARAMS IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_plan.JOB_INVOKER IS '任务执行函数';
                    
      COMMENT ON COLUMN nop_job_plan.DESCRIPTION IS '任务描述';
                    
      COMMENT ON COLUMN nop_job_plan.STATUS IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_plan.CRON_EXPR IS '定时表达式';
                    
      COMMENT ON COLUMN nop_job_plan.REPEAT_INTERVAL IS '定时执行间隔';
                    
      COMMENT ON COLUMN nop_job_plan.IS_FIXED_DELAY IS '是否固定延时';
                    
      COMMENT ON COLUMN nop_job_plan.MAX_EXECUTION_COUNT IS '最多执行次数';
                    
      COMMENT ON COLUMN nop_job_plan.MIN_SCHEDULE_TIME IS '最近调度时间';
                    
      COMMENT ON COLUMN nop_job_plan.MAX_SCHEDULE_TIME IS '最大调度时间';
                    
      COMMENT ON COLUMN nop_job_plan.MISFIRE_THRESHOLD IS '超时阈值';
                    
      COMMENT ON COLUMN nop_job_plan.MAX_FAILED_COUNT IS '最大允许失败次数';
                    
      COMMENT ON COLUMN nop_job_plan.IS_USE_DEFAULT_CALENDAR IS '使用系统内置日历';
                    
      COMMENT ON COLUMN nop_job_plan.PAUSE_CALENDARS IS '暂停日历';
                    
      COMMENT ON COLUMN nop_job_plan.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_plan.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_job_plan.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_plan.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_job_plan.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_plan.REMARK IS '备注';
                    
