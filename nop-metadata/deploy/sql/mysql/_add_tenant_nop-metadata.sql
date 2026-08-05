
    alter table nop_meta_module add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_data_source add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_semantic_type add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_reconciliation_entity add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_model_changed_event add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_glossary add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_classification add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_business_domain add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_orm_model add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_pipeline add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_checkpoint add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_manifest add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_glossary_term add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_tag add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_data_product add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_domain add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_dimension add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_measure add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_filter add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_catalog add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_profiling_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_data_contract add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_reconciliation_config add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_quality_score add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_lineage_edge add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_tag_label add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_field add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_relation add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_unique_key add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_entity_index add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_table_join add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_dict_item add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_profiling_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_reconciliation_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_meta_module drop primary key;
alter table nop_meta_module add primary key (NOP_TENANT_ID, META_MODULE_ID);

alter table nop_meta_data_source drop primary key;
alter table nop_meta_data_source add primary key (NOP_TENANT_ID, DATA_SOURCE_ID);

alter table nop_meta_semantic_type drop primary key;
alter table nop_meta_semantic_type add primary key (NOP_TENANT_ID, SEMANTIC_TYPE_ID);

alter table nop_meta_quality_rule drop primary key;
alter table nop_meta_quality_rule add primary key (NOP_TENANT_ID, QUALITY_RULE_ID);

alter table nop_meta_reconciliation_entity drop primary key;
alter table nop_meta_reconciliation_entity add primary key (NOP_TENANT_ID, RECON_ENTITY_ID);

alter table nop_meta_model_changed_event drop primary key;
alter table nop_meta_model_changed_event add primary key (NOP_TENANT_ID, MODEL_CHANGED_EVENT_ID);

alter table nop_meta_glossary drop primary key;
alter table nop_meta_glossary add primary key (NOP_TENANT_ID, GLOSSARY_ID);

alter table nop_meta_classification drop primary key;
alter table nop_meta_classification add primary key (NOP_TENANT_ID, CLASSIFICATION_ID);

alter table nop_meta_business_domain drop primary key;
alter table nop_meta_business_domain add primary key (NOP_TENANT_ID, BUSINESS_DOMAIN_ID);

alter table nop_meta_orm_model drop primary key;
alter table nop_meta_orm_model add primary key (NOP_TENANT_ID, ORM_MODEL_ID);

alter table nop_meta_table drop primary key;
alter table nop_meta_table add primary key (NOP_TENANT_ID, META_TABLE_ID);

alter table nop_meta_pipeline drop primary key;
alter table nop_meta_pipeline add primary key (NOP_TENANT_ID, PIPELINE_ID);

alter table nop_meta_quality_checkpoint drop primary key;
alter table nop_meta_quality_checkpoint add primary key (NOP_TENANT_ID, CHECKPOINT_ID);

alter table nop_meta_manifest drop primary key;
alter table nop_meta_manifest add primary key (NOP_TENANT_ID, MANIFEST_ID);

alter table nop_meta_quality_result drop primary key;
alter table nop_meta_quality_result add primary key (NOP_TENANT_ID, QUALITY_RESULT_ID);

alter table nop_meta_glossary_term drop primary key;
alter table nop_meta_glossary_term add primary key (NOP_TENANT_ID, GLOSSARY_TERM_ID);

alter table nop_meta_tag drop primary key;
alter table nop_meta_tag add primary key (NOP_TENANT_ID, TAG_ID);

alter table nop_meta_data_product drop primary key;
alter table nop_meta_data_product add primary key (NOP_TENANT_ID, DATA_PRODUCT_ID);

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

alter table nop_meta_catalog drop primary key;
alter table nop_meta_catalog add primary key (NOP_TENANT_ID, META_CATALOG_ID);

alter table nop_meta_profiling_rule drop primary key;
alter table nop_meta_profiling_rule add primary key (NOP_TENANT_ID, PROFILING_RULE_ID);

