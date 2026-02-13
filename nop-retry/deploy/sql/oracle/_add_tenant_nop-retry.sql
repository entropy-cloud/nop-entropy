
    alter table nop_retry_template add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_retry_record add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_retry_attempt add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_retry_dead_letter add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_retry_template drop constraint PK_nop_retry_template;
alter table nop_retry_template add constraint PK_nop_retry_template primary key (NOP_TENANT_ID, SID);

alter table nop_retry_record drop constraint PK_nop_retry_record;
alter table nop_retry_record add constraint PK_nop_retry_record primary key (NOP_TENANT_ID, SID);

alter table nop_retry_attempt drop constraint PK_nop_retry_attempt;
alter table nop_retry_attempt add constraint PK_nop_retry_attempt primary key (NOP_TENANT_ID, SID);

alter table nop_retry_dead_letter drop constraint PK_nop_retry_dead_letter;
alter table nop_retry_dead_letter add constraint PK_nop_retry_dead_letter primary key (NOP_TENANT_ID, SID);


