# 55 ORM XML Icon Rollout And Checker

> Plan Status: completed
> Last Reviewed: 2026-05-28
> Source: User requested repository-wide migration away from model `*.orm.xlsx` codegen inputs, semantic `ext:icon` coverage for every entity, and a checker that detects missing `ext:icon` in `model/*.orm.xml` without touching generated `_app.orm.xml`.
> Related: `ai-dev/plans/53-orm-menu-icon-propagation.md`, `ai-dev/plans/54-orm-menu-icon-rollout-sys-oauth.md`

## Purpose

Convert the remaining live module codegen paths to use `model/*.orm.xml` as the source of truth, complete semantic entity `ext:icon` coverage for the source ORM models owned by those same live modules, and add a repo check that reports missing entity icons from source ORM models without targeting generated downstream ORM outputs.

## Current Baseline

- Plan 53 landed the template chain `entity ext:icon -> generated xmeta ext:icon -> generated _*.action-auth.xml icon`.
- Plan 54 fixed `nop-oauth` by switching `nop-auth/nop-oauth/nop-oauth-codegen/postcompile/gen-orm.xgen` from `nop-auth/nop-oauth/model/nop-oauth.orm.xlsx` to `nop-auth/nop-oauth/model/nop-oauth.orm.xml` and verified generated outputs.
- Live repo scan shows remaining module codegen scripts still reading `model/*.orm.xlsx` in: `nop-ai`, `nop-batch`, `nop-demo/nop-ddd-demo`, `nop-dyn`, `nop-file`, `nop-report`, `nop-rule`, `nop-task`, `nop-tcc`, `nop-wf`, plus the default generator templates under `nop-kernel/nop-codegen` and `nop-ai/nop-ai-coder`.
- Live repo scan also shows many source `model/*.orm.xml` entities still lack `ext:icon`, including migrated live modules such as `nop-ai`, `nop-batch`, `nop-dyn`, `nop-file`, `nop-report`, `nop-rule`, `nop-task`, `nop-tcc`, and `nop-wf`, plus non-migrating source ORM modules like `nop-code`, `nop-job`, and `nop-retry`.
- Generated ORM outputs already exist under downstream `*-dao/src/main/resources/_vfs/**/orm/app.orm.xml` and `*_app.orm.xml`; both are generated outputs and must remain non-edit targets.

## Goals

- Switch remaining live ORM codegen entry points from `model/*.orm.xlsx` to `model/*.orm.xml`.
- Ensure every entity in the owned source `model/*.orm.xml` files of the migrated live modules has a semantic `ext:icon` value.
- Ensure every entity in the already-XML source models `nop-code/model/nop-code.orm.xml`, `nop-job/model/nop-job.orm.xml`, and `nop-retry/model/nop-retry.orm.xml` also has a semantic `ext:icon` value, because the user explicitly requested icon coverage for every entity and these source models are currently missing icons.
- Add a checker/tool that reports entities in source `model/*.orm.xml` missing `ext:icon`.
- Verify the migration with focused `mvn install -DskipTests` evidence and owner-doc/log/plan sync.

## Non-Goals

- Editing generated `_app.orm.xml` or other generated outputs by hand.
- Repository-wide cleanup of every documentation example that mentions historical `orm.xlsx` usage outside the owner docs touched by this plan.
- Frontend runtime icon behavior changes beyond generated resource content.

## Scope

### In Scope

- Live module `*-codegen/postcompile/gen-orm.xgen` files that still read `model/*.orm.xlsx`
- Generator templates that still default new projects to `model/*.orm.xlsx`
- Source `model/*.orm.xml` files for these migrated live modules: `nop-ai`, `nop-batch`, `nop-demo/nop-ddd-demo`, `nop-dyn`, `nop-file`, `nop-report`, `nop-rule`, `nop-task`, `nop-tcc`, `nop-wf`
- Additional already-XML source models that are explicitly owned for icon coverage in this plan: `nop-code/model/nop-code.orm.xml`, `nop-job/model/nop-job.orm.xml`, `nop-retry/model/nop-retry.orm.xml`
- Demo/source models that are explicitly owned in this plan because the repo ships them as editable source examples: `nop-ai/nop-ai-coder/demo/model/app-demo.orm.xml`, `nop-kernel/nop-kernel-cli/demo/model/demo.orm.xml`
- A repo checker under `ai-dev/tools/check-orm-icons.mjs` that validates source ORM icon coverage
- Focused verification commands, daily log, and owner docs required by this migration

