
CREATE TABLE nop_job_definition(
  sid VARCHAR(32) NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  job_name VARCHAR(100) NOT NULL ,
  job_group VARCHAR(100) NOT NULL ,
  job_params VARCHAR(4000)  ,
  job_invoker VARCHAR(200) NOT NULL ,
  description VARCHAR(4000)  ,
  status INT4 NOT NULL ,
  cron_expr VARCHAR(100)  ,
  repeat_interval INT4  ,
  is_fixed_delay INT4 default 0   ,
  max_execution_count INT4  ,
  min_schedule_time TIMESTAMP  ,
  max_schedule_time TIMESTAMP  ,
  misfire_threshold INT4  ,
  max_failed_count INT4  ,
  max_consec_failed_count INT4  ,
  is_use_default_calendar INT4 default 0   ,
  pause_calendars VARCHAR(4000)  ,
  partition_index INT4 NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_job_definition primary key (sid)
);

CREATE TABLE nop_job_assignment(
  server_id VARCHAR(50) NOT NULL ,
  assignment VARCHAR(1000) NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_job_assignment primary key (server_id)
);

CREATE TABLE nop_job_instance_his(
  job_instance_id VARCHAR(32) NOT NULL ,
  job_def_id VARCHAR(32)  ,
  job_name VARCHAR(100) NOT NULL ,
  job_group VARCHAR(100) NOT NULL ,
  job_params VARCHAR(4000)  ,
  job_invoker VARCHAR(200) NOT NULL ,
  status INT4 NOT NULL ,
  scheduled_exec_time TIMESTAMP NOT NULL ,
  exec_count INT8 NOT NULL ,
  exec_begin_time TIMESTAMP  ,
  exec_end_time TIMESTAMP  ,
  once_task BOOLEAN  ,
  manual_fire BOOLEAN  ,
  fired_by VARCHAR(50)  ,
  consecutive_fail_count INT4  ,
  total_fail_count INT4  ,
  err_code VARCHAR(200)  ,
  err_msg VARCHAR(500)  ,
  last_job_instance_id VARCHAR(32)  ,
  partition_index INT4 NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_job_instance_his primary key (job_instance_id)
);

