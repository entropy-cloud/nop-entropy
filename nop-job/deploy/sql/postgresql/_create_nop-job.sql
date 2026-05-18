
CREATE TABLE nop_job_schedule(
  job_schedule_id VARCHAR(32) NOT NULL ,
  namespace_id VARCHAR(50)  ,
  group_id VARCHAR(100)  ,
  job_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  description VARCHAR(4000)  ,
  schedule_status INT4 NOT NULL ,
  executor_kind VARCHAR(50)  ,
  job_params VARCHAR(4000)  ,
  trigger_type INT4  ,
  cron_expr VARCHAR(100)  ,
  repeat_interval_ms INT8  ,
  max_execution_count INT4  ,
  min_schedule_time TIMESTAMP  ,
  max_schedule_time TIMESTAMP  ,
  misfire_threshold_ms INT4  ,
  use_default_calendar INT4 default 0   ,
  pause_calendar_spec VARCHAR(4000)  ,
  block_strategy INT4  ,
  timeout_seconds INT4  ,
  retry_policy_id VARCHAR(32)  ,
  partition_index INT4 NOT NULL ,
  fire_count INT8 default 0  NOT NULL ,
  active_fire_count INT4 default 0  NOT NULL ,
  last_fire_time TIMESTAMP  ,
  last_end_time TIMESTAMP  ,
  next_fire_time TIMESTAMP  ,
  last_fire_status INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  last_duration_ms INT8  ,
  total_fire_count INT8 default 0   ,
  success_fire_count INT8 default 0   ,
  fail_fire_count INT8 default 0   ,
  constraint PK_nop_job_schedule primary key (job_schedule_id)
);

CREATE TABLE nop_job_fire(
  job_fire_id VARCHAR(32) NOT NULL ,
  job_schedule_id VARCHAR(32) NOT NULL ,
  namespace_id VARCHAR(50)  ,
  group_id VARCHAR(100)  ,
  job_name VARCHAR(100)  ,
  trigger_source INT4  ,
  scheduled_fire_time TIMESTAMP NOT NULL ,
  triggered_by VARCHAR(50)  ,
  fire_status INT4 NOT NULL ,
  planner_instance_id VARCHAR(100)  ,
  dispatch_instance_id VARCHAR(100)  ,
  start_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  duration_ms INT8  ,
  job_params_snapshot VARCHAR(4000)  ,
  executor_kind VARCHAR(50)  ,
  retry_policy_id VARCHAR(32)  ,
  retry_record_id VARCHAR(32)  ,
  error_code VARCHAR(200)  ,
  error_message VARCHAR(1000)  ,
  partition_index INT4 NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_job_fire primary key (job_fire_id)
);

