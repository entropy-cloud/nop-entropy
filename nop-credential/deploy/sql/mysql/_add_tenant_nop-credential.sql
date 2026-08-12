
    alter table nop_credential add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_credential_usage add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_credential drop primary key;
alter table nop_credential add primary key (NOP_TENANT_ID, CREDENTIAL_ID);

alter table nop_credential_usage drop primary key;
alter table nop_credential_usage add primary key (NOP_TENANT_ID, USAGE_ID);

alter table nop_credential_usage drop constraint UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER;
alter table nop_credential_usage add constraint UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER unique (NOP_TENANT_ID,CREDENTIAL_ID,CONSUMER_REF);

                
