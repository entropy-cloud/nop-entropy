
    alter table nop_dyn_app add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_module add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_entity add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_entity_relation add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_patch_file add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_module_dep add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_app_module add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_sql add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_file add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_page add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_entity_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_domain add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_entity_relation_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_function_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_prop_meta add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL
                ;

alter table nop_dyn_app drop primary key;
alter table nop_dyn_app add primary key (NOP_TENANT_ID, APP_ID);

alter table nop_dyn_module drop primary key;
alter table nop_dyn_module add primary key (NOP_TENANT_ID, MODULE_ID);

alter table nop_dyn_entity drop primary key;
alter table nop_dyn_entity add primary key (NOP_TENANT_ID, SID);

alter table nop_dyn_entity_relation drop primary key;
alter table nop_dyn_entity_relation add primary key (NOP_TENANT_ID, SID);

alter table nop_dyn_patch_file drop primary key;
alter table nop_dyn_patch_file add primary key (NOP_TENANT_ID, FILE_ID);

alter table nop_dyn_module_dep drop primary key;
alter table nop_dyn_module_dep add primary key (NOP_TENANT_ID, MODULE_ID,DEP_MODULE_ID);

alter table nop_dyn_app_module drop primary key;
alter table nop_dyn_app_module add primary key (NOP_TENANT_ID, APP_ID,MODULE_ID);

alter table nop_dyn_sql drop primary key;
alter table nop_dyn_sql add primary key (NOP_TENANT_ID, SQL_ID);

alter table nop_dyn_file drop primary key;
alter table nop_dyn_file add primary key (NOP_TENANT_ID, FILE_ID);

alter table nop_dyn_page drop primary key;
alter table nop_dyn_page add primary key (NOP_TENANT_ID, PAGE_ID);

alter table nop_dyn_entity_meta drop primary key;
alter table nop_dyn_entity_meta add primary key (NOP_TENANT_ID, ENTITY_META_ID);

alter table nop_dyn_domain drop primary key;
alter table nop_dyn_domain add primary key (NOP_TENANT_ID, DOMAIN_ID);

alter table nop_dyn_entity_relation_meta drop primary key;
alter table nop_dyn_entity_relation_meta add primary key (NOP_TENANT_ID, REL_META_ID);

alter table nop_dyn_function_meta drop primary key;
alter table nop_dyn_function_meta add primary key (NOP_TENANT_ID, FUNC_META_ID);

alter table nop_dyn_prop_meta drop primary key;
alter table nop_dyn_prop_meta add primary key (NOP_TENANT_ID, PROP_META_ID);


