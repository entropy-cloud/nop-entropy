
CREATE TABLE nop_auth_role_mfa_policy(
  ROLE_ID VARCHAR2(50) NOT NULL ,
  MIN_MFA_LEVEL INTEGER NOT NULL ,
  ALLOW_TRUSTED_DEVICE SMALLINT default 1 NOT NULL ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  TENANT_ID VARCHAR2(32)  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_role_mfa_policy primary key (ROLE_ID)
);

COMMENT ON TABLE nop_auth_role_mfa_policy IS '角色MFA策略';
COMMENT ON COLUMN nop_auth_role_mfa_policy.role_id IS '角色ID';
COMMENT ON COLUMN nop_auth_role_mfa_policy.min_mfa_level IS '最低MFA强度';
COMMENT ON COLUMN nop_auth_role_mfa_policy.allow_trusted_device IS '允许可信设备';
