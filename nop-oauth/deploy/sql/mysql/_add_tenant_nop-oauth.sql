
    alter table nop_oauth_authorization add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_oauth_authorization_consent add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_oauth_registered_client add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_oauth_authorization drop primary key;
alter table nop_oauth_authorization add primary key (NOP_TENANT_ID, ID);

alter table nop_oauth_authorization_consent drop primary key;
alter table nop_oauth_authorization_consent add primary key (NOP_TENANT_ID, REGISTERED_CLIENT_ID,PRINCIPAL_NAME);

alter table nop_oauth_registered_client drop primary key;
alter table nop_oauth_registered_client add primary key (NOP_TENANT_ID, ID);


