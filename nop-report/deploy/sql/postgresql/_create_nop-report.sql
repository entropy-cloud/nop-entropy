
CREATE TABLE nop_report_definition(
  rpt_id VARCHAR(32) NOT NULL ,
  rpt_no VARCHAR(200) NOT NULL ,
  rpt_name VARCHAR(200) NOT NULL ,
  description VARCHAR(1000)  ,
  rpt_text TEXT NOT NULL ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_report_definition primary key (rpt_id)
);

CREATE TABLE nop_report_dataset(
  sid VARCHAR(200) NOT NULL ,
  ds_name VARCHAR(200) NOT NULL ,
  is_single_row BOOLEAN NOT NULL ,
  description VARCHAR(1000)  ,
  ds_type VARCHAR(100) NOT NULL ,
  datasource_id VARCHAR(32)  ,
  ds_text TEXT NOT NULL ,
  ds_meta TEXT NOT NULL ,
  ds_config TEXT  ,
  filter_rule VARCHAR(4000)  ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_report_dataset primary key (sid)
);

CREATE TABLE nop_report_datasource(
  sid VARCHAR(32) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  datasource_type VARCHAR(20) NOT NULL ,
  datasource_config VARCHAR(4000) NOT NULL ,
  status INT4  ,
  remark VARCHAR(500)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_report_datasource primary key (sid)
);

CREATE TABLE nop_report_definition_auth(
  sid VARCHAR(32) NOT NULL ,
  rpt_id VARCHAR(32) NOT NULL ,
  role_id VARCHAR(200) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_report_definition_auth primary key (sid)
);

CREATE TABLE nop_report_dataset_ref(
  rpt_id VARCHAR(32) NOT NULL ,
  ds_id VARCHAR(32) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_report_dataset_ref primary key (rpt_id,ds_id)
);

