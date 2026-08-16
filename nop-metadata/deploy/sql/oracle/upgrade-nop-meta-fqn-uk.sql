-- Upgrade: redundant per-scope FQN unique keys dropped
-- (FQN global-uniqueness adjudication, plan-2026-08-16-0920-1 P2-29)
--
-- Applies to EXISTING NON-TENANT databases created before the P2-29 change.
-- New installs are covered by _create_nop-metadata.sql; tenant deployments are
-- covered by _add_tenant_nop-metadata.sql (regenerated, never hand-edited).
--
-- Rationale: for non-NULL FQN rows the kept global UK (FULLY_QUALIFIED_NAME)
-- logically implies (scope, FULLY_QUALIFIED_NAME) uniqueness, so the per-scope
-- UKs UK_NOP_META_GLOSSARY_TERM_G_FQN / UK_NOP_META_TAG_CLS_FQN were redundant
-- (duplicate index + semantic drift trap). No behavior change: every write
-- rejected before this change is still rejected by the kept global UKs
-- UK_NOP_META_GLOSSARY_TERM_FQN / UK_NOP_META_TAG_FQN.
--
-- No data migration required: dropping constraints cannot fail on existing data.
--
-- Resulting UK shape must match the live model:
--   nop_meta_glossary_term: UK_NOP_META_GLOSSARY_TERM_FQN unique (FULLY_QUALIFIED_NAME)
--   nop_meta_tag:           UK_NOP_META_TAG_FQN unique (FULLY_QUALIFIED_NAME)

alter table nop_meta_glossary_term drop constraint UK_NOP_META_GLOSSARY_TERM_G_FQN;
alter table nop_meta_tag drop constraint UK_NOP_META_TAG_CLS_FQN;
