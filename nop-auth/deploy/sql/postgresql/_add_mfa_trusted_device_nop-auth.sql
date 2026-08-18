CREATE TABLE nop_auth_mfa_trusted_device(
  sid VARCHAR(32) NOT NULL ,
  user_id VARCHAR(50) NOT NULL ,
  device_hash VARCHAR(64) NOT NULL ,
  device_name VARCHAR(100)  ,
  expire_at TIMESTAMP  ,
  last_used_at TIMESTAMP  ,
  tenant_id VARCHAR(32)  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint UK_NOP_AUTH_MFA_TRUSTED_DEVICE_UH unique (user_id,device_hash),
  constraint PK_nop_auth_mfa_trusted_device primary key (sid)
);

COMMENT ON TABLE nop_auth_mfa_trusted_device IS 'MFA可信设备';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.sid IS '主键';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.user_id IS '用户ID';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.device_hash IS '设备指纹';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.device_name IS '设备名称';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.expire_at IS '到期时间';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.last_used_at IS '最近使用时间';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.tenant_id IS '租户ID';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.created_by IS '创建人';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.create_time IS '创建时间';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.updated_by IS '修改人';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.update_time IS '修改时间';
