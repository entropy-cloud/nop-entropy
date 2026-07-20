
CREATE TABLE nop_meta_module(
  meta_module_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(100) NOT NULL ,
  module_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  module_version INT8 NOT NULL ,
  base_module_id VARCHAR(32)  ,
  status TEXT NOT NULL ,
  maven_group_id VARCHAR(100)  ,
  maven_artifact_id VARCHAR(100)  ,
  maven_version VARCHAR(50)  ,
  git_repo_path VARCHAR(500)  ,
  git_branch VARCHAR(100)  ,
  git_commit_id VARCHAR(64)  ,
  imported_at TIMESTAMP  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_module primary key (meta_module_id)
);

CREATE TABLE nop_meta_data_source(
  data_source_id VARCHAR(32) NOT NULL ,
  query_space VARCHAR(100) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  datasource_type VARCHAR(30) NOT NULL ,
  connection_config VARCHAR(4000)  ,
  status TEXT NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_data_source primary key (data_source_id)
);

CREATE TABLE nop_meta_semantic_type(
  semantic_type_id VARCHAR(32) NOT NULL ,
  type_name VARCHAR(50) NOT NULL ,
  display_name VARCHAR(200)  ,
  description VARCHAR(1000)  ,
  applicable_to VARCHAR(1000)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_semantic_type primary key (semantic_type_id)
);

CREATE TABLE nop_meta_quality_rule(
  quality_rule_id VARCHAR(32) NOT NULL ,
  rule_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  rule_type VARCHAR(30) NOT NULL ,
  entity_type VARCHAR(20) NOT NULL ,
  entity_id VARCHAR(32) NOT NULL ,
  severity TEXT NOT NULL ,
  sql_expression TEXT  ,
  threshold FLOAT8  ,
  params VARCHAR(4000)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_quality_rule primary key (quality_rule_id)
);

CREATE TABLE nop_meta_reconciliation_entity(
  recon_entity_id VARCHAR(32) NOT NULL ,
  entity_id VARCHAR(100) NOT NULL ,
  entity_name VARCHAR(200) NOT NULL ,
  entity_type VARCHAR(100)  ,
  identifier_space VARCHAR(200)  ,
  properties VARCHAR(4000)  ,
  last_synced_at TIMESTAMP  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_reconciliation_entity primary key (recon_entity_id)
);

CREATE TABLE nop_meta_model_changed_event(
  model_changed_event_id VARCHAR(32) NOT NULL ,
  event_type VARCHAR(30) NOT NULL ,
  entity_type VARCHAR(100) NOT NULL ,
  entity_id VARCHAR(100) NOT NULL ,
  entity_name VARCHAR(200)  ,
  change_source VARCHAR(30) NOT NULL ,
  before_snapshot TEXT  ,
  after_snapshot TEXT  ,
  changed_by VARCHAR(50)  ,
  change_time TIMESTAMP NOT NULL ,
  transaction_id VARCHAR(64)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_model_changed_event primary key (model_changed_event_id)
);

CREATE TABLE nop_meta_orm_model(
  orm_model_id VARCHAR(32) NOT NULL ,
  meta_module_id VARCHAR(32) NOT NULL ,
  model_name VARCHAR(100) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  source_content TEXT  ,
  imported_at TIMESTAMP  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_orm_model primary key (orm_model_id)
);

CREATE TABLE nop_meta_table(
  meta_table_id VARCHAR(32) NOT NULL ,
  meta_module_id VARCHAR(32) NOT NULL ,
  table_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  table_type VARCHAR(20) NOT NULL ,
  query_space VARCHAR(100)  ,
  source_sql TEXT  ,
  base_entity_id VARCHAR(32)  ,
  description VARCHAR(1000)  ,
  build_sql TEXT  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  schema VARCHAR(100)  ,
  constraint PK_nop_meta_table primary key (meta_table_id)
);

CREATE TABLE nop_meta_pipeline(
  pipeline_id VARCHAR(32) NOT NULL ,
  meta_module_id VARCHAR(32) NOT NULL ,
  pipeline_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  pipeline_type VARCHAR(20) NOT NULL ,
  source_sql TEXT  ,
  schedule VARCHAR(200)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_pipeline primary key (pipeline_id)
);

CREATE TABLE nop_meta_quality_checkpoint(
  checkpoint_id VARCHAR(32) NOT NULL ,
  checkpoint_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  meta_module_id VARCHAR(32)  ,
  description VARCHAR(1000)  ,
  validations TEXT  ,
  actions VARCHAR(4000)  ,
  status TEXT NOT NULL ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_quality_checkpoint primary key (checkpoint_id)
);

CREATE TABLE nop_meta_manifest(
  manifest_id VARCHAR(32) NOT NULL ,
  meta_module_id VARCHAR(32) NOT NULL ,
  manifest_version INT8 NOT NULL ,
  generated_at TIMESTAMP NOT NULL ,
  nop_metadata_version VARCHAR(50)  ,
  content TEXT  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_manifest primary key (manifest_id)
);

CREATE TABLE nop_meta_quality_result(
  quality_result_id VARCHAR(32) NOT NULL ,
  quality_rule_id VARCHAR(32) NOT NULL ,
  execute_time TIMESTAMP NOT NULL ,
  status TEXT NOT NULL ,
  actual_value FLOAT8  ,
  expected_value FLOAT8  ,
  message VARCHAR(1000)  ,
  details VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_quality_result primary key (quality_result_id)
);

