
    alter table nop_batch_file add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_record_result add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_task add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_task_state add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_file drop primary key;
alter table nop_batch_file add primary key (NOP_TENANT_ID, SID);

alter table nop_batch_record_result drop primary key;
alter table nop_batch_record_result add primary key (NOP_TENANT_ID, TASK_ID,RECORD_KEY);

alter table nop_batch_task drop primary key;
alter table nop_batch_task add primary key (NOP_TENANT_ID, SID);

alter table nop_batch_task_state drop primary key;
alter table nop_batch_task_state add primary key (NOP_TENANT_ID, TASK_ID,FIELD_NAME);


