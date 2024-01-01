
    alter table nop_dyn_app add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_app_module add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_domain add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity_meta add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity_relation add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_entity_relation_meta add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_function_meta add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_module add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_page add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_prop_meta add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_dyn_app drop primary key;
alter table nop_dyn_app add primary key (NOP_TENANT_ID, APP_ID);

alter table nop_dyn_app_module drop primary key;
alter table nop_dyn_app_module add primary key (NOP_TENANT_ID, APP_ID,MODULE_ID);

alter table nop_dyn_domain drop primary key;
alter table nop_dyn_domain add primary key (NOP_TENANT_ID, DOMAIN_ID);

alter table nop_dyn_entity drop primary key;
alter table nop_dyn_entity add primary key (NOP_TENANT_ID, SID);

alter table nop_dyn_entity_meta drop primary key;
alter table nop_dyn_entity_meta add primary key (NOP_TENANT_ID, ENTITY_META_ID);

alter table nop_dyn_entity_relation drop primary key;
alter table nop_dyn_entity_relation add primary key (NOP_TENANT_ID, SID);

alter table nop_dyn_entity_relation_meta drop primary key;
alter table nop_dyn_entity_relation_meta add primary key (NOP_TENANT_ID, REL_META_ID);

alter table nop_dyn_function_meta drop primary key;
alter table nop_dyn_function_meta add primary key (NOP_TENANT_ID, FUNC_META_ID);

alter table nop_dyn_module drop primary key;
alter table nop_dyn_module add primary key (NOP_TENANT_ID, MODULE_ID);

alter table nop_dyn_page drop primary key;
alter table nop_dyn_page add primary key (NOP_TENANT_ID, PAGE_ID);

alter table nop_dyn_prop_meta drop primary key;
alter table nop_dyn_prop_meta add primary key (NOP_TENANT_ID, PROP_META_ID);


