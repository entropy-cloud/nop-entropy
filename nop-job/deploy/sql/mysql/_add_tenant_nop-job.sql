
    alter table nop_job_assignment add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_definition add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_instance add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_instance_his add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_job_assignment drop primary key;
alter table nop_job_assignment add primary key (NOP_TENANT_ID, SERVER_ID);

alter table nop_job_definition drop primary key;
alter table nop_job_definition add primary key (NOP_TENANT_ID, SID);

alter table nop_job_instance drop primary key;
alter table nop_job_instance add primary key (NOP_TENANT_ID, JOB_INSTANCE_ID);

alter table nop_job_instance_his drop primary key;
alter table nop_job_instance_his add primary key (NOP_TENANT_ID, JOB_INSTANCE_ID);


