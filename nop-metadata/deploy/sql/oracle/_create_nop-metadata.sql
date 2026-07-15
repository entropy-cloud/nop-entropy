
CREATE TABLE nop_meta_module(
  META_MODULE_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(100) NOT NULL ,
  MODULE_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  VERSION NUMBER(20) NOT NULL ,
  BASE_MODULE_ID VARCHAR2(32)  ,
  STATUS CLOB NOT NULL ,
  MAVEN_GROUP_ID VARCHAR2(100)  ,
  MAVEN_ARTIFACT_ID VARCHAR2(100)  ,
  MAVEN_VERSION VARCHAR2(50)  ,
  GIT_REPO_PATH VARCHAR2(500)  ,
  GIT_BRANCH VARCHAR2(100)  ,
  GIT_COMMIT_ID VARCHAR2(64)  ,
  IMPORTED_AT TIMESTAMP  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_module primary key (META_MODULE_ID)
);

CREATE TABLE nop_meta_data_source(
  DATA_SOURCE_ID VARCHAR2(32) NOT NULL ,
  QUERY_SPACE VARCHAR2(100) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  DATASOURCE_TYPE VARCHAR2(30) NOT NULL ,
  CONNECTION_CONFIG VARCHAR2(4000)  ,
  STATUS CLOB NOT NULL ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_data_source primary key (DATA_SOURCE_ID)
);

CREATE TABLE nop_meta_semantic_type(
  SEMANTIC_TYPE_ID VARCHAR2(32) NOT NULL ,
  TYPE_NAME VARCHAR2(50) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  DESCRIPTION VARCHAR2(1000)  ,
  APPLICABLE_TO VARCHAR2(1000)  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_semantic_type primary key (SEMANTIC_TYPE_ID)
);

CREATE TABLE nop_meta_lineage_edge(
  LINEAGE_EDGE_ID VARCHAR2(32) NOT NULL ,
  SOURCE_TABLE_ID VARCHAR2(32) NOT NULL ,
  TARGET_TABLE_ID VARCHAR2(32) NOT NULL ,
  SOURCE_COLUMN VARCHAR2(100)  ,
  TARGET_COLUMN VARCHAR2(100)  ,
  TRANSFORM_TYPE VARCHAR2(20)  ,
  TRANSFORM_EXPR VARCHAR2(1000)  ,
  LINEAGE_SOURCE VARCHAR2(30)  ,
  PIPELINE_ID VARCHAR2(32)  ,
  CONFIDENCE BINARY_DOUBLE  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_lineage_edge primary key (LINEAGE_EDGE_ID)
);

CREATE TABLE nop_meta_quality_rule(
  QUALITY_RULE_ID VARCHAR2(32) NOT NULL ,
  RULE_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  RULE_TYPE VARCHAR2(30) NOT NULL ,
  ENTITY_TYPE VARCHAR2(20) NOT NULL ,
  ENTITY_ID VARCHAR2(32) NOT NULL ,
  SEVERITY CLOB NOT NULL ,
  SQL_EXPRESSION CLOB  ,
  THRESHOLD BINARY_DOUBLE  ,
  PARAMS VARCHAR2(4000)  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_quality_rule primary key (QUALITY_RULE_ID)
);

CREATE TABLE nop_meta_orm_model(
  ORM_MODEL_ID VARCHAR2(32) NOT NULL ,
  META_MODULE_ID VARCHAR2(32) NOT NULL ,
  MODEL_NAME VARCHAR2(100) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  SOURCE_CONTENT CLOB  ,
  IMPORTED_AT TIMESTAMP  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_orm_model primary key (ORM_MODEL_ID)
);

CREATE TABLE nop_meta_table(
  META_TABLE_ID VARCHAR2(32) NOT NULL ,
  META_MODULE_ID VARCHAR2(32) NOT NULL ,
  TABLE_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  TABLE_TYPE VARCHAR2(20) NOT NULL ,
  QUERY_SPACE VARCHAR2(100)  ,
  SOURCE_SQL CLOB  ,
  BASE_ENTITY_ID VARCHAR2(32)  ,
  DESCRIPTION VARCHAR2(1000)  ,
  BUILD_SQL CLOB  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_table primary key (META_TABLE_ID)
);

