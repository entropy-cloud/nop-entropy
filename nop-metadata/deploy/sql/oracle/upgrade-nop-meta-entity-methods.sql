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
  METHOD_ID VARCHAR2(32) NOT NULL ,
  META_ENTITY_ID VARCHAR2(32) NOT NULL ,
  IS_DELTA SMALLINT default 0  NOT NULL ,
  METHOD_NAME VARCHAR2(100) NOT NULL ,
  METHOD_KIND VARCHAR2(20) NOT NULL ,
  SOURCE_KIND VARCHAR2(20) NOT NULL ,
  IMPL_KIND VARCHAR2(20) NOT NULL ,
  IMPL_SOURCE CLOB  ,
  RETURN_TYPE VARCHAR2(200) NOT NULL ,
  ROLES VARCHAR2(500)  ,
  DISABLED SMALLINT default 0  ,
  EXT_CONFIG VARCHAR2(4000)  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint UK_NOP_META_METHOD_ENTITY_NAME unique (META_ENTITY_ID,METHOD_NAME),
  constraint PK_nop_meta_entity_method primary key (METHOD_ID)
);

create index IX_NOP_META_METHOD_ENTITY on nop_meta_entity_method (META_ENTITY_ID) ;

CREATE TABLE nop_meta_method_arg(
  ARG_ID VARCHAR2(32) NOT NULL ,
  METHOD_ID VARCHAR2(32) NOT NULL ,
  ARG_NAME VARCHAR2(100) NOT NULL ,
  ARG_TYPE VARCHAR2(200) NOT NULL ,
  MANDATORY SMALLINT default 0  ,
  DEFAULT_VALUE VARCHAR2(500)  ,
  DISPLAY_NAME VARCHAR2(200)  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint UK_NOP_META_METHOD_ARG_NAME unique (METHOD_ID,ARG_NAME),
  constraint PK_nop_meta_method_arg primary key (ARG_ID)
);

create index IX_NOP_META_METHOD_ARG_METHOD on nop_meta_method_arg (METHOD_ID) ;
