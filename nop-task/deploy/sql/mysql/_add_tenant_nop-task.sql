
    alter table nop_task_definition add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_definition_auth add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_step_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_task_definition drop primary key;
alter table nop_task_definition add primary key (NOP_TENANT_ID, TASK_DEF_ID);

alter table nop_task_definition_auth drop primary key;
alter table nop_task_definition_auth add primary key (NOP_TENANT_ID, SID);

alter table nop_task_instance drop primary key;
alter table nop_task_instance add primary key (NOP_TENANT_ID, TASK_INSTANCE_ID);

alter table nop_task_step_instance drop primary key;
alter table nop_task_step_instance add primary key (NOP_TENANT_ID, STEP_INSTANCE_ID);


