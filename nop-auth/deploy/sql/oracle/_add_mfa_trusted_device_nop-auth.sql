CREATE TABLE nop_auth_mfa_trusted_device(
  SID VARCHAR2(32) NOT NULL ,
  USER_ID VARCHAR2(50) NOT NULL ,
  DEVICE_HASH VARCHAR2(64) NOT NULL ,
  DEVICE_NAME VARCHAR2(100)  ,
  EXPIRE_AT TIMESTAMP  ,
  LAST_USED_AT TIMESTAMP  ,
  TENANT_ID VARCHAR2(32)  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint UK_NOP_AUTH_MFA_TRUSTED_DEVICE_UH unique (USER_ID,DEVICE_HASH),
  constraint PK_nop_auth_mfa_trusted_device primary key (SID)
);

COMMENT ON TABLE nop_auth_mfa_trusted_device IS 'MFA可信设备';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.SID IS '主键';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.USER_ID IS '用户ID';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.DEVICE_HASH IS '设备指纹';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.DEVICE_NAME IS '设备名称';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.EXPIRE_AT IS '到期时间';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.LAST_USED_AT IS '最近使用时间';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.TENANT_ID IS '租户ID';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.CREATED_BY IS '创建人';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.UPDATED_BY IS '修改人';
COMMENT ON COLUMN nop_auth_mfa_trusted_device.UPDATE_TIME IS '修改时间';
