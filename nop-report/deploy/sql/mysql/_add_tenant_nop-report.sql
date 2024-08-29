
    alter table nop_report_dataset add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_report_dataset_auth add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_report_definition add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_report_result_file add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_report_dataset drop primary key;
alter table nop_report_dataset add primary key (NOP_TENANT_ID, DS_ID);

alter table nop_report_dataset_auth drop primary key;
alter table nop_report_dataset_auth add primary key (NOP_TENANT_ID, DS_ID);

alter table nop_report_definition drop primary key;
alter table nop_report_definition add primary key (NOP_TENANT_ID, RPT_ID);

alter table nop_report_result_file drop primary key;
alter table nop_report_result_file add primary key (NOP_TENANT_ID, SID);