CREATE TABLE nop_meta_entity(
  meta_entity_id VARCHAR(32) NOT NULL ,
  orm_model_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  entity_name VARCHAR(200) NOT NULL ,
  table_name VARCHAR(100)  ,
  display_name VARCHAR(200)  ,
  class_name VARCHAR(300)  ,
  tag_set VARCHAR(500)  ,
  query_space VARCHAR(100)  ,
  persist_driver VARCHAR(50)  ,
  use_tenant INT4 default 0   ,
  use_revision INT4 default 0   ,
  use_logical_delete INT4 default 0   ,
  not_gen_code INT4 default 0   ,
  creater_prop VARCHAR(50)  ,
  create_time_prop VARCHAR(50)  ,
  updater_prop VARCHAR(50)  ,
  update_time_prop VARCHAR(50)  ,
  version_prop VARCHAR(50)  ,
  del_flag_prop VARCHAR(50)  ,
  del_version_prop VARCHAR(50)  ,
  db_catalog VARCHAR(100)  ,
  db_schema VARCHAR(100)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_entity primary key (meta_entity_id)
);

CREATE TABLE nop_meta_domain(
  meta_domain_id VARCHAR(32) NOT NULL ,
  orm_model_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  domain_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  description VARCHAR(1000)  ,
  std_domain VARCHAR(100)  ,
  std_data_type VARCHAR(30)  ,
  std_sql_type VARCHAR(30)  ,
  precision INT4  ,
  scale INT4  ,
  validation_pattern VARCHAR(500)  ,
  default_value VARCHAR(500)  ,
  is_global INT4 default 0   ,
  source_module_id VARCHAR(32)  ,
  tag_set VARCHAR(500)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_domain primary key (meta_domain_id)
);

CREATE TABLE nop_meta_dict(
  meta_dict_id VARCHAR(32) NOT NULL ,
  orm_model_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  dict_name VARCHAR(100) NOT NULL ,
  label VARCHAR(200)  ,
  value_type VARCHAR(20)  ,
  locale VARCHAR(20)  ,
  is_static INT4 default 0   ,
  normalized INT4 default 0   ,
  deprecated INT4 default 0   ,
  internal INT4 default 0   ,
  tag_set VARCHAR(500)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_dict primary key (meta_dict_id)
);

CREATE TABLE nop_meta_table_dimension(
  dimension_id VARCHAR(32) NOT NULL ,
  meta_table_id VARCHAR(32) NOT NULL ,
  dimension_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  entity_field_id VARCHAR(32)  ,
  dimension_type VARCHAR(30)  ,
  granularity VARCHAR(20)  ,
  format VARCHAR(100)  ,
  sort_order INT4  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  side VARCHAR(20)  ,
  constraint PK_nop_meta_table_dimension primary key (dimension_id)
);

CREATE TABLE nop_meta_table_measure(
  measure_id VARCHAR(32) NOT NULL ,
  meta_table_id VARCHAR(32) NOT NULL ,
  measure_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  entity_field_id VARCHAR(32)  ,
  agg_func VARCHAR(30)  ,
  expression VARCHAR(1000)  ,
  format VARCHAR(100)  ,
  currency_unit VARCHAR(20)  ,
  description VARCHAR(1000)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  side VARCHAR(20)  ,
  constraint PK_nop_meta_table_measure primary key (measure_id)
);

CREATE TABLE nop_meta_table_filter(
  filter_id VARCHAR(32) NOT NULL ,
  meta_table_id VARCHAR(32) NOT NULL ,
  filter_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  definition VARCHAR(4000) NOT NULL ,
  description VARCHAR(1000)  ,
  is_default INT4 default 0   ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_table_filter primary key (filter_id)
);

CREATE TABLE nop_meta_catalog(
  meta_catalog_id VARCHAR(32) NOT NULL ,
  meta_table_id VARCHAR(32) NOT NULL ,
  row_count INT8 NOT NULL ,
  size_bytes INT8  ,
  index_count INT4  ,
  partition_count INT4  ,
  last_modified TIMESTAMP  ,
  details TEXT  ,
  collected_at TIMESTAMP NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_catalog primary key (meta_catalog_id)
);

CREATE TABLE nop_meta_profiling_rule(
  profiling_rule_id VARCHAR(32) NOT NULL ,
  rule_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  meta_table_id VARCHAR(32) NOT NULL ,
  columns VARCHAR(4000)  ,
  stats VARCHAR(4000)  ,
  sample_size INT4  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_profiling_rule primary key (profiling_rule_id)
);

CREATE TABLE nop_meta_data_contract(
  contract_id VARCHAR(32) NOT NULL ,
  contract_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  entity_table_id VARCHAR(32)  ,
  status TEXT NOT NULL ,
  owner_user_id VARCHAR(50)  ,
  schema TEXT  ,
  sla VARCHAR(4000)  ,
  quality_expectations VARCHAR(4000)  ,
  security VARCHAR(4000)  ,
  latest_result TEXT  ,
  tag_set VARCHAR(500)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_data_contract primary key (contract_id)
);

