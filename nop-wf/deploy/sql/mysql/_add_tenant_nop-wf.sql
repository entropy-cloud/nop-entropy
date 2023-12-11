
    alter table nop_wf_action add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_definition add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_definition_auth add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_dyn_entity add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_instance add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_log add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_output add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_page add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_status_history add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_step_instance add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_step_instance_link add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_var add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_work add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_action drop primary key;
alter table nop_wf_action add primary key (NOP_TENANT_ID, SID);

alter table nop_wf_definition drop primary key;
alter table nop_wf_definition add primary key (NOP_TENANT_ID, WF_DEF_ID);

alter table nop_wf_definition_auth drop primary key;
alter table nop_wf_definition_auth add primary key (NOP_TENANT_ID, SID);

alter table nop_wf_dyn_entity drop primary key;
alter table nop_wf_dyn_entity add primary key (NOP_TENANT_ID, SID);

alter table nop_wf_instance drop primary key;
alter table nop_wf_instance add primary key (NOP_TENANT_ID, WF_ID);

alter table nop_wf_log drop primary key;
alter table nop_wf_log add primary key (NOP_TENANT_ID, SID);

alter table nop_wf_output drop primary key;
alter table nop_wf_output add primary key (NOP_TENANT_ID, WF_ID,FIELD_NAME);

alter table nop_wf_page drop primary key;
alter table nop_wf_page add primary key (NOP_TENANT_ID, SID);

alter table nop_wf_status_history drop primary key;
alter table nop_wf_status_history add primary key (NOP_TENANT_ID, SID);

alter table nop_wf_step_instance drop primary key;
alter table nop_wf_step_instance add primary key (NOP_TENANT_ID, STEP_ID);

alter table nop_wf_step_instance_link drop primary key;
alter table nop_wf_step_instance_link add primary key (NOP_TENANT_ID, WF_ID,STEP_ID,NEXT_STEP_ID);

alter table nop_wf_var drop primary key;
alter table nop_wf_var add primary key (NOP_TENANT_ID, WF_ID,FIELD_NAME);

alter table nop_wf_work drop primary key;
alter table nop_wf_work add primary key (NOP_TENANT_ID, WORK_ID);


