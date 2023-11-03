
    alter table nop_sys_cluster_leader add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_code_rule add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_dict add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_dict_option add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_ext_field add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_i18n add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_lock add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_maker_checker_record add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_notice_template add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_sequence add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_user_variable add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_sys_variable add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;


