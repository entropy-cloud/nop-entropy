
CREATE TABLE nop_auth_role_mfa_policy(
  role_id VARCHAR(50) NOT NULL ,
  min_mfa_level INT4 NOT NULL ,
  allow_trusted_device INT4 default 1 NOT NULL ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  tenant_id VARCHAR(32)  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_role_mfa_policy primary key (role_id)
);

COMMENT ON TABLE nop_auth_role_mfa_policy IS '角色MFA策略';
COMMENT ON COLUMN nop_auth_role_mfa_policy.role_id IS '角色ID';
COMMENT ON COLUMN nop_auth_role_mfa_policy.min_mfa_level IS '最低MFA强度';
COMMENT ON COLUMN nop_auth_role_mfa_policy.allow_trusted_device IS '允许可信设备';
