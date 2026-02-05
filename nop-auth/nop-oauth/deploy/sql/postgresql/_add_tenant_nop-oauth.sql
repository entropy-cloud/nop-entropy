
    alter table nop_oauth_authorization add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_oauth_authorization_consent add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_oauth_registered_client add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_oauth_authorization drop constraint PK_nop_oauth_authorization;
alter table nop_oauth_authorization add constraint PK_nop_oauth_authorization primary key (NOP_TENANT_ID, SID)                ;

alter table nop_oauth_authorization_consent drop constraint PK_nop_oauth_authorization_consent;
alter table nop_oauth_authorization_consent add constraint PK_nop_oauth_authorization_consent primary key (NOP_TENANT_ID, REGISTERED_CLIENT_ID,PRINCIPAL_NAME)
                ;

alter table nop_oauth_registered_client drop constraint PK_nop_oauth_registered_client;
alter table nop_oauth_registered_client add constraint PK_nop_oauth_registered_client primary key (NOP_TENANT_ID, SID)
                ;