### Out Of Scope

- Test fixtures and converter demos under `nop-runner/`, `nop-report` tests, or other intentionally retained sample `*.orm.xlsx` files unless they block the live migration
- Documentation-only examples under `docs/` and `docs-en/` unless owner-doc sync requires a source-of-truth correction
- Non-ORM metadata models that do not participate in this codegen path
- Hand-editing generated auth/page/xmeta outputs except via normal regeneration
- Hand-editing generated `*-dao/.../orm/app.orm.xml` or `*_app.orm.xml` outputs

## Execution Plan

### Phase 1 - Audit And Source-Path Migration

Status: completed
Targets: `*-codegen/postcompile/gen-orm.xgen`, generator templates, affected module `model/*.orm.xml`

- Item Types: `Fix | Decision | Proof`

- [x] Enumerate the live modules whose codegen still reads `model/*.orm.xlsx` and confirm their corresponding `model/*.orm.xml` source files exist.
- [x] Switch those live module `gen-orm.xgen` inputs from `*.orm.xlsx` to `*.orm.xml`.
- [x] Update default codegen templates that still emit `*.orm.xlsx` paths so new modules also use `*.orm.xml`.
- [x] Preserve generated downstream `app.orm.xml` and `_app.orm.xml` files as outputs only; no direct edits to generated ORM outputs.

Exit Criteria:

- [x] Every in-scope live module `gen-orm.xgen` reads `model/*.orm.xml` instead of `model/*.orm.xlsx`.
- [x] Default generator templates for new projects no longer emit `*.orm.xlsx` as the ORM source path.
- [x] **端到端验证**：running the normal Maven generation path for each migrated module set regenerates downstream ORM/meta/web outputs from `model/*.orm.xml`.
- [x] **接线验证**：for each migrated module, the codegen entry script now points to the source `model/*.orm.xml`, and downstream generation still resolves through generated `app.orm.xml` / xmeta / web outputs.
- [x] **无静默跳过**：no in-scope generator remains on `*.orm.xlsx` due to accidental omission; any deferred case is explicitly recorded.
- [x] `docs-for-ai/02-core-guides/model-first-development.md` and/or the smallest owning doc is updated if the documented source-of-truth convention changes.
- [x] `ai-dev/logs/` 对应日期条目已更新.

### Phase 2 - Semantic Icon Coverage

Status: completed
Targets: in-scope source `model/*.orm.xml`

- Item Types: `Fix | Decision | Proof`

- [x] Add semantic `ext:icon` values to every entity in the in-scope source ORM models that currently lack them.
- [x] Keep icon names in kebab-case and align them to entity semantics.
- [x] Reuse existing icon choices where already established to avoid needless churn.

Exit Criteria:

- [x] Every in-scope source ORM entity in owned `model/*.orm.xml` files has an `ext:icon` value.
- [x] Generated xmeta/auth outputs for every migrated live module consume those icon values where applicable.
- [x] **端到端验证**：for every migrated live module that generates admin resources, the path `model/*.orm.xml -> xmeta -> _*.action-auth.xml` is observed with semantic icons instead of fallback values.
- [x] **接线验证**：for every migrated live module, generated xmeta roots include `ext:icon`, and generated auth resources consume those values.
- [x] **无静默跳过**：no owned source entity remains without `ext:icon`; any excluded file/module is explicitly adjudicated.
- [x] Owner docs updated only if the icon convention itself changes; otherwise explicitly `No owner-doc update required`.
- [x] `ai-dev/logs/` 对应日期条目已更新.

### Phase 3 - Missing-Icon Checker

