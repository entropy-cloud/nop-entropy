
    alter table nop_credential add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_credential_usage add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_credential_auth add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_credential drop constraint PK_nop_credential;
alter table nop_credential add constraint PK_nop_credential primary key (NOP_TENANT_ID, credential_id);

alter table nop_credential_usage drop constraint PK_nop_credential_usage;
alter table nop_credential_usage add constraint PK_nop_credential_usage primary key (NOP_TENANT_ID, usage_id);

alter table nop_credential_auth drop constraint PK_nop_credential_auth;
alter table nop_credential_auth add constraint PK_nop_credential_auth primary key (NOP_TENANT_ID, auth_id);

alter table nop_credential_usage drop constraint UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER;
alter table nop_credential_usage add constraint UK_NOP_CREDENTIAL_USAGE_CRED_CONSUMER unique (NOP_TENANT_ID,credential_id,consumer_ref);

                alter table nop_credential_auth drop constraint UK_NOP_CREDENTIAL_AUTH_CRED_ROLE;
alter table nop_credential_auth add constraint UK_NOP_CREDENTIAL_AUTH_CRED_ROLE unique (NOP_TENANT_ID,credential_id,role_id);

                
