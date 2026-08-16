
CREATE TABLE nop_credential(
  credential_id VARCHAR(50) NOT NULL ,
  credential_name VARCHAR(100)  ,
  type_name VARCHAR(100)  ,
  data VARCHAR(4000)  ,
  status VARCHAR(20) default 'enabled'   ,
  del_flag INT4 NOT NULL ,
  usage_scope VARCHAR(50)  ,
  last_used_at TIMESTAMP  ,
  expire_at TIMESTAMP  ,
  test_result VARCHAR(500)  ,
  version INT4 NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP  ,
  updated_by VARCHAR(50)  ,
  remark VARCHAR(200)  ,
  constraint PK_nop_credential primary key (credential_id)
);

CREATE TABLE nop_credential_oauth_state(
  state VARCHAR(64) NOT NULL ,
  credential_id VARCHAR(50) NOT NULL ,
  user_id VARCHAR(50) NOT NULL ,
  expire_at INT8  ,
  consumed INT4 default 0  NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  constraint PK_nop_credential_oauth_state primary key (state)
);

CREATE TABLE nop_credential_usage(
  usage_id VARCHAR(50) NOT NULL ,
  credential_id VARCHAR(50) NOT NULL ,
  consumer_ref VARCHAR(200) NOT NULL ,
  create_time TIMESTAMP  ,
  constraint UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER unique (credential_id,consumer_ref),
  constraint PK_nop_credential_usage primary key (usage_id)
);


      COMMENT ON TABLE nop_credential IS '加密凭证';
                
      COMMENT ON COLUMN nop_credential.credential_id IS '凭证ID';
                    
      COMMENT ON COLUMN nop_credential.credential_name IS '凭证名';
                    
      COMMENT ON COLUMN nop_credential.type_name IS '凭证类型';
                    
      COMMENT ON COLUMN nop_credential.data IS '加密数据';
                    
      COMMENT ON COLUMN nop_credential.status IS '状态';
                    
      COMMENT ON COLUMN nop_credential.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_credential.usage_scope IS '使用范围';
                    
      COMMENT ON COLUMN nop_credential.last_used_at IS '最后使用时间';
                    
      COMMENT ON COLUMN nop_credential.expire_at IS '过期时间';
                    
      COMMENT ON COLUMN nop_credential.test_result IS '连通性测试结果';
                    
      COMMENT ON COLUMN nop_credential.version IS '版本';
                    
      COMMENT ON COLUMN nop_credential.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_credential.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_credential.update_time IS '更新时间';
                    
      COMMENT ON COLUMN nop_credential.updated_by IS '更新人';
                    
      COMMENT ON COLUMN nop_credential.remark IS '备注';
                    
      COMMENT ON TABLE nop_credential_oauth_state IS 'OAuth授权State绑定';
                
      COMMENT ON COLUMN nop_credential_oauth_state.state IS 'State令牌';
                    
      COMMENT ON COLUMN nop_credential_oauth_state.credential_id IS '凭证ID';
                    
      COMMENT ON COLUMN nop_credential_oauth_state.user_id IS '发起人';
                    
      COMMENT ON COLUMN nop_credential_oauth_state.expire_at IS '过期时间';
                    
      COMMENT ON COLUMN nop_credential_oauth_state.consumed IS '已消费';
                    
      COMMENT ON COLUMN nop_credential_oauth_state.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_credential_oauth_state.created_by IS '创建人';
                    
      COMMENT ON TABLE nop_credential_usage IS '凭证使用记录';
                
      COMMENT ON COLUMN nop_credential_usage.usage_id IS '使用记录ID';
                    
      COMMENT ON COLUMN nop_credential_usage.credential_id IS '凭证ID';
                    
      COMMENT ON COLUMN nop_credential_usage.consumer_ref IS '消费者引用';
                    
      COMMENT ON COLUMN nop_credential_usage.create_time IS '创建时间';
                    
