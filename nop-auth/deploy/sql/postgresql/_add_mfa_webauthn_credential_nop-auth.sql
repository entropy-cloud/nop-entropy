
CREATE TABLE nop_auth_mfa_credential(
  sid VARCHAR(32) NOT NULL ,
  user_id VARCHAR(50) NOT NULL ,
  credential_id VARCHAR(1400) NOT NULL ,
  public_key VARCHAR(1000) NOT NULL ,
  sign_count INT8 default 0   ,
  transports VARCHAR(100)  ,
  name VARCHAR(100)  ,
  status VARCHAR(20) default 'enabled'  NOT NULL ,
  last_used_at TIMESTAMP  ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  tenant_id VARCHAR(32)  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint UK_NOP_AUTH_MFA_CREDENTIAL_CRED unique (credential_id),
  constraint PK_nop_auth_mfa_credential primary key (sid)
);

COMMENT ON TABLE nop_auth_mfa_credential IS 'WebAuthn凭证';
COMMENT ON COLUMN nop_auth_mfa_credential.sid IS '主键';
COMMENT ON COLUMN nop_auth_mfa_credential.user_id IS '用户ID';
COMMENT ON COLUMN nop_auth_mfa_credential.credential_id IS '凭证ID';
COMMENT ON COLUMN nop_auth_mfa_credential.public_key IS 'COSE公钥';
COMMENT ON COLUMN nop_auth_mfa_credential.sign_count IS '签名计数';
COMMENT ON COLUMN nop_auth_mfa_credential.transports IS '传输方式';
COMMENT ON COLUMN nop_auth_mfa_credential.name IS '设备名称';
COMMENT ON COLUMN nop_auth_mfa_credential.status IS '凭证状态';
COMMENT ON COLUMN nop_auth_mfa_credential.last_used_at IS '最近使用时间';
COMMENT ON COLUMN nop_auth_mfa_credential.del_flag IS '删除标识';
COMMENT ON COLUMN nop_auth_mfa_credential.version IS '数据版本';
COMMENT ON COLUMN nop_auth_mfa_credential.tenant_id IS '租户ID';
COMMENT ON COLUMN nop_auth_mfa_credential.created_by IS '创建人';
COMMENT ON COLUMN nop_auth_mfa_credential.create_time IS '创建时间';
COMMENT ON COLUMN nop_auth_mfa_credential.updated_by IS '修改人';
COMMENT ON COLUMN nop_auth_mfa_credential.update_time IS '修改时间';
COMMENT ON COLUMN nop_auth_mfa_credential.remark IS '备注';
