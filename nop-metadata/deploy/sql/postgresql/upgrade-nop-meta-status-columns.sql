-- Upgrade: narrow 6 status/severity columns from TEXT to VARCHAR(20)
-- (P1-2.5-001 / plan 2373). Pre-check: any value longer than 20 chars fails
-- the ALTER loudly -- investigate before forcing the cast.
ALTER TABLE nop_meta_module ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE nop_meta_data_source ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE nop_meta_quality_check_result ALTER COLUMN severity TYPE VARCHAR(20);
ALTER TABLE nop_meta_quality_check_log ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE nop_meta_quality_check_task ALTER COLUMN status TYPE VARCHAR(20);
ALTER TABLE nop_meta_quality_dataset ALTER COLUMN status TYPE VARCHAR(20);
