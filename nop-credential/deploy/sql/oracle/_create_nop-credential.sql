
CREATE TABLE nop_credential(
  CREDENTIAL_ID VARCHAR2(50) NOT NULL ,
  CREDENTIAL_NAME VARCHAR2(100)  ,
  TYPE_NAME VARCHAR2(100)  ,
  DATA VARCHAR2(4000)  ,
  STATUS VARCHAR2(20) default 'enabled'   ,
  DEL_FLAG SMALLINT NOT NULL ,
  USAGE_SCOPE VARCHAR2(50)  ,
  LAST_USED_AT TIMESTAMP  ,
  EXPIRE_AT TIMESTAMP  ,
  TEST_RESULT VARCHAR2(500)  ,
  VERSION INTEGER NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP  ,
  UPDATED_BY VARCHAR2(50)  ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_credential primary key (CREDENTIAL_ID)
);

CREATE TABLE nop_credential_usage(
  USAGE_ID VARCHAR2(50) NOT NULL ,
  CREDENTIAL_ID VARCHAR2(50) NOT NULL ,
  CONSUMER_REF VARCHAR2(200) NOT NULL ,
  CREATE_TIME TIMESTAMP  ,
  constraint UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER unique (CREDENTIAL_ID,CONSUMER_REF),
  constraint PK_nop_credential_usage primary key (USAGE_ID)
);


      COMMENT ON TABLE nop_credential IS '加密凭证';
                
      COMMENT ON COLUMN nop_credential.CREDENTIAL_ID IS '凭证ID';
                    
      COMMENT ON COLUMN nop_credential.CREDENTIAL_NAME IS '凭证名';
                    
      COMMENT ON COLUMN nop_credential.TYPE_NAME IS '凭证类型';
                    
      COMMENT ON COLUMN nop_credential.DATA IS '加密数据';
                    
      COMMENT ON COLUMN nop_credential.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_credential.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_credential.USAGE_SCOPE IS '使用范围';
                    
      COMMENT ON COLUMN nop_credential.LAST_USED_AT IS '最后使用时间';
                    
      COMMENT ON COLUMN nop_credential.EXPIRE_AT IS '过期时间';
                    
      COMMENT ON COLUMN nop_credential.TEST_RESULT IS '连通性测试结果';
                    
      COMMENT ON COLUMN nop_credential.VERSION IS '版本';
                    
      COMMENT ON COLUMN nop_credential.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_credential.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_credential.UPDATE_TIME IS '更新时间';
                    
      COMMENT ON COLUMN nop_credential.UPDATED_BY IS '更新人';
                    
      COMMENT ON COLUMN nop_credential.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_credential_usage IS '凭证使用记录';
                
      COMMENT ON COLUMN nop_credential_usage.USAGE_ID IS '使用记录ID';
                    
      COMMENT ON COLUMN nop_credential_usage.CREDENTIAL_ID IS '凭证ID';
                    
      COMMENT ON COLUMN nop_credential_usage.CONSUMER_REF IS '消费者引用';
                    
      COMMENT ON COLUMN nop_credential_usage.CREATE_TIME IS '创建时间';
                    
