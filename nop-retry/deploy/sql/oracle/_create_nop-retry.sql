
CREATE TABLE nop_retry_policy(
  SID VARCHAR2(32) NOT NULL ,
  NAMESPACE_ID VARCHAR2(64) NOT NULL ,
  GROUP_ID VARCHAR2(64) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  STATUS VARCHAR2(1) default '1'   ,
  SAVE_RECORD_STRATEGY INTEGER default 2   ,
  IMMEDIATE_RETRY_COUNT INTEGER default 0   ,
  IMMEDIATE_RETRY_INTERVAL_MS NUMBER(20) default 1000   ,
  MAX_RETRY_COUNT INTEGER NOT NULL ,
  BACKOFF_STRATEGY INTEGER default 1   ,
  INITIAL_INTERVAL_MS NUMBER(20) default 5000   ,
  MAX_INTERVAL_MS NUMBER(20) default 60000   ,
  JITTER_RATIO NUMBER(5,4) default 0.5   ,
  EXECUTION_TIMEOUT_SECONDS INTEGER  ,
  DEADLINE_TIMEOUT_MS NUMBER(20)  ,
  BLOCK_STRATEGY INTEGER  ,
  CALLBACK_ENABLED VARCHAR2(1) default '0'   ,
  CALLBACK_TRIGGER_TYPE INTEGER  ,
  CALLBACK_POLICY_ID VARCHAR2(32)  ,
  OWNER_ID VARCHAR2(50)  ,
  DESCRIPTION VARCHAR2(4000)  ,
  VERSION INTEGER  ,
  CREATED_BY VARCHAR2(50)  ,
  CREATE_TIME TIMESTAMP  ,
  UPDATED_BY VARCHAR2(50)  ,
  UPDATE_TIME TIMESTAMP  ,
  RETRYING_TIMEOUT_MS NUMBER(20) default 600000   ,
  constraint PK_nop_retry_policy primary key (SID)
);

CREATE TABLE nop_retry_record(
  SID VARCHAR2(32) NOT NULL ,
  NAMESPACE_ID VARCHAR2(64) NOT NULL ,
  GROUP_ID VARCHAR2(64) NOT NULL ,
  POLICY_ID VARCHAR2(32)  ,
  IDEMPOTENT_ID VARCHAR2(64) NOT NULL ,
  BIZ_NO VARCHAR2(64)  ,
  TASK_TYPE INTEGER  ,
  STATUS INTEGER  ,
  RETRY_COUNT INTEGER  ,
  MAX_RETRY_COUNT INTEGER  ,
  NEXT_TRIGGER_TIME TIMESTAMP  ,
  PARTITION_INDEX INTEGER  ,
  EXECUTOR_NAME VARCHAR2(512)  ,
  REQUEST_PAYLOAD VARCHAR2(4000)  ,
  CONTEXT_PAYLOAD VARCHAR2(4000)  ,
  VERSION INTEGER  ,
  CREATED_BY VARCHAR2(50)  ,
  CREATE_TIME TIMESTAMP  ,
  UPDATED_BY VARCHAR2(50)  ,
  UPDATE_TIME TIMESTAMP  ,
  SERVICE_NAME VARCHAR2(200)  ,
  SERVICE_METHOD VARCHAR2(100)  ,
  constraint PK_nop_retry_record primary key (SID)
);

CREATE TABLE nop_retry_attempt(
  SID VARCHAR2(32) NOT NULL ,
  RECORD_ID VARCHAR2(32) NOT NULL ,
  ATTEMPT_NO INTEGER  ,
  STATUS INTEGER  ,
  START_TIME TIMESTAMP  ,
  END_TIME TIMESTAMP  ,
  DURATION_MS NUMBER(20)  ,
  ERROR_CODE VARCHAR2(50)  ,
  ERROR_MESSAGE VARCHAR2(500)  ,
  ERROR_STACK VARCHAR2(4000)  ,
  CLIENT_ADDRESS VARCHAR2(100)  ,
  REASON VARCHAR2(500)  ,
  REQUEST_PAYLOAD_SNAPSHOT VARCHAR2(4000)  ,
  VERSION INTEGER  ,
  CREATED_BY VARCHAR2(50)  ,
  CREATE_TIME TIMESTAMP  ,
  UPDATED_BY VARCHAR2(50)  ,
  UPDATE_TIME TIMESTAMP  ,
  constraint PK_nop_retry_attempt primary key (SID)
);

