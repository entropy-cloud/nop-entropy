
CREATE TABLE nop_report_definition(
  RPT_ID VARCHAR(200) NOT NULL ,
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
  DS_ID VARCHAR(200) NOT NULL ,
  DS_NAME VARCHAR(200) NOT NULL ,
  DESCRIPTION VARCHAR(1000)  ,
  DS_TYPE VARCHAR(100) NOT NULL ,
  DS_CONFIG VARCHAR(4000) NOT NULL ,
  DS_TEXT TEXT NOT NULL ,
  DS_META TEXT NOT NULL ,
  DS_VIEW TEXT  ,
  STATUS INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_dataset primary key (DS_ID)
);

CREATE TABLE nop_report_dataset_auth(
  DS_ID VARCHAR(200) NOT NULL ,
  ROLE_ID VARCHAR(200) NOT NULL ,
  PERMISSIONS VARCHAR(100) NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_report_dataset_auth primary key (DS_ID)
);

CREATE TABLE nop_report_result_file(
  SID VARCHAR(100) NOT NULL ,
  FILE_NAME VARCHAR(200) NOT NULL ,
  FILE_TYPE VARCHAR(10) NOT NULL ,
  FILE_PATH VARCHAR(100) NOT NULL ,
  DS_PARAMS VARCHAR(4000) NOT NULL ,
  DS_ID VARCHAR(200)  ,
  BIZ_DATE DATE  ,
  RPT_ID VARCHAR(200)  ,
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


      COMMENT ON TABLE nop_report_definition IS '报表定义';
                
      COMMENT ON COLUMN nop_report_definition.RPT_ID IS '主键';
                    
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
                
      COMMENT ON COLUMN nop_report_dataset.DS_ID IS '主键';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_NAME IS '数据集名称';
                    
      COMMENT ON COLUMN nop_report_dataset.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_TYPE IS '数据集类型';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_CONFIG IS '数据集配置';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_TEXT IS '数据集文本';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_META IS '数据集元数据';
                    
      COMMENT ON COLUMN nop_report_dataset.DS_VIEW IS '数据集显示配置';
                    
      COMMENT ON COLUMN nop_report_dataset.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_report_dataset.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_dataset.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_dataset.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_dataset.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_dataset.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_dataset.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_dataset_auth IS '数据集权限';
                
      COMMENT ON COLUMN nop_report_dataset_auth.DS_ID IS '主键';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.ROLE_ID IS '角色ID';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.PERMISSIONS IS '许可权限';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_dataset_auth.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_report_result_file IS '报表结果文件';
                
      COMMENT ON COLUMN nop_report_result_file.SID IS '主键';
                    
      COMMENT ON COLUMN nop_report_result_file.FILE_NAME IS '文件名称';
                    
      COMMENT ON COLUMN nop_report_result_file.FILE_TYPE IS '文件类型';
                    
      COMMENT ON COLUMN nop_report_result_file.FILE_PATH IS '文件路径';
                    
      COMMENT ON COLUMN nop_report_result_file.DS_PARAMS IS '数据集参数';
                    
      COMMENT ON COLUMN nop_report_result_file.DS_ID IS '数据集ID';
                    
      COMMENT ON COLUMN nop_report_result_file.BIZ_DATE IS '业务日期';
                    
      COMMENT ON COLUMN nop_report_result_file.RPT_ID IS '报表ID';
                    
      COMMENT ON COLUMN nop_report_result_file.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_report_result_file.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_report_result_file.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_report_result_file.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_report_result_file.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_report_result_file.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_report_result_file.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_report_result_file.REMARK IS '备注';
                    
