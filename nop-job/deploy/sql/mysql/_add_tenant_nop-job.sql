
    alter table nop_job_schedule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_fire add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_schedule drop primary key;
alter table nop_job_schedule add primary key (NOP_TENANT_ID, JOB_SCHEDULE_ID);

alter table nop_job_fire drop primary key;
alter table nop_job_fire add primary key (NOP_TENANT_ID, JOB_FIRE_ID);

alter table nop_job_task drop primary key;
alter table nop_job_task add primary key (NOP_TENANT_ID, JOB_TASK_ID);


