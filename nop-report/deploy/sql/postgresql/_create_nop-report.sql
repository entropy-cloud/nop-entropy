
CREATE TABLE nop_report_definition(
  RPT_ID VARCHAR(32) NOT NULL ,
  RPT_NO VARCHAR(200) NOT NULL ,
  RPT_NAME VARCHAR(200) NOT NULL ,
  DESCRIPTION VARCHAR(1000)  ,
  RPT_TEXT TEXT NOT NULL ,
  STATUS INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_definition primary key (RPT_ID)
);

CREATE TABLE nop_report_dataset(
  SID VARCHAR(200) NOT NULL ,
  DS_NAME VARCHAR(200) NOT NULL ,
  IS_SINGLE_ROW BOOLEAN NOT NULL ,
  DESCRIPTION VARCHAR(1000)  ,
  DS_TYPE VARCHAR(100) NOT NULL ,
  DATASOURCE_ID VARCHAR(32)  ,
  DS_TEXT TEXT NOT NULL ,
  DS_META TEXT NOT NULL ,
  DS_CONFIG TEXT  ,
  FILTER_RULE VARCHAR(4000)  ,
  STATUS INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_dataset primary key (SID)
);

CREATE TABLE nop_report_datasource(
  SID VARCHAR(32) NOT NULL ,
  NAME VARCHAR(100) NOT NULL ,
  DATASOURCE_TYPE VARCHAR(20) NOT NULL ,
  DATASOURCE_CONFIG VARCHAR(4000) NOT NULL ,
  STATUS INT4  ,
  REMARK VARCHAR(500)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_report_datasource primary key (SID)
);

CREATE TABLE nop_report_definition_auth(
  SID VARCHAR(32) NOT NULL ,
  RPT_ID VARCHAR(32) NOT NULL ,
  ROLE_ID VARCHAR(200) NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_definition_auth primary key (SID)
);

CREATE TABLE nop_report_dataset_ref(
  RPT_ID VARCHAR(32) NOT NULL ,
  DS_ID VARCHAR(32) NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_dataset_ref primary key (RPT_ID,DS_ID)
);

CREATE TABLE nop_report_result_file(
  SID VARCHAR(50) NOT NULL ,
  FILE_NAME VARCHAR(200) NOT NULL ,
  FILE_TYPE VARCHAR(10) NOT NULL ,
  FILE_PATH VARCHAR(100) NOT NULL ,
  FILE_LENGTH INT8 NOT NULL ,
  BIZ_DATE DATE  ,
  RPT_ID VARCHAR(200) NOT NULL ,
  RPT_PARAMS VARCHAR(4000) NOT NULL ,
  STATUS INT4 NOT NULL ,
  DESCRIPTION VARCHAR(1000)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_result_file primary key (SID)
);

CREATE TABLE nop_report_sub_dataset(
  SID VARCHAR(32) NOT NULL ,
  DS_ID VARCHAR(32) NOT NULL ,
  SUB_DS_ID VARCHAR(32) NOT NULL ,
  JOIN_FIELDS VARCHAR(500) NOT NULL ,
  DS_PARAMS VARCHAR(500)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_sub_dataset primary key (SID)
);

