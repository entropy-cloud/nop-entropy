-- Upgrade: add DB-level duplicate-prevention UK on nop_batch_task (P1-2.5-003 / plan 2373)
-- See ../mysql/upgrade-nop-batch-task-uk.sql for preconditions (duplicate pre-check).
-- Oracle: the PL/SQL block is idempotent (skips when the constraint exists).
DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM user_constraints
   WHERE constraint_name = 'UK_NOP_BATCH_TASK_NAME_KEY'
     AND table_name = 'NOP_BATCH_TASK';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE nop_batch_task ADD CONSTRAINT UK_NOP_BATCH_TASK_NAME_KEY UNIQUE (TASK_NAME, TASK_KEY)';
  END IF;
END;
/
