
    alter table nop_job_schedule add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_fire add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_task add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_schedule drop constraint PK_nop_job_schedule;
alter table nop_job_schedule add constraint PK_nop_job_schedule primary key (NOP_TENANT_ID, JOB_SCHEDULE_ID);

alter table nop_job_fire drop constraint PK_nop_job_fire;
alter table nop_job_fire add constraint PK_nop_job_fire primary key (NOP_TENANT_ID, JOB_FIRE_ID);

alter table nop_job_task drop constraint PK_nop_job_task;
alter table nop_job_task add constraint PK_nop_job_task primary key (NOP_TENANT_ID, JOB_TASK_ID);


