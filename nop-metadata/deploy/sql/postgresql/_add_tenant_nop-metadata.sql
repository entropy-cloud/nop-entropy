
    alter table nop_meta_module add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_data_source add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_semantic_type add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_lineage_edge add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_orm_model add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_pipeline add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_manifest add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_domain add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_dimension add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_measure add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_filter add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_catalog add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_profiling_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_field add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_relation add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_unique_key add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_index add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_join add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict_item add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_profiling_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_module drop constraint PK_nop_meta_module;
alter table nop_meta_module add constraint PK_nop_meta_module primary key (NOP_TENANT_ID, meta_module_id);

alter table nop_meta_data_source drop constraint PK_nop_meta_data_source;
alter table nop_meta_data_source add constraint PK_nop_meta_data_source primary key (NOP_TENANT_ID, data_source_id);

alter table nop_meta_semantic_type drop constraint PK_nop_meta_semantic_type;
alter table nop_meta_semantic_type add constraint PK_nop_meta_semantic_type primary key (NOP_TENANT_ID, semantic_type_id);

alter table nop_meta_lineage_edge drop constraint PK_nop_meta_lineage_edge;
alter table nop_meta_lineage_edge add constraint PK_nop_meta_lineage_edge primary key (NOP_TENANT_ID, lineage_edge_id);

alter table nop_meta_quality_rule drop constraint PK_nop_meta_quality_rule;
alter table nop_meta_quality_rule add constraint PK_nop_meta_quality_rule primary key (NOP_TENANT_ID, quality_rule_id);

alter table nop_meta_orm_model drop constraint PK_nop_meta_orm_model;
alter table nop_meta_orm_model add constraint PK_nop_meta_orm_model primary key (NOP_TENANT_ID, orm_model_id);

alter table nop_meta_table drop constraint PK_nop_meta_table;
alter table nop_meta_table add constraint PK_nop_meta_table primary key (NOP_TENANT_ID, meta_table_id);

alter table nop_meta_pipeline drop constraint PK_nop_meta_pipeline;
alter table nop_meta_pipeline add constraint PK_nop_meta_pipeline primary key (NOP_TENANT_ID, pipeline_id);

alter table nop_meta_manifest drop constraint PK_nop_meta_manifest;
alter table nop_meta_manifest add constraint PK_nop_meta_manifest primary key (NOP_TENANT_ID, manifest_id);

alter table nop_meta_quality_result drop constraint PK_nop_meta_quality_result;
alter table nop_meta_quality_result add constraint PK_nop_meta_quality_result primary key (NOP_TENANT_ID, quality_result_id);

alter table nop_meta_entity drop constraint PK_nop_meta_entity;
alter table nop_meta_entity add constraint PK_nop_meta_entity primary key (NOP_TENANT_ID, meta_entity_id);

alter table nop_meta_domain drop constraint PK_nop_meta_domain;
alter table nop_meta_domain add constraint PK_nop_meta_domain primary key (NOP_TENANT_ID, meta_domain_id);

alter table nop_meta_dict drop constraint PK_nop_meta_dict;
alter table nop_meta_dict add constraint PK_nop_meta_dict primary key (NOP_TENANT_ID, meta_dict_id);

alter table nop_meta_table_dimension drop constraint PK_nop_meta_table_dimension;
alter table nop_meta_table_dimension add constraint PK_nop_meta_table_dimension primary key (NOP_TENANT_ID, dimension_id);

alter table nop_meta_table_measure drop constraint PK_nop_meta_table_measure;
alter table nop_meta_table_measure add constraint PK_nop_meta_table_measure primary key (NOP_TENANT_ID, measure_id);

alter table nop_meta_table_filter drop constraint PK_nop_meta_table_filter;
alter table nop_meta_table_filter add constraint PK_nop_meta_table_filter primary key (NOP_TENANT_ID, filter_id);

alter table nop_meta_catalog drop constraint PK_nop_meta_catalog;
alter table nop_meta_catalog add constraint PK_nop_meta_catalog primary key (NOP_TENANT_ID, meta_catalog_id);

alter table nop_meta_profiling_rule drop constraint PK_nop_meta_profiling_rule;
alter table nop_meta_profiling_rule add constraint PK_nop_meta_profiling_rule primary key (NOP_TENANT_ID, profiling_rule_id);

alter table nop_meta_entity_field drop constraint PK_nop_meta_entity_field;
alter table nop_meta_entity_field add constraint PK_nop_meta_entity_field primary key (NOP_TENANT_ID, entity_field_id);

alter table nop_meta_entity_relation drop constraint PK_nop_meta_entity_relation;
alter table nop_meta_entity_relation add constraint PK_nop_meta_entity_relation primary key (NOP_TENANT_ID, relation_id);

alter table nop_meta_entity_unique_key drop constraint PK_nop_meta_entity_unique_key;
alter table nop_meta_entity_unique_key add constraint PK_nop_meta_entity_unique_key primary key (NOP_TENANT_ID, unique_key_id);

alter table nop_meta_entity_index drop constraint PK_nop_meta_entity_index;
alter table nop_meta_entity_index add constraint PK_nop_meta_entity_index primary key (NOP_TENANT_ID, index_id);

alter table nop_meta_table_join drop constraint PK_nop_meta_table_join;
alter table nop_meta_table_join add constraint PK_nop_meta_table_join primary key (NOP_TENANT_ID, join_id);

alter table nop_meta_dict_item drop constraint PK_nop_meta_dict_item;
alter table nop_meta_dict_item add constraint PK_nop_meta_dict_item primary key (NOP_TENANT_ID, dict_item_id);

alter table nop_meta_profiling_result drop constraint PK_nop_meta_profiling_result;
alter table nop_meta_profiling_result add constraint PK_nop_meta_profiling_result primary key (NOP_TENANT_ID, profiling_result_id);


