
    alter table nop_job_schedule add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_task_log add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_fire add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_task add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_schedule drop constraint PK_nop_job_schedule;
alter table nop_job_schedule add constraint PK_nop_job_schedule primary key (NOP_TENANT_ID, JOB_SCHEDULE_ID);

alter table nop_job_task_log drop constraint PK_nop_job_task_log;
alter table nop_job_task_log add constraint PK_nop_job_task_log primary key (NOP_TENANT_ID, JOB_TASK_LOG_ID);

alter table nop_job_fire drop constraint PK_nop_job_fire;
alter table nop_job_fire add constraint PK_nop_job_fire primary key (NOP_TENANT_ID, JOB_FIRE_ID);

alter table nop_job_task drop constraint PK_nop_job_task;
alter table nop_job_task add constraint PK_nop_job_task primary key (NOP_TENANT_ID, JOB_TASK_ID);

alter table nop_job_schedule drop constraint UK_NOP_JOB_SCHEDULE_NS_GROUP_NAME;
alter table nop_job_schedule add constraint UK_NOP_JOB_SCHEDULE_NS_GROUP_NAME unique (NOP_TENANT_ID,NAMESPACE_ID,GROUP_ID,JOB_NAME);

                alter table nop_job_fire drop constraint UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE;
alter table nop_job_fire add constraint UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE unique (NOP_TENANT_ID,JOB_SCHEDULE_ID,SCHEDULED_FIRE_TIME,TRIGGER_SOURCE);

                alter table nop_job_task drop constraint UK_NOP_JOB_TASK_FIRE_NO;
alter table nop_job_task add constraint UK_NOP_JOB_TASK_FIRE_NO unique (NOP_TENANT_ID,JOB_FIRE_ID,TASK_NO);

                
