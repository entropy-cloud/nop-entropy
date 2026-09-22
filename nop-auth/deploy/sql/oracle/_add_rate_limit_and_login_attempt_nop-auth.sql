CREATE TABLE nop_auth_rate_limit_counter(
  COUNTER_KEY VARCHAR2(150) NOT NULL ,
  COUNTER_COUNT NUMBER(20) default 0   ,
  EXPIRE_AT NUMBER(20)  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50)  ,
  UPDATE_TIME TIMESTAMP  ,
  constraint PK_nop_auth_rate_limit_counter primary key (COUNTER_KEY)
);

CREATE INDEX IX_NOP_AUTH_RATE_LIMIT_EXPIRE ON nop_auth_rate_limit_counter(EXPIRE_AT);

CREATE TABLE nop_auth_login_attempt(
  ATTEMPT_KEY VARCHAR2(150) NOT NULL ,
  EXPIRE_AT NUMBER(20)  ,
  FAIL_COUNT INTEGER default 0   ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50)  ,
  UPDATE_TIME TIMESTAMP  ,
  constraint PK_nop_auth_login_attempt primary key (ATTEMPT_KEY)
);

CREATE INDEX IX_NOP_AUTH_LOGIN_ATTEMPT_EXPIRE ON nop_auth_login_attempt(EXPIRE_AT);

COMMENT ON TABLE nop_auth_rate_limit_counter IS '发码限流计数';
COMMENT ON COLUMN nop_auth_rate_limit_counter.COUNTER_KEY IS '限流Key';
COMMENT ON COLUMN nop_auth_rate_limit_counter.COUNTER_COUNT IS '计数';
COMMENT ON COLUMN nop_auth_rate_limit_counter.EXPIRE_AT IS '过期时间';
COMMENT ON COLUMN nop_auth_rate_limit_counter.CREATED_BY IS '创建人';
COMMENT ON COLUMN nop_auth_rate_limit_counter.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN nop_auth_rate_limit_counter.UPDATED_BY IS '修改人';
COMMENT ON COLUMN nop_auth_rate_limit_counter.UPDATE_TIME IS '修改时间';

COMMENT ON TABLE nop_auth_login_attempt IS '登录失败计数';
COMMENT ON COLUMN nop_auth_login_attempt.ATTEMPT_KEY IS '尝试Key';
COMMENT ON COLUMN nop_auth_login_attempt.EXPIRE_AT IS '过期时间';
COMMENT ON COLUMN nop_auth_login_attempt.FAIL_COUNT IS '失败计数';
COMMENT ON COLUMN nop_auth_login_attempt.CREATED_BY IS '创建人';
COMMENT ON COLUMN nop_auth_login_attempt.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN nop_auth_login_attempt.UPDATED_BY IS '修改人';
COMMENT ON COLUMN nop_auth_login_attempt.UPDATE_TIME IS '修改时间';