CREATE TABLE nop_meta_pipeline(
  PIPELINE_ID VARCHAR2(32) NOT NULL ,
  META_MODULE_ID VARCHAR2(32) NOT NULL ,
  PIPELINE_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  PIPELINE_TYPE VARCHAR2(20) NOT NULL ,
  SOURCE_SQL CLOB  ,
  SCHEDULE VARCHAR2(200)  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_pipeline primary key (PIPELINE_ID)
);

CREATE TABLE nop_meta_quality_result(
  QUALITY_RESULT_ID VARCHAR2(32) NOT NULL ,
  QUALITY_RULE_ID VARCHAR2(32) NOT NULL ,
  EXECUTE_TIME TIMESTAMP NOT NULL ,
  STATUS CLOB NOT NULL ,
  ACTUAL_VALUE BINARY_DOUBLE  ,
  EXPECTED_VALUE BINARY_DOUBLE  ,
  MESSAGE VARCHAR2(1000)  ,
  DETAILS VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_quality_result primary key (QUALITY_RESULT_ID)
);

CREATE TABLE nop_meta_entity(
  META_ENTITY_ID VARCHAR2(32) NOT NULL ,
  ORM_MODEL_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  ENTITY_NAME VARCHAR2(200) NOT NULL ,
  TABLE_NAME VARCHAR2(100)  ,
  DISPLAY_NAME VARCHAR2(200)  ,
  CLASS_NAME VARCHAR2(300)  ,
  TAG_SET VARCHAR2(500)  ,
  QUERY_SPACE VARCHAR2(100)  ,
  PERSIST_DRIVER VARCHAR2(50)  ,
  USE_TENANT SMALLINT default 0   ,
  USE_REVISION SMALLINT default 0   ,
  USE_LOGICAL_DELETE SMALLINT default 0   ,
  NOT_GEN_CODE SMALLINT default 0   ,
  CREATER_PROP VARCHAR2(50)  ,
  CREATE_TIME_PROP VARCHAR2(50)  ,
  UPDATER_PROP VARCHAR2(50)  ,
  UPDATE_TIME_PROP VARCHAR2(50)  ,
  VERSION_PROP VARCHAR2(50)  ,
  DEL_FLAG_PROP VARCHAR2(50)  ,
  DEL_VERSION_PROP VARCHAR2(50)  ,
  DB_CATALOG VARCHAR2(100)  ,
  DB_SCHEMA VARCHAR2(100)  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_entity primary key (META_ENTITY_ID)
);

CREATE TABLE nop_meta_domain(
  META_DOMAIN_ID VARCHAR2(32) NOT NULL ,
  ORM_MODEL_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  DOMAIN_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  DESCRIPTION VARCHAR2(1000)  ,
  STD_DOMAIN VARCHAR2(100)  ,
  STD_DATA_TYPE VARCHAR2(30)  ,
  STD_SQL_TYPE VARCHAR2(30)  ,
  PRECISION INTEGER  ,
  SCALE INTEGER  ,
  VALIDATION_PATTERN VARCHAR2(500)  ,
  DEFAULT_VALUE VARCHAR2(500)  ,
  IS_GLOBAL SMALLINT default 0   ,
  SOURCE_MODULE_ID VARCHAR2(32)  ,
  TAG_SET VARCHAR2(500)  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_domain primary key (META_DOMAIN_ID)
);

CREATE TABLE nop_meta_dict(
  META_DICT_ID VARCHAR2(32) NOT NULL ,
  ORM_MODEL_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  DICT_NAME VARCHAR2(100) NOT NULL ,
  LABEL VARCHAR2(200)  ,
  VALUE_TYPE VARCHAR2(20)  ,
  LOCALE VARCHAR2(20)  ,
  IS_STATIC SMALLINT default 0   ,
  NORMALIZED SMALLINT default 0   ,
  DEPRECATED SMALLINT default 0   ,
  INTERNAL SMALLINT default 0   ,
  TAG_SET VARCHAR2(500)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_dict primary key (META_DICT_ID)
);

CREATE TABLE nop_meta_table_dimension(
  DIMENSION_ID VARCHAR2(32) NOT NULL ,
  META_TABLE_ID VARCHAR2(32) NOT NULL ,
  DIMENSION_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  ENTITY_FIELD_ID VARCHAR2(32)  ,
  DIMENSION_TYPE VARCHAR2(30)  ,
  GRANULARITY VARCHAR2(20)  ,
  FORMAT VARCHAR2(100)  ,
  SORT_ORDER INTEGER  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_table_dimension primary key (DIMENSION_ID)
);

