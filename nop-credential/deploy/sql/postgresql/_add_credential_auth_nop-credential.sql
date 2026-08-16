-- W11 Part B: 凭证级 RBAC 取用授权表（存量部署增量脚本，新建部署由 _create_ 覆盖）
-- 表结构经 model-first codegen（nop-credential.orm.xml），本脚本为手写增量（循 _add_oauth_state_ CREATE TABLE 先例）

CREATE TABLE nop_credential_auth(
  auth_id VARCHAR(50) NOT NULL ,
  credential_id VARCHAR(50) NOT NULL ,
  role_id VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  constraint UK_NOP_CREDENTIAL_AUTH_CRED_ROLE unique (credential_id,role_id),
  constraint PK_nop_credential_auth primary key (auth_id)
);

COMMENT ON TABLE nop_credential_auth IS '凭证取用授权';
COMMENT ON COLUMN nop_credential_auth.auth_id IS '授权ID';
COMMENT ON COLUMN nop_credential_auth.credential_id IS '凭证ID';
COMMENT ON COLUMN nop_credential_auth.role_id IS '角色ID';
COMMENT ON COLUMN nop_credential_auth.create_time IS '创建时间';
COMMENT ON COLUMN nop_credential_auth.created_by IS '创建人';
