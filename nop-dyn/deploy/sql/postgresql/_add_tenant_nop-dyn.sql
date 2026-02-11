
    alter table nop_dyn_app add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_module add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity_relation add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_patch_file add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_module_dep add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_app_module add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_sql add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_file add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_page add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_domain add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity_relation_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_function_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_prop_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_app drop constraint PK_nop_dyn_app;
alter table nop_dyn_app add constraint PK_nop_dyn_app primary key (NOP_TENANT_ID, app_id);

alter table nop_dyn_module drop constraint PK_nop_dyn_module;
alter table nop_dyn_module add constraint PK_nop_dyn_module primary key (NOP_TENANT_ID, module_id);

alter table nop_dyn_entity drop constraint PK_nop_dyn_entity;
alter table nop_dyn_entity add constraint PK_nop_dyn_entity primary key (NOP_TENANT_ID, sid);

alter table nop_dyn_entity_relation drop constraint PK_nop_dyn_entity_relation;
alter table nop_dyn_entity_relation add constraint PK_nop_dyn_entity_relation primary key (NOP_TENANT_ID, sid);

alter table nop_dyn_patch_file drop constraint PK_nop_dyn_patch_file;
alter table nop_dyn_patch_file add constraint PK_nop_dyn_patch_file primary key (NOP_TENANT_ID, file_id);

alter table nop_dyn_module_dep drop constraint PK_nop_dyn_module_dep;
alter table nop_dyn_module_dep add constraint PK_nop_dyn_module_dep primary key (NOP_TENANT_ID, module_id,dep_module_id);

alter table nop_dyn_app_module drop constraint PK_nop_dyn_app_module;
alter table nop_dyn_app_module add constraint PK_nop_dyn_app_module primary key (NOP_TENANT_ID, app_id,module_id);

alter table nop_dyn_sql drop constraint PK_nop_dyn_sql;
alter table nop_dyn_sql add constraint PK_nop_dyn_sql primary key (NOP_TENANT_ID, sql_id);

alter table nop_dyn_file drop constraint PK_nop_dyn_file;
alter table nop_dyn_file add constraint PK_nop_dyn_file primary key (NOP_TENANT_ID, file_id);

alter table nop_dyn_page drop constraint PK_nop_dyn_page;
alter table nop_dyn_page add constraint PK_nop_dyn_page primary key (NOP_TENANT_ID, page_id);

alter table nop_dyn_entity_meta drop constraint PK_nop_dyn_entity_meta;
alter table nop_dyn_entity_meta add constraint PK_nop_dyn_entity_meta primary key (NOP_TENANT_ID, entity_meta_id);

alter table nop_dyn_domain drop constraint PK_nop_dyn_domain;
alter table nop_dyn_domain add constraint PK_nop_dyn_domain primary key (NOP_TENANT_ID, domain_id);

alter table nop_dyn_entity_relation_meta drop constraint PK_nop_dyn_entity_relation_meta;
alter table nop_dyn_entity_relation_meta add constraint PK_nop_dyn_entity_relation_meta primary key (NOP_TENANT_ID, rel_meta_id);

alter table nop_dyn_function_meta drop constraint PK_nop_dyn_function_meta;
alter table nop_dyn_function_meta add constraint PK_nop_dyn_function_meta primary key (NOP_TENANT_ID, func_meta_id);

alter table nop_dyn_prop_meta drop constraint PK_nop_dyn_prop_meta;
alter table nop_dyn_prop_meta add constraint PK_nop_dyn_prop_meta primary key (NOP_TENANT_ID, prop_meta_id);


