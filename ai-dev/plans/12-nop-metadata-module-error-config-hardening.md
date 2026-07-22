# 12 nop-metadata Module Boundary, Error Consistency, and Config Hardening

> Plan Status: active
> Execution Order: 3
> Last Reviewed: 2026-07-22
> Source: `ai-dev/audits/2026-07-21-2039-open-audit-nop-metadata.md` (AR-27, AR-30, AR-31)
> Related: `08-nop-metadata-remaining-api-contract-code-quality.md`

## Purpose

Close three cross-cutting findings: (a) empty `nop-metadata-api` module with contradictory documentation, (b) `TableReferenceExecutor` rethrowing bare `RuntimeException` without ErrorCode wrapping, and (c) `application.yaml` enabling GraphQL schema introspection with a hardcoded JWT encryption key.

## Current Baseline

- `nop-metadata-api/` contains only `pom.xml` — zero Java sources, no beans.xml, no resources. `docs-for-ai/03-modules/nop-metadata.md` describes it as "跨模块 API 接口定义" but the 40 `INopMeta*Biz` interfaces reside in the dao module. Previous audits (01-03, D01-02) reported this unfixed.
- `TableReferenceExecutor.executeOnPlatformConnection()` (lines 103-109) and `executeOnExternalConnection()` (lines 127-134) rethrow bare `RuntimeException` without wrapping in `NopException`/ErrorCode. Framework-level errors in profiling/catalog/quality callbacks show up as raw exceptions instead of structured ErrorCodes.
- `nop-metadata-app/src/main/resources/application.yaml` has `graphql.schema-introspection.enabled: true` and a hardcoded `jwt.enc-key`. No `%prod` profile overrides this. Combined with `nop.debug: true` and `allow-create-default-user: true`, this is a production deployment risk.

## Goals

- Resolve the `nop-metadata-api` empty module: either populate it with the 40 Biz interfaces or remove it and update docs
- Wrap all non-NopException exceptions in `TableReferenceExecutor` with structured ErrorCode
- Add `%prod` config profile disabling GraphQL introspection and document enc-key expectations

## Non-Goals

- **Not** refactoring the 40 Biz interfaces themselves
- **Not** adding a full secrets management solution for JWT keys
- **Not** changing authentication/authorization logic in nop-auth
- **Not** restructuring TableReferenceExecutor into sub-components

## Scope

### In Scope

- Architectural decision on `nop-metadata-api`: either migrate the 40 Biz interfaces from dao to api, or remove the api module from reactor and update docs. Plan-first per audit recommendation.
- `TableReferenceExecutor`: wrap all non-NopException exceptions in `NopMetadataException(ERR_TABLEREF_EXEC_FAILED, e)` on both platform and external connection paths
- `application.yaml`: add `%prod` profile disabling `schema-introspection`, add comment/documentation about JWT enc-key sourcing. Do not remove the dev defaults.

### Out Of Scope

- Refactoring the 40 Biz interface signatures or adding new ones
- Migrating the api module location (only populating or removing it)
- Adding a full `application-prod.yaml` — just `%prod` profile inline
- Changing the JWT key value — only documenting that it should be overridden in production

## Execution Plan

### Phase 1 - Empty api module resolution

Status: planned
Targets: `nop-metadata/nop-metadata-api/`, `docs-for-ai/03-modules/nop-metadata.md`

- Item Types: `Decision | Fix`

- [ ] Make architectural decision: migrate 40 Biz interfaces from dao to api, or remove api module + update docs
- [ ] Option A (migrate): Move `INopMeta*Biz` interfaces from `nop-metadata-dao` to `nop-metadata-api`, update module dependencies, update `pom.xml`
- [ ] Option B (remove): Delete `nop-metadata-api/pom.xml`, remove module from parent POM reactor, update `docs-for-ai/03-modules/nop-metadata.md`
- [ ] Verify `./mvnw compile` passes across all affected modules

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `nop-metadata-api` is either populated with Biz interfaces or removed from reactor
- [ ] `docs-for-ai/03-modules/nop-metadata.md` updated to match reality
- [ ] `docs-for-ai/04-reference/module-map.md` updated if module was removed
- [ ] `./mvnw compile -pl nop-metadata -am` passes
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ErrorCode wrapping in TableReferenceExecutor

Status: planned
Targets: `nop-metadata-service/src/main/java/io/nop/metadata/service/tableref/TableReferenceExecutor.java`

- Item Types: `Fix`

- [ ] In `executeOnPlatformConnection()` catch block: replace bare `RuntimeException` rethrow with `NopMetadataException(ERR_TABLEREF_EXEC_FAILED, e)`
- [ ] Same fix for `executeOnExternalConnection()` catch block
- [ ] Ensure NopException subclasses still propagate naturally (check `e instanceof NopException` before wrapping)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Both connection paths wrap non-NopException exceptions in `NopMetadataException` with `ERR_TABLEREF_EXEC_FAILED`
- [ ] NopException subclasses still propagate without double-wrapping
- [ ] `./mvnw compile -pl nop-metadata-service -am` passes
- [ ] `./mvnw test -pl nop-metadata-service -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Production config hardening

Status: planned
Targets: `nop-metadata-app/src/main/resources/application.yaml`

- Item Types: `Fix`

- [ ] Add `---` `%prod` profile section disabling `graphql.schema-introspection.enabled`
- [ ] Add comment above `jwt.enc-key` documenting that this is a dev-only default and must be overridden in production via `%prod` or environment variable
- [ ] Verify `%prod` profile builds without error

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `%prod` profile exists with `graphql.schema-introspection.enabled: false`
- [ ] JWT enc-key has a comment warning it's a dev-only default
- [ ] `./mvnw compile -pl nop-metadata-app -am` passes
- [ ] No owner-doc update required (config files are self-documenting)
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `nop-metadata-api` no longer contradicts its documentation (either populated or removed)
- [ ] `TableReferenceExecutor` wraps all non-NopException exceptions in structured ErrorCode
- [ ] `application.yaml` has `%prod` profile disabling introspection with documented JWT key sourcing
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Full production configuration profile

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: The `%prod` profile addition addresses the minimal hardening. A full production configuration profile with all settings is a separate infrastructure concern.
- Successor Required: `no`

## Non-Blocking Follow-ups

- Audit other `application.yaml` files across nop-entropy for the same pattern (introspection enabled + hardcoded keys)

## Closure

Status Note: (to be filled on closure)
Completed: (to be filled on closure)

Closure Audit Evidence:

- Reviewer / Agent: (to be filled on closure)
- Evidence: (to be filled on closure)

Follow-up:

- (to be filled on closure)
