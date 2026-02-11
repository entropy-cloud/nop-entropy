
CREATE TABLE nop_tcc_record(
  txn_id VARCHAR(50) NOT NULL ,
  txn_group VARCHAR(50) NOT NULL ,
  txn_name VARCHAR(128)  ,
  status INT4 NOT NULL ,
  expire_time TIMESTAMP NOT NULL ,
  app_id VARCHAR(200) NOT NULL ,
  app_data VARCHAR(2000)  ,
  begin_time TIMESTAMP NOT NULL ,
  end_time TIMESTAMP  ,
  error_code VARCHAR(200)  ,
  error_message VARCHAR(1000)  ,
  error_stack VARCHAR(1000)  ,
  version INT4 NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_tcc_record primary key (txn_id)
);

CREATE TABLE nop_tcc_branch_record(
  branch_id VARCHAR(50) NOT NULL ,
  txn_id VARCHAR(50) NOT NULL ,
  branch_no INT4 NOT NULL ,
  parent_branch_id VARCHAR(50)  ,
  status INT4 NOT NULL ,
  expire_time TIMESTAMP NOT NULL ,
  service_name VARCHAR(200) NOT NULL ,
  service_method VARCHAR(200)  ,
  confirm_method VARCHAR(200)  ,
  cancel_method VARCHAR(200)  ,
  request_data TEXT  ,
  error_code VARCHAR(200)  ,
  error_message VARCHAR(1000)  ,
  error_stack VARCHAR(1000)  ,
  begin_time TIMESTAMP NOT NULL ,
  end_time TIMESTAMP  ,
  commit_error_code VARCHAR(200)  ,
  commit_error_message VARCHAR(1000)  ,
  commit_error_stack VARCHAR(1000)  ,
  cancel_error_code VARCHAR(200)  ,
  cancel_error_message VARCHAR(1000)  ,
  cancel_error_stack VARCHAR(1000)  ,
  retry_times INT4  ,
  max_retry_times INT4 NOT NULL ,
  next_retry_time TIMESTAMP  ,
  version INT4 NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_tcc_branch_record primary key (branch_id)
);


      COMMENT ON TABLE nop_tcc_record IS 'TCC事务记录';
                
      COMMENT ON COLUMN nop_tcc_record.txn_id IS '事务ID';
                    
      COMMENT ON COLUMN nop_tcc_record.txn_group IS '事务分组';
                    
      COMMENT ON COLUMN nop_tcc_record.txn_name IS '事务名';
                    
      COMMENT ON COLUMN nop_tcc_record.status IS '状态';
                    
      COMMENT ON COLUMN nop_tcc_record.expire_time IS '过期时间';
                    
      COMMENT ON COLUMN nop_tcc_record.app_id IS '应用ID';
                    
      COMMENT ON COLUMN nop_tcc_record.app_data IS '应用数据';
                    
      COMMENT ON COLUMN nop_tcc_record.begin_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_tcc_record.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_tcc_record.error_code IS '错误码';
                    
      COMMENT ON COLUMN nop_tcc_record.error_message IS '错误消息';
                    
      COMMENT ON COLUMN nop_tcc_record.error_stack IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_tcc_record.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_tcc_record.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_tcc_record.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_tcc_branch_record IS 'TCC事务分支记录';
                
      COMMENT ON COLUMN nop_tcc_branch_record.branch_id IS '事务分支ID';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.txn_id IS '事务ID';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.branch_no IS '事务分支序号';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.parent_branch_id IS '父分支ID';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.status IS '状态';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.expire_time IS '过期时间';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.service_name IS '服务名';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.service_method IS '服务方法';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.confirm_method IS '确认方法';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.cancel_method IS '取消方法';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.request_data IS '请求数据';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.error_code IS '错误码';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.error_message IS '错误消息';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.error_stack IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.begin_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.commit_error_code IS '提交阶段错误码';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.commit_error_message IS '提交阶段错误消息';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.commit_error_stack IS '提交阶段错误堆栈';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.cancel_error_code IS '取消阶段错误码';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.cancel_error_message IS '取消阶段错误消息';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.cancel_error_stack IS '取消阶段错误堆栈';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.retry_times IS '重试次数';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.max_retry_times IS '最大重试次数';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.next_retry_time IS '下次重试时间';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_tcc_branch_record.update_time IS '修改时间';
                    
