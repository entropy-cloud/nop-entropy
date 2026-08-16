-- W11 Part B: 凭证级 RBAC 取用授权表（存量部署增量脚本，新建部署由 _create_ 覆盖）
-- 表结构经 model-first codegen（nop-credential.orm.xml），本脚本为手写增量（循 _add_oauth_state_ CREATE TABLE 先例）

CREATE TABLE nop_credential_auth(
  AUTH_ID VARCHAR(50) NOT NULL    COMMENT '授权ID',
  CREDENTIAL_ID VARCHAR(50) NOT NULL    COMMENT '凭证ID',
  ROLE_ID VARCHAR(50) NOT NULL    COMMENT '角色ID',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  constraint UK_NOP_CREDENTIAL_AUTH_CRED_ROLE unique (CREDENTIAL_ID,ROLE_ID),
  constraint PK_nop_credential_auth primary key (AUTH_ID)
)CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

ALTER TABLE nop_credential_auth COMMENT '凭证取用授权';
