# 308 nop-metadata 接口契约补全

> Plan Status: completed
> Last Reviewed: 2026-07-21
> Source: `ai-dev/audits/2026-07-20-1816-open-audit-nop-metadata.md` (AR-01, AR-02, AR-03, NF-05), `ai-dev/audits/2026-07-20-1816-multi-audit-nop-metadata.md` (03-F01, 03-F02, 03-F03, 07-F2, 16-01)

## Purpose

修复 nop-metadata 模块中所有已确认的跨模块 API 契约缺口（P0/P1 级接口未声明方法），使 typed RPC 调用路径完整可用，并通过测试覆盖确保回归防护。

## Current Baseline

- `INopMetaDataProductBiz`（`nop-metadata-dao/.../biz/`）完全为空接口，BizModel 已实现 `linkAsset`/`unlinkAsset`/`getLinkedAssets`（P0）
- `INopMetaQualityResultBiz` 为空接口，BizModel 已实现 `approve`/`reject`（P1）
- `INopMetaDataContractBiz` 声明了 4/6 方法，缺少 `approve`/`reject` 声明（P1）
- `NopMetaQualityRuleBizModel.judgeByRuleId` 是 public 方法，但未在 `INopMetaQualityRuleBiz` 中声明且缺少 `@BizQuery`/`@BizMutation` 注解（P2）
- `TestNopMetaBizInterfaceCompleteness` 覆盖 9 个 I*Biz 接口，但跳过了 `INopMetaDataProductBiz` 和 `INopMetaQualityResultBiz`（P2）
- `NopMetaQualityResultBizModel.approve`/`reject` 零测试覆盖（P2）
- 以上 3 个接口缺口在第 3 轮审计中被声明为"P0-P2 已全部修复"但实际未修复

## Goals

- `INopMetaDataProductBiz` 声明 `linkAsset`/`unlinkAsset`/`getLinkedAssets` 方法签名
- `INopMetaQualityResultBiz` 声明 `approve`/`reject` 方法签名
- `INopMetaDataContractBiz` 补充 `approve`/`reject` 方法签名
- `INopMetaQualityRuleBiz` 声明 `judgeByRuleId` 方法 + 添加 `@BizQuery` 注解
- `TestNopMetaBizInterfaceCompleteness` 扩展覆盖以上所有接口
- 为 `approve`/`reject` 方法编写单元测试（`NopMetaQualityResultBizModel`）
- `./mvnw compile && ./mvnw test -pl nop-metadata -am` 通过

## Non-Goals

- 不涉及 `Map<String, Object>` → DTO 迁移（见 `307-nop-metadata-dto-migration-data-auth.md`）
- 不涉及 xmeta retention 层修复
- 不涉及 error handling 问题（见 `309-nop-metadata-error-handling-fixes.md`）
- 不涉及 ORM 模型约束或死模块清理

## Scope

### In Scope

- 3 个 I*Biz 接口的 missing method 声明（P0/P1）
- `judgeByRuleId` 接口声明 + 注解修复（P2）
- `TestNopMetaBizInterfaceCompleteness` 扩展（P2）
- `NopMetaQualityResultBizModel.approve`/`reject` 测试（P2）

### Out Of Scope

- DTO 返回类型迁移
- xmeta 字段权限覆盖
- ErrorCode / 异常处理改进
- 死模块清理

## Execution Plan

### Phase 1 — 补全 INopMetaDataProductBiz 接口

Status: completed
Targets: `INopMetaDataProductBiz.java`, `NopMetaDataProductBizModel.java`

- Item Types: `Fix`

- [x] 在 `INopMetaDataProductBiz` 中声明 `linkAsset`、`unlinkAsset`、`getLinkedAssets` 方法签名（`@BizMutation`/`@BizQuery` + `@Name` 参数，匹配 BizModel 实现签名）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `INopMetaDataProductBiz` 包含 `linkAsset`/`unlinkAsset`/`getLinkedAssets` 方法声明
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] No owner-doc update required（接口修复是 internal contract compliance，不改变用户可见行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 补全 INopMetaQualityResultBiz 接口

