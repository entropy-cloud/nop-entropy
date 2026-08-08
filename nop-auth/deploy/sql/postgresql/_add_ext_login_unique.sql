
alter table nop_auth_ext_login add constraint UK_NOP_AUTH_EXT_LOGIN_TYPE_EXTID unique (login_type,ext_id);