CREATE TABLE nop_meta_reconciliation_config(
  config_id VARCHAR(32) NOT NULL ,
  config_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  meta_module_id VARCHAR(32)  ,
  meta_table_id VARCHAR(32) NOT NULL ,
  column_name VARCHAR(100) NOT NULL ,
  identifier_space VARCHAR(200)  ,
  target_entity_type VARCHAR(100)  ,
  match_strategy VARCHAR(30) NOT NULL ,
  auto_match INT4 default 0  NOT NULL ,
  auto_match_threshold FLOAT8 NOT NULL ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_reconciliation_config primary key (config_id)
);

CREATE TABLE nop_meta_quality_score(
  quality_score_id VARCHAR(32) NOT NULL ,
  meta_table_id VARCHAR(32) NOT NULL ,
  score_time TIMESTAMP NOT NULL ,
  overall_score FLOAT8 NOT NULL ,
  dimension_scores TEXT  ,
  rule_summary VARCHAR(4000)  ,
  trend VARCHAR(4000)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_quality_score primary key (quality_score_id)
);

CREATE TABLE nop_meta_lineage_edge(
  lineage_edge_id VARCHAR(32) NOT NULL ,
  source_table_id VARCHAR(32) NOT NULL ,
  target_table_id VARCHAR(32) NOT NULL ,
  source_column VARCHAR(100)  ,
  target_column VARCHAR(100)  ,
  transform_type VARCHAR(20)  ,
  transform_expr VARCHAR(1000)  ,
  lineage_source VARCHAR(30)  ,
  pipeline_id VARCHAR(32)  ,
  confidence FLOAT8  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_lineage_edge primary key (lineage_edge_id)
);

CREATE TABLE nop_meta_entity_field(
  entity_field_id VARCHAR(32) NOT NULL ,
  meta_entity_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  field_name VARCHAR(100) NOT NULL ,
  column_code VARCHAR(100)  ,
  prop_id INT4  ,
  std_data_type VARCHAR(30)  ,
  std_sql_type VARCHAR(30)  ,
  precision INT4  ,
  scale INT4  ,
  mandatory INT4 default 0   ,
  "primary" INT4 default 0   ,
  lazy INT4 default 0   ,
  insertable INT4 default 0   ,
  updatable INT4 default 0   ,
  domain VARCHAR(100)  ,
  std_domain VARCHAR(100)  ,
  fixed_value VARCHAR(500)  ,
  default_value VARCHAR(500)  ,
  semantic_type VARCHAR(50)  ,
  tag_set VARCHAR(500)  ,
  display_name VARCHAR(200)  ,
  comment VARCHAR(1000)  ,
  native_sql_type VARCHAR(100)  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_entity_field primary key (entity_field_id)
);

CREATE TABLE nop_meta_entity_relation(
  relation_id VARCHAR(32) NOT NULL ,
  meta_entity_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  relation_name VARCHAR(100) NOT NULL ,
  relation_type VARCHAR(20) NOT NULL ,
  ref_entity_name VARCHAR(200)  ,
  ref_prop_name VARCHAR(100)  ,
  cascade_delete INT4 default 0   ,
  auto_cascade_delete INT4 default 0   ,
  queryable INT4 default 0   ,
  embedded INT4 default 0   ,
  not_gen_code INT4 default 0   ,
  tag_set VARCHAR(500)  ,
  join_conditions VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_entity_relation primary key (relation_id)
);

CREATE TABLE nop_meta_entity_unique_key(
  unique_key_id VARCHAR(32) NOT NULL ,
  meta_entity_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  uk_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  columns VARCHAR(1000) NOT NULL ,
  "constraint" VARCHAR(100)  ,
  tag_set VARCHAR(500)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_entity_unique_key primary key (unique_key_id)
);

CREATE TABLE nop_meta_entity_index(
  index_id VARCHAR(32) NOT NULL ,
  meta_entity_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  index_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  index_type VARCHAR(30)  ,
  "unique" INT4 default 0   ,
  index_columns VARCHAR(4000) NOT NULL ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_entity_index primary key (index_id)
);

CREATE TABLE nop_meta_table_join(
  join_id VARCHAR(32) NOT NULL ,
  meta_table_id VARCHAR(32) NOT NULL ,
  join_type VARCHAR(20) NOT NULL ,
  left_entity_id VARCHAR(32)  ,
  right_entity_id VARCHAR(32)  ,
  left_field VARCHAR(100)  ,
  right_field VARCHAR(100)  ,
  alias VARCHAR(100)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  left_table_id VARCHAR(32)  ,
  right_table_id VARCHAR(32)  ,
  constraint PK_nop_meta_table_join primary key (join_id)
);

CREATE TABLE nop_meta_dict_item(
  dict_item_id VARCHAR(32) NOT NULL ,
  meta_dict_id VARCHAR(32) NOT NULL ,
  item_value VARCHAR(100) NOT NULL ,
  item_label VARCHAR(200)  ,
  item_code VARCHAR(100)  ,
  item_group VARCHAR(100)  ,
  description VARCHAR(1000)  ,
  sort_order INT4  ,
  deprecated INT4 default 0   ,
  internal INT4 default 0   ,
  is_delta INT4 default 0   ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_dict_item primary key (dict_item_id)
);