CREATE TABLE nop_meta_table_measure(
  MEASURE_ID VARCHAR2(32) NOT NULL ,
  META_TABLE_ID VARCHAR2(32) NOT NULL ,
  MEASURE_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  ENTITY_FIELD_ID VARCHAR2(32)  ,
  AGG_FUNC VARCHAR2(30)  ,
  EXPRESSION VARCHAR2(1000)  ,
  FORMAT VARCHAR2(100)  ,
  CURRENCY_UNIT VARCHAR2(20)  ,
  DESCRIPTION VARCHAR2(1000)  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_table_measure primary key (MEASURE_ID)
);

CREATE TABLE nop_meta_table_filter(
  FILTER_ID VARCHAR2(32) NOT NULL ,
  META_TABLE_ID VARCHAR2(32) NOT NULL ,
  FILTER_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  DEFINITION VARCHAR2(4000) NOT NULL ,
  DESCRIPTION VARCHAR2(1000)  ,
  IS_DEFAULT SMALLINT default 0   ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_table_filter primary key (FILTER_ID)
);

CREATE TABLE nop_meta_table_join(
  JOIN_ID VARCHAR2(32) NOT NULL ,
  META_TABLE_ID VARCHAR2(32) NOT NULL ,
  JOIN_TYPE VARCHAR2(20) NOT NULL ,
  LEFT_ENTITY_ID VARCHAR2(32)  ,
  RIGHT_ENTITY_ID VARCHAR2(32)  ,
  LEFT_FIELD VARCHAR2(100)  ,
  RIGHT_FIELD VARCHAR2(100)  ,
  ALIAS VARCHAR2(100)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_table_join primary key (JOIN_ID)
);

CREATE TABLE nop_meta_entity_field(
  ENTITY_FIELD_ID VARCHAR2(32) NOT NULL ,
  META_ENTITY_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  FIELD_NAME VARCHAR2(100) NOT NULL ,
  COLUMN_CODE VARCHAR2(100)  ,
  PROP_ID INTEGER  ,
  STD_DATA_TYPE VARCHAR2(30)  ,
  STD_SQL_TYPE VARCHAR2(30)  ,
  PRECISION INTEGER  ,
  SCALE INTEGER  ,
  MANDATORY SMALLINT default 0   ,
  PRIMARY SMALLINT default 0   ,
  LAZY SMALLINT default 0   ,
  INSERTABLE SMALLINT default 0   ,
  UPDATABLE SMALLINT default 0   ,
  DOMAIN VARCHAR2(100)  ,
  STD_DOMAIN VARCHAR2(100)  ,
  FIXED_VALUE VARCHAR2(500)  ,
  DEFAULT_VALUE VARCHAR2(500)  ,
  SEMANTIC_TYPE VARCHAR2(50)  ,
  TAG_SET VARCHAR2(500)  ,
  DISPLAY_NAME VARCHAR2(200)  ,
  "COMMENT" VARCHAR2(1000)  ,
  NATIVE_SQL_TYPE VARCHAR2(100)  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_entity_field primary key (ENTITY_FIELD_ID)
);

CREATE TABLE nop_meta_entity_relation(
  RELATION_ID VARCHAR2(32) NOT NULL ,
  META_ENTITY_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  RELATION_NAME VARCHAR2(100) NOT NULL ,
  RELATION_TYPE VARCHAR2(20) NOT NULL ,
  REF_ENTITY_NAME VARCHAR2(200)  ,
  REF_PROP_NAME VARCHAR2(100)  ,
  CASCADE_DELETE SMALLINT default 0   ,
  AUTO_CASCADE_DELETE SMALLINT default 0   ,
  QUERYABLE SMALLINT default 0   ,
  EMBEDDED SMALLINT default 0   ,
  NOT_GEN_CODE SMALLINT default 0   ,
  TAG_SET VARCHAR2(500)  ,
  JOIN_CONDITIONS VARCHAR2(4000)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_entity_relation primary key (RELATION_ID)
);

CREATE TABLE nop_meta_entity_unique_key(
  UNIQUE_KEY_ID VARCHAR2(32) NOT NULL ,
  META_ENTITY_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  UK_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  COLUMNS VARCHAR2(1000) NOT NULL ,
  CONSTRAINT VARCHAR2(100)  ,
  TAG_SET VARCHAR2(500)  ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_entity_unique_key primary key (UNIQUE_KEY_ID)
);

