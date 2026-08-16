-- W9: OAuth 授权码闭环 state 绑定表（存量部署增量脚本，新建部署由 _create_ 覆盖）
-- 表结构经 model-first codegen（nop-credential.orm.xml），本脚本为手写增量（循 _add_tenant_ 先例）

CREATE TABLE nop_credential_oauth_state(
  STATE VARCHAR(64) NOT NULL    COMMENT 'State令牌',
  CREDENTIAL_ID VARCHAR(50) NOT NULL    COMMENT '凭证ID',
  USER_ID VARCHAR(50) NOT NULL    COMMENT '发起人',
  EXPIRE_AT BIGINT NULL    COMMENT '过期时间',
  CONSUMED TINYINT default 0  NOT NULL    COMMENT '已消费',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  constraint PK_nop_credential_oauth_state primary key (STATE)
)CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

ALTER TABLE nop_credential_oauth_state COMMENT 'OAuth授权State绑定';

CREATE INDEX IX_NOP_CREDENTIAL_OAUTH_STATE_CRED ON nop_credential_oauth_state (CREDENTIAL_ID);
CREATE INDEX IX_NOP_CREDENTIAL_OAUTH_STATE_EXPIRE ON nop_credential_oauth_state (EXPIRE_AT);
