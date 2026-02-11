
CREATE TABLE nop_dyn_app(
  app_id VARCHAR(32) NOT NULL ,
  app_name VARCHAR(200) NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  app_version INT4 default 1  NOT NULL ,
  sort_order INT4  ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_app primary key (app_id)
);

CREATE TABLE nop_dyn_module(
  module_id VARCHAR(32) NOT NULL ,
  module_name VARCHAR(100) NOT NULL ,
  module_version INT4 default 1  NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  base_module_id VARCHAR(100)  ,
  base_package_name VARCHAR(200)  ,
  entity_package_name VARCHAR(200)  ,
  maven_group_id VARCHAR(200)  ,
  status INT4 default 0  NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_module primary key (module_id)
);

CREATE TABLE nop_dyn_entity(
  sid VARCHAR(32) NOT NULL ,
  nop_obj_type VARCHAR(100) NOT NULL ,
  nop_name VARCHAR(100)  ,
  nop_display_name VARCHAR(500)  ,
  nop_sort_order INT4  ,
  nop_flow_id VARCHAR(32)  ,
  nop_status INT4  ,
  nop_biz_state VARCHAR(50)  ,
  nop_parent_id VARCHAR(32)  ,
  nop_owner_name VARCHAR(50)  ,
  nop_owner_id VARCHAR(50)  ,
  nop_dept_id VARCHAR(50)  ,
  nop_string_fld1 VARCHAR(4000)  ,
  nop_decimal_fld1 NUMERIC(30,6)  ,
  nop_int_fld1 INT4  ,
  nop_long_fld1 INT8  ,
  nop_date_fld1 DATE  ,
  nop_timestamp_fld1 TIMESTAMP  ,
  nop_file_fld1 VARCHAR(200)  ,
  nop_string_fld2 VARCHAR(4000)  ,
  nop_decimal_fld2 NUMERIC(30,6)  ,
  nop_int_fld2 INT4  ,
  nop_long_fld2 INT8  ,
  nop_date_fld2 DATE  ,
  nop_timestamp_fld2 TIMESTAMP  ,
  nop_file_fld2 VARCHAR(200)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_entity primary key (sid)
);

CREATE TABLE nop_dyn_entity_relation(
  sid VARCHAR(32) NOT NULL ,
  relation_name VARCHAR(50) NOT NULL ,
  entity_name1 VARCHAR(100) NOT NULL ,
  entity_id1 VARCHAR(50) NOT NULL ,
  entity_name2 VARCHAR(100) NOT NULL ,
  entity_id2 VARCHAR(50) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_entity_relation primary key (sid)
);

CREATE TABLE nop_dyn_patch_file(
  file_id VARCHAR(32) NOT NULL ,
  app_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(32)  ,
  file_path VARCHAR(800) default 'pages'  NOT NULL ,
  file_name VARCHAR(200) NOT NULL ,
  file_type VARCHAR(50) NOT NULL ,
  file_length INT4 NOT NULL ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_patch_file primary key (file_id)
);

CREATE TABLE nop_dyn_module_dep(
  module_id VARCHAR(32) NOT NULL ,
  dep_module_id VARCHAR(32) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_module_dep primary key (module_id,dep_module_id)
);

CREATE TABLE nop_dyn_app_module(
  app_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(32) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_app_module primary key (app_id,module_id)
);

CREATE TABLE nop_dyn_sql(
  sql_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(32) NOT NULL ,
  name VARCHAR(100) default 'pages'  NOT NULL ,
  display_name VARCHAR(200)  ,
  sql_method VARCHAR(10)  ,
  row_type VARCHAR(100)  ,
  description VARCHAR(2000)  ,
  cache_name VARCHAR(100)  ,
  cache_key_expr VARCHAR(200)  ,
  batch_load_selection VARCHAR(200)  ,
  sql_kind VARCHAR(10)  ,
  query_space VARCHAR(100)  ,
  source TEXT  ,
  fetch_size INT4  ,
  timeout INT4  ,
  disable_logical_delete INT4 default 0   ,
  enable_filter INT4 default 0   ,
  refresh_behavior VARCHAR(10)  ,
  col_name_camel_case INT4 default 0   ,
  args VARCHAR(4000)  ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_sql primary key (sql_id)
);

CREATE TABLE nop_dyn_file(
  file_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(32) NOT NULL ,
  file_name VARCHAR(200) NOT NULL ,
  file_path VARCHAR(800) default 'pages'  NOT NULL ,
  file_type VARCHAR(50) NOT NULL ,
  file_length INT4 NOT NULL ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_file primary key (file_id)
);

