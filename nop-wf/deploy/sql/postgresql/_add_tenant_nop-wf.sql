
    alter table nop_wf_action add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_definition add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_instance add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_log add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_output add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_step_instance add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_step_instance_link add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_var add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_wf_work add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;


