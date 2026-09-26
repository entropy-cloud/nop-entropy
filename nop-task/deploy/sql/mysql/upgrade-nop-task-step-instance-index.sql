-- Upgrade: add lookup index IX_TASK_STEP_TASK_ID on nop_task_step_instance
-- (plan 364 [audit 03-01]; ORM source model declares the same <indexes> block)
--
-- WHY: DaoTaskStateStore.findStepEntity queries WHERE TASK_INSTANCE_ID=? AND STEP_PATH=?
-- (4 call sites per persisted step lifecycle); without the index every lookup is a full
-- table scan over the unbounded step-instance history.
--
-- Applies to EXISTING databases created before this change. Fresh installs get the index
-- automatically via DataBaseSchemaInitializer reading the ORM model (<indexes> block);
-- the regenerated _create_nop-task.sql is unchanged (CREATE TABLE does not emit indexes).
--
-- Single-column index by design: STEP_PATH is VARCHAR(2000); under MySQL utf8mb4 a
-- composite (TASK_INSTANCE_ID, STEP_PATH) key would exceed the 3072-byte InnoDB limit.
--
-- RESULTING INDEX must match the live model:
--   IX_TASK_STEP_TASK_ID (TASK_INSTANCE_ID)

CREATE INDEX IX_TASK_STEP_TASK_ID ON nop_task_step_instance (TASK_INSTANCE_ID);
