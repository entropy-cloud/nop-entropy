
    alter table nop_tcc_branch_record add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_tcc_record add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_tcc_branch_record drop primary key;
alter table nop_tcc_branch_record add primary key (NOP_TENANT_ID, BRANCH_ID);

alter table nop_tcc_record drop primary key;
alter table nop_tcc_record add primary key (NOP_TENANT_ID, TXN_ID);