alter table nop_meta_data_contract drop primary key;
alter table nop_meta_data_contract add primary key (NOP_TENANT_ID, CONTRACT_ID);

alter table nop_meta_reconciliation_config drop primary key;
alter table nop_meta_reconciliation_config add primary key (NOP_TENANT_ID, CONFIG_ID);

alter table nop_meta_quality_score drop primary key;
alter table nop_meta_quality_score add primary key (NOP_TENANT_ID, QUALITY_SCORE_ID);

alter table nop_meta_lineage_edge drop primary key;
alter table nop_meta_lineage_edge add primary key (NOP_TENANT_ID, LINEAGE_EDGE_ID);

alter table nop_meta_tag_label drop primary key;
alter table nop_meta_tag_label add primary key (NOP_TENANT_ID, TAG_LABEL_ID);

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

alter table nop_meta_profiling_result drop primary key;
alter table nop_meta_profiling_result add primary key (NOP_TENANT_ID, PROFILING_RESULT_ID);

alter table nop_meta_reconciliation_result drop primary key;
alter table nop_meta_reconciliation_result add primary key (NOP_TENANT_ID, RESULT_ID);

alter table nop_meta_module drop constraint UK_NOP_META_MODULE_ID_VER;
alter table nop_meta_module add constraint UK_NOP_META_MODULE_ID_VER unique (NOP_TENANT_ID,MODULE_ID,MODULE_VERSION);

                alter table nop_meta_data_source drop constraint UK_NOP_META_DS_QUERY_SPACE;
alter table nop_meta_data_source add constraint UK_NOP_META_DS_QUERY_SPACE unique (NOP_TENANT_ID,QUERY_SPACE);

                alter table nop_meta_data_source drop constraint UK_NOP_META_DATA_SOURCE_NAME;
alter table nop_meta_data_source add constraint UK_NOP_META_DATA_SOURCE_NAME unique (NOP_TENANT_ID,NAME);

                alter table nop_meta_semantic_type drop constraint UK_NOP_META_SEM_TYPE_NAME;
alter table nop_meta_semantic_type add constraint UK_NOP_META_SEM_TYPE_NAME unique (NOP_TENANT_ID,TYPE_NAME);

                alter table nop_meta_quality_rule drop constraint UK_NOP_META_QRULE_NAME;
alter table nop_meta_quality_rule add constraint UK_NOP_META_QRULE_NAME unique (NOP_TENANT_ID,RULE_NAME);

                alter table nop_meta_reconciliation_entity drop constraint UK_NOP_META_RECONCILIATION_ENTITY_ID;
alter table nop_meta_reconciliation_entity add constraint UK_NOP_META_RECONCILIATION_ENTITY_ID unique (NOP_TENANT_ID,ENTITY_ID,ENTITY_TYPE);

                alter table nop_meta_glossary drop constraint UK_NOP_META_GLOSSARY_NAME;
alter table nop_meta_glossary add constraint UK_NOP_META_GLOSSARY_NAME unique (NOP_TENANT_ID,NAME);

                alter table nop_meta_classification drop constraint UK_NOP_META_CLASSIFICATION_NAME;
alter table nop_meta_classification add constraint UK_NOP_META_CLASSIFICATION_NAME unique (NOP_TENANT_ID,NAME);

                alter table nop_meta_business_domain drop constraint UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME;
alter table nop_meta_business_domain add constraint UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME unique (NOP_TENANT_ID,PARENT_DOMAIN_ID,NAME);

                alter table nop_meta_orm_model drop constraint UK_NOP_META_ORM_MODEL_MODULE_NAME;
alter table nop_meta_orm_model add constraint UK_NOP_META_ORM_MODEL_MODULE_NAME unique (NOP_TENANT_ID,META_MODULE_ID,MODEL_NAME,IS_DELTA);

                alter table nop_meta_table drop constraint UK_NOP_META_TABLE_MODULE_NAME;
