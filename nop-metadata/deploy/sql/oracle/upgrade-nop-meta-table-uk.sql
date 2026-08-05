-- Upgrade: UK_NOP_META_TABLE_MODULE_NAME extended with META_SCHEMA dimension
-- (multi-schema support, plan-2026-08-05-1625-1 R4.2 adjudication D3)
--
-- Applies to EXISTING NON-TENANT databases created before the R4.2 change.
-- New installs are covered by _create_nop-metadata.sql; tenant deployments are
-- covered by _add_tenant_nop-metadata.sql (regenerated, never hand-edited).
--
-- PRECONDITION: databases created before R3.19 (zero-UK DDL era) may contain
-- duplicate (META_MODULE_ID, TABLE_NAME, IS_DELTA) rows. Deduplicate them
-- first, otherwise the ADD CONSTRAINT below fails (fail-fast by design).
--
-- Oracle notes: nullable unique columns allow multiple NULLs (null-schema rows
-- stay DB-enforced as before); do NOT use '' as a schema value here -- Oracle
-- treats '' as NULL and such an UPDATE would be a silent no-op.
--
-- Resulting UK shape must match the live model:
--   UK_NOP_META_TABLE_MODULE_NAME unique (META_MODULE_ID,TABLE_NAME,IS_DELTA,META_SCHEMA)

alter table nop_meta_table drop constraint UK_NOP_META_TABLE_MODULE_NAME;
alter table nop_meta_table add constraint UK_NOP_META_TABLE_MODULE_NAME unique (META_MODULE_ID,TABLE_NAME,IS_DELTA,META_SCHEMA);
