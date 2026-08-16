
alter table nop_auth_mfa_challenge add column SCENE VARCHAR(20) NULL COMMENT '场景';
alter table nop_auth_mfa_challenge add column PAYLOAD VARCHAR(500) NULL COMMENT '场景数据';
alter table nop_auth_mfa_challenge add column VERIFIED_AT BIGINT NULL COMMENT '验证时间';
