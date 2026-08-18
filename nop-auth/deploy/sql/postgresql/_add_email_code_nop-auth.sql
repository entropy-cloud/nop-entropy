CREATE TABLE nop_auth_email_code(
  code_key VARCHAR(100) NOT NULL ,
  email VARCHAR(100)  ,
  code VARCHAR(20)  ,
  expire_at INT8  ,
  fail_count INT4 default 0   ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50)  ,
  update_time TIMESTAMP  ,
  constraint PK_nop_auth_email_code primary key (code_key)
);

COMMENT ON TABLE nop_auth_email_code IS '邮件验证码';
COMMENT ON COLUMN nop_auth_email_code.code_key IS '验证码Key';
COMMENT ON COLUMN nop_auth_email_code.email IS '邮箱';
COMMENT ON COLUMN nop_auth_email_code.code IS '验证码';
COMMENT ON COLUMN nop_auth_email_code.expire_at IS '过期时间';
COMMENT ON COLUMN nop_auth_email_code.fail_count IS '失败计数';
COMMENT ON COLUMN nop_auth_email_code.created_by IS '创建人';
COMMENT ON COLUMN nop_auth_email_code.create_time IS '创建时间';
COMMENT ON COLUMN nop_auth_email_code.updated_by IS '修改人';
COMMENT ON COLUMN nop_auth_email_code.update_time IS '修改时间';
