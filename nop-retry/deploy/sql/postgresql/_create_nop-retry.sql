
CREATE TABLE nop_retry_policy(
  sid VARCHAR(32) NOT NULL ,
  namespace_id VARCHAR(64) NOT NULL ,
  group_id VARCHAR(64) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  status VARCHAR(1) default '1'   ,
  save_record_strategy INT4 default 2   ,
  immediate_retry_count INT4 default 0   ,
  immediate_retry_interval_ms INT8 default 1000   ,
  max_retry_count INT4 NOT NULL ,
  backoff_strategy INT4 default 1   ,
  initial_interval_ms INT8 default 5000   ,
  max_interval_ms INT8 default 60000   ,
  jitter_ratio NUMERIC(5,4) default 0.5   ,
  execution_timeout_seconds INT4  ,
  deadline_timeout_ms INT8  ,
  block_strategy INT4  ,
  callback_enabled VARCHAR(1) default '0'   ,
  callback_trigger_type INT4  ,
  callback_policy_id VARCHAR(32)  ,
  owner_id VARCHAR(50)  ,
  description VARCHAR(4000)  ,
  version INT4  ,
  created_by VARCHAR(50)  ,
  create_time TIMESTAMP  ,
  updated_by VARCHAR(50)  ,
  update_time TIMESTAMP  ,
  retrying_timeout_ms INT8 default 600000   ,
  constraint PK_nop_retry_policy primary key (sid)
);

CREATE TABLE nop_retry_record(
  sid VARCHAR(32) NOT NULL ,
  namespace_id VARCHAR(64) NOT NULL ,
  group_id VARCHAR(64) NOT NULL ,
  policy_id VARCHAR(32)  ,
  idempotent_id VARCHAR(64) NOT NULL ,
  biz_no VARCHAR(64)  ,
  task_type INT4  ,
  status INT4  ,
  retry_count INT4  ,
  max_retry_count INT4  ,
  next_trigger_time TIMESTAMP  ,
  partition_index INT4  ,
  executor_name VARCHAR(512)  ,
  request_payload VARCHAR(4000)  ,
  context_payload VARCHAR(4000)  ,
  version INT4  ,
  created_by VARCHAR(50)  ,
  create_time TIMESTAMP  ,
  updated_by VARCHAR(50)  ,
  update_time TIMESTAMP  ,
  service_name VARCHAR(200)  ,
  service_method VARCHAR(100)  ,
  constraint PK_nop_retry_record primary key (sid)
);

CREATE TABLE nop_retry_attempt(
  sid VARCHAR(32) NOT NULL ,
  record_id VARCHAR(32) NOT NULL ,
  attempt_no INT4  ,
  status INT4  ,
  start_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  duration_ms INT8  ,
  error_code VARCHAR(50)  ,
  error_message VARCHAR(500)  ,
  error_stack VARCHAR(4000)  ,
  client_address VARCHAR(100)  ,
  reason VARCHAR(500)  ,
  request_payload_snapshot VARCHAR(4000)  ,
  version INT4  ,
  created_by VARCHAR(50)  ,
  create_time TIMESTAMP  ,
  updated_by VARCHAR(50)  ,
  update_time TIMESTAMP  ,
  constraint PK_nop_retry_attempt primary key (sid)
);

