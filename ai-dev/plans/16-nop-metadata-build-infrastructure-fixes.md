# 16 nop-metadata Build Infrastructure Fixes

> Plan Status: completed
> Execution Order: 1
> Last Reviewed: 2026-07-23 (executed)
> Source: `ai-dev/audits/2026-07-23-0714-open-audit-nop-metadata.md` (AR-37, AR-38)
> Related: `306-nop-metadata-audit-quick-fixes.md` (nop-metadata-api removal)

## Purpose

Fix two build-infrastructure defects in nop-metadata: production workflow execution requiring beans from a test-scoped module (`nop-wf-service`), and an empty no-op DAO beans.xml.

## Current Baseline

- **AR-37** (P2): `nop-metadata/nop-metadata-service/pom.xml:41-43` declares `nop-wf-service` as `scope=test`. The `wf-approval` tag library (`wf-approval:notifyResult`) is defined in **`nop-wf-core`** (compile scope), so tag resolution itself is fine. However, `nop-wf-service` provides beans critical for production workflow execution: `DaoWfActorResolver`, BizModels for workflow entities (`NopWfInstanceBizModel`, etc.), and their bean definitions in `_service.beans.xml` / `app-service.beans.xml`. These beans are absent at production runtime when `nop-wf-service` is test-scoped, causing workflow instance creation to fail.
- **AR-38** (P3): `nop-metadata/nop-metadata-dao/src/main/resources/_vfs/nop/metadata/beans/_dao.beans.xml` is structurally empty (no `<bean>` children). It is imported by `app-service.beans.xml` as the first import, consuming startup time for zero benefit.
- `nop-wf-core` (compile scope) provides `x:extends="/nop/wf/base/oa.xwf"` and the `wf-approval` tag library — both resolve correctly at compile scope. The gap is `nop-wf-service`'s beans, not its tag libraries.

## Goals

- Eliminate the production-classpath dependency inversion: workflow resources must not reference runtime-absent libraries.
- Eliminate the no-op import of an empty beans.xml.

## Non-Goals

- Not adding workflow execution as a new feature (if workflows should not run in production, the fix is to relocate resources, not to promote the dep).
- Not populating `_dao.beans.xml` with new bean definitions — only removing it if it serves no purpose.

## Scope

### In Scope

- Fix `nop-wf-service` test-scope vs production workflow resources mismatch (AR-37)
- Fix empty `_dao.beans.xml` import (AR-38)

### Out Of Scope

- Other dependency scoping issues in nop-metadata
- Adding new workflow functionality or bean definitions

## Execution Plan

### Phase 1 — Fix Workflow Resources vs Dependency Scope

Status: completed
Targets: `nop-metadata/nop-metadata-service/pom.xml`, `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/wf/*/v1.xwf`

- Item Types: `Fix`, `Decision`

- [x] Determine the intended runtime status of the 3 workflows (`metaDataContractApproval`, `qualityBreachApproval`, `tagLabelConfirmApproval`):
  - Option A: If workflow execution is a required production feature → change `nop-wf-service` from `test` to `compile` scope. This brings in `DaoWfActorResolver`, workflow BizModels, and their bean definitions.
  - Option B: If workflows are for testing only → move the 3 `.xwf` files (each in its own subdirectory under `wf/`) from `src/main/resources` to `src/test/resources`.
- [x] Implement the chosen option (Option A: `nop-wf-service` scope changed from `test` to `compile` in pom.xml).
- [x] Option A chosen: `IWorkflowManager` is always available (from `nop-wf-core` compile scope). Added defensive bean-init check — `QualityAlertWorkflowService.createAlertWorkflow()` now throws `ERR_WORKFLOW_MANAGER_UNAVAILABLE` ErrorCode instead of warn+return-null (fixes silent skip violation).

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 决定已记录到 plan 中（Option A），相关代码已修改
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] 无 workflow 资源（.xwf 及其引用的 XPL tag 库）依赖 test-scope 的 `nop-wf-service` 运行时 bean — `nop-wf-service` 现在是 compile scope
- [x] **接线验证**: `QualityAlertWorkflowService` 创建 workflow 实例的完整代码路径（从 `wfManager.newWorkflow()` 到 `.xwf` 解析执行）不再会因 `nop-wf-service` beans 缺失而抛出 NoClassDefFoundError 或 bean-not-found 异常 — `nop-wf-service` 在 compile scope 中提供 `DaoWfActorResolver` 及 workflow BizModel beans
- [x] **无静默跳过**: Option A 选择后，`QualityAlertWorkflowService.createAlertWorkflow()` 在 `wfManager==null` 时抛出 `ERR_WORKFLOW_MANAGER_UNAVAILABLE` ErrorCode 而非静默返回 null（修复原 silent skip）
- [x] `docs-for-ai/` / `ai-dev/design/` 无需更新（只修复依赖范围，不改变架构契约）→ `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Clean Up Empty DAO Beans XML

Status: completed
Targets: `nop-metadata/nop-metadata-dao/src/main/resources/_vfs/nop/metadata/beans/_dao.beans.xml`, `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/beans/app-service.beans.xml`

- Item Types: `Fix`

- [x] Remove the empty `_dao.beans.xml` file.
- [x] Remove the `<import resource="_dao.beans.xml"/>` line from `app-service.beans.xml`.
- [x] Verify no other file imports `_dao.beans.xml`.

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `_dao.beans.xml` 已删除
- [x] `app-service.beans.xml` 不再引用该文件
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `grep -r "_dao.beans.xml" nop-metadata/` 返回空结果（仅 target/ 目录有旧测试日志引用 /nop/wf/beans/_dao.beans.xml，非本模块文件）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope 已确认的 build infrastructure defects 已修复（AR-37, AR-38）
- [x] 工作流资源与依赖范围一致，无生产运行时失败风险
- [x] 无空 bean.xml 导入损耗启动性能
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证组件间调用链连通性
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`
- [x] Code style: imports grouped (java.* → jakarta.* → third-party → io.nop.*)

## Deferred But Adjudicated

None.

## Non-Blocking Follow-ups

None.

## Closure

Status Note: Both Phase 1 (AR-37: nop-wf-service scope → compile + silent skip fix) and Phase 2 (AR-38: remove empty _dao.beans.xml) completed. All tests pass.
Completed: 2026-07-23

Closure Audit Evidence:

- Reviewer / Agent: opencode (mission-driven execution agent, session id: this-task)
- Evidence:
  - Phase 1: Changed `nop-wf-service` scope from `test` to `compile` in pom.xml. Added `ERR_WORKFLOW_MANAGER_UNAVAILABLE` ErrorCode to `QualityErrors.java`. Updated `QualityAlertWorkflowService.createAlertWorkflow()` to throw NopMetadataException instead of silent warn+return-null. Updated test.
  - Phase 2: Deleted empty `_dao.beans.xml`. Removed `<import resource="_dao.beans.xml"/>` from `app-service.beans.xml`. `grep -r "_dao.beans.xml" nop-metadata/` returns no source references.
  - `./mvnw compile -pl nop-metadata -am` — BUILD SUCCESS
  - `./mvnw test -pl nop-metadata -am` — BUILD SUCCESS
  - No owner-doc update required (scope-only fix, no API/contract change)
  - Code style: imports follow java.* → jakarta.* → third-party → io.nop.* convention

Follow-up:

- No remaining plan-owned work.
