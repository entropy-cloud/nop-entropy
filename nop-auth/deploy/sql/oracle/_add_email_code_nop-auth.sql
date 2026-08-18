CREATE TABLE nop_auth_email_code(
  CODE_KEY VARCHAR2(100) NOT NULL ,
  EMAIL VARCHAR2(100)  ,
  CODE VARCHAR2(20)  ,
  EXPIRE_AT NUMBER(20)  ,
  FAIL_COUNT INTEGER default 0   ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50)  ,
  UPDATE_TIME TIMESTAMP  ,
  constraint PK_nop_auth_email_code primary key (CODE_KEY)
);

COMMENT ON TABLE nop_auth_email_code IS '邮件验证码';
COMMENT ON COLUMN nop_auth_email_code.CODE_KEY IS '验证码Key';
COMMENT ON COLUMN nop_auth_email_code.EMAIL IS '邮箱';
COMMENT ON COLUMN nop_auth_email_code.CODE IS '验证码';
COMMENT ON COLUMN nop_auth_email_code.EXPIRE_AT IS '过期时间';
COMMENT ON COLUMN nop_auth_email_code.FAIL_COUNT IS '失败计数';
COMMENT ON COLUMN nop_auth_email_code.CREATED_BY IS '创建人';
COMMENT ON COLUMN nop_auth_email_code.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN nop_auth_email_code.UPDATED_BY IS '修改人';
COMMENT ON COLUMN nop_auth_email_code.UPDATE_TIME IS '修改时间';
