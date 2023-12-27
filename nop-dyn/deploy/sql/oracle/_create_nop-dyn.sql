
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

CREATE TABLE nop_dyn_page(
  PAGE_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  PAGE_NAME VARCHAR2(200) NOT NULL ,
  PAGE_GROUP VARCHAR2(100) NOT NULL ,
  PAGE_SCHEMA_TYPE VARCHAR2(100) NOT NULL ,
  PAGE_CONTENT CLOB NOT NULL ,
  STATUS INTEGER NOT NULL ,
  OWNER_ID VARCHAR2(50)  ,
  OWNER_NAME VARCHAR2(50)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_page primary key (PAGE_ID)
);

CREATE TABLE nop_dyn_prop_meta(
  PROP_META_ID VARCHAR2(32) NOT NULL ,
  ENTITY_META_ID VARCHAR2(32) NOT NULL ,
  PROP_NAME VARCHAR2(50) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  STD_SQL_TYPE VARCHAR2(10) NOT NULL ,
  LENGTH INTEGER NOT NULL ,
  SCALE INTEGER NOT NULL ,
  UI_SHOW VARCHAR2(10) NOT NULL ,
  UI_CONTROL VARCHAR2(100)  ,
  STD_DOMAIN VARCHAR2(50) NOT NULL ,
  DICT_NAME VARCHAR2(100)  ,
  DYN_FIELD_MAPPING VARCHAR2(100)  ,
  TAG_SET VARCHAR2(200)  ,
  REF_ENTITY_NAME VARCHAR2(200)  ,
  REF_PROP_NAME VARCHAR2(100)  ,
  REF_PROP_DISPLAY_NAME VARCHAR2(100)  ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_prop_meta primary key (PROP_META_ID)
);

CREATE TABLE nop_dyn_entity(
  SID VARCHAR2(32) NOT NULL ,
  OBJ_TYPE VARCHAR2(100) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(500)  ,
  SORT_ORDER INTEGER  ,
  NOP_FLOW_ID VARCHAR2(32)  ,
  BIZ_STATUS INTEGER  ,
  BIZ_STATE VARCHAR2(50)  ,
  PARENT_ID VARCHAR2(32)  ,
  OWNER_NAME VARCHAR2(50)  ,
  OWNER_ID VARCHAR2(50)  ,
  DEPT_ID VARCHAR2(50)  ,
  STRING_FLD1 VARCHAR2(4000)  ,
  DECIMAL_FLD1 NUMBER(30,6)  ,
  INT_FLD1 INTEGER  ,
  LONG_FLD1 NUMBER(20)  ,
  DATE_FLD1 DATE  ,
  TIMESTAMP_FLD1 TIMESTAMP  ,
  FILE_FLD1 VARCHAR2(200)  ,
  STRING_FLD2 VARCHAR2(4000)  ,
  DECIMAL_FLD2 NUMBER(30,6)  ,
  INT_FLD2 INTEGER  ,
  LONG_FLD2 NUMBER(20)  ,
  DATE_FLD2 DATE  ,
  TIMESTAMP_FLD2 TIMESTAMP  ,
  FILE_FLD2 VARCHAR2(200)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_entity primary key (SID)
);

CREATE TABLE nop_dyn_app(
  APP_ID VARCHAR2(32) NOT NULL ,
  APP_NAME VARCHAR2(200) NOT NULL ,
  DISPLAY_NAME INTEGER NOT NULL ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_app primary key (APP_ID)
);

CREATE TABLE nop_dyn_entity_meta(
  ENTITY_META_ID VARCHAR2(32) NOT NULL ,
  MODULE_ID VARCHAR2(32) NOT NULL ,
  ENTITY_NAME VARCHAR2(200) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  STORE_TYPE INTEGER NOT NULL ,
  META_CONTENT CLOB NOT NULL ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_dyn_entity_meta primary key (ENTITY_META_ID)
);