Status: completed
Targets: `INopMetaQualityResultBiz.java`, `NopMetaQualityResultBizModel.java`

- Item Types: `Fix`

- [x] 在 `INopMetaQualityResultBiz` 中声明 `approve`、`reject` 方法签名（匹配 BizModel 实现签名）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `INopMetaQualityResultBiz` 包含 `approve`/`reject` 方法声明
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 补全 INopMetaDataContractBiz 接口

Status: completed
Targets: `INopMetaDataContractBiz.java`, `NopMetaDataContractBizModel.java`

- Item Types: `Fix`

- [x] 在 `INopMetaDataContractBiz` 中声明 `approve`、`reject` 方法签名

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `INopMetaDataContractBiz` 包含 `approve`/`reject` 方法声明
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 补全 judgeByRuleId 接口 + 注解

Status: completed
Targets: `INopMetaQualityRuleBiz.java`, `NopMetaQualityRuleBizModel.java`

- Item Types: `Fix`

- [x] 在 `INopMetaQualityRuleBiz` 中声明 `judgeByRuleId`（`@BizQuery`，返回类型 `Map<String, Object>` 与接口现有方法一致，参数 `@Name("ruleId") String ruleId, IServiceContext context`）
- [x] 修改 `NopMetaQualityRuleBizModel.judgeByRuleId` 签名匹配接口（添加 `IServiceContext context` 参数 + `@BizQuery` 注解；并将返回类型改为 `Map<String, Object>`，内容委托给原 `QualityRuleJudgment` 逻辑并转换为 map，因为 `QualityRuleJudgment` 在 service 模块中，DAO 模块不可引用，而接口位于 DAO 模块）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `INopMetaQualityRuleBiz` 包含 `judgeByRuleId` 方法声明
- [x] `NopMetaQualityRuleBizModel.judgeByRuleId` 带有 `@BizQuery` 注解
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 扩展接口完备性测试 + 新增 approve/reject 测试

Status: completed
Targets: `TestNopMetaBizInterfaceCompleteness.java`, `TestNopMetaQualityResultBizModel.java`

- Item Types: `Fix | Proof`

- [x] 在 `TestNopMetaBizInterfaceCompleteness.testRequiredInterfacesContainCustomMethods()` 中添加 `INopMetaDataProductBiz`（`linkAsset`/`unlinkAsset`/`getLinkedAssets`）和 `INopMetaQualityResultBiz`（`approve`/`reject`）的断言
- [x] 为 `NopMetaQualityResultBizModel.approve`/`reject` 编写单元测试（包含正常审批路径和异常路径）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增接口断言在 `TestNopMetaBizInterfaceCompleteness` 中通过
- [x] `NopMetaQualityResultBizModel.approve`/`reject` 有 focused 测试覆盖
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 3 个 P0/P1 接口缺口（AR-01/02/03）已修复
- [x] `judgeByRuleId` 接口 + 注解已修复（07-F2）
- [x] 接口完备性测试已覆盖所有修复接口（NF-05）
- [x] `approve`/`reject` 测试已存在（16-01）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] No owner-doc update required
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `./mvnw test -pl nop-metadata -am` 通过

## Deferred But Adjudicated

（无 — 所有 in-scope 项均为 Fix 类型，无延期项）

## Non-Blocking Follow-ups

（无 — 所有 in-scope 项在本 plan 内完成）

## Closure

Status Note: Executed by opencode agent; all 5 Phases completed.
Completed: 2026-07-21

Closure Audit Evidence:

- Reviewer / Agent: opencode
- Evidence: All 690 tests pass (0 failures); `./mvnw compile -pl nop-metadata -am` passes; see daily log at `ai-dev/logs/2026/07-21.md`

Follow-up:

- （无）
