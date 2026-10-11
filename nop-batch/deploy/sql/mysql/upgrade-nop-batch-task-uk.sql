-- Upgrade: add DB-level duplicate-prevention UK on nop_batch_task
-- (P1-2.5-003 / plan 2373; the 03d05ed88c commit claimed a DB-level gate
--  but the generated DDL never materialized it because the model-level
--  unique-key lacked the constraint attribute -- R1.9 platform ticket fixed.)
--
-- Applies to EXISTING databases created before this change. New installs are
-- covered by _create_nop-batch.sql (regenerated with the constraint).
--
-- Preconditions (MUST verify before executing -- adding a UNIQUE constraint
-- fails if existing data violates it):
--   SELECT taskName, taskKey, COUNT(*) c FROM nop_batch_task
--    GROUP BY taskName, taskKey HAVING c > 1;
-- If duplicates exist, merge/dedupe rows first.
--
-- MySQL idempotence: MySQL has no ADD CONSTRAINT IF NOT EXISTS; guard via
-- information_schema (this statement is a no-op when the UK already exists).

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'nop_batch_task'
      AND CONSTRAINT_NAME = 'UK_NOP_BATCH_TASK_NAME_KEY') = 0,
  'ALTER TABLE nop_batch_task ADD CONSTRAINT UK_NOP_BATCH_TASK_NAME_KEY UNIQUE (TASK_NAME, TASK_KEY)',
  'SELECT ''UK_NOP_BATCH_TASK_NAME_KEY already exists'' AS note');
PREPARE stmt FROM @stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
