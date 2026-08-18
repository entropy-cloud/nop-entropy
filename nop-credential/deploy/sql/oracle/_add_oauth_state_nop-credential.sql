-- W9: OAuth 授权码闭环 state 绑定表（存量部署增量脚本，新建部署由 _create_ 覆盖）
-- 表结构经 model-first codegen（nop-credential.orm.xml），本脚本为手写增量（循 _add_tenant_ 先例）

CREATE TABLE nop_credential_oauth_state(
  STATE VARCHAR2(64) NOT NULL ,
  CREDENTIAL_ID VARCHAR2(50) NOT NULL ,
  USER_ID VARCHAR2(50) NOT NULL ,
  EXPIRE_AT NUMBER(20)  ,
  CONSUMED SMALLINT default 0  NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  constraint PK_nop_credential_oauth_state primary key (STATE)
);

COMMENT ON COLUMN nop_credential_oauth_state.STATE IS 'State令牌';
COMMENT ON COLUMN nop_credential_oauth_state.CREDENTIAL_ID IS '凭证ID';
COMMENT ON COLUMN nop_credential_oauth_state.USER_ID IS '发起人';
COMMENT ON COLUMN nop_credential_oauth_state.EXPIRE_AT IS '过期时间';
COMMENT ON COLUMN nop_credential_oauth_state.CONSUMED IS '已消费';
COMMENT ON COLUMN nop_credential_oauth_state.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN nop_credential_oauth_state.CREATED_BY IS '创建人';

CREATE INDEX IX_NOP_CREDENTIAL_OAUTH_STATE_CRED ON nop_credential_oauth_state (CREDENTIAL_ID);
CREATE INDEX IX_NOP_CREDENTIAL_OAUTH_STATE_EXPIRE ON nop_credential_oauth_state (EXPIRE_AT);
