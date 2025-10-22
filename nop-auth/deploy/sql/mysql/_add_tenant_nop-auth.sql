
    alter table nop_auth_dept add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_position add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_role add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_site add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_role_data_auth add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_resource add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_ext_login add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_user_role add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_user_substitution add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_group add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_role_resource add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_group_dept add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_group_user add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_auth_dept drop primary key;
alter table nop_auth_dept add primary key (NOP_TENANT_ID, DEPT_ID);

alter table nop_auth_position drop primary key;
alter table nop_auth_position add primary key (NOP_TENANT_ID, POSITION_ID);

alter table nop_auth_role drop primary key;
alter table nop_auth_role add primary key (NOP_TENANT_ID, ROLE_ID);

alter table nop_auth_site drop primary key;
alter table nop_auth_site add primary key (NOP_TENANT_ID, SITE_ID);

alter table nop_auth_role_data_auth drop primary key;
alter table nop_auth_role_data_auth add primary key (NOP_TENANT_ID, SID);

alter table nop_auth_resource drop primary key;
alter table nop_auth_resource add primary key (NOP_TENANT_ID, RESOURCE_ID);

alter table nop_auth_ext_login drop primary key;
alter table nop_auth_ext_login add primary key (NOP_TENANT_ID, SID);

alter table nop_auth_user_role drop primary key;
alter table nop_auth_user_role add primary key (NOP_TENANT_ID, USER_ID,ROLE_ID);

alter table nop_auth_user_substitution drop primary key;
alter table nop_auth_user_substitution add primary key (NOP_TENANT_ID, SID);

alter table nop_auth_group drop primary key;
alter table nop_auth_group add primary key (NOP_TENANT_ID, GROUP_ID);

alter table nop_auth_role_resource drop primary key;
alter table nop_auth_role_resource add primary key (NOP_TENANT_ID, SID);

alter table nop_auth_group_dept drop primary key;
alter table nop_auth_group_dept add primary key (NOP_TENANT_ID, DEPT_ID,GROUP_ID);

alter table nop_auth_group_user drop primary key;
alter table nop_auth_group_user add primary key (NOP_TENANT_ID, USER_ID,GROUP_ID);

alter table nop_auth_role drop constraint UK_NOP_AUTH_ROLE_NAME;
alter table nop_auth_role add constraint UK_NOP_AUTH_ROLE_NAME
                     unique (NOP_TENANT_ID,ROLE_NAME);

                
