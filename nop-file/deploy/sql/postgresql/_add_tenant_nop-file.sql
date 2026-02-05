
    alter table nop_file_record add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_file_record drop constraint PK_nop_file_record;
alter table nop_file_record add constraint PK_nop_file_record primary key (NOP_TENANT_ID, FILE_ID)
                ;


