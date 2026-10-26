-- Upgrade: entity field kind columns (design 14 §3.2, plan 2026-10-26-2047-1 M2)
--
-- Applies to EXISTING databases created before the M2 change.
-- New installs are covered by _create_nop-metadata.sql; tenant deployments are
-- covered by _add_tenant_nop-metadata.sql (regenerated, never hand-edited).
--
-- Adds fieldKind/computeExpr/mapToProp to nop_meta_entity_field. All existing rows
-- implicitly keep storage semantics (fieldKind NULL is treated as storage at runtime;
-- the save path injects the 'storage' default). No data migration required.

ALTER TABLE nop_meta_entity_field ADD COLUMN FIELD_KIND VARCHAR(20) NULL COMMENT '字段类别';
ALTER TABLE nop_meta_entity_field ADD COLUMN COMPUTE_EXPR VARCHAR(1000) NULL COMMENT '计算表达式';
ALTER TABLE nop_meta_entity_field ADD COLUMN MAP_TO_PROP VARCHAR(100) NULL COMMENT '别名目标';