alter table nop_meta_table add constraint UK_NOP_META_TABLE_MODULE_NAME unique (NOP_TENANT_ID,META_MODULE_ID,TABLE_NAME,IS_DELTA,META_SCHEMA);

                alter table nop_meta_pipeline drop constraint UK_NOP_META_PIPELINE_MODULE_NAME;
alter table nop_meta_pipeline add constraint UK_NOP_META_PIPELINE_MODULE_NAME unique (NOP_TENANT_ID,META_MODULE_ID,PIPELINE_NAME);

                alter table nop_meta_quality_checkpoint drop constraint UK_NOP_META_QCHECKPOINT_NAME;
alter table nop_meta_quality_checkpoint add constraint UK_NOP_META_QCHECKPOINT_NAME unique (NOP_TENANT_ID,CHECKPOINT_NAME);

                alter table nop_meta_manifest drop constraint UK_NOP_META_MANIFEST_MODULE_VER;
alter table nop_meta_manifest add constraint UK_NOP_META_MANIFEST_MODULE_VER unique (NOP_TENANT_ID,META_MODULE_ID,MANIFEST_VERSION);

                alter table nop_meta_glossary_term drop constraint UK_NOP_META_GLOSSARY_TERM_FQN;
alter table nop_meta_glossary_term add constraint UK_NOP_META_GLOSSARY_TERM_FQN unique (NOP_TENANT_ID,FULLY_QUALIFIED_NAME);

                alter table nop_meta_glossary_term drop constraint UK_NOP_META_GLOSSARY_TERM_G_FQN;
alter table nop_meta_glossary_term add constraint UK_NOP_META_GLOSSARY_TERM_G_FQN unique (NOP_TENANT_ID,GLOSSARY_ID,FULLY_QUALIFIED_NAME);

                alter table nop_meta_tag drop constraint UK_NOP_META_TAG_FQN;
alter table nop_meta_tag add constraint UK_NOP_META_TAG_FQN unique (NOP_TENANT_ID,FULLY_QUALIFIED_NAME);

                alter table nop_meta_tag drop constraint UK_NOP_META_TAG_CLS_FQN;
alter table nop_meta_tag add constraint UK_NOP_META_TAG_CLS_FQN unique (NOP_TENANT_ID,CLASSIFICATION_ID,FULLY_QUALIFIED_NAME);

                alter table nop_meta_data_product drop constraint UK_NOP_META_DATA_PRODUCT_DOMAIN_NAME;
alter table nop_meta_data_product add constraint UK_NOP_META_DATA_PRODUCT_DOMAIN_NAME unique (NOP_TENANT_ID,BUSINESS_DOMAIN_ID,NAME);

                alter table nop_meta_entity drop constraint UK_NOP_META_ENTITY_MODEL_NAME;
alter table nop_meta_entity add constraint UK_NOP_META_ENTITY_MODEL_NAME unique (NOP_TENANT_ID,ORM_MODEL_ID,ENTITY_NAME);

                alter table nop_meta_domain drop constraint UK_NOP_META_DOMAIN_MODEL_NAME;
alter table nop_meta_domain add constraint UK_NOP_META_DOMAIN_MODEL_NAME unique (NOP_TENANT_ID,ORM_MODEL_ID,DOMAIN_NAME);

                alter table nop_meta_dict drop constraint UK_NOP_META_DICT_MODEL_NAME;
alter table nop_meta_dict add constraint UK_NOP_META_DICT_MODEL_NAME unique (NOP_TENANT_ID,ORM_MODEL_ID,DICT_NAME);

                alter table nop_meta_table_dimension drop constraint UK_NOP_META_DIM_TABLE_NAME;
alter table nop_meta_table_dimension add constraint UK_NOP_META_DIM_TABLE_NAME unique (NOP_TENANT_ID,META_TABLE_ID,DIMENSION_NAME);

                alter table nop_meta_table_measure drop constraint UK_NOP_META_MEASURE_TABLE_NAME;