CREATE TABLE nop_job_task(
  job_task_id VARCHAR(32) NOT NULL ,
  job_fire_id VARCHAR(32) NOT NULL ,
  task_no INT4 default 1  NOT NULL ,
  task_status INT4 NOT NULL ,
  worker_instance_id VARCHAR(100)  ,
  worker_address VARCHAR(200)  ,
  task_payload VARCHAR(4000)  ,
  start_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  duration_ms INT8  ,
  result_payload VARCHAR(4000)  ,
  error_code VARCHAR(200)  ,
  error_message VARCHAR(1000)  ,
  partition_index INT4 NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  progress INT4  ,
  progress_message VARCHAR(500)  ,
  constraint PK_nop_job_task primary key (job_task_id)
);


      COMMENT ON TABLE nop_job_schedule IS '调度定义';
                
      COMMENT ON COLUMN nop_job_schedule.job_schedule_id IS '调度ID';
                    
      COMMENT ON COLUMN nop_job_schedule.namespace_id IS '命名空间';
                    
      COMMENT ON COLUMN nop_job_schedule.group_id IS '分组';
                    
      COMMENT ON COLUMN nop_job_schedule.job_name IS '作业名';
                    
      COMMENT ON COLUMN nop_job_schedule.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_job_schedule.description IS '描述';
                    
      COMMENT ON COLUMN nop_job_schedule.schedule_status IS '调度状态';
                    
      COMMENT ON COLUMN nop_job_schedule.executor_kind IS '执行器类型';
                    
      COMMENT ON COLUMN nop_job_schedule.job_params IS '任务参数';
                    
      COMMENT ON COLUMN nop_job_schedule.trigger_type IS '触发器类型';
                    
      COMMENT ON COLUMN nop_job_schedule.cron_expr IS 'CRON表达式';
                    
      COMMENT ON COLUMN nop_job_schedule.repeat_interval_ms IS '重复间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.max_execution_count IS '最大执行次数';
                    
      COMMENT ON COLUMN nop_job_schedule.min_schedule_time IS '最早调度时间';
                    
      COMMENT ON COLUMN nop_job_schedule.max_schedule_time IS '最晚调度时间';
                    
      COMMENT ON COLUMN nop_job_schedule.misfire_threshold_ms IS 'Misfire阈值(毫秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.use_default_calendar IS '使用默认日历';
                    
      COMMENT ON COLUMN nop_job_schedule.pause_calendar_spec IS '暂停日历配置';
                    
      COMMENT ON COLUMN nop_job_schedule.block_strategy IS '阻塞策略';
                    
      COMMENT ON COLUMN nop_job_schedule.timeout_seconds IS '超时时间(秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.retry_policy_id IS '重试策略ID';
                    
      COMMENT ON COLUMN nop_job_schedule.partition_index IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_schedule.fire_count IS '已触发次数';
                    
      COMMENT ON COLUMN nop_job_schedule.active_fire_count IS '活跃触发数';
                    
      COMMENT ON COLUMN nop_job_schedule.last_fire_time IS '上次触发时间';
                    
      COMMENT ON COLUMN nop_job_schedule.last_end_time IS '上次结束时间';
                    
      COMMENT ON COLUMN nop_job_schedule.next_fire_time IS '下次触发时间';
                    
      COMMENT ON COLUMN nop_job_schedule.last_fire_status IS '上次触发状态';
                    
      COMMENT ON COLUMN nop_job_schedule.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_schedule.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_job_schedule.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_schedule.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_job_schedule.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_schedule.remark IS '备注';
                    
      COMMENT ON COLUMN nop_job_schedule.last_duration_ms IS '上次执行耗时(毫秒)';
                    
      COMMENT ON COLUMN nop_job_schedule.total_fire_count IS '总触发次数';
                    
      COMMENT ON COLUMN nop_job_schedule.success_fire_count IS '成功触发次数';
                    
      COMMENT ON COLUMN nop_job_schedule.fail_fire_count IS '失败触发次数';
                    
      COMMENT ON TABLE nop_job_fire IS '触发批次';
                
      COMMENT ON COLUMN nop_job_fire.job_fire_id IS '触发批次ID';
                    
      COMMENT ON COLUMN nop_job_fire.job_schedule_id IS '调度ID';
                    
      COMMENT ON COLUMN nop_job_fire.namespace_id IS '命名空间';
                    
      COMMENT ON COLUMN nop_job_fire.group_id IS '分组';
                    
      COMMENT ON COLUMN nop_job_fire.job_name IS '作业名';
                    
      COMMENT ON COLUMN nop_job_fire.trigger_source IS '触发来源';
                    
      COMMENT ON COLUMN nop_job_fire.scheduled_fire_time IS '计划触发时间';
                    
      COMMENT ON COLUMN nop_job_fire.triggered_by IS '触发人';
                    
      COMMENT ON COLUMN nop_job_fire.fire_status IS '批次状态';
                    
      COMMENT ON COLUMN nop_job_fire.planner_instance_id IS '计划节点ID';
                    
      COMMENT ON COLUMN nop_job_fire.dispatch_instance_id IS '分发节点ID';
                    
      COMMENT ON COLUMN nop_job_fire.start_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_job_fire.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_job_fire.duration_ms IS '执行时长(毫秒)';
                    
      COMMENT ON COLUMN nop_job_fire.job_params_snapshot IS '参数快照';
                    
      COMMENT ON COLUMN nop_job_fire.executor_kind IS '执行器类型';
                    
      COMMENT ON COLUMN nop_job_fire.retry_policy_id IS '重试策略ID';
                    
      COMMENT ON COLUMN nop_job_fire.retry_record_id IS '重试记录ID';
                    
      COMMENT ON COLUMN nop_job_fire.error_code IS '错误码';
                    
      COMMENT ON COLUMN nop_job_fire.error_message IS '错误消息';
                    
      COMMENT ON COLUMN nop_job_fire.partition_index IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_fire.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_fire.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_job_fire.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_fire.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_job_fire.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_fire.remark IS '备注';
                    
      COMMENT ON TABLE nop_job_task IS '执行任务';
                
      COMMENT ON COLUMN nop_job_task.job_task_id IS '任务ID';
                    
      COMMENT ON COLUMN nop_job_task.job_fire_id IS '批次ID';
                    
      COMMENT ON COLUMN nop_job_task.task_no IS '任务序号';
                    
      COMMENT ON COLUMN nop_job_task.task_status IS '任务状态';
                    
      COMMENT ON COLUMN nop_job_task.worker_instance_id IS '执行节点ID';
                    
      COMMENT ON COLUMN nop_job_task.worker_address IS '执行节点地址';
                    
      COMMENT ON COLUMN nop_job_task.task_payload IS '投递参数';
                    
      COMMENT ON COLUMN nop_job_task.start_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_job_task.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_job_task.duration_ms IS '执行时长(毫秒)';
                    
      COMMENT ON COLUMN nop_job_task.result_payload IS '执行结果';
                    
      COMMENT ON COLUMN nop_job_task.error_code IS '错误码';
                    
      COMMENT ON COLUMN nop_job_task.error_message IS '错误消息';
                    
      COMMENT ON COLUMN nop_job_task.partition_index IS '分区索引';
                    
      COMMENT ON COLUMN nop_job_task.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_job_task.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_job_task.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_job_task.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_job_task.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_job_task.remark IS '备注';
                    
      COMMENT ON COLUMN nop_job_task.progress IS '执行进度';
                    
      COMMENT ON COLUMN nop_job_task.progress_message IS '进度消息';
                    
