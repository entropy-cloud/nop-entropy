
    alter table nop_dyn_app add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_app_module add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity_meta add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_module add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_page add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_prop_meta add column NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_app drop constraint PK_nop_dyn_app;
alter table nop_dyn_app add constraint PK_nop_dyn_app primary key (NOP_TENANT_ID, APP_ID);

alter table nop_dyn_app_module drop constraint PK_nop_dyn_app_module;
alter table nop_dyn_app_module add constraint PK_nop_dyn_app_module primary key (NOP_TENANT_ID, APP_ID,MODULE_ID);

alter table nop_dyn_entity drop constraint PK_nop_dyn_entity;
alter table nop_dyn_entity add constraint PK_nop_dyn_entity primary key (NOP_TENANT_ID, SID);

alter table nop_dyn_entity_meta drop constraint PK_nop_dyn_entity_meta;
alter table nop_dyn_entity_meta add constraint PK_nop_dyn_entity_meta primary key (NOP_TENANT_ID, ENTITY_META_ID);

alter table nop_dyn_module drop constraint PK_nop_dyn_module;
alter table nop_dyn_module add constraint PK_nop_dyn_module primary key (NOP_TENANT_ID, MODULE_ID);

alter table nop_dyn_page drop constraint PK_nop_dyn_page;
alter table nop_dyn_page add constraint PK_nop_dyn_page primary key (NOP_TENANT_ID, PAGE_ID);

alter table nop_dyn_prop_meta drop constraint PK_nop_dyn_prop_meta;
alter table nop_dyn_prop_meta add constraint PK_nop_dyn_prop_meta primary key (NOP_TENANT_ID, PROP_META_ID);