CREATE TABLE nop_dyn_module(
  MODULE_ID VARCHAR2(32) NOT NULL ,
  MODULE_NAME VARCHAR2(200) NOT NULL ,
  DISPLAY_NAME INTEGER NOT NULL ,
  BASE_MODULE_ID VARCHAR2(32)  ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_dyn_module primary key (MODULE_ID)
);


      COMMENT ON TABLE nop_dyn_app_module IS '应用模块映射';
                
      COMMENT ON COLUMN nop_dyn_app_module.APP_ID IS '应用ID';
                    
      COMMENT ON COLUMN nop_dyn_app_module.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_app_module.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_app_module.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_app_module.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_app_module.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_app_module.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_dyn_page IS '页面定义';
                
      COMMENT ON COLUMN nop_dyn_page.PAGE_ID IS '页面ID';
                    
      COMMENT ON COLUMN nop_dyn_page.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_NAME IS '页面名称';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_GROUP IS '页面分组';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_SCHEMA_TYPE IS '页面类型';
                    
      COMMENT ON COLUMN nop_dyn_page.PAGE_CONTENT IS '页面内容';
                    
      COMMENT ON COLUMN nop_dyn_page.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_page.OWNER_ID IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_dyn_page.OWNER_NAME IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_dyn_page.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_page.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_page.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_page.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_page.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_page.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_prop_meta IS '属性元数据';
                
      COMMENT ON COLUMN nop_dyn_prop_meta.PROP_META_ID IS '属性定义ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.ENTITY_META_ID IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.PROP_NAME IS '属性名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.STD_SQL_TYPE IS '标准SQL数据类型';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.LENGTH IS '长度';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.SCALE IS '小数位数';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UI_SHOW IS '显示控制';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UI_CONTROL IS '显示控件';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.STD_DOMAIN IS '标准域';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DICT_NAME IS '数据字典';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.DYN_FIELD_MAPPING IS '动态字段映射';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.TAG_SET IS '标签';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.REF_ENTITY_NAME IS '关联实体名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.REF_PROP_NAME IS '关联属性名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.REF_PROP_DISPLAY_NAME IS '关联属性显示名';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_prop_meta.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_entity IS '动态实体';
                
      COMMENT ON COLUMN nop_dyn_entity.SID IS '主键';
                    
      COMMENT ON COLUMN nop_dyn_entity.OBJ_TYPE IS '对象类型';
                    
      COMMENT ON COLUMN nop_dyn_entity.NAME IS '名称';
                    
      COMMENT ON COLUMN nop_dyn_entity.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_dyn_entity.SORT_ORDER IS '排序';
                    
      COMMENT ON COLUMN nop_dyn_entity.NOP_FLOW_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.BIZ_STATUS IS '业务状态码';
                    
      COMMENT ON COLUMN nop_dyn_entity.BIZ_STATE IS '业务状态';
                    
      COMMENT ON COLUMN nop_dyn_entity.PARENT_ID IS '父ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.OWNER_NAME IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_dyn_entity.OWNER_ID IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.DEPT_ID IS '部门ID';
                    
      COMMENT ON COLUMN nop_dyn_entity.STRING_FLD1 IS '字符串字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.DECIMAL_FLD1 IS '浮点型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.INT_FLD1 IS '整数型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.LONG_FLD1 IS '长整型字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.DATE_FLD1 IS '日期字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.TIMESTAMP_FLD1 IS '时间戳字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.FILE_FLD1 IS '文件字段1';
                    
      COMMENT ON COLUMN nop_dyn_entity.STRING_FLD2 IS '字符串字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.DECIMAL_FLD2 IS '浮点型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.INT_FLD2 IS '整数型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.LONG_FLD2 IS '长整型字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.DATE_FLD2 IS '日期字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.TIMESTAMP_FLD2 IS '时间戳字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.FILE_FLD2 IS '文件字段2';
                    
      COMMENT ON COLUMN nop_dyn_entity.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity.REMARK IS '备注';
                    
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
                    
      COMMENT ON TABLE nop_dyn_entity_meta IS '实体元数据';
                
      COMMENT ON COLUMN nop_dyn_entity_meta.ENTITY_META_ID IS '实体定义ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.ENTITY_NAME IS '实体名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.STORE_TYPE IS '存储类型';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.META_CONTENT IS '元数据内容';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_dyn_entity_meta.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_dyn_module IS '模块定义';
                
      COMMENT ON COLUMN nop_dyn_module.MODULE_ID IS '模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module.MODULE_NAME IS '模块名';
                    
      COMMENT ON COLUMN nop_dyn_module.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_dyn_module.BASE_MODULE_ID IS '基础模块ID';
                    
      COMMENT ON COLUMN nop_dyn_module.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_dyn_module.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_dyn_module.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_dyn_module.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_dyn_module.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_dyn_module.UPDATE_TIME IS '修改时间';
                    
