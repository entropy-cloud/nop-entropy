
CREATE TABLE nop_dyn_app(
  APP_ID VARCHAR2(32) NOT NULL ,
  APP_NAME VARCHAR2(200) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_app primary key (APP_ID)
);

CREATE TABLE nop_dyn_module(
  MODULE_ID VARCHAR2(32) NOT NULL ,
  MODULE_NAME VARCHAR2(100) NOT NULL ,
  MODULE_VERSION INTEGER NOT NULL  default '1' ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  BASE_MODULE_ID VARCHAR2(100)  ,
  BASE_PACKAGE_NAME VARCHAR2(200)  ,
  ENTITY_PACKAGE_NAME VARCHAR2(200)  ,
  MAVEN_GROUP_ID VARCHAR2(200)  ,
  STATUS INTEGER NOT NULL  default '0' ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_module primary key (MODULE_ID)
);

CREATE TABLE nop_dyn_entity(
  SID VARCHAR2(32) NOT NULL ,
  NOP_OBJ_TYPE VARCHAR2(100) NOT NULL ,
  NOP_NAME VARCHAR2(100)  ,
  NOP_DISPLAY_NAME VARCHAR2(500)  ,
  NOP_SORT_ORDER INTEGER  ,
  NOP_FLOW_ID VARCHAR2(32)  ,
  NOP_STATUS INTEGER  ,
  NOP_BIZ_STATE VARCHAR2(50)  ,
  NOP_PARENT_ID VARCHAR2(32)  ,
  NOP_OWNER_NAME VARCHAR2(50)  ,
  NOP_OWNER_ID VARCHAR2(50)  ,
  NOP_DEPT_ID VARCHAR2(50)  ,
  NOP_STRING_FLD1 VARCHAR2(4000)  ,
  NOP_DECIMAL_FLD1 NUMBER(30,6)  ,
  NOP_INT_FLD1 INTEGER  ,
  NOP_LONG_FLD1 NUMBER(20)  ,
  NOP_DATE_FLD1 DATE  ,
  NOP_TIMESTAMP_FLD1 TIMESTAMP  ,
  NOP_FILE_FLD1 VARCHAR2(200)  ,
  NOP_STRING_FLD2 VARCHAR2(4000)  ,
  NOP_DECIMAL_FLD2 NUMBER(30,6)  ,
  NOP_INT_FLD2 INTEGER  ,
  NOP_LONG_FLD2 NUMBER(20)  ,
  NOP_DATE_FLD2 DATE  ,
  NOP_TIMESTAMP_FLD2 TIMESTAMP  ,
  NOP_FILE_FLD2 VARCHAR2(200)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_entity primary key (SID)
);

CREATE TABLE nop_dyn_entity_relation(
  SID VARCHAR2(32) NOT NULL ,
  RELATION_NAME VARCHAR2(50) NOT NULL ,
  ENTITY_NAME1 VARCHAR2(100) NOT NULL ,
  ENTITY_ID1 VARCHAR2(50) NOT NULL ,
  ENTITY_NAME2 VARCHAR2(100) NOT NULL ,
  ENTITY_ID2 VARCHAR2(50) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_entity_relation primary key (SID)
);

CREATE TABLE nop_dyn_module_dep(
  MODULE_ID VARCHAR2(32) NOT NULL ,
  DEP_MODULE_ID VARCHAR2(32) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_module_dep primary key (MODULE_ID,DEP_MODULE_ID)
);

CREATE TABLE nop_dyn_app_module(
  APP_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_app_module primary key (APP_ID,MODULE_ID)
);

