
    alter table nop_job_schedule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_fire add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_schedule drop constraint PK_nop_job_schedule;
alter table nop_job_schedule add constraint PK_nop_job_schedule primary key (NOP_TENANT_ID, job_schedule_id);

alter table nop_job_fire drop constraint PK_nop_job_fire;
alter table nop_job_fire add constraint PK_nop_job_fire primary key (NOP_TENANT_ID, job_fire_id);

alter table nop_job_task drop constraint PK_nop_job_task;
alter table nop_job_task add constraint PK_nop_job_task primary key (NOP_TENANT_ID, job_task_id);


