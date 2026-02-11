
CREATE TABLE nop_batch_file(
  sid VARCHAR(32) NOT NULL ,
  file_name VARCHAR(500) NOT NULL ,
  file_path VARCHAR(2000) NOT NULL ,
  file_length INT8 NOT NULL ,
  file_category VARCHAR(100) NOT NULL ,
  file_source VARCHAR(10) NOT NULL ,
  batch_task_id VARCHAR(32) NOT NULL ,
  process_state VARCHAR(10) NOT NULL ,
  accept_date DATE NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_batch_file primary key (sid)
);

CREATE TABLE nop_batch_task(
  sid VARCHAR(32) NOT NULL ,
  task_name VARCHAR(50) NOT NULL ,
  task_key VARCHAR(100) NOT NULL ,
  task_status INT4 NOT NULL ,
  start_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  task_params VARCHAR(4000)  ,
  exec_count INT4 NOT NULL ,
  worker_id VARCHAR(100) NOT NULL ,
  input_file_id VARCHAR(32)  ,
  flow_step_id VARCHAR(50)  ,
  flow_id VARCHAR(50)  ,
  restart_time TIMESTAMP  ,
  result_status INT4  ,
  result_code VARCHAR(100)  ,
  result_msg VARCHAR(500)  ,
  error_stack VARCHAR(4000)  ,
  completed_index INT8 NOT NULL ,
  complete_item_count INT8 NOT NULL ,
  load_retry_count INT4 NOT NULL ,
  load_skip_count INT8 NOT NULL ,
  retry_item_count INT4 NOT NULL ,
  process_item_count INT8 NOT NULL ,
  skip_item_count INT8 NOT NULL ,
  write_item_count INT8 NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_batch_task primary key (sid)
);

CREATE TABLE nop_batch_task_var(
  batch_task_id VARCHAR(32) NOT NULL ,
  field_name VARCHAR(100) NOT NULL ,
  field_type INT4 NOT NULL ,
  string_value VARCHAR(4000)  ,
  decimal_value NUMERIC(30,6)  ,
  long_value INT8  ,
  date_value DATE  ,
  timestamp_value TIMESTAMP  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_batch_task_var primary key (batch_task_id,field_name)
);

