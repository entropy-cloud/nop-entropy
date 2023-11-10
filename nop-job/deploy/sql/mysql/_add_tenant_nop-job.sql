
    alter table nop_job_plan add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_plan drop primary key;
alter table nop_job_plan add primary key (NOP_TENANT_ID, SID);


