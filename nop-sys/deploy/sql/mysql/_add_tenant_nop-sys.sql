
    alter table nop_sys_change_log add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_checker_record add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_cluster_leader add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_code_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_dict add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_dict_option add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_event add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_ext_field add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_i18n add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_lock add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_notice_template add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_obj_tag add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_sequence add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_service_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_tag add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_user_variable add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_variable add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_sys_change_log drop primary key;
alter table nop_sys_change_log add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_checker_record drop primary key;
alter table nop_sys_checker_record add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_cluster_leader drop primary key;
alter table nop_sys_cluster_leader add primary key (NOP_TENANT_ID, CLUSTER_ID);

alter table nop_sys_code_rule drop primary key;
alter table nop_sys_code_rule add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_dict drop primary key;
alter table nop_sys_dict add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_dict_option drop primary key;
alter table nop_sys_dict_option add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_event drop primary key;
alter table nop_sys_event add primary key (NOP_TENANT_ID, EVENT_ID);

alter table nop_sys_ext_field drop primary key;
alter table nop_sys_ext_field add primary key (NOP_TENANT_ID, ENTITY_NAME,ENTITY_ID,FIELD_NAME);

alter table nop_sys_i18n drop primary key;
alter table nop_sys_i18n add primary key (NOP_TENANT_ID, I18N_KEY,I18N_LOCALE);

alter table nop_sys_lock drop primary key;
alter table nop_sys_lock add primary key (NOP_TENANT_ID, LOCK_NAME,LOCK_GROUP);

alter table nop_sys_notice_template drop primary key;
alter table nop_sys_notice_template add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_obj_tag drop primary key;
alter table nop_sys_obj_tag add primary key (NOP_TENANT_ID, BIZ_OBJ_ID,BIZ_OBJ_NAME,TAG_ID);

alter table nop_sys_sequence drop primary key;
alter table nop_sys_sequence add primary key (NOP_TENANT_ID, SEQ_NAME);

alter table nop_sys_service_instance drop primary key;
alter table nop_sys_service_instance add primary key (NOP_TENANT_ID, INSTANCE_ID);

alter table nop_sys_tag drop primary key;
alter table nop_sys_tag add primary key (NOP_TENANT_ID, SID);

alter table nop_sys_user_variable drop primary key;
alter table nop_sys_user_variable add primary key (NOP_TENANT_ID, USER_ID,VAR_NAME);

alter table nop_sys_variable drop primary key;
alter table nop_sys_variable add primary key (NOP_TENANT_ID, VAR_NAME);


