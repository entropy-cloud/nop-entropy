
    alter table nop_task_definition add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_definition_auth add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_step_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_definition drop constraint PK_nop_task_definition;
alter table nop_task_definition add constraint PK_nop_task_definition primary key (NOP_TENANT_ID, task_def_id);

alter table nop_task_instance drop constraint PK_nop_task_instance;
alter table nop_task_instance add constraint PK_nop_task_instance primary key (NOP_TENANT_ID, task_instance_id);

alter table nop_task_definition_auth drop constraint PK_nop_task_definition_auth;
alter table nop_task_definition_auth add constraint PK_nop_task_definition_auth primary key (NOP_TENANT_ID, sid);

alter table nop_task_step_instance drop constraint PK_nop_task_step_instance;
alter table nop_task_step_instance add constraint PK_nop_task_step_instance primary key (NOP_TENANT_ID, step_instance_id);


