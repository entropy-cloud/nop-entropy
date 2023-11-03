
    alter table nop_batch_file add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_record_result add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_task add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_batch_task_state add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;