CREATE TABLE nop_report_result_file(
  sid VARCHAR(50) NOT NULL ,
  file_name VARCHAR(200) NOT NULL ,
  file_type VARCHAR(10) NOT NULL ,
  file_path VARCHAR(100) NOT NULL ,
  file_length INT8 NOT NULL ,
  biz_date DATE  ,
  rpt_id VARCHAR(200) NOT NULL ,
  rpt_params VARCHAR(4000) NOT NULL ,
  status INT4 NOT NULL ,
  description VARCHAR(1000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_report_result_file primary key (sid)
);

CREATE TABLE nop_report_sub_dataset(
  sid VARCHAR(32) NOT NULL ,
  ds_id VARCHAR(32) NOT NULL ,
  sub_ds_id VARCHAR(32) NOT NULL ,
  join_fields VARCHAR(500) NOT NULL ,
  ds_params VARCHAR(500)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_report_sub_dataset primary key (sid)
);

CREATE TABLE nop_report_datasource_auth(
  sid VARCHAR(32) NOT NULL ,
  datasource_id VARCHAR(32) NOT NULL ,
  role_id VARCHAR(200) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_report_datasource_auth primary key (sid)
);


      COMMENT ON TABLE nop_report_definition IS '报表定义';
                
      COMMENT ON COLUMN nop_report_definition.rpt_id IS '主键';
                    
      COMMENT ON COLUMN nop_report_definition.rpt_no IS '报表编号';
                    
      COMMENT ON COLUMN nop_report_definition.rpt_name IS '报表名称';
                    
      COMMENT ON COLUMN nop_report_definition.description IS '描述';
                    
      COMMENT ON COLUMN nop_report_definition.rpt_text IS '报表文件';
                    
      COMMENT ON COLUMN nop_report_definition.status IS '状态';
                    
      COMMENT ON COLUMN nop_report_definition.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_definition.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_definition.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_definition.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_definition.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_definition.remark IS '备注';
                    
      COMMENT ON TABLE nop_report_dataset IS '数据集定义';
                
      COMMENT ON COLUMN nop_report_dataset.sid IS '主键';
                    
      COMMENT ON COLUMN nop_report_dataset.ds_name IS '数据集名称';
                    
      COMMENT ON COLUMN nop_report_dataset.is_single_row IS '是否单行';
                    
      COMMENT ON COLUMN nop_report_dataset.description IS '描述';
                    
      COMMENT ON COLUMN nop_report_dataset.ds_type IS '数据集类型';
                    
      COMMENT ON COLUMN nop_report_dataset.datasource_id IS '数据源ID';
                    
      COMMENT ON COLUMN nop_report_dataset.ds_text IS '数据集文本';
                    
      COMMENT ON COLUMN nop_report_dataset.ds_meta IS '数据集元数据';
                    
      COMMENT ON COLUMN nop_report_dataset.ds_config IS '数据集配置';
                    
      COMMENT ON COLUMN nop_report_dataset.filter_rule IS '过滤规则';
                    
      COMMENT ON COLUMN nop_report_dataset.status IS '状态';
                    
      COMMENT ON COLUMN nop_report_dataset.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_dataset.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_dataset.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_dataset.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_dataset.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_dataset.remark IS '备注';
                    
      COMMENT ON TABLE nop_report_datasource IS '数据源定义';
                
      COMMENT ON COLUMN nop_report_datasource.sid IS '主键ID';
                    
      COMMENT ON COLUMN nop_report_datasource.name IS '数据源名称';
                    
      COMMENT ON COLUMN nop_report_datasource.datasource_type IS '数据源类型';
                    
      COMMENT ON COLUMN nop_report_datasource.datasource_config IS '数据源配置';
                    
      COMMENT ON COLUMN nop_report_datasource.status IS '状态';
                    
      COMMENT ON COLUMN nop_report_datasource.remark IS '备注说明';
                    
      COMMENT ON COLUMN nop_report_datasource.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_datasource.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_datasource.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_datasource.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_datasource.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_report_definition_auth IS 'Report访问权限';
                
      COMMENT ON COLUMN nop_report_definition_auth.sid IS '主键';
                    
      COMMENT ON COLUMN nop_report_definition_auth.rpt_id IS '报表ID';
                    
      COMMENT ON COLUMN nop_report_definition_auth.role_id IS '角色ID';
                    
      COMMENT ON COLUMN nop_report_definition_auth.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_definition_auth.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_definition_auth.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_definition_auth.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_definition_auth.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_definition_auth.remark IS '备注';
                    
      COMMENT ON TABLE nop_report_dataset_ref IS '报表引用数据源';
                
      COMMENT ON COLUMN nop_report_dataset_ref.rpt_id IS '报表主键';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.ds_id IS '数据集ID';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.remark IS '备注';
                    
      COMMENT ON TABLE nop_report_result_file IS '报表结果文件';
                
      COMMENT ON COLUMN nop_report_result_file.sid IS '主键';
                    
      COMMENT ON COLUMN nop_report_result_file.file_name IS '文件名称';
                    
      COMMENT ON COLUMN nop_report_result_file.file_type IS '文件类型';
                    
      COMMENT ON COLUMN nop_report_result_file.file_path IS '文件路径';
                    
      COMMENT ON COLUMN nop_report_result_file.file_length IS '文件长度';
                    
      COMMENT ON COLUMN nop_report_result_file.biz_date IS '业务日期';
                    
      COMMENT ON COLUMN nop_report_result_file.rpt_id IS '报表ID';
                    
      COMMENT ON COLUMN nop_report_result_file.rpt_params IS '报表参数';
                    
      COMMENT ON COLUMN nop_report_result_file.status IS '状态';
                    
      COMMENT ON COLUMN nop_report_result_file.description IS '描述';
                    
      COMMENT ON COLUMN nop_report_result_file.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_result_file.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_result_file.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_result_file.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_result_file.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_result_file.remark IS '备注';
                    
      COMMENT ON TABLE nop_report_sub_dataset IS '子数据源';
                
      COMMENT ON COLUMN nop_report_sub_dataset.sid IS '主键';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.ds_id IS '数据集ID';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.sub_ds_id IS '子数据集ID';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.join_fields IS '关联字段';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.ds_params IS '子数据集参数';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.remark IS '备注';
                    
      COMMENT ON TABLE nop_report_datasource_auth IS '数据源访问权限';
                
      COMMENT ON COLUMN nop_report_datasource_auth.sid IS '主键';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.datasource_id IS '数据源ID';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.role_id IS '角色ID';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.remark IS '备注';
                    
