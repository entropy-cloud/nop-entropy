
    alter table nop_tcc_branch_record add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_tcc_record add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;