CREATE TABLE nop_meta_profiling_result(
  profiling_result_id VARCHAR(32) NOT NULL ,
  profiling_rule_id VARCHAR(32)  ,
  meta_table_id VARCHAR(32) NOT NULL ,
  snapshot_time TIMESTAMP NOT NULL ,
  table_stats TEXT  ,
  column_stats TEXT  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_profiling_result primary key (profiling_result_id)
);

CREATE TABLE nop_meta_reconciliation_result(
  result_id VARCHAR(32) NOT NULL ,
  config_id VARCHAR(32) NOT NULL ,
  meta_table_id VARCHAR(32) NOT NULL ,
  execute_time TIMESTAMP NOT NULL ,
  statistics VARCHAR(4000)  ,
  details TEXT  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_meta_reconciliation_result primary key (result_id)
);


      COMMENT ON TABLE nop_meta_module IS '元数据模块';
                
      COMMENT ON COLUMN nop_meta_module.meta_module_id IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_module.module_id IS '模块标识';
                    
      COMMENT ON COLUMN nop_meta_module.module_name IS '模块名';
                    
      COMMENT ON COLUMN nop_meta_module.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_module.module_version IS '模块版本号';
                    
      COMMENT ON COLUMN nop_meta_module.base_module_id IS '基线模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_module.status IS '模块状态';
                    
      COMMENT ON COLUMN nop_meta_module.maven_group_id IS 'Maven GroupId';
                    
      COMMENT ON COLUMN nop_meta_module.maven_artifact_id IS 'Maven ArtifactId';
                    
      COMMENT ON COLUMN nop_meta_module.maven_version IS 'Maven版本';
                    
      COMMENT ON COLUMN nop_meta_module.git_repo_path IS 'Git仓库路径';
                    
      COMMENT ON COLUMN nop_meta_module.git_branch IS 'Git分支';
                    
      COMMENT ON COLUMN nop_meta_module.git_commit_id IS 'Git提交';
                    
      COMMENT ON COLUMN nop_meta_module.imported_at IS '导入时间';
                    
      COMMENT ON COLUMN nop_meta_module.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_module.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_module.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_module.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_module.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_module.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_module.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_data_source IS '数据源';
                
      COMMENT ON COLUMN nop_meta_data_source.data_source_id IS '数据源ID';
                    
      COMMENT ON COLUMN nop_meta_data_source.query_space IS '查询空间';
                    
      COMMENT ON COLUMN nop_meta_data_source.name IS '名称';
                    
      COMMENT ON COLUMN nop_meta_data_source.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_data_source.datasource_type IS '数据源类型';
                    
      COMMENT ON COLUMN nop_meta_data_source.connection_config IS '连接配置';
                    
      COMMENT ON COLUMN nop_meta_data_source.status IS '状态';
                    
      COMMENT ON COLUMN nop_meta_data_source.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_data_source.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_data_source.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_data_source.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_data_source.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_data_source.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_semantic_type IS '语义类型';
                
      COMMENT ON COLUMN nop_meta_semantic_type.semantic_type_id IS '语义类型ID';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.type_name IS '类型名';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.description IS '描述';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.applicable_to IS '适用数据类型';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_quality_rule IS '质量规则';
                
      COMMENT ON COLUMN nop_meta_quality_rule.quality_rule_id IS '规则ID';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.rule_name IS '规则名';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.rule_type IS '规则类型';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.entity_type IS '对象类型';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.entity_id IS '挂载对象ID';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.severity IS '严重级别';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.sql_expression IS 'SQL表达式';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.threshold IS '阈值';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.params IS '参数';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_reconciliation_entity IS '对账实体';
                
      COMMENT ON COLUMN nop_meta_reconciliation_entity.recon_entity_id IS '对账实体ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.entity_name IS '实体名';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.entity_type IS '实体类型';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.identifier_space IS '标识符空间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.properties IS '实体属性';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.last_synced_at IS '最后同步时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_entity.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_model_changed_event IS '元数据变更事件';
                
      COMMENT ON COLUMN nop_meta_model_changed_event.model_changed_event_id IS '事件ID';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.event_type IS '事件类型';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.entity_type IS '实体类型';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.entity_name IS '实体名称';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.change_source IS '变更来源';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.before_snapshot IS '变更前快照';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.after_snapshot IS '变更后快照';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.changed_by IS '操作人';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.change_time IS '变更时间';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.transaction_id IS '事务ID';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_model_changed_event.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_orm_model IS 'ORM模型';
                
      COMMENT ON COLUMN nop_meta_orm_model.orm_model_id IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_orm_model.meta_module_id IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_orm_model.model_name IS '模型名';
                    
      COMMENT ON COLUMN nop_meta_orm_model.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_orm_model.source_content IS '原始内容';
                    
      COMMENT ON COLUMN nop_meta_orm_model.imported_at IS '导入时间';
                    
      COMMENT ON COLUMN nop_meta_orm_model.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_orm_model.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_orm_model.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_orm_model.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_orm_model.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_orm_model.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_table IS '逻辑表';
                
      COMMENT ON COLUMN nop_meta_table.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table.meta_module_id IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_table.table_name IS '表名';
                    
      COMMENT ON COLUMN nop_meta_table.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table.table_type IS '表类型';
                    
      COMMENT ON COLUMN nop_meta_table.query_space IS '查询空间';
                    
      COMMENT ON COLUMN nop_meta_table.source_sql IS '来源SQL';
                    
      COMMENT ON COLUMN nop_meta_table.base_entity_id IS '主要实体ID';
                    
      COMMENT ON COLUMN nop_meta_table.description IS '描述';
                    
      COMMENT ON COLUMN nop_meta_table.build_sql IS '合成SQL';
                    
      COMMENT ON COLUMN nop_meta_table.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table.remark IS '备注';
                    
      COMMENT ON COLUMN nop_meta_table.schema IS '源schema';
                    
      COMMENT ON TABLE nop_meta_pipeline IS '数据管道';
                
      COMMENT ON COLUMN nop_meta_pipeline.pipeline_id IS '管道ID';
                    
      COMMENT ON COLUMN nop_meta_pipeline.meta_module_id IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_pipeline.pipeline_name IS '管道名';
                    
      COMMENT ON COLUMN nop_meta_pipeline.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_pipeline.pipeline_type IS '管道类型';
                    
      COMMENT ON COLUMN nop_meta_pipeline.source_sql IS '处理SQL';
                    
      COMMENT ON COLUMN nop_meta_pipeline.schedule IS '调度表达式';
                    
      COMMENT ON COLUMN nop_meta_pipeline.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_pipeline.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_pipeline.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_pipeline.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_pipeline.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_pipeline.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_pipeline.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_quality_checkpoint IS '质量检查点';
                
      COMMENT ON COLUMN nop_meta_quality_checkpoint.checkpoint_id IS '检查点ID';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.checkpoint_name IS '检查点名';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.meta_module_id IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.description IS '描述';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.validations IS '验证配置';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.actions IS '执行动作';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.status IS '状态';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_quality_checkpoint.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_manifest IS '元数据快照';
                
      COMMENT ON COLUMN nop_meta_manifest.manifest_id IS '快照ID';
                    
      COMMENT ON COLUMN nop_meta_manifest.meta_module_id IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_manifest.manifest_version IS '快照版本号';
                    
      COMMENT ON COLUMN nop_meta_manifest.generated_at IS '生成时间';
                    
      COMMENT ON COLUMN nop_meta_manifest.nop_metadata_version IS '平台版本';
                    
      COMMENT ON COLUMN nop_meta_manifest.content IS '快照内容';
                    
      COMMENT ON COLUMN nop_meta_manifest.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_manifest.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_manifest.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_manifest.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_manifest.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_manifest.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_quality_result IS '质量结果';
                
      COMMENT ON COLUMN nop_meta_quality_result.quality_result_id IS '结果ID';
                    
      COMMENT ON COLUMN nop_meta_quality_result.quality_rule_id IS '规则ID';
                    
      COMMENT ON COLUMN nop_meta_quality_result.execute_time IS '执行时间';
                    
      COMMENT ON COLUMN nop_meta_quality_result.status IS '状态';
                    
      COMMENT ON COLUMN nop_meta_quality_result.actual_value IS '实际值';
                    
      COMMENT ON COLUMN nop_meta_quality_result.expected_value IS '期望值';
                    
      COMMENT ON COLUMN nop_meta_quality_result.message IS '结果描述';
                    
      COMMENT ON COLUMN nop_meta_quality_result.details IS '详情';
                    
      COMMENT ON COLUMN nop_meta_quality_result.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_quality_result.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_quality_result.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_quality_result.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_quality_result.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_quality_result.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity IS '元数据实体';
                
      COMMENT ON COLUMN nop_meta_entity.meta_entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity.orm_model_id IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_entity.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity.entity_name IS '实体名';
                    
      COMMENT ON COLUMN nop_meta_entity.table_name IS '表名';
                    
      COMMENT ON COLUMN nop_meta_entity.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity.class_name IS '类名';
                    
      COMMENT ON COLUMN nop_meta_entity.tag_set IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity.query_space IS '查询空间';
                    
      COMMENT ON COLUMN nop_meta_entity.persist_driver IS '持久化驱动';
                    
      COMMENT ON COLUMN nop_meta_entity.use_tenant IS '使用租户';
                    
      COMMENT ON COLUMN nop_meta_entity.use_revision IS '使用版本';
                    
      COMMENT ON COLUMN nop_meta_entity.use_logical_delete IS '逻辑删除';
                    
      COMMENT ON COLUMN nop_meta_entity.not_gen_code IS '不生成代码';
                    
      COMMENT ON COLUMN nop_meta_entity.creater_prop IS '创建人属性';
                    
      COMMENT ON COLUMN nop_meta_entity.create_time_prop IS '创建时间属性';
                    
      COMMENT ON COLUMN nop_meta_entity.updater_prop IS '修改人属性';
                    
      COMMENT ON COLUMN nop_meta_entity.update_time_prop IS '修改时间属性';
                    
      COMMENT ON COLUMN nop_meta_entity.version_prop IS '版本属性';
                    
      COMMENT ON COLUMN nop_meta_entity.del_flag_prop IS '删除标记属性';
                    
      COMMENT ON COLUMN nop_meta_entity.del_version_prop IS '删除版本属性';
                    
      COMMENT ON COLUMN nop_meta_entity.db_catalog IS '数据库目录';
                    
      COMMENT ON COLUMN nop_meta_entity.db_schema IS '数据库Schema';
                    
      COMMENT ON COLUMN nop_meta_entity.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_entity.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_domain IS '域定义';
                
      COMMENT ON COLUMN nop_meta_domain.meta_domain_id IS '域ID';
                    
      COMMENT ON COLUMN nop_meta_domain.orm_model_id IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_domain.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_domain.domain_name IS '域名';
                    
      COMMENT ON COLUMN nop_meta_domain.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_domain.description IS '描述';
                    
      COMMENT ON COLUMN nop_meta_domain.std_domain IS '标准域';
                    
      COMMENT ON COLUMN nop_meta_domain.std_data_type IS '数据类型';
                    
      COMMENT ON COLUMN nop_meta_domain.std_sql_type IS 'SQL类型';
                    
      COMMENT ON COLUMN nop_meta_domain.precision IS '精度';
                    
      COMMENT ON COLUMN nop_meta_domain.scale IS '标度';
                    
      COMMENT ON COLUMN nop_meta_domain.validation_pattern IS '校验正则';
                    
      COMMENT ON COLUMN nop_meta_domain.default_value IS '默认值';
                    
      COMMENT ON COLUMN nop_meta_domain.is_global IS '全局通用域';
                    
      COMMENT ON COLUMN nop_meta_domain.source_module_id IS '来源模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_domain.tag_set IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_domain.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_domain.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_domain.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_domain.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_domain.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_domain.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_domain.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_dict IS '元数据字典';
                
      COMMENT ON COLUMN nop_meta_dict.meta_dict_id IS '字典ID';
                    
      COMMENT ON COLUMN nop_meta_dict.orm_model_id IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_dict.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_dict.dict_name IS '字典名';
                    
      COMMENT ON COLUMN nop_meta_dict.label IS '字典标签';
                    
      COMMENT ON COLUMN nop_meta_dict.value_type IS '值类型';
                    
      COMMENT ON COLUMN nop_meta_dict.locale IS '区域';
                    
      COMMENT ON COLUMN nop_meta_dict.is_static IS '静态字典';
                    
      COMMENT ON COLUMN nop_meta_dict.normalized IS '已标准化';
                    
      COMMENT ON COLUMN nop_meta_dict.deprecated IS '已废弃';
                    
      COMMENT ON COLUMN nop_meta_dict.internal IS '内部使用';
                    
      COMMENT ON COLUMN nop_meta_dict.tag_set IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_dict.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_dict.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_dict.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_dict.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_dict.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_dict.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_table_dimension IS '表维度';
                
      COMMENT ON COLUMN nop_meta_table_dimension.dimension_id IS '维度ID';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.dimension_name IS '维度名';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.entity_field_id IS '实体字段ID';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.dimension_type IS '维度类型';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.granularity IS '时间粒度';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.format IS '显示格式';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.sort_order IS '排序';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.remark IS '备注';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.side IS '侧别';
                    
      COMMENT ON TABLE nop_meta_table_measure IS '表指标';
                
      COMMENT ON COLUMN nop_meta_table_measure.measure_id IS '指标ID';
                    
      COMMENT ON COLUMN nop_meta_table_measure.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_measure.measure_name IS '指标名';
                    
      COMMENT ON COLUMN nop_meta_table_measure.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table_measure.entity_field_id IS '实体字段ID';
                    
      COMMENT ON COLUMN nop_meta_table_measure.agg_func IS '聚合函数';
                    
      COMMENT ON COLUMN nop_meta_table_measure.expression IS '表达式';
                    
      COMMENT ON COLUMN nop_meta_table_measure.format IS '显示格式';
                    
      COMMENT ON COLUMN nop_meta_table_measure.currency_unit IS '货币单位';
                    
      COMMENT ON COLUMN nop_meta_table_measure.description IS '描述';
                    
      COMMENT ON COLUMN nop_meta_table_measure.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_table_measure.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_measure.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_measure.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_measure.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_measure.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_measure.remark IS '备注';
                    
      COMMENT ON COLUMN nop_meta_table_measure.side IS '侧别';
                    
      COMMENT ON TABLE nop_meta_table_filter IS '表过滤器';
                
      COMMENT ON COLUMN nop_meta_table_filter.filter_id IS '过滤器ID';
                    
      COMMENT ON COLUMN nop_meta_table_filter.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_filter.filter_name IS '过滤器名';
                    
      COMMENT ON COLUMN nop_meta_table_filter.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table_filter.definition IS '筛选条件';
                    
      COMMENT ON COLUMN nop_meta_table_filter.description IS '描述';
                    
      COMMENT ON COLUMN nop_meta_table_filter.is_default IS '默认过滤器';
                    
      COMMENT ON COLUMN nop_meta_table_filter.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_table_filter.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_filter.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_filter.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_filter.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_filter.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_filter.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_catalog IS '运行时统计快照';
                
      COMMENT ON COLUMN nop_meta_catalog.meta_catalog_id IS '统计快照ID';
                    
      COMMENT ON COLUMN nop_meta_catalog.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_catalog.row_count IS '行数';
                    
      COMMENT ON COLUMN nop_meta_catalog.size_bytes IS '表物理大小';
                    
      COMMENT ON COLUMN nop_meta_catalog.index_count IS '索引数量';
                    
      COMMENT ON COLUMN nop_meta_catalog.partition_count IS '分区数';
                    
      COMMENT ON COLUMN nop_meta_catalog.last_modified IS '最后修改时间';
                    
      COMMENT ON COLUMN nop_meta_catalog.details IS '扩展详情';
                    
      COMMENT ON COLUMN nop_meta_catalog.collected_at IS '收集时间';
                    
      COMMENT ON COLUMN nop_meta_catalog.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_catalog.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_catalog.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_catalog.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_catalog.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_catalog.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_profiling_rule IS '数据剖析规则';
                
      COMMENT ON COLUMN nop_meta_profiling_rule.profiling_rule_id IS '剖析规则ID';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.rule_name IS '规则名';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.meta_table_id IS '剖析表ID';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.columns IS '剖析列';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.stats IS '统计指标';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.sample_size IS '采样大小';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_profiling_rule.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_data_contract IS '数据契约';
                
      COMMENT ON COLUMN nop_meta_data_contract.contract_id IS '契约ID';
                    
      COMMENT ON COLUMN nop_meta_data_contract.contract_name IS '契约名';
                    
      COMMENT ON COLUMN nop_meta_data_contract.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_data_contract.entity_table_id IS '关联数据表ID';
                    
      COMMENT ON COLUMN nop_meta_data_contract.status IS '契约状态';
                    
      COMMENT ON COLUMN nop_meta_data_contract.owner_user_id IS '契约所有者';
                    
      COMMENT ON COLUMN nop_meta_data_contract.schema IS 'JSON Schema 定义';
                    
      COMMENT ON COLUMN nop_meta_data_contract.sla IS 'SLA 定义';
                    
      COMMENT ON COLUMN nop_meta_data_contract.quality_expectations IS '质量期望';
                    
      COMMENT ON COLUMN nop_meta_data_contract.security IS '安全策略';
                    
      COMMENT ON COLUMN nop_meta_data_contract.latest_result IS '最新执行结果';
                    
      COMMENT ON COLUMN nop_meta_data_contract.tag_set IS '标签集合';
                    
      COMMENT ON COLUMN nop_meta_data_contract.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_data_contract.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_data_contract.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_data_contract.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_data_contract.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_data_contract.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_data_contract.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_reconciliation_config IS '对账配置';
                
      COMMENT ON COLUMN nop_meta_reconciliation_config.config_id IS '配置ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.config_name IS '配置名';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.meta_module_id IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.column_name IS '待对账列名';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.identifier_space IS '标识符空间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.target_entity_type IS '目标实体类型';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.match_strategy IS '匹配策略';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.auto_match IS '是否自动匹配';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.auto_match_threshold IS '自动匹配阈值';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_config.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_quality_score IS '质量评分';
                
      COMMENT ON COLUMN nop_meta_quality_score.quality_score_id IS '评分ID';
                    
      COMMENT ON COLUMN nop_meta_quality_score.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_quality_score.score_time IS '评分时间';
                    
      COMMENT ON COLUMN nop_meta_quality_score.overall_score IS '总分';
                    
      COMMENT ON COLUMN nop_meta_quality_score.dimension_scores IS '维度评分';
                    
      COMMENT ON COLUMN nop_meta_quality_score.rule_summary IS '规则汇总';
                    
      COMMENT ON COLUMN nop_meta_quality_score.trend IS '趋势';
                    
      COMMENT ON COLUMN nop_meta_quality_score.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_quality_score.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_quality_score.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_quality_score.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_quality_score.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_quality_score.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_quality_score.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_lineage_edge IS '血缘边';
                
      COMMENT ON COLUMN nop_meta_lineage_edge.lineage_edge_id IS '血缘边ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.source_table_id IS '源表ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.target_table_id IS '目标表ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.source_column IS '源列名';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.target_column IS '目标列名';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.transform_type IS '转换类型';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.transform_expr IS '转换表达式';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.lineage_source IS '血缘来源';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.pipeline_id IS '管道ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.confidence IS '置信度';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_field IS '实体字段';
                
      COMMENT ON COLUMN nop_meta_entity_field.entity_field_id IS '字段ID';
                    
      COMMENT ON COLUMN nop_meta_entity_field.meta_entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_field.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_field.field_name IS '属性名';
                    
      COMMENT ON COLUMN nop_meta_entity_field.column_code IS '列名';
                    
      COMMENT ON COLUMN nop_meta_entity_field.prop_id IS '属性序号';
                    
      COMMENT ON COLUMN nop_meta_entity_field.std_data_type IS '数据类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.std_sql_type IS 'SQL类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.precision IS '精度';
                    
      COMMENT ON COLUMN nop_meta_entity_field.scale IS '标度';
                    
      COMMENT ON COLUMN nop_meta_entity_field.mandatory IS '必填';
                    
      COMMENT ON COLUMN nop_meta_entity_field."primary" IS '主键';
                    
      COMMENT ON COLUMN nop_meta_entity_field.lazy IS '懒加载';
                    
      COMMENT ON COLUMN nop_meta_entity_field.insertable IS '可插入';
                    
      COMMENT ON COLUMN nop_meta_entity_field.updatable IS '可更新';
                    
      COMMENT ON COLUMN nop_meta_entity_field.domain IS '域';
                    
      COMMENT ON COLUMN nop_meta_entity_field.std_domain IS '标准域';
                    
      COMMENT ON COLUMN nop_meta_entity_field.fixed_value IS '固定值';
                    
      COMMENT ON COLUMN nop_meta_entity_field.default_value IS '默认值';
                    
      COMMENT ON COLUMN nop_meta_entity_field.semantic_type IS '语义类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.tag_set IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity_field.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity_field.comment IS '注释';
                    
      COMMENT ON COLUMN nop_meta_entity_field.native_sql_type IS '原生SQL类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_entity_field.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_field.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_field.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_field.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_field.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_field.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_relation IS '实体关系';
                
      COMMENT ON COLUMN nop_meta_entity_relation.relation_id IS '关系ID';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.meta_entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.relation_name IS '关系名';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.relation_type IS '关系类型';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.ref_entity_name IS '引用实体名';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.ref_prop_name IS '引用属性名';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.cascade_delete IS '级联删除';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.auto_cascade_delete IS '自动级联删除';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.queryable IS '可查询';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.embedded IS '内嵌';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.not_gen_code IS '不生成代码';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.tag_set IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.join_conditions IS '关联条件';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_unique_key IS '实体唯一键';
                
      COMMENT ON COLUMN nop_meta_entity_unique_key.unique_key_id IS '唯一键ID';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.meta_entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.uk_name IS '唯一键名';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.columns IS '字段列表';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key."constraint" IS '约束名';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.tag_set IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_index IS '实体索引';
                
      COMMENT ON COLUMN nop_meta_entity_index.index_id IS '索引ID';
                    
      COMMENT ON COLUMN nop_meta_entity_index.meta_entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_index.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_index.index_name IS '索引名';
                    
      COMMENT ON COLUMN nop_meta_entity_index.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity_index.index_type IS '索引类型';
                    
      COMMENT ON COLUMN nop_meta_entity_index."unique" IS '唯一索引';
                    
      COMMENT ON COLUMN nop_meta_entity_index.index_columns IS '索引列';
                    
      COMMENT ON COLUMN nop_meta_entity_index.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_index.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_index.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_index.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_index.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_index.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_table_join IS '表关联';
                
      COMMENT ON COLUMN nop_meta_table_join.join_id IS '关联ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.join_type IS '关联类型';
                    
      COMMENT ON COLUMN nop_meta_table_join.left_entity_id IS '左实体ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.right_entity_id IS '右实体ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.left_field IS '左关联字段';
                    
      COMMENT ON COLUMN nop_meta_table_join.right_field IS '右关联字段';
                    
      COMMENT ON COLUMN nop_meta_table_join.alias IS '右表别名';
                    
      COMMENT ON COLUMN nop_meta_table_join.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_join.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_join.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_join.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_join.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_join.remark IS '备注';
                    
      COMMENT ON COLUMN nop_meta_table_join.left_table_id IS '左表ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.right_table_id IS '右表ID';
                    
      COMMENT ON TABLE nop_meta_dict_item IS '字典项';
                
      COMMENT ON COLUMN nop_meta_dict_item.dict_item_id IS '字典项ID';
                    
      COMMENT ON COLUMN nop_meta_dict_item.meta_dict_id IS '字典ID';
                    
      COMMENT ON COLUMN nop_meta_dict_item.item_value IS '字典值';
                    
      COMMENT ON COLUMN nop_meta_dict_item.item_label IS '字典标签';
                    
      COMMENT ON COLUMN nop_meta_dict_item.item_code IS '字典编码';
                    
      COMMENT ON COLUMN nop_meta_dict_item.item_group IS '分组';
                    
      COMMENT ON COLUMN nop_meta_dict_item.description IS '描述';
                    
      COMMENT ON COLUMN nop_meta_dict_item.sort_order IS '排序';
                    
      COMMENT ON COLUMN nop_meta_dict_item.deprecated IS '已废弃';
                    
      COMMENT ON COLUMN nop_meta_dict_item.internal IS '内部使用';
                    
      COMMENT ON COLUMN nop_meta_dict_item.is_delta IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_dict_item.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_dict_item.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_dict_item.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_dict_item.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_dict_item.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_dict_item.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_profiling_result IS '数据剖析结果';
                
      COMMENT ON COLUMN nop_meta_profiling_result.profiling_result_id IS '剖析结果ID';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.profiling_rule_id IS '剖析规则ID';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.snapshot_time IS '快照时间';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.table_stats IS '表级统计';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.column_stats IS '列级统计';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_profiling_result.remark IS '备注';
                    
      COMMENT ON TABLE nop_meta_reconciliation_result IS '对账结果';
                
      COMMENT ON COLUMN nop_meta_reconciliation_result.result_id IS '结果ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.config_id IS '配置ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.meta_table_id IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.execute_time IS '执行时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.statistics IS '统计信息';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.details IS '明细';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_reconciliation_result.remark IS '备注';
                    
