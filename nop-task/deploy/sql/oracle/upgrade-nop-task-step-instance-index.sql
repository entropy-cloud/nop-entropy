-- Upgrade: add lookup index IX_TASK_STEP_TASK_ID on nop_task_step_instance
-- (plan 364 [audit 03-01]; ORM source model declares the same <indexes> block)
--
-- Applies to EXISTING databases created before this change. Fresh installs get the index
-- automatically via DataBaseSchemaInitializer reading the ORM model (<indexes> block).
--
-- RESULTING INDEX must match the live model:
--   IX_TASK_STEP_TASK_ID (TASK_INSTANCE_ID)

CREATE INDEX IX_TASK_STEP_TASK_ID ON nop_task_step_instance (TASK_INSTANCE_ID);
