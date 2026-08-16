-- W11 Part B: 凭证级 RBAC 取用授权表（存量部署增量脚本，新建部署由 _create_ 覆盖）
-- 表结构经 model-first codegen（nop-credential.orm.xml），本脚本为手写增量（循 _add_oauth_state_ CREATE TABLE 先例）

CREATE TABLE nop_credential_auth(
  AUTH_ID VARCHAR2(50) NOT NULL ,
  CREDENTIAL_ID VARCHAR2(50) NOT NULL ,
  ROLE_ID VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  constraint UK_NOP_CREDENTIAL_AUTH_CRED_ROLE unique (CREDENTIAL_ID,ROLE_ID),
  constraint PK_nop_credential_auth primary key (AUTH_ID)
);

COMMENT ON TABLE nop_credential_auth IS '凭证取用授权';
COMMENT ON COLUMN nop_credential_auth.AUTH_ID IS '授权ID';
COMMENT ON COLUMN nop_credential_auth.CREDENTIAL_ID IS '凭证ID';
COMMENT ON COLUMN nop_credential_auth.ROLE_ID IS '角色ID';
COMMENT ON COLUMN nop_credential_auth.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN nop_credential_auth.CREATED_BY IS '创建人';