CREATE TABLE nop_job_instance(
  job_instance_id VARCHAR(32) NOT NULL ,
  job_def_id VARCHAR(32)  ,
  job_name VARCHAR(100) NOT NULL ,
  job_group VARCHAR(100) NOT NULL ,
  job_params VARCHAR(4000)  ,
  job_invoker VARCHAR(200) NOT NULL ,
  status INT4 NOT NULL ,
  scheduled_exec_time TIMESTAMP NOT NULL ,
  exec_count INT8 NOT NULL ,
  exec_begin_time TIMESTAMP  ,
  exec_end_time TIMESTAMP  ,
  once_task BOOLEAN  ,
  manual_fire BOOLEAN  ,
  fired_by VARCHAR(50)  ,
  consecutive_fail_count INT4  ,
  total_fail_count INT4  ,
  err_code VARCHAR(200)  ,
  err_msg VARCHAR(500)  ,
  last_job_instance_id VARCHAR(32)  ,
  partition_index INT4 NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_job_instance primary key (job_instance_id)
);


      COMMENT ON TABLE nop_job_definition IS '作业定义';
                
      COMMENT ON COLUMN nop_job_definition.sid IS 'SID';
                    
      COMMENT ON COLUMN nop_job_definition.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_job_definition.job_name IS '任务名';
                    
      COMMENT ON COLUMN nop_job_definition.job_group IS '任务组';
                    
      COMMENT ON COLUMN nop_job_definition.job_params IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_definition.job_invoker IS '任务执行函数';
                    
      COMMENT ON COLUMN nop_job_definition.description IS '任务描述';
                    
      COMMENT ON COLUMN nop_job_definition.status IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_definition.cron_expr IS '定时表达式';
                    
      COMMENT ON COLUMN nop_job_definition.repeat_interval IS '定时执行间隔';
                    
      COMMENT ON COLUMN nop_job_definition.is_fixed_delay IS '是否固定延时';
                    
      COMMENT ON COLUMN nop_job_definition.max_execution_count IS '最多执行次数';
                    
      COMMENT ON COLUMN nop_job_definition.min_schedule_time IS '最近调度时间';
                    
      COMMENT ON COLUMN nop_job_definition.max_schedule_time IS '最大调度时间';
                    
      COMMENT ON COLUMN nop_job_definition.misfire_threshold IS '超时阈值';
                    
      COMMENT ON COLUMN nop_job_definition.max_failed_count IS '最大允许失败次数';
                    
      COMMENT ON COLUMN nop_job_definition.max_consec_failed_count IS '最大允许连续失败次数';
                    
      COMMENT ON COLUMN nop_job_definition.is_use_default_calendar IS '使用系统内置日历';
                    
      COMMENT ON COLUMN nop_job_definition.pause_calendars IS '暂停日历';
                    
      COMMENT ON COLUMN nop_job_definition.partition_index IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_definition.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_definition.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_job_definition.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_definition.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_job_definition.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_definition.remark IS '备注';
                    
      COMMENT ON TABLE nop_job_assignment IS '任务分配';
                
      COMMENT ON COLUMN nop_job_assignment.server_id IS '服务实例ID';
                    
      COMMENT ON COLUMN nop_job_assignment.assignment IS '任务分配';
                    
      COMMENT ON COLUMN nop_job_assignment.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_assignment.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_job_assignment.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_assignment.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_job_assignment.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_assignment.remark IS '备注';
                    
      COMMENT ON TABLE nop_job_instance_his IS '任务实例历史';
                
      COMMENT ON COLUMN nop_job_instance_his.job_instance_id IS '任务实例ID';
                    
      COMMENT ON COLUMN nop_job_instance_his.job_def_id IS '任务定义ID';
                    
      COMMENT ON COLUMN nop_job_instance_his.job_name IS '任务名';
                    
      COMMENT ON COLUMN nop_job_instance_his.job_group IS '任务组';
                    
      COMMENT ON COLUMN nop_job_instance_his.job_params IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_instance_his.job_invoker IS '任务执行函数';
                    
      COMMENT ON COLUMN nop_job_instance_his.status IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_instance_his.scheduled_exec_time IS '调度执行时间';
                    
      COMMENT ON COLUMN nop_job_instance_his.exec_count IS '执行次数';
                    
      COMMENT ON COLUMN nop_job_instance_his.exec_begin_time IS '本次执行开始时间';
                    
      COMMENT ON COLUMN nop_job_instance_his.exec_end_time IS '本次执行完成时间';
                    
      COMMENT ON COLUMN nop_job_instance_his.once_task IS '是否只执行一次';
                    
      COMMENT ON COLUMN nop_job_instance_his.manual_fire IS '是否手工触发';
                    
      COMMENT ON COLUMN nop_job_instance_his.fired_by IS '触发执行的用户';
                    
      COMMENT ON COLUMN nop_job_instance_his.consecutive_fail_count IS '连续失败次数';
                    
      COMMENT ON COLUMN nop_job_instance_his.total_fail_count IS '总失败次数';
                    
      COMMENT ON COLUMN nop_job_instance_his.err_code IS '错误码';
                    
      COMMENT ON COLUMN nop_job_instance_his.err_msg IS '错误消息';
                    
      COMMENT ON COLUMN nop_job_instance_his.last_job_instance_id IS '上次任务实例ID';
                    
      COMMENT ON COLUMN nop_job_instance_his.partition_index IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_instance_his.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_instance_his.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_job_instance_his.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_instance_his.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_job_instance_his.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_instance_his.remark IS '备注';
                    
      COMMENT ON TABLE nop_job_instance IS '任务实例';
                
      COMMENT ON COLUMN nop_job_instance.job_instance_id IS '任务实例ID';
                    
      COMMENT ON COLUMN nop_job_instance.job_def_id IS '任务定义ID';
                    
      COMMENT ON COLUMN nop_job_instance.job_name IS '任务名';
                    
      COMMENT ON COLUMN nop_job_instance.job_group IS '任务组';
                    
      COMMENT ON COLUMN nop_job_instance.job_params IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_instance.job_invoker IS '任务执行函数';
                    
      COMMENT ON COLUMN nop_job_instance.status IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_instance.scheduled_exec_time IS '调度执行时间';
                    
      COMMENT ON COLUMN nop_job_instance.exec_count IS '执行次数';
                    
      COMMENT ON COLUMN nop_job_instance.exec_begin_time IS '本次执行开始时间';
                    
      COMMENT ON COLUMN nop_job_instance.exec_end_time IS '本次执行完成时间';
                    
      COMMENT ON COLUMN nop_job_instance.once_task IS '是否只执行一次';
                    
      COMMENT ON COLUMN nop_job_instance.manual_fire IS '是否手工触发';
                    
      COMMENT ON COLUMN nop_job_instance.fired_by IS '触发执行的用户';
                    
      COMMENT ON COLUMN nop_job_instance.consecutive_fail_count IS '连续失败次数';
                    
      COMMENT ON COLUMN nop_job_instance.total_fail_count IS '总失败次数';
                    
      COMMENT ON COLUMN nop_job_instance.err_code IS '错误码';
                    
      COMMENT ON COLUMN nop_job_instance.err_msg IS '错误消息';
                    
      COMMENT ON COLUMN nop_job_instance.last_job_instance_id IS '上次任务实例ID';
                    
      COMMENT ON COLUMN nop_job_instance.partition_index IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_instance.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_instance.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_job_instance.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_instance.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_job_instance.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_instance.remark IS '备注';
                    
