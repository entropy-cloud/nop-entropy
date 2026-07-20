
    alter table nop_meta_module add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_data_source add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_semantic_type add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_rule add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_reconciliation_entity add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_model_changed_event add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_orm_model add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_pipeline add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_checkpoint add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_manifest add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_result add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_domain add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_dimension add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_measure add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_filter add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_catalog add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_profiling_rule add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_data_contract add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_reconciliation_config add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_score add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_lineage_edge add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_field add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_relation add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_unique_key add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_index add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_join add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict_item add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_profiling_result add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_reconciliation_result add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_meta_module drop constraint PK_nop_meta_module;
alter table nop_meta_module add constraint PK_nop_meta_module primary key (NOP_TENANT_ID, META_MODULE_ID);

alter table nop_meta_data_source drop constraint PK_nop_meta_data_source;
alter table nop_meta_data_source add constraint PK_nop_meta_data_source primary key (NOP_TENANT_ID, DATA_SOURCE_ID);

alter table nop_meta_semantic_type drop constraint PK_nop_meta_semantic_type;
alter table nop_meta_semantic_type add constraint PK_nop_meta_semantic_type primary key (NOP_TENANT_ID, SEMANTIC_TYPE_ID);

alter table nop_meta_quality_rule drop constraint PK_nop_meta_quality_rule;
alter table nop_meta_quality_rule add constraint PK_nop_meta_quality_rule primary key (NOP_TENANT_ID, QUALITY_RULE_ID);

alter table nop_meta_reconciliation_entity drop constraint PK_nop_meta_reconciliation_entity;
alter table nop_meta_reconciliation_entity add constraint PK_nop_meta_reconciliation_entity primary key (NOP_TENANT_ID, RECON_ENTITY_ID);

alter table nop_meta_model_changed_event drop constraint PK_nop_meta_model_changed_event;
alter table nop_meta_model_changed_event add constraint PK_nop_meta_model_changed_event primary key (NOP_TENANT_ID, MODEL_CHANGED_EVENT_ID);

alter table nop_meta_orm_model drop constraint PK_nop_meta_orm_model;
alter table nop_meta_orm_model add constraint PK_nop_meta_orm_model primary key (NOP_TENANT_ID, ORM_MODEL_ID);

alter table nop_meta_table drop constraint PK_nop_meta_table;
alter table nop_meta_table add constraint PK_nop_meta_table primary key (NOP_TENANT_ID, META_TABLE_ID);

alter table nop_meta_pipeline drop constraint PK_nop_meta_pipeline;
alter table nop_meta_pipeline add constraint PK_nop_meta_pipeline primary key (NOP_TENANT_ID, PIPELINE_ID);

alter table nop_meta_quality_checkpoint drop constraint PK_nop_meta_quality_checkpoint;
alter table nop_meta_quality_checkpoint add constraint PK_nop_meta_quality_checkpoint primary key (NOP_TENANT_ID, CHECKPOINT_ID);

alter table nop_meta_manifest drop constraint PK_nop_meta_manifest;
alter table nop_meta_manifest add constraint PK_nop_meta_manifest primary key (NOP_TENANT_ID, MANIFEST_ID);

alter table nop_meta_quality_result drop constraint PK_nop_meta_quality_result;
alter table nop_meta_quality_result add constraint PK_nop_meta_quality_result primary key (NOP_TENANT_ID, QUALITY_RESULT_ID);

alter table nop_meta_entity drop constraint PK_nop_meta_entity;
alter table nop_meta_entity add constraint PK_nop_meta_entity primary key (NOP_TENANT_ID, META_ENTITY_ID);

alter table nop_meta_domain drop constraint PK_nop_meta_domain;
alter table nop_meta_domain add constraint PK_nop_meta_domain primary key (NOP_TENANT_ID, META_DOMAIN_ID);

alter table nop_meta_dict drop constraint PK_nop_meta_dict;
alter table nop_meta_dict add constraint PK_nop_meta_dict primary key (NOP_TENANT_ID, META_DICT_ID);

