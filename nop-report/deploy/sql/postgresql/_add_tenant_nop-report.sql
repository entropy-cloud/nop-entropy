
    alter table nop_report_dataset add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_report_dataset_auth add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_report_definition add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_report_result_file add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;


