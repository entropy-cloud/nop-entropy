# 17 nop-metadata BizModel Compliance & Data Auth Remediation

> Plan Status: active
> Execution Order: 2
> Last Reviewed: 2026-07-23
> Source:
>   - `ai-dev/audits/2026-07-23-0714-multi-audit-nop-metadata/07-bizmodel-conformance.md` (维度07-001~008)
>   - `ai-dev/audits/2026-07-23-0714-open-audit-nop-metadata.md` (AR-25, AR-30)
> Related: `308-nop-metadata-interface-contract-gaps.md`, `307-nop-metadata-dto-migration-data-auth.md`

## Purpose

Resolve all confirmed BizModel conformance violations and data-authorization bypass patterns in nop-metadata, eliminating security-relevant gaps and aligning with Nop platform service-layer conventions.

## Current Baseline

- **维度07-001** (P2): `NopMetaSearchBizModel` has `@BizModel("NopMetaSearch")` but no corresponding ORM entity, no `*.xmeta` file, and does not extend `CrudBizModel` — a "pseudo BizModel" violating the `@BizModel ↔ entity ↔ xmeta` contract.
- **维度07-002** (P2): `NopMetaDataContractBizModel.approve()` and `reject()` contain explicit `txn().runInTransaction(...)` wrappers inside `@BizMutation` methods, creating redundant nested transactions.
- **维度07-003** (P2): ~20 methods across 8 BizModel files use `dao().getEntityById(id)` + manual null check instead of `requireEntity(id, actionName, context)`, bypassing `CrudBizModel`'s built-in data-authorization pipeline.
- **维度07-004** (P2): `NopMetaTableBizModel.queryJoinData()` / `queryAggregation()` return `List<Map<String, Object>>`, losing GraphQL type safety.
- **维度07-005** (P2): `INopMetaDataContractBiz` interface missing `checkContractReadOnly` declaration (method is `@BizQuery` and public in the BizModel).
- **维度07-006** (P2): `INopMetaTagLabelBiz` interface missing `propagateTags` / `suggestTags` declarations.
- **维度07-007** (P3): `NopMetaLineageEdgeBizModel.recordLineage` uses `dao().saveEntity(entity)` directly, bypassing `CrudBizModel.save()`.
- **维度07-008** (P3): `activateContract`/`deprecateContract`/`retireContract` remain `@Deprecated` in `NopMetaDataContractBizModel` with no removal timeline.
- **AR-25** (P2): N+1 upsert pattern in lineage edge — confirmed in all 3 upsert methods of `NopMetaLineageEdgeBizModel`.
- **AR-30** (P3): `RuntimeException` pass-through in multiple BizModel methods — exceptions are not wrapped into `NopException` with proper ErrorCode.

## Goals

- Eliminate all data-authorization bypass patterns — no BizModel method should use `dao().getEntityById()` instead of `requireEntity()`.
- Align all `@BizModel` classes with the entity+xmeta convention.
- Complete all missing I*Biz interface declarations.
- Remove redundant transaction wrappers.
- Replace `List<Map<String, Object>>` with typed DTOs.
- Eliminate N+1 upsert in lineage edge operations.
- Replace bare `RuntimeException` usages with `NopException` + ErrorCode.
- Every code change must have corresponding test coverage.

## Non-Goals

- Not restructuring BizModel inheritance hierarchy (e.g., converting to CrudBizModel subclasses is out of scope if functional equivalence is maintained).
- Not migrating `Map<String, Object>` return types beyond the two identified methods in `NopMetaTableBizModel`.
- Not adding new BizModel methods — only fixing existing ones.
- Not rewriting `recordLineage` batch processing logic — only fixing the persistence bypass.

## Scope

### In Scope

- `dao().getEntityById()` → `requireEntity()` migration in all ~20 identified methods (8 BizModel files)
- `NopMetaSearchBizModel` compliance: either add entity+xmeta or convert to utility/service class
- Redundant `txn().runInTransaction()` removal in `NopMetaDataContractBizModel`
- Interface declarations for `checkContractReadOnly`, `propagateTags`, `suggestTags` in I*Biz interfaces
- `queryJoinData`/`queryAggregation` return type migration from `List<Map<String, Object>>` to typed DTO
- `dao().saveEntity()` → proper `CrudBizModel` persistence path in `recordLineage`
- `@Deprecated` method removal in `NopMetaDataContractBizModel`
- N+1 upsert fix in `NopMetaLineageEdgeBizModel`
- `RuntimeException` → `NopException` migration in identified BizModel methods
- Focused tests for each fix

### Out Of Scope

- General DTO migration across the entire module (see `307-nop-metadata-dto-migration-data-auth.md`)
- Error handling reform outside BizModel methods (see `309-nop-metadata-error-handling-fixes.md`)
- ORM model or xmeta changes (see `18-nop-metadata-orm-model-polish.md`)

## Execution Plan

### Phase 1 — Data Auth Bypass Fix (维度07-003)

Status: planned
Targets: 8 BizModel files in `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/`

- Item Types: `Fix`

