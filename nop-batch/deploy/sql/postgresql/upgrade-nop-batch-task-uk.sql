-- Upgrade: add DB-level duplicate-prevention UK on nop_batch_task (P1-2.5-003 / plan 2373)
-- See ../mysql/upgrade-nop-batch-task-uk.sql for preconditions (duplicate pre-check).
--
-- PostgreSQL: the DO block is idempotent (skips when the constraint exists).
DO $$
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM pg_constraint con
      JOIN pg_class rel ON rel.oid = con.conrelid
      WHERE con.conname = 'UK_NOP_BATCH_TASK_NAME_KEY'
        AND rel.relname = 'nop_batch_task') THEN
    ALTER TABLE nop_batch_task
      ADD CONSTRAINT UK_NOP_BATCH_TASK_NAME_KEY UNIQUE (TASK_NAME, TASK_KEY);
  END IF;
END $$;