alter table nop_meta_table_measure add constraint UK_NOP_META_MEASURE_TABLE_NAME unique (NOP_TENANT_ID,META_TABLE_ID,MEASURE_NAME);

                alter table nop_meta_table_filter drop constraint UK_NOP_META_FILTER_TABLE_NAME;
alter table nop_meta_table_filter add constraint UK_NOP_META_FILTER_TABLE_NAME unique (NOP_TENANT_ID,META_TABLE_ID,FILTER_NAME);

                alter table nop_meta_profiling_rule drop constraint UK_NOP_META_PROFRULE_TABLE_NAME;
alter table nop_meta_profiling_rule add constraint UK_NOP_META_PROFRULE_TABLE_NAME unique (NOP_TENANT_ID,META_TABLE_ID,RULE_NAME);

                alter table nop_meta_data_contract drop constraint UK_NOP_META_CONTRACT_NAME;
alter table nop_meta_data_contract add constraint UK_NOP_META_CONTRACT_NAME unique (NOP_TENANT_ID,CONTRACT_NAME);

                alter table nop_meta_reconciliation_config drop constraint UK_NOP_META_RECONCILIATION_CONFIG_NAME;
alter table nop_meta_reconciliation_config add constraint UK_NOP_META_RECONCILIATION_CONFIG_NAME unique (NOP_TENANT_ID,CONFIG_NAME);

                alter table nop_meta_lineage_edge drop constraint UK_NOP_META_LINEAGE_EDGE_SRC_TGT_TYPE;
alter table nop_meta_lineage_edge add constraint UK_NOP_META_LINEAGE_EDGE_SRC_TGT_TYPE unique (NOP_TENANT_ID,SOURCE_TABLE_ID,SOURCE_COLUMN,TARGET_TABLE_ID,TARGET_COLUMN);

                alter table nop_meta_tag_label drop constraint UK_NOP_META_TAG_LABEL;
alter table nop_meta_tag_label add constraint UK_NOP_META_TAG_LABEL unique (NOP_TENANT_ID,ENTITY_TYPE,ENTITY_ID,TAG_ID,SOURCE);

                alter table nop_meta_entity_field drop constraint UK_NOP_META_FIELD_ENTITY_NAME;
alter table nop_meta_entity_field add constraint UK_NOP_META_FIELD_ENTITY_NAME unique (NOP_TENANT_ID,META_ENTITY_ID,FIELD_NAME);

                alter table nop_meta_entity_relation drop constraint UK_NOP_META_REL_ENTITY_NAME;
alter table nop_meta_entity_relation add constraint UK_NOP_META_REL_ENTITY_NAME unique (NOP_TENANT_ID,META_ENTITY_ID,RELATION_NAME);

                alter table nop_meta_entity_unique_key drop constraint UK_NOP_META_UK_ENTITY_NAME;
alter table nop_meta_entity_unique_key add constraint UK_NOP_META_UK_ENTITY_NAME unique (NOP_TENANT_ID,META_ENTITY_ID,UK_NAME);

                alter table nop_meta_entity_index drop constraint UK_NOP_META_IDX_ENTITY_NAME;
alter table nop_meta_entity_index add constraint UK_NOP_META_IDX_ENTITY_NAME unique (NOP_TENANT_ID,META_ENTITY_ID,INDEX_NAME);

                alter table nop_meta_table_join drop constraint UK_NOP_META_JOIN_TABLE_ALIAS;
alter table nop_meta_table_join add constraint UK_NOP_META_JOIN_TABLE_ALIAS unique (NOP_TENANT_ID,META_TABLE_ID,ALIAS);

                alter table nop_meta_dict_item drop constraint UK_NOP_META_DICT_ITEM_VALUE;
alter table nop_meta_dict_item add constraint UK_NOP_META_DICT_ITEM_VALUE unique (NOP_TENANT_ID,META_DICT_ID,ITEM_VALUE);

                