- [ ] Replace `dao().getEntityById(id)` + null check + manual `throw` with `requireEntity(id, actionName, context)` in:
  - `NopMetaDataContractBizModel.java` (lines 44, 76, 102-106, 112-116, 122-126, 133)
  - `NopMetaTableBizModel.java` (lines 127, 187, 211, 241, 268)
  - `NopMetaQualityRuleBizModel.java` (lines 133, 200, 377)
  - `NopMetaModuleBizModel.java` (lines 386, 442)
  - `NopMetaReconciliationConfigBizModel.java` (lines 99-100, 107-108)
- [ ] For methods that currently call `checkDataAuth` independently, verify `requireEntity` preserves or improves the auth check
- [ ] Add/update unit tests to verify that data authorization is invoked for each mutated method

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 所有 ~20 处 `dao().getEntityById()` 已替换为 `requireEntity()`
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata -am` 通过
- [ ] **接线验证**: 至少一个测试验证 `requireEntity` 的 data-auth 回调在修复后的方法中被调用
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Interface Completeness (维度07-005, 维度07-006)

Status: planned
Targets: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaDataContractBiz.java`, `INopMetaTagLabelBiz.java`

- Item Types: `Fix`

- [ ] Add `checkContractReadOnly` method signature to `INopMetaDataContractBiz`
- [ ] Add `propagateTags` and `suggestTags` method signatures to `INopMetaTagLabelBiz`
- [ ] Extend `TestNopMetaBizInterfaceCompleteness` to cover these interfaces (following pattern from `308-nop-metadata-interface-contract-gaps.md`)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 所有 3 个缺失方法声明已补全
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `TestNopMetaBizInterfaceCompleteness` 覆盖这些接口
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — BizModel Pattern Fixes (维度07-001, 维度07-002, 维度07-004, 维度07-007, 维度07-008, AR-30)

Status: planned
Targets: Multiple BizModel files

- Item Types: `Fix`, `Decision`, `Proof`

- [ ] **维度07-001**: Decide on `NopMetaSearchBizModel` approach:
  - Option A: Add a `NopMetaSearch` xmeta + entity (if search aggregation is a first-class domain concept)
  - Option B: Convert methods to static utilities or move them into an existing BizModel (if search is just a helper)
  - Implement chosen option
- [ ] **维度07-002**: Remove `txn().runInTransaction(...)` wrappers from `approve()` and `reject()` in `NopMetaDataContractBizModel`
- [ ] **维度07-004**: Replace `List<Map<String, Object>>` with typed `@DataBean` DTO classes in `NopMetaTableBizModel.queryJoinData()` and `queryAggregation()`
- [ ] **维度07-007**: Replace `dao().saveEntity(entity)` with `CrudBizModel` persistence path in `NopMetaLineageEdgeBizModel.recordLineage()`
- [ ] **维度07-008**: Remove `@Deprecated` methods (`activateContract`, `deprecateContract`, `retireContract`) from `NopMetaDataContractBizModel` and their interface declarations
- [ ] **AR-30**: Identify all BizModel methods throwing `RuntimeException` directly → replace with `NopException` + appropriate ErrorCode
- [ ] Write focused tests for each change

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `NopMetaSearchBizModel` 已合规（实体+xmeta 已添加 或 已重构为工具类）
- [ ] `txn().runInTransaction` 已从所有 `@BizMutation` 方法中移除
- [ ] `queryJoinData` / `queryAggregation` 返回类型已迁移为 typed DTO
- [ ] `recordLineage` 不再使用裸 `dao().saveEntity()`
- [ ] 所有 `@Deprecated` 方法已移除
- [ ] 所有 BizModel 方法中的裸 `RuntimeException` 已替换为 `NopException`
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata -am` 通过
- [ ] **端到端验证**: `NopMetaTableBizModel.queryJoinData` 从 GraphQL 入口到返回 typed DTO 的完整路径已验证
- [ ] **无静默跳过**: 所有缺失功能/异常场景抛 `NopException` 而非静默返回
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — N+1 Lineage Edge Upsert Fix (AR-25)

Status: planned
Targets: `NopMetaLineageEdgeBizModel.java`

- Item Types: `Fix`

- [ ] Analyze the 3 upsert methods in `NopMetaLineageEdgeBizModel` to understand the N+1 pattern
- [ ] Refactor to batch the upsert operations (e.g., collect all edges, batch-save in one transaction)
- [ ] Add focused test verifying that upsert operations issue no more than N+0 queries for N edges (or a reasonable bound)

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] N+1 pattern in lineage edge upsert is eliminated
- [ ] `./mvnw compile -pl nop-metadata -am` 通过
- [ ] `./mvnw test -pl nop-metadata -am` 通过
- [ ] **端到端验证**: upsert 性能测试确认批量化减少查询次数
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope BizModel 合规问题已修复（维度07-001~008）
- [ ] 所有 data-auth bypass 模式已消除
- [ ] N+1 lineage edge upsert 已修复
- [ ] 裸 `RuntimeException` pass-through 已消除
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**: closure audit 已验证组件间调用链在运行时确实连通
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

- 其他 BizModel 中可能隐伏的 data-auth bypass（本次只修复已确认的 ~20 处）；未来审计可扩展覆盖

## Closure

Status Note:
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- No remaining plan-owned work.
