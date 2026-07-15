
    alter table nop_meta_module add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_data_source add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_semantic_type add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_lineage_edge add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_orm_model add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_pipeline add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_domain add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_dimension add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_measure add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_filter add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_field add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_relation add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_unique_key add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_index add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_join add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict_item add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_module drop primary key;
alter table nop_meta_module add primary key (NOP_TENANT_ID, META_MODULE_ID);

alter table nop_meta_data_source drop primary key;
alter table nop_meta_data_source add primary key (NOP_TENANT_ID, DATA_SOURCE_ID);

alter table nop_meta_semantic_type drop primary key;
alter table nop_meta_semantic_type add primary key (NOP_TENANT_ID, SEMANTIC_TYPE_ID);

alter table nop_meta_lineage_edge drop primary key;
alter table nop_meta_lineage_edge add primary key (NOP_TENANT_ID, LINEAGE_EDGE_ID);

alter table nop_meta_quality_rule drop primary key;
alter table nop_meta_quality_rule add primary key (NOP_TENANT_ID, QUALITY_RULE_ID);

alter table nop_meta_orm_model drop primary key;
alter table nop_meta_orm_model add primary key (NOP_TENANT_ID, ORM_MODEL_ID);

alter table nop_meta_table drop primary key;
alter table nop_meta_table add primary key (NOP_TENANT_ID, META_TABLE_ID);

alter table nop_meta_pipeline drop primary key;
alter table nop_meta_pipeline add primary key (NOP_TENANT_ID, PIPELINE_ID);

alter table nop_meta_quality_result drop primary key;
alter table nop_meta_quality_result add primary key (NOP_TENANT_ID, QUALITY_RESULT_ID);

alter table nop_meta_entity drop primary key;
alter table nop_meta_entity add primary key (NOP_TENANT_ID, META_ENTITY_ID);

alter table nop_meta_domain drop primary key;
alter table nop_meta_domain add primary key (NOP_TENANT_ID, META_DOMAIN_ID);

alter table nop_meta_dict drop primary key;
alter table nop_meta_dict add primary key (NOP_TENANT_ID, META_DICT_ID);

alter table nop_meta_table_dimension drop primary key;
alter table nop_meta_table_dimension add primary key (NOP_TENANT_ID, DIMENSION_ID);

alter table nop_meta_table_measure drop primary key;
alter table nop_meta_table_measure add primary key (NOP_TENANT_ID, MEASURE_ID);

alter table nop_meta_table_filter drop primary key;
alter table nop_meta_table_filter add primary key (NOP_TENANT_ID, FILTER_ID);

alter table nop_meta_entity_field drop primary key;
alter table nop_meta_entity_field add primary key (NOP_TENANT_ID, ENTITY_FIELD_ID);

alter table nop_meta_entity_relation drop primary key;
alter table nop_meta_entity_relation add primary key (NOP_TENANT_ID, RELATION_ID);

alter table nop_meta_entity_unique_key drop primary key;
alter table nop_meta_entity_unique_key add primary key (NOP_TENANT_ID, UNIQUE_KEY_ID);

alter table nop_meta_entity_index drop primary key;
alter table nop_meta_entity_index add primary key (NOP_TENANT_ID, INDEX_ID);

alter table nop_meta_table_join drop primary key;
alter table nop_meta_table_join add primary key (NOP_TENANT_ID, JOIN_ID);

alter table nop_meta_dict_item drop primary key;
alter table nop_meta_dict_item add primary key (NOP_TENANT_ID, DICT_ITEM_ID);


