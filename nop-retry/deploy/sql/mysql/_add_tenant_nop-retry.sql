
    alter table nop_retry_template add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_retry_record add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_retry_attempt add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_retry_dead_letter add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_retry_template drop primary key;
alter table nop_retry_template add primary key (NOP_TENANT_ID, SID);

alter table nop_retry_record drop primary key;
alter table nop_retry_record add primary key (NOP_TENANT_ID, SID);

alter table nop_retry_attempt drop primary key;
alter table nop_retry_attempt add primary key (NOP_TENANT_ID, SID);

alter table nop_retry_dead_letter drop primary key;
alter table nop_retry_dead_letter add primary key (NOP_TENANT_ID, SID);


