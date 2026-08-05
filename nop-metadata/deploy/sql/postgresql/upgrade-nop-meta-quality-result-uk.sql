-- Upgrade: UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE added to nop_meta_quality_result
-- (checkpoint execution idempotency, plan-2026-08-05-1625-2 R4.3 adjudication D2/D3)
--
-- Applies to EXISTING NON-TENANT databases created before the R4.3 change.
-- New installs are covered by _create_nop-metadata.sql; tenant deployments are
-- covered by _add_tenant_nop-metadata.sql (regenerated, never hand-edited).
--
-- NULL semantics (three dialects identical): any NULL column in a composite
-- unique index means the row does NOT participate in uniqueness checks.
--   - Existing rows have CHECKPOINT_ID/RUN_ID all NULL -> no conflict
--     (this holds even for pre-R3.19 zero-UK era duplicates on QUALITY_RULE_ID)
--   - Single-rule execution path (CHECKPOINT_ID/RUN_ID NULL) is unconstrained
--   - Checkpoint execution path (non-NULL) is fully enforced
-- No data migration required (new columns are nullable).
--
-- Resulting UK shape must match the live model:
--   UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE unique (checkpoint_id,run_id,quality_rule_id)

alter table nop_meta_quality_result add constraint UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE unique (checkpoint_id,run_id,quality_rule_id);
