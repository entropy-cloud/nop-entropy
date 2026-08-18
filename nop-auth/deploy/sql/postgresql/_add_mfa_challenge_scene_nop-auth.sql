
alter table nop_auth_mfa_challenge add column scene VARCHAR(20) NULL;
alter table nop_auth_mfa_challenge add column payload VARCHAR(500) NULL;
alter table nop_auth_mfa_challenge add column verified_at INT8 NULL;
