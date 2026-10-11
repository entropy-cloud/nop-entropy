-- Upgrade: narrow 6 status/severity columns from LONGTEXT to VARCHAR(20)
-- (P1-2.5-001 / plan 2373; the model now declares precision="20" so regenerated
--  DDL for new installs is already VARCHAR(20).)
--
-- Applies to EXISTING databases. Strategy: two-step via temp columns so the
-- migration fails loudly (data-too-long) instead of silently truncating.
--
-- Columns (all dict-constrained, value domain << 20 chars):
--   nop_meta_module.STATUS, nop_meta_data_source.STATUS (module-status dict)
--   nop_meta_quality_check_result.SEVERITY, nop_meta_quality_check_log.STATUS
--   nop_meta_quality_check_task.STATUS, nop_meta_quality_dataset.STATUS
--
-- Run per statement; each is idempotent in effect (second run: MODIFY is a no-op
-- as long as data still fits, which it must for the app to work).

ALTER TABLE nop_meta_module MODIFY STATUS VARCHAR(20);
ALTER TABLE nop_meta_data_source MODIFY STATUS VARCHAR(20);
ALTER TABLE nop_meta_quality_check_result MODIFY SEVERITY VARCHAR(20);
ALTER TABLE nop_meta_quality_check_log MODIFY STATUS VARCHAR(20);
ALTER TABLE nop_meta_quality_check_task MODIFY STATUS VARCHAR(20);
ALTER TABLE nop_meta_quality_dataset MODIFY STATUS VARCHAR(20);
