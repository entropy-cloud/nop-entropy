
    alter table nop_auth_dept add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_ext_login add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_group add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_group_dept add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_group_user add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_position add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_resource add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_role add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_role_data_auth add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_role_resource add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_site add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_user_role add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_user_substitution add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_dept drop constraint PK_nop_auth_dept;
alter table nop_auth_dept add constraint PK_nop_auth_dept primary key (NOP_TENANT_ID, DEPT_ID);

alter table nop_auth_ext_login drop constraint PK_nop_auth_ext_login;
alter table nop_auth_ext_login add constraint PK_nop_auth_ext_login primary key (NOP_TENANT_ID, SID);

alter table nop_auth_group drop constraint PK_nop_auth_group;
alter table nop_auth_group add constraint PK_nop_auth_group primary key (NOP_TENANT_ID, GROUP_ID);

alter table nop_auth_group_dept drop constraint PK_nop_auth_group_dept;
alter table nop_auth_group_dept add constraint PK_nop_auth_group_dept primary key (NOP_TENANT_ID, DEPT_ID,GROUP_ID);

alter table nop_auth_group_user drop constraint PK_nop_auth_group_user;
alter table nop_auth_group_user add constraint PK_nop_auth_group_user primary key (NOP_TENANT_ID, USER_ID,GROUP_ID);

alter table nop_auth_position drop constraint PK_nop_auth_position;
alter table nop_auth_position add constraint PK_nop_auth_position primary key (NOP_TENANT_ID, POSITION_ID);

alter table nop_auth_resource drop constraint PK_nop_auth_resource;
alter table nop_auth_resource add constraint PK_nop_auth_resource primary key (NOP_TENANT_ID, RESOURCE_ID);

alter table nop_auth_role drop constraint PK_nop_auth_role;
alter table nop_auth_role add constraint PK_nop_auth_role primary key (NOP_TENANT_ID, ROLE_ID);

alter table nop_auth_role_data_auth drop constraint PK_nop_auth_role_data_auth;
alter table nop_auth_role_data_auth add constraint PK_nop_auth_role_data_auth primary key (NOP_TENANT_ID, SID);

alter table nop_auth_role_resource drop constraint PK_nop_auth_role_resource;
alter table nop_auth_role_resource add constraint PK_nop_auth_role_resource primary key (NOP_TENANT_ID, SID);

alter table nop_auth_site drop constraint PK_nop_auth_site;
alter table nop_auth_site add constraint PK_nop_auth_site primary key (NOP_TENANT_ID, SITE_ID);

alter table nop_auth_user_role drop constraint PK_nop_auth_user_role;
alter table nop_auth_user_role add constraint PK_nop_auth_user_role primary key (NOP_TENANT_ID, USER_ID,ROLE_ID);

alter table nop_auth_user_substitution drop constraint PK_nop_auth_user_substitution;
alter table nop_auth_user_substitution add constraint PK_nop_auth_user_substitution primary key (NOP_TENANT_ID, SID);

alter table nop_auth_role drop constraint UK_NOP_AUTH_ROLE_NAME;
alter table nop_auth_role add constraint UK_NOP_AUTH_ROLE_NAME
                     unique (NOP_TENANT_ID,ROLE_NAME);

                