CREATE TABLE nop_report_datasource_auth(
  SID VARCHAR(32) NOT NULL ,
  DATASOURCE_ID VARCHAR(32) NOT NULL ,
  ROLE_ID VARCHAR(200) NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_datasource_auth primary key (SID)
);


      COMMENT ON TABLE nop_report_definition IS '报表定义';
                
      COMMENT ON COLUMN nop_report_definition.RPT_ID IS '主键';
                    
      COMMENT ON COLUMN nop_report_definition.RPT_NO IS '报表编号';
                    
      COMMENT ON COLUMN nop_report_definition.RPT_NAME IS '报表名称';
                    
      COMMENT ON COLUMN nop_report_definition.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_report_definition.RPT_TEXT IS '报表文件';
                    
      COMMENT ON COLUMN nop_report_definition.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_report_definition.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_definition.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_definition.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_definition.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_definition.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_definition.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_dataset IS '数据集定义';
                
      COMMENT ON COLUMN nop_report_dataset.SID IS '主键';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_NAME IS '数据集名称';
                    
      COMMENT ON COLUMN nop_report_dataset.IS_SINGLE_ROW IS '是否单行';
                    
      COMMENT ON COLUMN nop_report_dataset.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_TYPE IS '数据集类型';
                    
      COMMENT ON COLUMN nop_report_dataset.DATASOURCE_ID IS '数据源ID';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_TEXT IS '数据集文本';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_META IS '数据集元数据';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_CONFIG IS '数据集配置';
                    
      COMMENT ON COLUMN nop_report_dataset.FILTER_RULE IS '过滤规则';
                    
      COMMENT ON COLUMN nop_report_dataset.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_report_dataset.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_dataset.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_dataset.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_dataset.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_dataset.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_dataset.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_datasource IS '数据源定义';
                
      COMMENT ON COLUMN nop_report_datasource.SID IS '主键ID';
                    
      COMMENT ON COLUMN nop_report_datasource.NAME IS '数据源名称';
                    
      COMMENT ON COLUMN nop_report_datasource.DATASOURCE_TYPE IS '数据源类型';
                    
      COMMENT ON COLUMN nop_report_datasource.DATASOURCE_CONFIG IS '数据源配置';
                    
      COMMENT ON COLUMN nop_report_datasource.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_report_datasource.REMARK IS '备注说明';
                    
      COMMENT ON COLUMN nop_report_datasource.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_datasource.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_datasource.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_datasource.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_datasource.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_report_definition_auth IS 'Report访问权限';
                
      COMMENT ON COLUMN nop_report_definition_auth.SID IS '主键';
                    
      COMMENT ON COLUMN nop_report_definition_auth.RPT_ID IS '报表ID';
                    
      COMMENT ON COLUMN nop_report_definition_auth.ROLE_ID IS '角色ID';
                    
      COMMENT ON COLUMN nop_report_definition_auth.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_definition_auth.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_definition_auth.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_definition_auth.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_definition_auth.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_definition_auth.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_dataset_ref IS '报表引用数据源';
                
      COMMENT ON COLUMN nop_report_dataset_ref.RPT_ID IS '报表主键';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.DS_ID IS '数据集ID';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_dataset_ref.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_result_file IS '报表结果文件';
                
      COMMENT ON COLUMN nop_report_result_file.SID IS '主键';
                    
      COMMENT ON COLUMN nop_report_result_file.FILE_NAME IS '文件名称';
                    
      COMMENT ON COLUMN nop_report_result_file.FILE_TYPE IS '文件类型';
                    
      COMMENT ON COLUMN nop_report_result_file.FILE_PATH IS '文件路径';
                    
      COMMENT ON COLUMN nop_report_result_file.FILE_LENGTH IS '文件长度';
                    
      COMMENT ON COLUMN nop_report_result_file.BIZ_DATE IS '业务日期';
                    
      COMMENT ON COLUMN nop_report_result_file.RPT_ID IS '报表ID';
                    
      COMMENT ON COLUMN nop_report_result_file.RPT_PARAMS IS '报表参数';
                    
      COMMENT ON COLUMN nop_report_result_file.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_report_result_file.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_report_result_file.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_result_file.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_result_file.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_result_file.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_result_file.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_result_file.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_sub_dataset IS '子数据源';
                
      COMMENT ON COLUMN nop_report_sub_dataset.SID IS '主键';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.DS_ID IS '数据集ID';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.SUB_DS_ID IS '子数据集ID';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.JOIN_FIELDS IS '关联字段';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.DS_PARAMS IS '子数据集参数';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_sub_dataset.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_datasource_auth IS '数据源访问权限';
                
      COMMENT ON COLUMN nop_report_datasource_auth.SID IS '主键';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.DATASOURCE_ID IS '数据源ID';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.ROLE_ID IS '角色ID';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_datasource_auth.REMARK IS '备注';
                    