CREATE TABLE nop_retry_dead_letter(
  sid VARCHAR(32) NOT NULL ,
  namespace_id VARCHAR(64) NOT NULL ,
  group_id VARCHAR(64) NOT NULL ,
  policy_id VARCHAR(32)  ,
  record_id VARCHAR(32) NOT NULL ,
  idempotent_id VARCHAR(64) NOT NULL ,
  biz_no VARCHAR(64)  ,
  executor_name VARCHAR(512)  ,
  request_payload VARCHAR(4000)  ,
  failure_code VARCHAR(50)  ,
  failure_message VARCHAR(500)  ,
  error_stack VARCHAR(4000)  ,
  final_status INT4  ,
  version INT4  ,
  created_by VARCHAR(50)  ,
  create_time TIMESTAMP  ,
  updated_by VARCHAR(50)  ,
  update_time TIMESTAMP  ,
  service_name VARCHAR(200)  ,
  service_method VARCHAR(100)  ,
  constraint PK_nop_retry_dead_letter primary key (sid)
);


      COMMENT ON TABLE nop_retry_policy IS '重试策略';
                
      COMMENT ON COLUMN nop_retry_policy.sid IS '主键';
                    
      COMMENT ON COLUMN nop_retry_policy.namespace_id IS '命名空间ID';
                    
      COMMENT ON COLUMN nop_retry_policy.group_id IS '组ID';
                    
      COMMENT ON COLUMN nop_retry_policy.name IS '策略名称';
                    
      COMMENT ON COLUMN nop_retry_policy.status IS '状态';
                    
      COMMENT ON COLUMN nop_retry_policy.save_record_strategy IS '保存记录策略';
                    
      COMMENT ON COLUMN nop_retry_policy.immediate_retry_count IS '立刻重试次数';
                    
      COMMENT ON COLUMN nop_retry_policy.immediate_retry_interval_ms IS '立刻重试间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.max_retry_count IS '最大重试次数';
                    
      COMMENT ON COLUMN nop_retry_policy.backoff_strategy IS '退避策略';
                    
      COMMENT ON COLUMN nop_retry_policy.initial_interval_ms IS '初始间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.max_interval_ms IS '最大间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.jitter_ratio IS '抖动比例';
                    
      COMMENT ON COLUMN nop_retry_policy.execution_timeout_seconds IS '执行超时(秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.deadline_timeout_ms IS '截止超时(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.block_strategy IS '阻塞策略';
                    
      COMMENT ON COLUMN nop_retry_policy.callback_enabled IS '启用回调';
                    
      COMMENT ON COLUMN nop_retry_policy.callback_trigger_type IS '回调触发类型';
                    
      COMMENT ON COLUMN nop_retry_policy.callback_policy_id IS '回调策略ID';
                    
      COMMENT ON COLUMN nop_retry_policy.owner_id IS '所有者ID';
                    
      COMMENT ON COLUMN nop_retry_policy.description IS '描述';
                    
      COMMENT ON COLUMN nop_retry_policy.version IS '版本';
                    
      COMMENT ON COLUMN nop_retry_policy.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_policy.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_policy.updated_by IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_policy.update_time IS '更新时间';
                    
      COMMENT ON COLUMN nop_retry_policy.retrying_timeout_ms IS '执行中锁定超时(毫秒)';
                    
      COMMENT ON TABLE nop_retry_record IS '重试记录';
                
      COMMENT ON COLUMN nop_retry_record.sid IS '主键';
                    
      COMMENT ON COLUMN nop_retry_record.namespace_id IS '命名空间ID';
                    
      COMMENT ON COLUMN nop_retry_record.group_id IS '组ID';
                    
      COMMENT ON COLUMN nop_retry_record.policy_id IS '策略ID';
                    
      COMMENT ON COLUMN nop_retry_record.idempotent_id IS '幂等ID';
                    
      COMMENT ON COLUMN nop_retry_record.biz_no IS '业务号';
                    
      COMMENT ON COLUMN nop_retry_record.task_type IS '任务类型';
                    
      COMMENT ON COLUMN nop_retry_record.status IS '状态';
                    
      COMMENT ON COLUMN nop_retry_record.retry_count IS '重试次数';
                    
      COMMENT ON COLUMN nop_retry_record.max_retry_count IS '最大重试次数';
                    
      COMMENT ON COLUMN nop_retry_record.next_trigger_time IS '下次触发时间';
                    
      COMMENT ON COLUMN nop_retry_record.partition_index IS '分区索引';
                    
      COMMENT ON COLUMN nop_retry_record.executor_name IS '执行器名称';
                    
      COMMENT ON COLUMN nop_retry_record.request_payload IS '请求参数';
                    
      COMMENT ON COLUMN nop_retry_record.context_payload IS '上下文参数';
                    
      COMMENT ON COLUMN nop_retry_record.version IS '版本';
                    
      COMMENT ON COLUMN nop_retry_record.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_record.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_record.updated_by IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_record.update_time IS '更新时间';
                    
      COMMENT ON COLUMN nop_retry_record.service_name IS '服务名';
                    
      COMMENT ON COLUMN nop_retry_record.service_method IS '服务方法';
                    
      COMMENT ON TABLE nop_retry_attempt IS '重试尝试';
                
      COMMENT ON COLUMN nop_retry_attempt.sid IS '主键';
                    
      COMMENT ON COLUMN nop_retry_attempt.record_id IS '记录ID';
                    
      COMMENT ON COLUMN nop_retry_attempt.attempt_no IS '尝试序号';
                    
      COMMENT ON COLUMN nop_retry_attempt.status IS '状态';
                    
      COMMENT ON COLUMN nop_retry_attempt.start_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_retry_attempt.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_retry_attempt.duration_ms IS '持续时间(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_attempt.error_code IS '错误码';
                    
      COMMENT ON COLUMN nop_retry_attempt.error_message IS '错误消息';
                    
      COMMENT ON COLUMN nop_retry_attempt.error_stack IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_retry_attempt.client_address IS '客户端地址';
                    
      COMMENT ON COLUMN nop_retry_attempt.reason IS '原因';
                    
      COMMENT ON COLUMN nop_retry_attempt.request_payload_snapshot IS '请求参数快照';
                    
      COMMENT ON COLUMN nop_retry_attempt.version IS '版本';
                    
      COMMENT ON COLUMN nop_retry_attempt.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_attempt.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_attempt.updated_by IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_attempt.update_time IS '更新时间';
                    
      COMMENT ON TABLE nop_retry_dead_letter IS '重试死信';
                
      COMMENT ON COLUMN nop_retry_dead_letter.sid IS '主键';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.namespace_id IS '命名空间ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.group_id IS '组ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.policy_id IS '策略ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.record_id IS '记录ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.idempotent_id IS '幂等ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.biz_no IS '业务号';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.executor_name IS '执行器名称';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.request_payload IS '请求参数';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.failure_code IS '失败码';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.failure_message IS '失败消息';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.error_stack IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.final_status IS '最终状态';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.version IS '版本';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.updated_by IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.update_time IS '更新时间';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.service_name IS '服务名';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.service_method IS '服务方法';
                    