CREATE TABLE nop_dyn_page(
  page_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(32) NOT NULL ,
  page_name VARCHAR(200) NOT NULL ,
  page_group VARCHAR(100) default 'pages'  NOT NULL ,
  page_schema_type VARCHAR(100) NOT NULL ,
  page_content TEXT NOT NULL ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_page primary key (page_id)
);

CREATE TABLE nop_dyn_entity_meta(
  entity_meta_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(32) NOT NULL ,
  entity_name VARCHAR(200) NOT NULL ,
  display_name VARCHAR(100) NOT NULL ,
  table_name VARCHAR(100)  ,
  query_space VARCHAR(100)  ,
  store_type INT4 NOT NULL ,
  tags_text VARCHAR(200)  ,
  is_external BOOLEAN NOT NULL ,
  status INT4 NOT NULL ,
  ext_config VARCHAR(1000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_entity_meta primary key (entity_meta_id)
);

CREATE TABLE nop_dyn_domain(
  domain_id VARCHAR(32) NOT NULL ,
  module_id VARCHAR(32) NOT NULL ,
  domain_name VARCHAR(50) NOT NULL ,
  display_name VARCHAR(100) NOT NULL ,
  std_domain_name VARCHAR(50)  ,
  std_sql_type VARCHAR(10) NOT NULL ,
  precision INT4  ,
  scale INT4 default 0   ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_domain primary key (domain_id)
);

CREATE TABLE nop_dyn_entity_relation_meta(
  rel_meta_id VARCHAR(32) NOT NULL ,
  entity_meta_id VARCHAR(32) NOT NULL ,
  ref_entity_meta_id VARCHAR(32) NOT NULL ,
  relation_name VARCHAR(100) NOT NULL ,
  relation_display_name VARCHAR(100) NOT NULL ,
  relation_type VARCHAR(10) NOT NULL ,
  middle_table_name VARCHAR(100)  ,
  middle_entity_name VARCHAR(100)  ,
  left_prop_name VARCHAR(100) NOT NULL ,
  right_prop_name VARCHAR(100) NOT NULL ,
  ref_set_key_prop VARCHAR(50)  ,
  ref_set_sort VARCHAR(100)  ,
  status INT4 NOT NULL ,
  tags_text VARCHAR(200)  ,
  ext_config VARCHAR(1000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_entity_relation_meta primary key (rel_meta_id)
);

CREATE TABLE nop_dyn_function_meta(
  func_meta_id VARCHAR(32) NOT NULL ,
  entity_meta_id VARCHAR(32) NOT NULL ,
  name VARCHAR(50) NOT NULL ,
  display_name VARCHAR(100) NOT NULL ,
  function_type VARCHAR(10) NOT NULL ,
  return_type VARCHAR(100)  ,
  return_gql_type VARCHAR(100)  ,
  status INT4 NOT NULL ,
  tags_text VARCHAR(200)  ,
  script_lang VARCHAR(50)  ,
  func_meta VARCHAR(4000)  ,
  source VARCHAR(4000) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_function_meta primary key (func_meta_id)
);

CREATE TABLE nop_dyn_prop_meta(
  prop_meta_id VARCHAR(32) NOT NULL ,
  entity_meta_id VARCHAR(32) NOT NULL ,
  is_mandatory BOOLEAN NOT NULL ,
  prop_name VARCHAR(50) NOT NULL ,
  display_name VARCHAR(100) NOT NULL ,
  std_sql_type VARCHAR(10) NOT NULL ,
  precision INT4  ,
  scale INT4 default 0   ,
  prop_id INT4 NOT NULL ,
  ui_show VARCHAR(10)  ,
  ui_control VARCHAR(100)  ,
  domain_id VARCHAR(32)  ,
  std_domain_name VARCHAR(50)  ,
  dict_name VARCHAR(100)  ,
  dyn_prop_mapping VARCHAR(100)  ,
  tags_text VARCHAR(200)  ,
  default_value VARCHAR(100)  ,
  ext_config VARCHAR(1000)  ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_dyn_prop_meta primary key (prop_meta_id)
);


      COMMENT ON TABLE nop_dyn_app IS '应用定义';
                
      COMMENT ON COLUMN nop_dyn_app.app_id IS '应用ID';
                    
      COMMENT ON COLUMN nop_dyn_app.app_name IS '应用名';
                    
      COMMENT ON COLUMN nop_dyn_app.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_app.app_version IS '应用版本';
                    
      COMMENT ON COLUMN nop_dyn_app.sort_order IS '排序';
                    
      COMMENT ON COLUMN nop_dyn_app.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_app.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_app.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_app.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_app.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_app.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_module IS '模块定义';
                
      COMMENT ON COLUMN nop_dyn_module.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module.module_name IS '模块名';
                    
      COMMENT ON COLUMN nop_dyn_module.module_version IS '模块版本';
                    
      COMMENT ON COLUMN nop_dyn_module.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_module.base_module_id IS '基础模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module.base_package_name IS 'Java包名';
                    
      COMMENT ON COLUMN nop_dyn_module.entity_package_name IS '实体包名';
                    
      COMMENT ON COLUMN nop_dyn_module.maven_group_id IS 'Maven组名';
                    
      COMMENT ON COLUMN nop_dyn_module.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_module.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_module.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_module.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_module.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_module.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_entity IS '动态实体';
                
      COMMENT ON COLUMN nop_dyn_entity.sid IS '主键';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_obj_type IS '对象类型';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_name IS '名称';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_sort_order IS '排序';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_flow_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_status IS '业务状态码';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_biz_state IS '业务状态';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_parent_id IS '父ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_owner_name IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_owner_id IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_dept_id IS '部门ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_string_fld1 IS '字符串字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_decimal_fld1 IS '浮点型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_int_fld1 IS '整数型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_long_fld1 IS '长整型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_date_fld1 IS '日期字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_timestamp_fld1 IS '时间戳字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_file_fld1 IS '文件字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_string_fld2 IS '字符串字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_decimal_fld2 IS '浮点型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_int_fld2 IS '整数型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_long_fld2 IS '长整型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_date_fld2 IS '日期字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_timestamp_fld2 IS '时间戳字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.nop_file_fld2 IS '文件字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_entity_relation IS '实体关联';
                
      COMMENT ON COLUMN nop_dyn_entity_relation.sid IS '主键';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.relation_name IS '关联名称';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.entity_name1 IS '实体名称1';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.entity_id1 IS '实体ID1';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.entity_name2 IS '实体名称2';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.entity_id2 IS '实体ID2';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_patch_file IS '补丁文件';
                
      COMMENT ON COLUMN nop_dyn_patch_file.file_id IS '文件ID';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.app_id IS 'App ID';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.file_path IS '文件路径';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.file_name IS '文件名称';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.file_type IS '文件类型';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.file_length IS '文件大小';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_patch_file.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_module_dep IS '模块依赖';
                
      COMMENT ON COLUMN nop_dyn_module_dep.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.dep_module_id IS '被依赖模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_app_module IS '应用模块映射';
                
      COMMENT ON COLUMN nop_dyn_app_module.app_id IS '应用ID';
                    
      COMMENT ON COLUMN nop_dyn_app_module.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_app_module.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_app_module.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_app_module.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_app_module.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_app_module.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_sql IS 'SQL定义';
                
      COMMENT ON COLUMN nop_dyn_sql.sql_id IS 'SQL ID';
                    
      COMMENT ON COLUMN nop_dyn_sql.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_sql.name IS 'SQL名称';
                    
      COMMENT ON COLUMN nop_dyn_sql.display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_dyn_sql.sql_method IS 'SQL方法';
                    
      COMMENT ON COLUMN nop_dyn_sql.row_type IS '行类型';
                    
      COMMENT ON COLUMN nop_dyn_sql.description IS '描述';
                    
      COMMENT ON COLUMN nop_dyn_sql.cache_name IS '缓存名称';
                    
      COMMENT ON COLUMN nop_dyn_sql.cache_key_expr IS '缓存键表达式';
                    
      COMMENT ON COLUMN nop_dyn_sql.batch_load_selection IS '批量加载选择集';
                    
      COMMENT ON COLUMN nop_dyn_sql.sql_kind IS '类型';
                    
      COMMENT ON COLUMN nop_dyn_sql.query_space IS '查询空间';
                    
      COMMENT ON COLUMN nop_dyn_sql.source IS 'SQL文本';
                    
      COMMENT ON COLUMN nop_dyn_sql.fetch_size IS '读取块大小';
                    
      COMMENT ON COLUMN nop_dyn_sql.timeout IS '超时时间';
                    
      COMMENT ON COLUMN nop_dyn_sql.disable_logical_delete IS '禁用逻辑删除';
                    
      COMMENT ON COLUMN nop_dyn_sql.enable_filter IS '启用数据权限';
                    
      COMMENT ON COLUMN nop_dyn_sql.refresh_behavior IS '实体刷新规则';
                    
      COMMENT ON COLUMN nop_dyn_sql.col_name_camel_case IS '列名需要转换为驼峰';
                    
      COMMENT ON COLUMN nop_dyn_sql.args IS '参数列表';
                    
      COMMENT ON COLUMN nop_dyn_sql.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_sql.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_sql.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_sql.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_sql.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_sql.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_sql.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_file IS '模块文件';
                
      COMMENT ON COLUMN nop_dyn_file.file_id IS '文件ID';
                    
      COMMENT ON COLUMN nop_dyn_file.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_file.file_name IS '文件名称';
                    
      COMMENT ON COLUMN nop_dyn_file.file_path IS '文件路径';
                    
      COMMENT ON COLUMN nop_dyn_file.file_type IS '文件类型';
                    
      COMMENT ON COLUMN nop_dyn_file.file_length IS '文件大小';
                    
      COMMENT ON COLUMN nop_dyn_file.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_file.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_file.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_file.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_file.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_file.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_file.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_page IS '页面定义';
                
      COMMENT ON COLUMN nop_dyn_page.page_id IS '页面ID';
                    
      COMMENT ON COLUMN nop_dyn_page.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_page.page_name IS '页面名称';
                    
      COMMENT ON COLUMN nop_dyn_page.page_group IS '页面分组';
                    
      COMMENT ON COLUMN nop_dyn_page.page_schema_type IS '页面类型';
                    
      COMMENT ON COLUMN nop_dyn_page.page_content IS '页面内容';
                    
      COMMENT ON COLUMN nop_dyn_page.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_page.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_page.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_page.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_page.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_page.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_page.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_entity_meta IS '实体元数据';
                
      COMMENT ON COLUMN nop_dyn_entity_meta.entity_meta_id IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.entity_name IS '实体名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.table_name IS '表名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.query_space IS '查询空间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.store_type IS '存储类型';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.tags_text IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.is_external IS '是否外部实体';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_domain IS '数据域';
                
      COMMENT ON COLUMN nop_dyn_domain.domain_id IS '数据域ID';
                    
      COMMENT ON COLUMN nop_dyn_domain.module_id IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_domain.domain_name IS '数据域名称';
                    
      COMMENT ON COLUMN nop_dyn_domain.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_domain.std_domain_name IS '标准域';
                    
      COMMENT ON COLUMN nop_dyn_domain.std_sql_type IS '标准SQL数据类型';
                    
      COMMENT ON COLUMN nop_dyn_domain.precision IS '长度';
                    
      COMMENT ON COLUMN nop_dyn_domain.scale IS '小数位数';
                    
      COMMENT ON COLUMN nop_dyn_domain.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_domain.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_domain.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_domain.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_domain.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_domain.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_entity_relation_meta IS '实体关联属性定义';
                
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.rel_meta_id IS '关联定义ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.entity_meta_id IS '实体元数据';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.ref_entity_meta_id IS '关联实体';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.relation_name IS '关联名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.relation_display_name IS '关联显示名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.relation_type IS '关联类型';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.middle_table_name IS '中间表表名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.middle_entity_name IS '中间表实体名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.left_prop_name IS '左属性名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.right_prop_name IS '右属性名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.ref_set_key_prop IS '集合内唯一标识';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.ref_set_sort IS '集合排序条件';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.tags_text IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_function_meta IS '实体函数定义';
                
      COMMENT ON COLUMN nop_dyn_function_meta.func_meta_id IS '函数定义ID';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.entity_meta_id IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.name IS '函数名';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.function_type IS '函数类型';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.return_type IS '返回类型';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.return_gql_type IS 'GraphQL返回类型';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.tags_text IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.script_lang IS '脚本语言';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.func_meta IS '函数元数据';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.source IS '源码';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.remark IS '备注';
                    
      COMMENT ON TABLE nop_dyn_prop_meta IS '属性元数据';
                
      COMMENT ON COLUMN nop_dyn_prop_meta.prop_meta_id IS '属性定义ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.entity_meta_id IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.is_mandatory IS '是否非空';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.prop_name IS '属性名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.std_sql_type IS '标准SQL数据类型';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.precision IS '长度';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.scale IS '小数位数';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.prop_id IS '属性编号';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.ui_show IS '显示控制';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.ui_control IS '显示控件';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.domain_id IS '数据域ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.std_domain_name IS '标准域';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.dict_name IS '数据字典';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.dyn_prop_mapping IS '动态字段映射';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.tags_text IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.default_value IS '缺省值';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.status IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.remark IS '备注';
                    
