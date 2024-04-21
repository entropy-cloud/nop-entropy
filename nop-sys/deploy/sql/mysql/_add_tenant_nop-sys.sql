
    alter table nop_sys_cluster_leader add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_code_rule add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_dict add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_dict_option add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_ext_field add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_i18n add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_lock add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_maker_checker_record add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_notice_template add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_sequence add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_user_variable add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_variable add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_cluster_leader drop primary key;
alter table nop_sys_cluster_leader add primary key (NOP_TENANT_ID, CLUSTER_ID);

alter table nop_sys_code_rule drop primary key;
alter table nop_sys_code_rule add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_dict drop primary key;
alter table nop_sys_dict add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_dict_option drop primary key;
alter table nop_sys_dict_option add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_ext_field drop primary key;
alter table nop_sys_ext_field add primary key (NOP_TENANT_ID, ENTITY_NAME,ENTITY_ID,FIELD_NAME);

alter table nop_sys_i18n drop primary key;
alter table nop_sys_i18n add primary key (NOP_TENANT_ID, I18N_KEY,I18N_LOCALE);

alter table nop_sys_lock drop primary key;
alter table nop_sys_lock add primary key (NOP_TENANT_ID, LOCK_GROUP,LOCK_NAME);

alter table nop_sys_maker_checker_record drop primary key;
alter table nop_sys_maker_checker_record add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_notice_template drop primary key;
alter table nop_sys_notice_template add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_sequence drop primary key;
alter table nop_sys_sequence add primary key (NOP_TENANT_ID, SEQ_NAME);

alter table nop_sys_user_variable drop primary key;
alter table nop_sys_user_variable add primary key (NOP_TENANT_ID, USER_ID,VAR_NAME);

alter table nop_sys_variable drop primary key;
alter table nop_sys_variable add primary key (NOP_TENANT_ID, VAR_NAME);

;

;

;

;

;

;

;

;

;

;

;

;


