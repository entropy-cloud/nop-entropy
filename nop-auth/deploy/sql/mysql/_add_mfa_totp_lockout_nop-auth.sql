
alter table nop_auth_mfa_setting add column TOTP_FAIL_COUNT INTEGER default 0 NULL COMMENT 'TOTP失败计数';
alter table nop_auth_mfa_setting add column TOTP_FAIL_AT DATETIME(3) NULL COMMENT 'TOTP失败时间';
