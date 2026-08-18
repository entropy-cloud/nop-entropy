
alter table nop_auth_mfa_setting add TOTP_FAIL_COUNT INTEGER default 0;
alter table nop_auth_mfa_setting add TOTP_FAIL_AT TIMESTAMP;

COMMENT ON COLUMN nop_auth_mfa_setting.totp_fail_count IS 'TOTP失败计数';
COMMENT ON COLUMN nop_auth_mfa_setting.totp_fail_at IS 'TOTP失败时间';
