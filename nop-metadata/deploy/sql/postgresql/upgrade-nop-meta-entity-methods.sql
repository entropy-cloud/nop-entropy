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
  method_id VARCHAR(32) NOT NULL ,
  meta_entity_id VARCHAR(32) NOT NULL ,
  is_delta INT4 default 0  NOT NULL ,
  method_name VARCHAR(100) NOT NULL ,
  method_kind VARCHAR(20) NOT NULL ,
  source_kind VARCHAR(20) NOT NULL ,
  impl_kind VARCHAR(20) NOT NULL ,
  impl_source TEXT  ,
  return_type VARCHAR(200) NOT NULL ,
  roles VARCHAR(500)  ,
  disabled INT4 default 0  ,
  ext_config VARCHAR(4000)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint UK_NOP_META_METHOD_ENTITY_NAME unique (meta_entity_id,method_name),
  constraint PK_nop_meta_entity_method primary key (method_id)
);

create index IX_NOP_META_METHOD_ENTITY on nop_meta_entity_method (meta_entity_id) ;

CREATE TABLE nop_meta_method_arg(
  arg_id VARCHAR(32) NOT NULL ,
  method_id VARCHAR(32) NOT NULL ,
  arg_name VARCHAR(100) NOT NULL ,
  arg_type VARCHAR(200) NOT NULL ,
  mandatory INT4 default 0  ,
  default_value VARCHAR(500)  ,
  display_name VARCHAR(200)  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint UK_NOP_META_METHOD_ARG_NAME unique (method_id,arg_name),
  constraint PK_nop_meta_method_arg primary key (arg_id)
);

create index IX_NOP_META_METHOD_ARG_METHOD on nop_meta_method_arg (method_id) ;
