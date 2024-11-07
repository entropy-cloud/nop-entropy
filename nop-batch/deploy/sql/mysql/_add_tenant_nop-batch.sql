
    alter table nop_batch_file add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_record_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_task_var add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_file drop primary key;
alter table nop_batch_file add primary key (NOP_TENANT_ID, SID);

alter table nop_batch_record_result drop primary key;
alter table nop_batch_record_result add primary key (NOP_TENANT_ID, TASK_ID,RECORD_KEY);

alter table nop_batch_task drop primary key;
alter table nop_batch_task add primary key (NOP_TENANT_ID, SID);

alter table nop_batch_task_var drop primary key;
alter table nop_batch_task_var add primary key (NOP_TENANT_ID, TASK_ID,FIELD_NAME);


