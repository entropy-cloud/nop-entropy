
    alter table nop_report_dataset add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_report_dataset_auth add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_report_definition add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_report_result_file add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_report_dataset drop constraint PK_nop_report_dataset;
alter table nop_report_dataset add constraint PK_nop_report_dataset primary key (NOP_TENANT_ID, DS_ID);

alter table nop_report_dataset_auth drop constraint PK_nop_report_dataset_auth;
alter table nop_report_dataset_auth add constraint PK_nop_report_dataset_auth primary key (NOP_TENANT_ID, DS_ID);

alter table nop_report_definition drop constraint PK_nop_report_definition;
alter table nop_report_definition add constraint PK_nop_report_definition primary key (NOP_TENANT_ID, RPT_ID);

alter table nop_report_result_file drop constraint PK_nop_report_result_file;
alter table nop_report_result_file add constraint PK_nop_report_result_file primary key (NOP_TENANT_ID, SID);


