
    alter table nop_file_record add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_file_record drop primary key;
alter table nop_file_record add primary key (NOP_TENANT_ID, FILE_ID);

;


