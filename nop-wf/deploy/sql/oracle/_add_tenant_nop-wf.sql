
    alter table nop_wf_definition add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_instance add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_definition_auth add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_status_history add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_step_instance add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_output add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_var add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_step_instance_link add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_action add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_work add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_log add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_wf_definition drop constraint PK_nop_wf_definition;
alter table nop_wf_definition add constraint PK_nop_wf_definition primary key (NOP_TENANT_ID, WF_DEF_ID);

alter table nop_wf_instance drop constraint PK_nop_wf_instance;
alter table nop_wf_instance add constraint PK_nop_wf_instance primary key (NOP_TENANT_ID, WF_ID);

alter table nop_wf_definition_auth drop constraint PK_nop_wf_definition_auth;
alter table nop_wf_definition_auth add constraint PK_nop_wf_definition_auth primary key (NOP_TENANT_ID, SID);

alter table nop_wf_status_history drop constraint PK_nop_wf_status_history;
alter table nop_wf_status_history add constraint PK_nop_wf_status_history primary key (NOP_TENANT_ID, SID);

alter table nop_wf_step_instance drop constraint PK_nop_wf_step_instance;
alter table nop_wf_step_instance add constraint PK_nop_wf_step_instance primary key (NOP_TENANT_ID, STEP_ID);

alter table nop_wf_output drop constraint PK_nop_wf_output;
alter table nop_wf_output add constraint PK_nop_wf_output primary key (NOP_TENANT_ID, WF_ID,FIELD_NAME);

alter table nop_wf_var drop constraint PK_nop_wf_var;
alter table nop_wf_var add constraint PK_nop_wf_var primary key (NOP_TENANT_ID, WF_ID,FIELD_NAME);

alter table nop_wf_step_instance_link drop constraint PK_nop_wf_step_instance_link;
alter table nop_wf_step_instance_link add constraint PK_nop_wf_step_instance_link primary key (NOP_TENANT_ID, WF_ID,STEP_ID,NEXT_STEP_ID);

alter table nop_wf_action drop constraint PK_nop_wf_action;
alter table nop_wf_action add constraint PK_nop_wf_action primary key (NOP_TENANT_ID, SID);

alter table nop_wf_work drop constraint PK_nop_wf_work;
alter table nop_wf_work add constraint PK_nop_wf_work primary key (NOP_TENANT_ID, WORK_ID);

alter table nop_wf_log drop constraint PK_nop_wf_log;
alter table nop_wf_log add constraint PK_nop_wf_log primary key (NOP_TENANT_ID, SID);