Status: completed
Targets: `ai-dev/tools/check-orm-icons.mjs`, verification commands, docs/logs if needed

- Item Types: `Fix | Proof`

- [x] Add a repo checker at `ai-dev/tools/check-orm-icons.mjs` that scans owned source `model/*.orm.xml` files and reports entities missing `ext:icon`.
- [x] Ensure the checker includes only source model paths matching `**/model/*.orm.xml` and excludes generated `**/orm/app.orm.xml`, `**/orm/_app.orm.xml`, and other non-source locations.
- [x] Encode the intended invocation path and exit behavior directly in the script usage and log evidence.

Exit Criteria:

- [x] A source-controlled checker exists at `ai-dev/tools/check-orm-icons.mjs` and runs locally against the repo.
- [x] The checker reports missing `ext:icon` only from owned source `model/*.orm.xml` files, not generated `app.orm.xml` / `_app.orm.xml` outputs.
- [x] **端到端验证**：running the checker on the post-migration repo produces a repo-observable result consistent with the actual source models.
- [x] **接线验证**：the checker's file-selection logic explicitly targets source `**/model/*.orm.xml` paths and excludes generated ORM outputs.
- [x] **无静默跳过**：missing icons cause a non-zero exit code with explicit file/entity reporting rather than being silently ignored.
- [x] No owner-doc update required beyond invocation notes unless the check becomes a standard repo contract.
- [x] `ai-dev/logs/` 对应日期条目已更新.

## Closure Gates

- [x] All in-scope `gen-orm.xgen` inputs have been migrated from `*.orm.xlsx` to `*.orm.xml`.
- [x] All in-scope source ORM entities have semantic `ext:icon` coverage.
- [x] The missing-icon checker is landed and validated against source models.
- [x] Focused `./mvnw install -pl ... -am -DskipTests -T 1C` verification passes for the touched module sets.
- [x] `./mvnw test -pl ... -am -T 1C` passes for the touched module sets or an explicitly justified equivalent repo-required test baseline is recorded.
- [x] checkstyle / code-style review passes for the touched code and scripts.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` passes after plan/log/doc updates.
- [x] Independent closure audit completed by a separate subagent and recorded in the final report.

## Deferred But Adjudicated

### Retained xlsx fixtures and examples

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: test fixtures, converter demos, and documentation examples outside the live module generation path do not affect the supported source-of-truth workflow once live module codegen and source models are migrated.
- Successor Required: `no`
- Successor Path: n/a

## Closure

Status Note: completed

Closure Audit Evidence:

- Reviewer / Agent: first closure audit (`general` subagent, task id `ses_192920a91ffejaheHdhx1BOAOs`)
- Evidence: `FAIL` because `ai-dev/tools/check-orm-icons.mjs` accepted any `.orm.xml` inside a `model/` subtree rather than only direct `**/model/*.orm.xml` matches; follow-up fix landed by tightening the positive match to direct children of `model/` directories and re-running `node ai-dev/tools/check-orm-icons.mjs` plus `node ai-dev/tools/check-doc-links.mjs --strict`.
- Reviewer / Agent: second independent closure audit (`general` subagent, task id `ses_1928d354fffez8ayQHcHsElLX1`)
- Evidence: `PASS`. Verified live `ai-dev/tools/check-orm-icons.mjs:35-49,52-66,122-128` now matches only direct `**/model/*.orm.xml` source files, still excludes generated outputs, and still fails with explicit non-zero reporting. Repo search found no remaining in-scope `model/*.orm.xlsx` references in live `gen-orm.xgen` entry points or default templates. Re-ran `node ai-dev/tools/check-orm-icons.mjs` (`OK: all source ORM entities have ext:icon (19 files checked)`) and `node ai-dev/tools/check-doc-links.mjs --strict` (`0 errors`). `ai-dev/logs/2026/05-28.md:7-11` records the equivalent `mvn install -pl ... -am -DskipTests -T 1C` verification baseline and the post-fix reruns. All closure gates are satisfied.

Follow-up:

- none