CREATE TABLE nop_meta_entity_index(
  INDEX_ID VARCHAR2(32) NOT NULL ,
  META_ENTITY_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  INDEX_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  INDEX_TYPE VARCHAR2(30)  ,
  "UNIQUE" SMALLINT default 0   ,
  INDEX_COLUMNS VARCHAR2(4000) NOT NULL ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_entity_index primary key (INDEX_ID)
);

CREATE TABLE nop_meta_dict_item(
  DICT_ITEM_ID VARCHAR2(32) NOT NULL ,
  META_DICT_ID VARCHAR2(32) NOT NULL ,
  ITEM_VALUE VARCHAR2(100) NOT NULL ,
  ITEM_LABEL VARCHAR2(200)  ,
  ITEM_CODE VARCHAR2(100)  ,
  ITEM_GROUP VARCHAR2(100)  ,
  DESCRIPTION VARCHAR2(1000)  ,
  SORT_ORDER INTEGER  ,
  DEPRECATED SMALLINT default 0   ,
  INTERNAL SMALLINT default 0   ,
  DEL_VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_meta_dict_item primary key (DICT_ITEM_ID)
);


      COMMENT ON TABLE nop_meta_module IS '元数据模块';
                
      COMMENT ON COLUMN nop_meta_module.META_MODULE_ID IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_module.MODULE_ID IS '模块标识';
                    
      COMMENT ON COLUMN nop_meta_module.MODULE_NAME IS '模块名';
                    
      COMMENT ON COLUMN nop_meta_module.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_module.VERSION IS '模块版本号';
                    
      COMMENT ON COLUMN nop_meta_module.BASE_MODULE_ID IS '基线模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_module.STATUS IS '模块状态';
                    
      COMMENT ON COLUMN nop_meta_module.MAVEN_GROUP_ID IS 'Maven GroupId';
                    
      COMMENT ON COLUMN nop_meta_module.MAVEN_ARTIFACT_ID IS 'Maven ArtifactId';
                    
      COMMENT ON COLUMN nop_meta_module.MAVEN_VERSION IS 'Maven版本';
                    
      COMMENT ON COLUMN nop_meta_module.GIT_REPO_PATH IS 'Git仓库路径';
                    
      COMMENT ON COLUMN nop_meta_module.GIT_BRANCH IS 'Git分支';
                    
      COMMENT ON COLUMN nop_meta_module.GIT_COMMIT_ID IS 'Git提交';
                    
      COMMENT ON COLUMN nop_meta_module.IMPORTED_AT IS '导入时间';
                    
      COMMENT ON COLUMN nop_meta_module.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_module.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_module.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_module.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_module.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_module.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_module.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_data_source IS '数据源';
                
      COMMENT ON COLUMN nop_meta_data_source.DATA_SOURCE_ID IS '数据源ID';
                    
      COMMENT ON COLUMN nop_meta_data_source.QUERY_SPACE IS '查询空间';
                    
      COMMENT ON COLUMN nop_meta_data_source.NAME IS '名称';
                    
      COMMENT ON COLUMN nop_meta_data_source.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_data_source.DATASOURCE_TYPE IS '数据源类型';
                    
      COMMENT ON COLUMN nop_meta_data_source.CONNECTION_CONFIG IS '连接配置';
                    
      COMMENT ON COLUMN nop_meta_data_source.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_meta_data_source.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_data_source.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_data_source.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_data_source.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_data_source.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_data_source.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_semantic_type IS '语义类型';
                
      COMMENT ON COLUMN nop_meta_semantic_type.SEMANTIC_TYPE_ID IS '语义类型ID';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.TYPE_NAME IS '类型名';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.APPLICABLE_TO IS '适用数据类型';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_semantic_type.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_lineage_edge IS '血缘边';
                
      COMMENT ON COLUMN nop_meta_lineage_edge.LINEAGE_EDGE_ID IS '血缘边ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.SOURCE_TABLE_ID IS '源表ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.TARGET_TABLE_ID IS '目标表ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.SOURCE_COLUMN IS '源列名';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.TARGET_COLUMN IS '目标列名';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.TRANSFORM_TYPE IS '转换类型';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.TRANSFORM_EXPR IS '转换表达式';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.LINEAGE_SOURCE IS '血缘来源';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.PIPELINE_ID IS '管道ID';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.CONFIDENCE IS '置信度';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_lineage_edge.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_quality_rule IS '质量规则';
                
      COMMENT ON COLUMN nop_meta_quality_rule.QUALITY_RULE_ID IS '规则ID';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.RULE_NAME IS '规则名';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.RULE_TYPE IS '规则类型';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.ENTITY_TYPE IS '对象类型';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.ENTITY_ID IS '挂载对象ID';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.SEVERITY IS '严重级别';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.SQL_EXPRESSION IS 'SQL表达式';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.THRESHOLD IS '阈值';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.PARAMS IS '参数';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_quality_rule.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_orm_model IS 'ORM模型';
                
      COMMENT ON COLUMN nop_meta_orm_model.ORM_MODEL_ID IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_orm_model.META_MODULE_ID IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_orm_model.MODEL_NAME IS '模型名';
                    
      COMMENT ON COLUMN nop_meta_orm_model.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_orm_model.SOURCE_CONTENT IS '原始内容';
                    
      COMMENT ON COLUMN nop_meta_orm_model.IMPORTED_AT IS '导入时间';
                    
      COMMENT ON COLUMN nop_meta_orm_model.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_orm_model.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_orm_model.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_orm_model.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_orm_model.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_orm_model.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_table IS '逻辑表';
                
      COMMENT ON COLUMN nop_meta_table.META_TABLE_ID IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table.META_MODULE_ID IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_table.TABLE_NAME IS '表名';
                    
      COMMENT ON COLUMN nop_meta_table.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table.TABLE_TYPE IS '表类型';
                    
      COMMENT ON COLUMN nop_meta_table.QUERY_SPACE IS '查询空间';
                    
      COMMENT ON COLUMN nop_meta_table.SOURCE_SQL IS '来源SQL';
                    
      COMMENT ON COLUMN nop_meta_table.BASE_ENTITY_ID IS '主要实体ID';
                    
      COMMENT ON COLUMN nop_meta_table.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_meta_table.BUILD_SQL IS '合成SQL';
                    
      COMMENT ON COLUMN nop_meta_table.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_pipeline IS '数据管道';
                
      COMMENT ON COLUMN nop_meta_pipeline.PIPELINE_ID IS '管道ID';
                    
      COMMENT ON COLUMN nop_meta_pipeline.META_MODULE_ID IS '模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_pipeline.PIPELINE_NAME IS '管道名';
                    
      COMMENT ON COLUMN nop_meta_pipeline.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_pipeline.PIPELINE_TYPE IS '管道类型';
                    
      COMMENT ON COLUMN nop_meta_pipeline.SOURCE_SQL IS '处理SQL';
                    
      COMMENT ON COLUMN nop_meta_pipeline.SCHEDULE IS '调度表达式';
                    
      COMMENT ON COLUMN nop_meta_pipeline.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_pipeline.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_pipeline.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_pipeline.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_pipeline.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_pipeline.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_pipeline.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_quality_result IS '质量结果';
                
      COMMENT ON COLUMN nop_meta_quality_result.QUALITY_RESULT_ID IS '结果ID';
                    
      COMMENT ON COLUMN nop_meta_quality_result.QUALITY_RULE_ID IS '规则ID';
                    
      COMMENT ON COLUMN nop_meta_quality_result.EXECUTE_TIME IS '执行时间';
                    
      COMMENT ON COLUMN nop_meta_quality_result.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_meta_quality_result.ACTUAL_VALUE IS '实际值';
                    
      COMMENT ON COLUMN nop_meta_quality_result.EXPECTED_VALUE IS '期望值';
                    
      COMMENT ON COLUMN nop_meta_quality_result.MESSAGE IS '结果描述';
                    
      COMMENT ON COLUMN nop_meta_quality_result.DETAILS IS '详情';
                    
      COMMENT ON COLUMN nop_meta_quality_result.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_quality_result.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_quality_result.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_quality_result.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_quality_result.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_quality_result.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity IS '元数据实体';
                
      COMMENT ON COLUMN nop_meta_entity.META_ENTITY_ID IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity.ORM_MODEL_ID IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_entity.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity.ENTITY_NAME IS '实体名';
                    
      COMMENT ON COLUMN nop_meta_entity.TABLE_NAME IS '表名';
                    
      COMMENT ON COLUMN nop_meta_entity.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity.CLASS_NAME IS '类名';
                    
      COMMENT ON COLUMN nop_meta_entity.TAG_SET IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity.QUERY_SPACE IS '查询空间';
                    
      COMMENT ON COLUMN nop_meta_entity.PERSIST_DRIVER IS '持久化驱动';
                    
      COMMENT ON COLUMN nop_meta_entity.USE_TENANT IS '使用租户';
                    
      COMMENT ON COLUMN nop_meta_entity.USE_REVISION IS '使用版本';
                    
      COMMENT ON COLUMN nop_meta_entity.USE_LOGICAL_DELETE IS '逻辑删除';
                    
      COMMENT ON COLUMN nop_meta_entity.NOT_GEN_CODE IS '不生成代码';
                    
      COMMENT ON COLUMN nop_meta_entity.CREATER_PROP IS '创建人属性';
                    
      COMMENT ON COLUMN nop_meta_entity.CREATE_TIME_PROP IS '创建时间属性';
                    
      COMMENT ON COLUMN nop_meta_entity.UPDATER_PROP IS '修改人属性';
                    
      COMMENT ON COLUMN nop_meta_entity.UPDATE_TIME_PROP IS '修改时间属性';
                    
      COMMENT ON COLUMN nop_meta_entity.VERSION_PROP IS '版本属性';
                    
      COMMENT ON COLUMN nop_meta_entity.DEL_FLAG_PROP IS '删除标记属性';
                    
      COMMENT ON COLUMN nop_meta_entity.DEL_VERSION_PROP IS '删除版本属性';
                    
      COMMENT ON COLUMN nop_meta_entity.DB_CATALOG IS '数据库目录';
                    
      COMMENT ON COLUMN nop_meta_entity.DB_SCHEMA IS '数据库Schema';
                    
      COMMENT ON COLUMN nop_meta_entity.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_entity.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_domain IS '域定义';
                
      COMMENT ON COLUMN nop_meta_domain.META_DOMAIN_ID IS '域ID';
                    
      COMMENT ON COLUMN nop_meta_domain.ORM_MODEL_ID IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_domain.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_domain.DOMAIN_NAME IS '域名';
                    
      COMMENT ON COLUMN nop_meta_domain.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_domain.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_meta_domain.STD_DOMAIN IS '标准域';
                    
      COMMENT ON COLUMN nop_meta_domain.STD_DATA_TYPE IS '数据类型';
                    
      COMMENT ON COLUMN nop_meta_domain.STD_SQL_TYPE IS 'SQL类型';
                    
      COMMENT ON COLUMN nop_meta_domain.PRECISION IS '精度';
                    
      COMMENT ON COLUMN nop_meta_domain.SCALE IS '标度';
                    
      COMMENT ON COLUMN nop_meta_domain.VALIDATION_PATTERN IS '校验正则';
                    
      COMMENT ON COLUMN nop_meta_domain.DEFAULT_VALUE IS '默认值';
                    
      COMMENT ON COLUMN nop_meta_domain.IS_GLOBAL IS '全局通用域';
                    
      COMMENT ON COLUMN nop_meta_domain.SOURCE_MODULE_ID IS '来源模块版本ID';
                    
      COMMENT ON COLUMN nop_meta_domain.TAG_SET IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_domain.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_domain.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_domain.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_domain.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_domain.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_domain.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_domain.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_dict IS '元数据字典';
                
      COMMENT ON COLUMN nop_meta_dict.META_DICT_ID IS '字典ID';
                    
      COMMENT ON COLUMN nop_meta_dict.ORM_MODEL_ID IS '模型ID';
                    
      COMMENT ON COLUMN nop_meta_dict.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_dict.DICT_NAME IS '字典名';
                    
      COMMENT ON COLUMN nop_meta_dict.LABEL IS '字典标签';
                    
      COMMENT ON COLUMN nop_meta_dict.VALUE_TYPE IS '值类型';
                    
      COMMENT ON COLUMN nop_meta_dict.LOCALE IS '区域';
                    
      COMMENT ON COLUMN nop_meta_dict.IS_STATIC IS '静态字典';
                    
      COMMENT ON COLUMN nop_meta_dict.NORMALIZED IS '已标准化';
                    
      COMMENT ON COLUMN nop_meta_dict.DEPRECATED IS '已废弃';
                    
      COMMENT ON COLUMN nop_meta_dict.INTERNAL IS '内部使用';
                    
      COMMENT ON COLUMN nop_meta_dict.TAG_SET IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_dict.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_dict.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_dict.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_dict.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_dict.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_dict.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_table_dimension IS '表维度';
                
      COMMENT ON COLUMN nop_meta_table_dimension.DIMENSION_ID IS '维度ID';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.META_TABLE_ID IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.DIMENSION_NAME IS '维度名';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.ENTITY_FIELD_ID IS '实体字段ID';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.DIMENSION_TYPE IS '维度类型';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.GRANULARITY IS '时间粒度';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.FORMAT IS '显示格式';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.SORT_ORDER IS '排序';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_dimension.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_table_measure IS '表指标';
                
      COMMENT ON COLUMN nop_meta_table_measure.MEASURE_ID IS '指标ID';
                    
      COMMENT ON COLUMN nop_meta_table_measure.META_TABLE_ID IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_measure.MEASURE_NAME IS '指标名';
                    
      COMMENT ON COLUMN nop_meta_table_measure.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table_measure.ENTITY_FIELD_ID IS '实体字段ID';
                    
      COMMENT ON COLUMN nop_meta_table_measure.AGG_FUNC IS '聚合函数';
                    
      COMMENT ON COLUMN nop_meta_table_measure.EXPRESSION IS '表达式';
                    
      COMMENT ON COLUMN nop_meta_table_measure.FORMAT IS '显示格式';
                    
      COMMENT ON COLUMN nop_meta_table_measure.CURRENCY_UNIT IS '货币单位';
                    
      COMMENT ON COLUMN nop_meta_table_measure.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_meta_table_measure.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_table_measure.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_measure.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_measure.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_measure.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_measure.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_measure.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_table_filter IS '表过滤器';
                
      COMMENT ON COLUMN nop_meta_table_filter.FILTER_ID IS '过滤器ID';
                    
      COMMENT ON COLUMN nop_meta_table_filter.META_TABLE_ID IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_filter.FILTER_NAME IS '过滤器名';
                    
      COMMENT ON COLUMN nop_meta_table_filter.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_table_filter.DEFINITION IS '筛选条件';
                    
      COMMENT ON COLUMN nop_meta_table_filter.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_meta_table_filter.IS_DEFAULT IS '默认过滤器';
                    
      COMMENT ON COLUMN nop_meta_table_filter.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_table_filter.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_filter.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_filter.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_filter.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_filter.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_filter.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_table_join IS '表关联';
                
      COMMENT ON COLUMN nop_meta_table_join.JOIN_ID IS '关联ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.META_TABLE_ID IS '逻辑表ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.JOIN_TYPE IS '关联类型';
                    
      COMMENT ON COLUMN nop_meta_table_join.LEFT_ENTITY_ID IS '左实体ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.RIGHT_ENTITY_ID IS '右实体ID';
                    
      COMMENT ON COLUMN nop_meta_table_join.LEFT_FIELD IS '左关联字段';
                    
      COMMENT ON COLUMN nop_meta_table_join.RIGHT_FIELD IS '右关联字段';
                    
      COMMENT ON COLUMN nop_meta_table_join.ALIAS IS '右表别名';
                    
      COMMENT ON COLUMN nop_meta_table_join.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_table_join.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_table_join.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_table_join.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_table_join.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_table_join.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_field IS '实体字段';
                
      COMMENT ON COLUMN nop_meta_entity_field.ENTITY_FIELD_ID IS '字段ID';
                    
      COMMENT ON COLUMN nop_meta_entity_field.META_ENTITY_ID IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_field.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_field.FIELD_NAME IS '属性名';
                    
      COMMENT ON COLUMN nop_meta_entity_field.COLUMN_CODE IS '列名';
                    
      COMMENT ON COLUMN nop_meta_entity_field.PROP_ID IS '属性序号';
                    
      COMMENT ON COLUMN nop_meta_entity_field.STD_DATA_TYPE IS '数据类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.STD_SQL_TYPE IS 'SQL类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.PRECISION IS '精度';
                    
      COMMENT ON COLUMN nop_meta_entity_field.SCALE IS '标度';
                    
      COMMENT ON COLUMN nop_meta_entity_field.MANDATORY IS '必填';
                    
      COMMENT ON COLUMN nop_meta_entity_field.PRIMARY IS '主键';
                    
      COMMENT ON COLUMN nop_meta_entity_field.LAZY IS '懒加载';
                    
      COMMENT ON COLUMN nop_meta_entity_field.INSERTABLE IS '可插入';
                    
      COMMENT ON COLUMN nop_meta_entity_field.UPDATABLE IS '可更新';
                    
      COMMENT ON COLUMN nop_meta_entity_field.DOMAIN IS '域';
                    
      COMMENT ON COLUMN nop_meta_entity_field.STD_DOMAIN IS '标准域';
                    
      COMMENT ON COLUMN nop_meta_entity_field.FIXED_VALUE IS '固定值';
                    
      COMMENT ON COLUMN nop_meta_entity_field.DEFAULT_VALUE IS '默认值';
                    
      COMMENT ON COLUMN nop_meta_entity_field.SEMANTIC_TYPE IS '语义类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.TAG_SET IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity_field.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity_field."COMMENT" IS '注释';
                    
      COMMENT ON COLUMN nop_meta_entity_field.NATIVE_SQL_TYPE IS '原生SQL类型';
                    
      COMMENT ON COLUMN nop_meta_entity_field.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_meta_entity_field.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_field.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_field.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_field.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_field.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_field.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_relation IS '实体关系';
                
      COMMENT ON COLUMN nop_meta_entity_relation.RELATION_ID IS '关系ID';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.META_ENTITY_ID IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.RELATION_NAME IS '关系名';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.RELATION_TYPE IS '关系类型';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.REF_ENTITY_NAME IS '引用实体名';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.REF_PROP_NAME IS '引用属性名';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.CASCADE_DELETE IS '级联删除';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.AUTO_CASCADE_DELETE IS '自动级联删除';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.QUERYABLE IS '可查询';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.EMBEDDED IS '内嵌';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.NOT_GEN_CODE IS '不生成代码';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.TAG_SET IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.JOIN_CONDITIONS IS '关联条件';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_relation.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_unique_key IS '实体唯一键';
                
      COMMENT ON COLUMN nop_meta_entity_unique_key.UNIQUE_KEY_ID IS '唯一键ID';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.META_ENTITY_ID IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.UK_NAME IS '唯一键名';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.COLUMNS IS '字段列表';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.CONSTRAINT IS '约束名';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.TAG_SET IS '标签集';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_unique_key.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_entity_index IS '实体索引';
                
      COMMENT ON COLUMN nop_meta_entity_index.INDEX_ID IS '索引ID';
                    
      COMMENT ON COLUMN nop_meta_entity_index.META_ENTITY_ID IS '实体ID';
                    
      COMMENT ON COLUMN nop_meta_entity_index.IS_DELTA IS '是否Delta';
                    
      COMMENT ON COLUMN nop_meta_entity_index.INDEX_NAME IS '索引名';
                    
      COMMENT ON COLUMN nop_meta_entity_index.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_meta_entity_index.INDEX_TYPE IS '索引类型';
                    
      COMMENT ON COLUMN nop_meta_entity_index."UNIQUE" IS '唯一索引';
                    
      COMMENT ON COLUMN nop_meta_entity_index.INDEX_COLUMNS IS '索引列';
                    
      COMMENT ON COLUMN nop_meta_entity_index.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_entity_index.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_entity_index.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_entity_index.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_entity_index.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_entity_index.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_meta_dict_item IS '字典项';
                
      COMMENT ON COLUMN nop_meta_dict_item.DICT_ITEM_ID IS '字典项ID';
                    
      COMMENT ON COLUMN nop_meta_dict_item.META_DICT_ID IS '字典ID';
                    
      COMMENT ON COLUMN nop_meta_dict_item.ITEM_VALUE IS '字典值';
                    
      COMMENT ON COLUMN nop_meta_dict_item.ITEM_LABEL IS '字典标签';
                    
      COMMENT ON COLUMN nop_meta_dict_item.ITEM_CODE IS '字典编码';
                    
      COMMENT ON COLUMN nop_meta_dict_item.ITEM_GROUP IS '分组';
                    
      COMMENT ON COLUMN nop_meta_dict_item.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_meta_dict_item.SORT_ORDER IS '排序';
                    
      COMMENT ON COLUMN nop_meta_dict_item.DEPRECATED IS '已废弃';
                    
      COMMENT ON COLUMN nop_meta_dict_item.INTERNAL IS '内部使用';
                    
      COMMENT ON COLUMN nop_meta_dict_item.DEL_VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_meta_dict_item.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_meta_dict_item.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_meta_dict_item.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_meta_dict_item.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_meta_dict_item.REMARK IS '备注';
                    