alter table nop_meta_table_dimension drop constraint PK_nop_meta_table_dimension;
alter table nop_meta_table_dimension add constraint PK_nop_meta_table_dimension primary key (NOP_TENANT_ID, DIMENSION_ID);

alter table nop_meta_table_measure drop constraint PK_nop_meta_table_measure;
alter table nop_meta_table_measure add constraint PK_nop_meta_table_measure primary key (NOP_TENANT_ID, MEASURE_ID);

alter table nop_meta_table_filter drop constraint PK_nop_meta_table_filter;
alter table nop_meta_table_filter add constraint PK_nop_meta_table_filter primary key (NOP_TENANT_ID, FILTER_ID);

alter table nop_meta_catalog drop constraint PK_nop_meta_catalog;
alter table nop_meta_catalog add constraint PK_nop_meta_catalog primary key (NOP_TENANT_ID, META_CATALOG_ID);

alter table nop_meta_profiling_rule drop constraint PK_nop_meta_profiling_rule;
alter table nop_meta_profiling_rule add constraint PK_nop_meta_profiling_rule primary key (NOP_TENANT_ID, PROFILING_RULE_ID);

alter table nop_meta_data_contract drop constraint PK_nop_meta_data_contract;
alter table nop_meta_data_contract add constraint PK_nop_meta_data_contract primary key (NOP_TENANT_ID, CONTRACT_ID);

alter table nop_meta_reconciliation_config drop constraint PK_nop_meta_reconciliation_config;
alter table nop_meta_reconciliation_config add constraint PK_nop_meta_reconciliation_config primary key (NOP_TENANT_ID, CONFIG_ID);

alter table nop_meta_quality_score drop constraint PK_nop_meta_quality_score;
alter table nop_meta_quality_score add constraint PK_nop_meta_quality_score primary key (NOP_TENANT_ID, QUALITY_SCORE_ID);

alter table nop_meta_lineage_edge drop constraint PK_nop_meta_lineage_edge;
alter table nop_meta_lineage_edge add constraint PK_nop_meta_lineage_edge primary key (NOP_TENANT_ID, LINEAGE_EDGE_ID);

alter table nop_meta_entity_field drop constraint PK_nop_meta_entity_field;
alter table nop_meta_entity_field add constraint PK_nop_meta_entity_field primary key (NOP_TENANT_ID, ENTITY_FIELD_ID);

alter table nop_meta_entity_relation drop constraint PK_nop_meta_entity_relation;
alter table nop_meta_entity_relation add constraint PK_nop_meta_entity_relation primary key (NOP_TENANT_ID, RELATION_ID);

alter table nop_meta_entity_unique_key drop constraint PK_nop_meta_entity_unique_key;
alter table nop_meta_entity_unique_key add constraint PK_nop_meta_entity_unique_key primary key (NOP_TENANT_ID, UNIQUE_KEY_ID);

alter table nop_meta_entity_index drop constraint PK_nop_meta_entity_index;
alter table nop_meta_entity_index add constraint PK_nop_meta_entity_index primary key (NOP_TENANT_ID, INDEX_ID);

alter table nop_meta_table_join drop constraint PK_nop_meta_table_join;
alter table nop_meta_table_join add constraint PK_nop_meta_table_join primary key (NOP_TENANT_ID, JOIN_ID);

alter table nop_meta_dict_item drop constraint PK_nop_meta_dict_item;
alter table nop_meta_dict_item add constraint PK_nop_meta_dict_item primary key (NOP_TENANT_ID, DICT_ITEM_ID);

alter table nop_meta_profiling_result drop constraint PK_nop_meta_profiling_result;
alter table nop_meta_profiling_result add constraint PK_nop_meta_profiling_result primary key (NOP_TENANT_ID, PROFILING_RESULT_ID);

alter table nop_meta_reconciliation_result drop constraint PK_nop_meta_reconciliation_result;
alter table nop_meta_reconciliation_result add constraint PK_nop_meta_reconciliation_result primary key (NOP_TENANT_ID, RESULT_ID);


