
CREATE TABLE nop_auth_mfa_credential(
  SID VARCHAR2(32) NOT NULL ,
  USER_ID VARCHAR2(50) NOT NULL ,
  CREDENTIAL_ID VARCHAR2(1400) NOT NULL ,
  PUBLIC_KEY VARCHAR2(1000) NOT NULL ,
  SIGN_COUNT NUMBER(20) default 0   ,
  TRANSPORTS VARCHAR2(100)  ,
  NAME VARCHAR2(100)  ,
  STATUS VARCHAR2(20) default 'enabled'  NOT NULL ,
  LAST_USED_AT TIMESTAMP  ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  TENANT_ID VARCHAR2(32)  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint UK_NOP_AUTH_MFA_CREDENTIAL_CRED unique (CREDENTIAL_ID),
  constraint PK_nop_auth_mfa_credential primary key (SID)
);

COMMENT ON TABLE nop_auth_mfa_credential IS 'WebAuthn凭证';
COMMENT ON COLUMN nop_auth_mfa_credential.SID IS '主键';
COMMENT ON COLUMN nop_auth_mfa_credential.USER_ID IS '用户ID';
COMMENT ON COLUMN nop_auth_mfa_credential.CREDENTIAL_ID IS '凭证ID';
COMMENT ON COLUMN nop_auth_mfa_credential.PUBLIC_KEY IS 'COSE公钥';
COMMENT ON COLUMN nop_auth_mfa_credential.SIGN_COUNT IS '签名计数';
COMMENT ON COLUMN nop_auth_mfa_credential.TRANSPORTS IS '传输方式';
COMMENT ON COLUMN nop_auth_mfa_credential.NAME IS '设备名称';
COMMENT ON COLUMN nop_auth_mfa_credential.STATUS IS '凭证状态';
COMMENT ON COLUMN nop_auth_mfa_credential.LAST_USED_AT IS '最近使用时间';
COMMENT ON COLUMN nop_auth_mfa_credential.DEL_FLAG IS '删除标识';
COMMENT ON COLUMN nop_auth_mfa_credential.VERSION IS '数据版本';
COMMENT ON COLUMN nop_auth_mfa_credential.TENANT_ID IS '租户ID';
COMMENT ON COLUMN nop_auth_mfa_credential.CREATED_BY IS '创建人';
COMMENT ON COLUMN nop_auth_mfa_credential.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN nop_auth_mfa_credential.UPDATED_BY IS '修改人';
COMMENT ON COLUMN nop_auth_mfa_credential.UPDATE_TIME IS '修改时间';
COMMENT ON COLUMN nop_auth_mfa_credential.REMARK IS '备注';
