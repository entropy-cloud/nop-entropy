-- W9: OAuth 授权码闭环 state 绑定表（存量部署增量脚本，新建部署由 _create_ 覆盖）
-- 表结构经 model-first codegen（nop-credential.orm.xml），本脚本为手写增量（循 _add_tenant_ 先例）

CREATE TABLE nop_credential_oauth_state(
  state VARCHAR(64) NOT NULL ,
  credential_id VARCHAR(50) NOT NULL ,
  user_id VARCHAR(50) NOT NULL ,
  expire_at INT8  ,
  consumed INT4 default 0  NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  constraint PK_nop_credential_oauth_state primary key (state)
);

COMMENT ON TABLE nop_credential_oauth_state IS 'OAuth授权State绑定';
COMMENT ON COLUMN nop_credential_oauth_state.state IS 'State令牌';
COMMENT ON COLUMN nop_credential_oauth_state.credential_id IS '凭证ID';
COMMENT ON COLUMN nop_credential_oauth_state.user_id IS '发起人';
COMMENT ON COLUMN nop_credential_oauth_state.expire_at IS '过期时间';
COMMENT ON COLUMN nop_credential_oauth_state.consumed IS '已消费';
COMMENT ON COLUMN nop_credential_oauth_state.create_time IS '创建时间';
COMMENT ON COLUMN nop_credential_oauth_state.created_by IS '创建人';

CREATE INDEX IX_NOP_CREDENTIAL_OAUTH_STATE_CRED ON nop_credential_oauth_state (credential_id);
CREATE INDEX IX_NOP_CREDENTIAL_OAUTH_STATE_EXPIRE ON nop_credential_oauth_state (expire_at);
