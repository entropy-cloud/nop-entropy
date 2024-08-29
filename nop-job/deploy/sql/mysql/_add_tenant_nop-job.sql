
    alter table nop_job_definition add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_definition drop primary key;
alter table nop_job_definition add primary key (NOP_TENANT_ID, SID);

alter table nop_job_instance drop primary key;
alter table nop_job_instance add primary key (NOP_TENANT_ID, JOB_ID);


