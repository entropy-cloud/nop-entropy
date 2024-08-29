
    alter table nop_job_definition add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_instance add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_job_definition drop constraint PK_nop_job_definition;
alter table nop_job_definition add constraint PK_nop_job_definition primary key (NOP_TENANT_ID, SID);

alter table nop_job_instance drop constraint PK_nop_job_instance;
alter table nop_job_instance add constraint PK_nop_job_instance primary key (NOP_TENANT_ID, JOB_ID);


