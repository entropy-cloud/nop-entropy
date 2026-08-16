
alter table nop_auth_mfa_challenge add SCENE VARCHAR2(20) NULL;
alter table nop_auth_mfa_challenge add PAYLOAD VARCHAR2(500) NULL;
alter table nop_auth_mfa_challenge add VERIFIED_AT NUMBER(19) NULL;
