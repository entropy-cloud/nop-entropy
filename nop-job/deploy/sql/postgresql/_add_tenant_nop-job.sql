
    alter table nop_job_schedule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_task_log add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_fire add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_schedule drop constraint PK_nop_job_schedule;
alter table nop_job_schedule add constraint PK_nop_job_schedule primary key (NOP_TENANT_ID, job_schedule_id);

alter table nop_job_task_log drop constraint PK_nop_job_task_log;
alter table nop_job_task_log add constraint PK_nop_job_task_log primary key (NOP_TENANT_ID, job_task_log_id);

alter table nop_job_fire drop constraint PK_nop_job_fire;
alter table nop_job_fire add constraint PK_nop_job_fire primary key (NOP_TENANT_ID, job_fire_id);

alter table nop_job_task drop constraint PK_nop_job_task;
alter table nop_job_task add constraint PK_nop_job_task primary key (NOP_TENANT_ID, job_task_id);

alter table nop_job_schedule drop constraint UK_NOP_JOB_SCHEDULE_NS_GROUP_NAME;
alter table nop_job_schedule add constraint UK_NOP_JOB_SCHEDULE_NS_GROUP_NAME unique (NOP_TENANT_ID,namespace_id,group_id,job_name);

                alter table nop_job_fire drop constraint UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE;
alter table nop_job_fire add constraint UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE unique (NOP_TENANT_ID,job_schedule_id,scheduled_fire_time,trigger_source);

                alter table nop_job_task drop constraint UK_NOP_JOB_TASK_FIRE_NO;
alter table nop_job_task add constraint UK_NOP_JOB_TASK_FIRE_NO unique (NOP_TENANT_ID,job_fire_id,task_no);

                
