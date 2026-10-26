-- Upgrade: entity method registry tables (design 14 §3.1, plan 2026-10-26-2047-1 M1)
--
-- Applies to EXISTING databases created before the M1 change.
-- New installs are covered by _create_nop-metadata.sql; tenant deployments are
-- covered by _add_tenant_nop-metadata.sql (regenerated, never hand-edited).
--
-- Adds two new tables: nop_meta_entity_method (method registry) and
-- nop_meta_method_arg (method argument sub-table). No existing table is altered,
-- no data migration required.

CREATE TABLE nop_meta_entity_method(
  METHOD_ID VARCHAR(32) NOT NULL    COMMENT '方法ID',
  META_ENTITY_ID VARCHAR(32) NOT NULL    COMMENT '实体ID',
  IS_DELTA TINYINT default 0  NOT NULL    COMMENT '是否Delta',
  METHOD_NAME VARCHAR(100) NOT NULL    COMMENT '方法名',
  METHOD_KIND VARCHAR(20) NOT NULL    COMMENT '方法类别',
  SOURCE_KIND VARCHAR(20) NOT NULL    COMMENT '来源类别',
  IMPL_KIND VARCHAR(20) NOT NULL    COMMENT '实现类别',
  IMPL_SOURCE MEDIUMTEXT NULL    COMMENT '实现体',
  RETURN_TYPE VARCHAR(200) NOT NULL    COMMENT '返回类型',
  ROLES VARCHAR(500) NULL    COMMENT '行级角色',
  DISABLED TINYINT default 0  NULL    COMMENT '禁用',
  EXT_CONFIG VARCHAR(4000) NULL    COMMENT '扩展配置',
  VERSION BIGINT NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint UK_NOP_META_METHOD_ENTITY_NAME unique (META_ENTITY_ID,METHOD_NAME),
  constraint PK_nop_meta_entity_method primary key (METHOD_ID)
)CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

create index IX_NOP_META_METHOD_ENTITY on nop_meta_entity_method (META_ENTITY_ID) ;

CREATE TABLE nop_meta_method_arg(
  ARG_ID VARCHAR(32) NOT NULL    COMMENT '参数ID',
  METHOD_ID VARCHAR(32) NOT NULL    COMMENT '方法ID',
  ARG_NAME VARCHAR(100) NOT NULL    COMMENT '参数名',
  ARG_TYPE VARCHAR(200) NOT NULL    COMMENT '参数类型',
  MANDATORY TINYINT default 0  NULL    COMMENT '必填',
  DEFAULT_VALUE VARCHAR(500) NULL    COMMENT '默认值',
  DISPLAY_NAME VARCHAR(200) NULL    COMMENT '显示名',
  VERSION BIGINT NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  constraint UK_NOP_META_METHOD_ARG_NAME unique (METHOD_ID,ARG_NAME),
  constraint PK_nop_meta_method_arg primary key (ARG_ID)
)CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

create index IX_NOP_META_METHOD_ARG_METHOD on nop_meta_method_arg (METHOD_ID) ;
