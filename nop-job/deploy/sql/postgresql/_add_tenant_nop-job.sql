
    alter table nop_job_plan add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_plan drop constraint PK_nop_job_plan;
alter table nop_job_plan add constraint PK_nop_job_plan primary key (NOP_TENANT_ID, SID);