CREATE TABLE nop_dyn_sql(
  SQL_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL  default 'pages' ,
  DISPLAY_NAME VARCHAR2(200)  ,
  SQL_METHOD VARCHAR2(10)  ,
  ROW_TYPE VARCHAR2(100)  ,
  DESCRIPTION VARCHAR2(2000)  ,
  CACHE_NAME VARCHAR2(100)  ,
  CACHE_KEY_EXPR VARCHAR2(200)  ,
  BATCH_LOAD_SELECTION VARCHAR2(200)  ,
  SQL_KIND VARCHAR2(10)  ,
  QUERY_SPACE VARCHAR2(100)  ,
  SOURCE CLOB  ,
  FETCH_SIZE INTEGER  ,
  TIMEOUT INTEGER  ,
  DISABLE_LOGICAL_DELETE SMALLINT   default '0' ,
  ENABLE_FILTER SMALLINT   default '0' ,
  REFRESH_BEHAVIOR VARCHAR2(10)  ,
  COL_NAME_CAMEL_CASE SMALLINT   default '0' ,
  ARGS VARCHAR2(4000)  ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_sql primary key (SQL_ID)
);

CREATE TABLE nop_dyn_file(
  FILE_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  FILE_NAME VARCHAR2(200) NOT NULL ,
  FILE_PATH VARCHAR2(800) NOT NULL  default 'pages' ,
  FILE_TYPE VARCHAR2(50) NOT NULL ,
  FILE_LENGTH INTEGER NOT NULL ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_file primary key (FILE_ID)
);

CREATE TABLE nop_dyn_page(
  PAGE_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  PAGE_NAME VARCHAR2(200) NOT NULL ,
  PAGE_GROUP VARCHAR2(100) NOT NULL  default 'pages' ,
  PAGE_SCHEMA_TYPE VARCHAR2(100) NOT NULL ,
  PAGE_CONTENT CLOB NOT NULL ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_page primary key (PAGE_ID)
);

CREATE TABLE nop_dyn_entity_meta(
  ENTITY_META_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  ENTITY_NAME VARCHAR2(200) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  TABLE_NAME VARCHAR2(100)  ,
  QUERY_SPACE VARCHAR2(100)  ,
  STORE_TYPE INTEGER NOT NULL ,
  TAGS_TEXT VARCHAR2(200)  ,
  IS_EXTERNAL CHAR(1) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  EXT_CONFIG VARCHAR2(1000)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_entity_meta primary key (ENTITY_META_ID)
);

CREATE TABLE nop_dyn_domain(
  DOMAIN_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  DOMAIN_NAME VARCHAR2(50) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  STD_DOMAIN_NAME VARCHAR2(50)  ,
  STD_SQL_TYPE VARCHAR2(10) NOT NULL ,
  PRECISION INTEGER  ,
  SCALE INTEGER   default '0' ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_domain primary key (DOMAIN_ID)
);

CREATE TABLE nop_dyn_entity_relation_meta(
  REL_META_ID VARCHAR2(32) NOT NULL ,
  ENTITY_META_ID VARCHAR2(32) NOT NULL ,
  REF_ENTITY_META_ID VARCHAR2(32) NOT NULL ,
  RELATION_NAME VARCHAR2(100) NOT NULL ,
  RELATION_DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  RELATION_TYPE VARCHAR2(10) NOT NULL ,
  MIDDLE_TABLE_NAME VARCHAR2(100)  ,
  MIDDLE_ENTITY_NAME VARCHAR2(100)  ,
  LEFT_PROP_NAME VARCHAR2(100) NOT NULL ,
  RIGHT_PROP_NAME VARCHAR2(100) NOT NULL ,
  REF_SET_KEY_PROP VARCHAR2(50)  ,
  REF_SET_SORT VARCHAR2(100)  ,
  STATUS INTEGER NOT NULL ,
  TAGS_TEXT VARCHAR2(200)  ,
  EXT_CONFIG VARCHAR2(1000)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_entity_relation_meta primary key (REL_META_ID)
);