CREATE TABLE nop_retry_dead_letter(
  SID VARCHAR2(32) NOT NULL ,
  NAMESPACE_ID VARCHAR2(64) NOT NULL ,
  GROUP_ID VARCHAR2(64) NOT NULL ,
  POLICY_ID VARCHAR2(32)  ,
  RECORD_ID VARCHAR2(32) NOT NULL ,
  IDEMPOTENT_ID VARCHAR2(64) NOT NULL ,
  BIZ_NO VARCHAR2(64)  ,
  EXECUTOR_NAME VARCHAR2(512)  ,
  REQUEST_PAYLOAD VARCHAR2(4000)  ,
  FAILURE_CODE VARCHAR2(50)  ,
  FAILURE_MESSAGE VARCHAR2(500)  ,
  ERROR_STACK VARCHAR2(4000)  ,
  FINAL_STATUS INTEGER  ,
  VERSION INTEGER  ,
  CREATED_BY VARCHAR2(50)  ,
  CREATE_TIME TIMESTAMP  ,
  UPDATED_BY VARCHAR2(50)  ,
  UPDATE_TIME TIMESTAMP  ,
  SERVICE_NAME VARCHAR2(200)  ,
  SERVICE_METHOD VARCHAR2(100)  ,
  constraint PK_nop_retry_dead_letter primary key (SID)
);


      COMMENT ON TABLE nop_retry_policy IS '重试策略';
                
      COMMENT ON COLUMN nop_retry_policy.SID IS '主键';
                    
      COMMENT ON COLUMN nop_retry_policy.NAMESPACE_ID IS '命名空间ID';
                    
      COMMENT ON COLUMN nop_retry_policy.GROUP_ID IS '组ID';
                    
      COMMENT ON COLUMN nop_retry_policy.NAME IS '策略名称';
                    
      COMMENT ON COLUMN nop_retry_policy.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_retry_policy.SAVE_RECORD_STRATEGY IS '保存记录策略';
                    
      COMMENT ON COLUMN nop_retry_policy.IMMEDIATE_RETRY_COUNT IS '立刻重试次数';
                    
      COMMENT ON COLUMN nop_retry_policy.IMMEDIATE_RETRY_INTERVAL_MS IS '立刻重试间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.MAX_RETRY_COUNT IS '最大重试次数';
                    
      COMMENT ON COLUMN nop_retry_policy.BACKOFF_STRATEGY IS '退避策略';
                    
      COMMENT ON COLUMN nop_retry_policy.INITIAL_INTERVAL_MS IS '初始间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.MAX_INTERVAL_MS IS '最大间隔(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.JITTER_RATIO IS '抖动比例';
                    
      COMMENT ON COLUMN nop_retry_policy.EXECUTION_TIMEOUT_SECONDS IS '执行超时(秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.DEADLINE_TIMEOUT_MS IS '截止超时(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_policy.BLOCK_STRATEGY IS '阻塞策略';
                    
      COMMENT ON COLUMN nop_retry_policy.CALLBACK_ENABLED IS '启用回调';
                    
      COMMENT ON COLUMN nop_retry_policy.CALLBACK_TRIGGER_TYPE IS '回调触发类型';
                    
      COMMENT ON COLUMN nop_retry_policy.CALLBACK_POLICY_ID IS '回调策略ID';
                    
      COMMENT ON COLUMN nop_retry_policy.OWNER_ID IS '所有者ID';
                    
      COMMENT ON COLUMN nop_retry_policy.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_retry_policy.VERSION IS '版本';
                    
      COMMENT ON COLUMN nop_retry_policy.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_policy.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_policy.UPDATED_BY IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_policy.UPDATE_TIME IS '更新时间';
                    
      COMMENT ON COLUMN nop_retry_policy.RETRYING_TIMEOUT_MS IS '执行中锁定超时(毫秒)';
                    
      COMMENT ON TABLE nop_retry_record IS '重试记录';
                
      COMMENT ON COLUMN nop_retry_record.SID IS '主键';
                    
      COMMENT ON COLUMN nop_retry_record.NAMESPACE_ID IS '命名空间ID';
                    
      COMMENT ON COLUMN nop_retry_record.GROUP_ID IS '组ID';
                    
      COMMENT ON COLUMN nop_retry_record.POLICY_ID IS '策略ID';
                    
      COMMENT ON COLUMN nop_retry_record.IDEMPOTENT_ID IS '幂等ID';
                    
      COMMENT ON COLUMN nop_retry_record.BIZ_NO IS '业务号';
                    
      COMMENT ON COLUMN nop_retry_record.TASK_TYPE IS '任务类型';
                    
      COMMENT ON COLUMN nop_retry_record.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_retry_record.RETRY_COUNT IS '重试次数';
                    
      COMMENT ON COLUMN nop_retry_record.MAX_RETRY_COUNT IS '最大重试次数';
                    
      COMMENT ON COLUMN nop_retry_record.NEXT_TRIGGER_TIME IS '下次触发时间';
                    
      COMMENT ON COLUMN nop_retry_record.PARTITION_INDEX IS '分区索引';
                    
      COMMENT ON COLUMN nop_retry_record.EXECUTOR_NAME IS '执行器名称';
                    
      COMMENT ON COLUMN nop_retry_record.REQUEST_PAYLOAD IS '请求参数';
                    
      COMMENT ON COLUMN nop_retry_record.CONTEXT_PAYLOAD IS '上下文参数';
                    
      COMMENT ON COLUMN nop_retry_record.VERSION IS '版本';
                    
      COMMENT ON COLUMN nop_retry_record.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_record.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_record.UPDATED_BY IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_record.UPDATE_TIME IS '更新时间';
                    
      COMMENT ON COLUMN nop_retry_record.SERVICE_NAME IS '服务名';
                    
      COMMENT ON COLUMN nop_retry_record.SERVICE_METHOD IS '服务方法';
                    
      COMMENT ON TABLE nop_retry_attempt IS '重试尝试';
                
      COMMENT ON COLUMN nop_retry_attempt.SID IS '主键';
                    
      COMMENT ON COLUMN nop_retry_attempt.RECORD_ID IS '记录ID';
                    
      COMMENT ON COLUMN nop_retry_attempt.ATTEMPT_NO IS '尝试序号';
                    
      COMMENT ON COLUMN nop_retry_attempt.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_retry_attempt.START_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_retry_attempt.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_retry_attempt.DURATION_MS IS '持续时间(毫秒)';
                    
      COMMENT ON COLUMN nop_retry_attempt.ERROR_CODE IS '错误码';
                    
      COMMENT ON COLUMN nop_retry_attempt.ERROR_MESSAGE IS '错误消息';
                    
      COMMENT ON COLUMN nop_retry_attempt.ERROR_STACK IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_retry_attempt.CLIENT_ADDRESS IS '客户端地址';
                    
      COMMENT ON COLUMN nop_retry_attempt.REASON IS '原因';
                    
      COMMENT ON COLUMN nop_retry_attempt.REQUEST_PAYLOAD_SNAPSHOT IS '请求参数快照';
                    
      COMMENT ON COLUMN nop_retry_attempt.VERSION IS '版本';
                    
      COMMENT ON COLUMN nop_retry_attempt.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_attempt.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_attempt.UPDATED_BY IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_attempt.UPDATE_TIME IS '更新时间';
                    
      COMMENT ON TABLE nop_retry_dead_letter IS '重试死信';
                
      COMMENT ON COLUMN nop_retry_dead_letter.SID IS '主键';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.NAMESPACE_ID IS '命名空间ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.GROUP_ID IS '组ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.POLICY_ID IS '策略ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.RECORD_ID IS '记录ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.IDEMPOTENT_ID IS '幂等ID';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.BIZ_NO IS '业务号';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.EXECUTOR_NAME IS '执行器名称';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.REQUEST_PAYLOAD IS '请求参数';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.FAILURE_CODE IS '失败码';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.FAILURE_MESSAGE IS '失败消息';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.ERROR_STACK IS '错误堆栈';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.FINAL_STATUS IS '最终状态';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.VERSION IS '版本';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.UPDATED_BY IS '更新人';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.UPDATE_TIME IS '更新时间';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.SERVICE_NAME IS '服务名';
                    
      COMMENT ON COLUMN nop_retry_dead_letter.SERVICE_METHOD IS '服务方法';
                    
