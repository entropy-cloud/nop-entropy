
    alter table nop_job_definition add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_assignment add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_instance_his add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_definition drop constraint PK_nop_job_definition;
alter table nop_job_definition add constraint PK_nop_job_definition primary key (NOP_TENANT_ID, SID)                ;

alter table nop_job_assignment drop constraint PK_nop_job_assignment;
alter table nop_job_assignment add constraint PK_nop_job_assignment primary key (NOP_TENANT_ID, SERVER_ID)
                ;

alter table nop_job_instance_his drop constraint PK_nop_job_instance_his;
alter table nop_job_instance_his add constraint PK_nop_job_instance_his primary key (NOP_TENANT_ID, JOB_INSTANCE_ID)
                ;

alter table nop_job_instance drop constraint PK_nop_job_instance;
alter table nop_job_instance add constraint PK_nop_job_instance primary key (NOP_TENANT_ID, JOB_INSTANCE_ID)
                ;