CREATE TABLE nop_dyn_function_meta(
  FUNC_META_ID VARCHAR2(32) NOT NULL ,
  ENTITY_META_ID VARCHAR2(32) NOT NULL ,
  NAME VARCHAR2(50) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  FUNCTION_TYPE VARCHAR2(10) NOT NULL ,
  RETURN_TYPE VARCHAR2(100)  ,
  RETURN_GQL_TYPE VARCHAR2(100)  ,
  STATUS INTEGER NOT NULL ,
  TAGS_TEXT VARCHAR2(200)  ,
  FUNC_META VARCHAR2(4000)  ,
  SOURCE VARCHAR2(4000) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_function_meta primary key (FUNC_META_ID)
);

CREATE TABLE nop_dyn_prop_meta(
  PROP_META_ID VARCHAR2(32) NOT NULL ,
  ENTITY_META_ID VARCHAR2(32) NOT NULL ,
  IS_MANDATORY CHAR(1) NOT NULL ,
  PROP_NAME VARCHAR2(50) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  STD_SQL_TYPE VARCHAR2(10) NOT NULL ,
  PRECISION INTEGER  ,
  SCALE INTEGER   default '0' ,
  PROP_ID INTEGER NOT NULL ,
  UI_SHOW VARCHAR2(10)  ,
  UI_CONTROL VARCHAR2(100)  ,
  DOMAIN_ID VARCHAR2(32)  ,
  STD_DOMAIN_NAME VARCHAR2(50)  ,
  DICT_NAME VARCHAR2(100)  ,
  DYN_PROP_MAPPING VARCHAR2(100)  ,
  TAGS_TEXT VARCHAR2(200)  ,
  DEFAULT_VALUE VARCHAR2(100)  ,
  EXT_CONFIG VARCHAR2(1000)  ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_prop_meta primary key (PROP_META_ID)
);


      COMMENT ON TABLE nop_dyn_app IS '应用定义';
                
      COMMENT ON COLUMN nop_dyn_app.APP_ID IS '应用ID';
                    
      COMMENT ON COLUMN nop_dyn_app.APP_NAME IS '应用名';
                    
      COMMENT ON COLUMN nop_dyn_app.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_app.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_app.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_app.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_app.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_app.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_app.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_module IS '模块定义';
                
      COMMENT ON COLUMN nop_dyn_module.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module.MODULE_NAME IS '模块名';
                    
      COMMENT ON COLUMN nop_dyn_module.MODULE_VERSION IS '模块版本';
                    
      COMMENT ON COLUMN nop_dyn_module.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_module.BASE_MODULE_ID IS '基础模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module.BASE_PACKAGE_NAME IS 'Java包名';
                    
      COMMENT ON COLUMN nop_dyn_module.ENTITY_PACKAGE_NAME IS '实体包名';
                    
      COMMENT ON COLUMN nop_dyn_module.MAVEN_GROUP_ID IS 'Maven组名';
                    
      COMMENT ON COLUMN nop_dyn_module.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_module.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_module.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_module.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_module.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_module.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_entity IS '动态实体';
                
      COMMENT ON COLUMN nop_dyn_entity.SID IS '主键';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_OBJ_TYPE IS '对象类型';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_NAME IS '名称';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_SORT_ORDER IS '排序';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_FLOW_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_STATUS IS '业务状态码';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_BIZ_STATE IS '业务状态';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_PARENT_ID IS '父ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_OWNER_NAME IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_OWNER_ID IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_DEPT_ID IS '部门ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_STRING_FLD1 IS '字符串字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_DECIMAL_FLD1 IS '浮点型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_INT_FLD1 IS '整数型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_LONG_FLD1 IS '长整型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_DATE_FLD1 IS '日期字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_TIMESTAMP_FLD1 IS '时间戳字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_FILE_FLD1 IS '文件字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_STRING_FLD2 IS '字符串字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_DECIMAL_FLD2 IS '浮点型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_INT_FLD2 IS '整数型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_LONG_FLD2 IS '长整型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_DATE_FLD2 IS '日期字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_TIMESTAMP_FLD2 IS '时间戳字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_FILE_FLD2 IS '文件字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_entity_relation IS '实体关联';
                
      COMMENT ON COLUMN nop_dyn_entity_relation.SID IS '主键';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.RELATION_NAME IS '关联名称';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.ENTITY_NAME1 IS '实体名称1';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.ENTITY_ID1 IS '实体ID1';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.ENTITY_NAME2 IS '实体名称2';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.ENTITY_ID2 IS '实体ID2';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_module_dep IS '模块依赖';
                
      COMMENT ON COLUMN nop_dyn_module_dep.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.DEP_MODULE_ID IS '被依赖模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_module_dep.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_app_module IS '应用模块映射';
                
      COMMENT ON COLUMN nop_dyn_app_module.APP_ID IS '应用ID';
                    
      COMMENT ON COLUMN nop_dyn_app_module.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_app_module.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_app_module.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_app_module.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_app_module.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_app_module.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_sql IS 'SQL定义';
                
      COMMENT ON COLUMN nop_dyn_sql.SQL_ID IS 'SQL ID';
                    
      COMMENT ON COLUMN nop_dyn_sql.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_sql.NAME IS 'SQL名称';
                    
      COMMENT ON COLUMN nop_dyn_sql.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_dyn_sql.SQL_METHOD IS 'SQL方法';
                    
      COMMENT ON COLUMN nop_dyn_sql.ROW_TYPE IS '行类型';
                    
      COMMENT ON COLUMN nop_dyn_sql.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_dyn_sql.CACHE_NAME IS '缓存名称';
                    
      COMMENT ON COLUMN nop_dyn_sql.CACHE_KEY_EXPR IS '缓存键表达式';
                    
      COMMENT ON COLUMN nop_dyn_sql.BATCH_LOAD_SELECTION IS '批量加载选择集';
                    
      COMMENT ON COLUMN nop_dyn_sql.SQL_KIND IS '类型';
                    
      COMMENT ON COLUMN nop_dyn_sql.QUERY_SPACE IS '查询空间';
                    
      COMMENT ON COLUMN nop_dyn_sql.SOURCE IS 'SQL文本';
                    
      COMMENT ON COLUMN nop_dyn_sql.FETCH_SIZE IS '读取块大小';
                    
      COMMENT ON COLUMN nop_dyn_sql.TIMEOUT IS '超时时间';
                    
      COMMENT ON COLUMN nop_dyn_sql.DISABLE_LOGICAL_DELETE IS '禁用逻辑删除';
                    
      COMMENT ON COLUMN nop_dyn_sql.ENABLE_FILTER IS '启用数据权限';
                    
      COMMENT ON COLUMN nop_dyn_sql.REFRESH_BEHAVIOR IS '实体刷新规则';
                    
      COMMENT ON COLUMN nop_dyn_sql.COL_NAME_CAMEL_CASE IS '列名需要转换为驼峰';
                    
      COMMENT ON COLUMN nop_dyn_sql.ARGS IS '参数列表';
                    
      COMMENT ON COLUMN nop_dyn_sql.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_sql.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_sql.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_sql.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_sql.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_sql.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_sql.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_file IS '模块文件';
                
      COMMENT ON COLUMN nop_dyn_file.FILE_ID IS '文件ID';
                    
      COMMENT ON COLUMN nop_dyn_file.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_file.FILE_NAME IS '文件名称';
                    
      COMMENT ON COLUMN nop_dyn_file.FILE_PATH IS '文件路径';
                    
      COMMENT ON COLUMN nop_dyn_file.FILE_TYPE IS '文件类型';
                    
      COMMENT ON COLUMN nop_dyn_file.FILE_LENGTH IS '文件大小';
                    
      COMMENT ON COLUMN nop_dyn_file.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_file.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_file.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_file.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_file.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_file.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_file.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_page IS '页面定义';
                
      COMMENT ON COLUMN nop_dyn_page.PAGE_ID IS '页面ID';
                    
      COMMENT ON COLUMN nop_dyn_page.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_NAME IS '页面名称';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_GROUP IS '页面分组';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_SCHEMA_TYPE IS '页面类型';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_CONTENT IS '页面内容';
                    
      COMMENT ON COLUMN nop_dyn_page.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_page.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_page.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_page.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_page.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_page.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_page.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_entity_meta IS '实体元数据';
                
      COMMENT ON COLUMN nop_dyn_entity_meta.ENTITY_META_ID IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.ENTITY_NAME IS '实体名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.TABLE_NAME IS '表名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.QUERY_SPACE IS '查询空间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.STORE_TYPE IS '存储类型';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.TAGS_TEXT IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.IS_EXTERNAL IS '是否外部实体';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_domain IS '数据域';
                
      COMMENT ON COLUMN nop_dyn_domain.DOMAIN_ID IS '数据域ID';
                    
      COMMENT ON COLUMN nop_dyn_domain.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_domain.DOMAIN_NAME IS '数据域名称';
                    
      COMMENT ON COLUMN nop_dyn_domain.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_domain.STD_DOMAIN_NAME IS '标准域';
                    
      COMMENT ON COLUMN nop_dyn_domain.STD_SQL_TYPE IS '标准SQL数据类型';
                    
      COMMENT ON COLUMN nop_dyn_domain.PRECISION IS '长度';
                    
      COMMENT ON COLUMN nop_dyn_domain.SCALE IS '小数位数';
                    
      COMMENT ON COLUMN nop_dyn_domain.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_domain.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_domain.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_domain.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_domain.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_domain.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_entity_relation_meta IS '实体关联属性定义';
                
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.REL_META_ID IS '关联定义ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.ENTITY_META_ID IS '实体元数据';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.REF_ENTITY_META_ID IS '关联实体';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.RELATION_NAME IS '关联名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.RELATION_DISPLAY_NAME IS '关联显示名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.RELATION_TYPE IS '关联类型';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.MIDDLE_TABLE_NAME IS '中间表表名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.MIDDLE_ENTITY_NAME IS '中间表实体名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.LEFT_PROP_NAME IS '左属性名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.RIGHT_PROP_NAME IS '右属性名';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.REF_SET_KEY_PROP IS '集合内唯一标识';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.REF_SET_SORT IS '集合排序条件';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.TAGS_TEXT IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_relation_meta.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_function_meta IS '实体函数定义';
                
      COMMENT ON COLUMN nop_dyn_function_meta.FUNC_META_ID IS '函数定义ID';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.ENTITY_META_ID IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.NAME IS '函数名';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.FUNCTION_TYPE IS '函数类型';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.RETURN_TYPE IS '返回类型';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.RETURN_GQL_TYPE IS 'GraphQL返回类型';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.TAGS_TEXT IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.FUNC_META IS '函数元数据';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.SOURCE IS '源码';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_function_meta.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_prop_meta IS '属性元数据';
                
      COMMENT ON COLUMN nop_dyn_prop_meta.PROP_META_ID IS '属性定义ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.ENTITY_META_ID IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.IS_MANDATORY IS '是否非空';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.PROP_NAME IS '属性名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.STD_SQL_TYPE IS '标准SQL数据类型';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.PRECISION IS '长度';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.SCALE IS '小数位数';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.PROP_ID IS '属性编号';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UI_SHOW IS '显示控制';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UI_CONTROL IS '显示控件';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DOMAIN_ID IS '数据域ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.STD_DOMAIN_NAME IS '标准域';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DICT_NAME IS '数据字典';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DYN_PROP_MAPPING IS '动态字段映射';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.TAGS_TEXT IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DEFAULT_VALUE IS '缺省值';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.REMARK IS '备注';
                    