CREATE TABLE nop_batch_record_result(
  batch_task_id VARCHAR(32) NOT NULL ,
  record_key VARCHAR(200) NOT NULL ,
  result_status INT4 NOT NULL ,
  result_code VARCHAR(100)  ,
  result_msg VARCHAR(500)  ,
  error_stack VARCHAR(4000)  ,
  record_info VARCHAR(2000)  ,
  retry_count INT4 NOT NULL ,
  batch_size INT4 NOT NULL ,
  handle_status INT4 NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_batch_record_result primary key (batch_task_id,record_key)
);


      COMMENT ON TABLE nop_batch_file IS '批处理文件';
                
      COMMENT ON COLUMN nop_batch_file.sid IS '主键';
                    
      COMMENT ON COLUMN nop_batch_file.file_name IS '文件名';
                    
      COMMENT ON COLUMN nop_batch_file.file_path IS '文件路径';
                    
      COMMENT ON COLUMN nop_batch_file.file_length IS '文件长度';
                    
      COMMENT ON COLUMN nop_batch_file.file_category IS '文件分类';
                    
      COMMENT ON COLUMN nop_batch_file.file_source IS '文件来源';
                    
      COMMENT ON COLUMN nop_batch_file.batch_task_id IS '批处理任务';
                    
      COMMENT ON COLUMN nop_batch_file.process_state IS '处理状态';
                    
      COMMENT ON COLUMN nop_batch_file.accept_date IS '文件接收时间';
                    
      COMMENT ON COLUMN nop_batch_file.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_batch_file.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_batch_file.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_batch_file.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_batch_file.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_batch_file.remark IS '备注';
                    
      COMMENT ON TABLE nop_batch_task IS '批处理任务';
                
      COMMENT ON COLUMN nop_batch_task.sid IS 'SID';
                    
      COMMENT ON COLUMN nop_batch_task.task_name IS '任务名';
                    
      COMMENT ON COLUMN nop_batch_task.task_key IS '唯一Key';
                    
      COMMENT ON COLUMN nop_batch_task.task_status IS '任务状态';
                    
      COMMENT ON COLUMN nop_batch_task.start_time IS '任务启动时间';
                    
      COMMENT ON COLUMN nop_batch_task.end_time IS '任务结束时间';
                    
      COMMENT ON COLUMN nop_batch_task.task_params IS '任务参数';
                    
      COMMENT ON COLUMN nop_batch_task.exec_count IS '执行次数';
                    
      COMMENT ON COLUMN nop_batch_task.worker_id IS '执行者';
                    
      COMMENT ON COLUMN nop_batch_task.input_file_id IS '输入文件';
                    
      COMMENT ON COLUMN nop_batch_task.flow_step_id IS '关联流程步骤ID';
                    
      COMMENT ON COLUMN nop_batch_task.flow_id IS '关联流程ID';
                    
      COMMENT ON COLUMN nop_batch_task.restart_time IS '重启时间';
                    
      COMMENT ON COLUMN nop_batch_task.result_status IS '返回状态码';
                    
      COMMENT ON COLUMN nop_batch_task.result_code IS '返回码';
                    
      COMMENT ON COLUMN nop_batch_task.result_msg IS '返回消息';
                    
      COMMENT ON COLUMN nop_batch_task.error_stack IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_batch_task.completed_index IS '已完成记录下标';
                    
      COMMENT ON COLUMN nop_batch_task.complete_item_count IS '完成条目数量';
                    
      COMMENT ON COLUMN nop_batch_task.load_retry_count IS '重试加载次数';
                    
      COMMENT ON COLUMN nop_batch_task.load_skip_count IS '加载跳过数量';
                    
      COMMENT ON COLUMN nop_batch_task.retry_item_count IS '重试条目次数';
                    
      COMMENT ON COLUMN nop_batch_task.process_item_count IS '处理条目数量';
                    
      COMMENT ON COLUMN nop_batch_task.skip_item_count IS '跳过条目数量';
                    
      COMMENT ON COLUMN nop_batch_task.write_item_count IS '写入条目数量';
                    
      COMMENT ON COLUMN nop_batch_task.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_batch_task.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_batch_task.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_batch_task.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_batch_task.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_batch_task.remark IS '备注';
                    
      COMMENT ON TABLE nop_batch_task_var IS '批处理任务状态变量';
                
      COMMENT ON COLUMN nop_batch_task_var.batch_task_id IS '主键';
                    
      COMMENT ON COLUMN nop_batch_task_var.field_name IS '变量名';
                    
      COMMENT ON COLUMN nop_batch_task_var.field_type IS '变量类型';
                    
      COMMENT ON COLUMN nop_batch_task_var.string_value IS '字符串值';
                    
      COMMENT ON COLUMN nop_batch_task_var.decimal_value IS '浮点值';
                    
      COMMENT ON COLUMN nop_batch_task_var.long_value IS '整数型';
                    
      COMMENT ON COLUMN nop_batch_task_var.date_value IS '日期值';
                    
      COMMENT ON COLUMN nop_batch_task_var.timestamp_value IS '时间点值';
                    
      COMMENT ON COLUMN nop_batch_task_var.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_batch_task_var.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_batch_task_var.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_batch_task_var.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_batch_task_var.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_batch_record_result IS '批处理记录结果';
                
      COMMENT ON COLUMN nop_batch_record_result.batch_task_id IS '主键';
                    
      COMMENT ON COLUMN nop_batch_record_result.record_key IS '记录唯一键';
                    
      COMMENT ON COLUMN nop_batch_record_result.result_status IS '返回状态码';
                    
      COMMENT ON COLUMN nop_batch_record_result.result_code IS '返回码';
                    
      COMMENT ON COLUMN nop_batch_record_result.result_msg IS '返回消息';
                    
      COMMENT ON COLUMN nop_batch_record_result.error_stack IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_batch_record_result.record_info IS '记录信息';
                    
      COMMENT ON COLUMN nop_batch_record_result.retry_count IS '重试次数';
                    
      COMMENT ON COLUMN nop_batch_record_result.batch_size IS '批次大小';
                    
      COMMENT ON COLUMN nop_batch_record_result.handle_status IS '处理状态';
                    
      COMMENT ON COLUMN nop_batch_record_result.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_batch_record_result.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_batch_record_result.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_batch_record_result.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_batch_record_result.update_time IS '修改时间';
                    
