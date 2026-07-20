# 2026-07-20-2000-3 nop-metadata Quality Alert Workflow

> Plan Status: completed
> Last Reviewed: 2026-07-20
> Source: `ai-dev/design/nop-metadata/12-data-contract-and-governance-workflow.md` §3.3.3 + §六 Phase 3 + `ai-dev/design/nop-metadata/06-data-quality-extended.md` + `ai-dev/design/nop-metadata/10-event-model.md` + `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` G3
> Related: `2026-07-17-0027-1-nop-metadata-quality-checkpoint-orchestration.md`（P2-8 质量检查点编排）

## Purpose

在质量规则执行引擎的基础上，为 FAIL + severity=ERROR 的规则结果增加告警工作流：`MetaQualityRuleExecutor` 在 FAIL+ERROR 时直接触发工作流创建 → 数据 Owner 处理（修复或标记误报）→ 自动重验或关闭。

## Current Baseline

- `MetaQualityRuleExecutor.judge()` 已实现：执行规则 SQL/校验 → 返回 PASS/FAIL + 写入 `NopMetaQualityResult`
- `NopMetaQualityResult` 实体列：`qualityResultId`, `qualityRuleId`（FK→NopMetaQualityRule）, `executeTime`, `status`（PASS/FAIL/ERROR/SKIP）, `actualValue`, `expectedValue`, `message`, `details`(json-4000), 审计字段
- `NopMetaQualityResult` **无** `severity` 列 —— `severity` 在 `NopMetaQualityRule` 实体上（propId=7, ext:dict=meta/quality-severity）
- `NopMetaQualityResult` **无** `isFalsePositive` 列 —— 需新增
- `NopMetaQualityResult` **无** `tableId` 列 —— 可通过 `qualityRuleId → NopMetaQualityRule.entityId(entityType=table)` 间接获取
- `NopMetaQualityRule` 实体含 `severity`, `entityType`, `entityId`（挂载表或字段）, `ruleType`, `sqlExpression`, `threshold`, `params`
- nop-wf 平台能力已完备（`IWorkflowManager.newWorkflow()`, `wf-approval.xlib:notifyResult`）
- `nop-metadata-service/pom.xml` 当前无 `nop-wf-core` 依赖
- nop-metadata 无现有事件监听器或 `@NopEventListener` 使用 —— 本 plan 不构建独立事件总线，改为 `MetaQualityRuleExecutor` 直接调用工作流创建服务（同步路径，避免事件基础设施依赖）
- 设计文档 `12-data-contract-and-governance-workflow.md` §3.3.3 定义了质量告警工作流草案

## Goals

- `NopMetaQualityResult` 新增 `isFalsePositive` 字段
- `nop-metadata-service/pom.xml` 新增 `nop-wf-core` 依赖
- 新建 `QualityAlertWorkflowService`（被 `MetaQualityRuleExecutor` 在 FAIL+ERROR 时调用）
- `QualityAlertWorkflowService` 通过 `IWorkflowManager.newWorkflow("qualityBreachApproval", ...)` 创建 nop-wf 工作流实例
- 定义 `qualityBreachApproval/v1.xwf` 工作流（owner-investigate → auto-verify）
- 工作流 `agree("已修复")` 时自动重新执行 `MetaQualityRuleExecutor.judge()` 验证
- 工作流 `disagree("误报")` 时标记 `NopMetaQualityResult.isFalsePositive=true`
- 验证质量告警工作流端到端

## Non-Goals

- 不实现 DataContract 审批流（G1）
- 不实现 TagLabel 治理工作流（G2）
- 不修改存量质量执行逻辑的语义
- 不实现独立事件总线或消息队列
- 不实现告警通知（邮件/IM 推送）
- 不实现告警聚合与抑制

## Scope

### In Scope

- `nop-metadata-service/pom.xml` 新增 `nop-wf-core` 依赖
- `NopMetaQualityResult` ORM 新增 `isFalsePositive` 列（boolFlag, nullable）
- 创建 `_vfs/nop/metadata/wf/` 目录 + `qualityBreachApproval/v1.xwf` 工作流
- 新建 `QualityAlertWorkflowService`（含 `createAlertWorkflow(ruleId, tableId, resultId, severity, message)` 方法）
- `MetaQualityRuleExecutor.judge()` 在 FAIL + severity=ERROR 时调用 `QualityAlertWorkflowService.createAlertWorkflow()`（而非发布事件）
- 工作流 `owner-investigate` 步骤的 agree→re-judge 回调：重新调用 `MetaQualityRuleExecutor.judge(ruleId)`（从 DB 加载 `NopMetaQualityRule` 全参重建执行上下文）
- 工作流 `owner-investigate` 步骤的 disagree→设置 `isFalsePositive=true`
- 新增集成测试：FAIL→工作流创建→agree→re-judge PASS→关闭  +  disagree→falsePositive→关闭
- `12-data-contract-and-governance-workflow.md` 更新 Phase 3 为已实现

### Out Of Scope

- 告警通知（邮件/IM/Webhook）
- 告警聚合和抑制
- 质量评分触发告警
- 历史告警的批量重处理
- 独立事件总线

## Execution Plan

### Phase 1 - 依赖 + ORM 字段 + 工作流定义

Status: completed
Targets: `nop-metadata-service/pom.xml` + `nop-metadata/model/nop-metadata.orm.xml` + `qualityBreachApproval/v1.xwf`

- Item Types: `Fix`

- [x] `nop-metadata-service/pom.xml` 新增 `nop-wf-core` 依赖（scope: compile）
- [x] `NopMetaQualityResult` 追加 `isFalsePositive` 列（`byte` -> `domain=boolFlag`, nullable, propId 递增）
- [x] 创建 `nop-metadata-service/src/main/resources/_vfs/nop/metadata/wf/` 目录
- [x] 创建 `qualityBreachApproval/v1.xwf`：
  - steps: start → owner-investigate（actor: 规则表 Owner） → verify → end
  - `owner-investigate` 步骤:
    - `on agree("已修复")`: 通过 `<c:script>` 调用 BizModel 的 re-judge 逻辑 → PASS 则 verify 自动通过 → end；FAIL 则退回 owner-investigate（记录 "验证未通过"）
    - `on disagree("误报")`: 通过 `<wf-approval:notifyResult bizObj="NopMetaQualityResult" approved="false"/>` 触发 BizModel 的 `reject()` action → BizModel 设置 `isFalsePositive=true` → end
    - `on end`（仅 agree 已修复且 re-judge PASS 时触发）:
      - no explicit `notifyResult` needed because re-judge PASS skips the notify path
- [x] 运行 codegen + `./mvnw compile -pl nop-metadata -am` 编译通过

Exit Criteria:

- [x] `nop-wf-core` 依赖在 `nop-metadata-service/pom.xml` 中存在
- [x] `NopMetaQualityResult.isFalsePositive` 列在 ORM XML 中存在（boolFlag, nullable）
- [x] `qualityBreachApproval/v1.xwf` 文件存在且 XDef 语法正确
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] **无静默跳过**：新增 `isFalsePositive` 列 nullable，不影响存量数据
- [x] No owner-doc update required（ORM schema 变更已在 design doc 中规划）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - WorkflowService + MetaQualityRuleExecutor 集成

Status: completed
Targets: `nop-metadata-service/.../quality/QualityAlertWorkflowService.java` + `MetaQualityRuleExecutor.java` + `NopMetaQualityResultBizModel.java`

- Item Types: `Fix | Proof`

- [x] 新建 `QualityAlertWorkflowService`（`@Service` bean）：
  - `createAlertWorkflow(NopMetaQualityResult result)`:
    1. 通过 `qualityRuleId` 加载 `NopMetaQualityRule`，读取 `severity`、`entityId`（tableId）
    2. 调用 `IWorkflowManager.newWorkflow("qualityBreachApproval", bizObjName="NopMetaQualityResult", bizObjId=resultId, vars={ruleId, tableId, severity, message})`
- [x] `MetaQualityRuleExecutor.judge()` 返回 FAIL 时检查 `severity`：若为 ERROR 则调用 `QualityAlertWorkflowService.createAlertWorkflow(result)`；若为非 ERROR（WARNING/INFO）则不触发
- [x] `NopMetaQualityResultBizModel` override `approve` action：`super.approve()` 后执行 re-judge 逻辑（同意"已修复"场景：重新加载 `qualityRuleId` 对应的 `NopMetaQualityRule`，调用 `MetaQualityRuleExecutor.judge(ruleId, ...)` 重建上下文，若 PASS 则更新 `result.status=PASS`；若 FAIL 则更新 `result.status=FAIL` 并记录"验证未通过"）
  - ⚠️ `approve()` 中不检查 `approved` 参数 —— `notifyResult` 的 `approved` 仅决定路由到 `approve()` 还是 `reject()`，不传递给方法内部
- [x] `NopMetaQualityResultBizModel` override `reject` action：`super.reject()` 后设置 `result.isFalsePositive=true`，记录驳回理由至 `result.remark`（对应 disagree/"误报" 场景）
- [x] `MetaQualityRuleExecutor` 新增 `judgeByRuleId(String ruleId)` 公有方法：从 DB 加载 `NopMetaQualityRule` 全字段，重建 `(Connection, TableReference, schemaPattern, ruleType, entityType, paramsJson, sqlExpression, threshold, productName)` 上下文，调用既有 `judge(...)` 内部逻辑
- [x] 新增集成测试（至少 2 类）：
  - (a) 质量规则 FAIL+ERROR → `QualityAlertWorkflowService` 创建 `qualityBreachApproval` 工作流实例 → agree → `judgeByRuleId` re-judge PASS → 工作流关闭
  - (b) 质量规则 FAIL+ERROR → 工作流创建 → disagree → `isFalsePositive=true` → 工作流关闭

Exit Criteria:

- [x] `QualityAlertWorkflowService.createAlertWorkflow()` 在 FAIL+ERROR 时创建 nop-wf 工作流实例
- [x] `judge()` FAIL + severity=ERROR 路径触发了工作流创建（通过 mock `IWorkflowManager` 验证）
- [x] `judge()` FAIL + severity=WARNING 不触发工作流
- [x] `judge()` PASS 不触发工作流
- [x] `judgeByRuleId(String ruleId)` 从 DB 加载 rule 全参并正确执行 judge 逻辑
- [x] `NopMetaQualityResultBizModel.approve` override 正确执行 re-judge 逻辑（agree 场景）
- [x] `NopMetaQualityResultBizModel.reject` override 正确设置 `isFalsePositive=true`（disagree 场景）
- [x] **端到端验证**：质量规则 FAIL+ERROR → 工作流创建 → agree → re-judge PASS → 流程关闭 完整路径
- [x] **端到端验证**：质量规则 FAIL+ERROR → 工作流创建 → disagree → `isFalsePositive=true` → 流程关闭
- [x] **接线验证**：`judge()` 返回 FAIL+ERROR 时确实调用了 `QualityAlertWorkflowService`（非空壳组件，通过 mock/spy 验证）
- [x] **无静默跳过**：工作流创建失败、re-judge 失败、标记 falsePositive 失败各路径显式抛异常
- [x] `ai-dev/design/nop-metadata/12-data-contract-and-governance-workflow.md` 更新 Phase 3 为已实现
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `nop-wf-core` 依赖添加完毕
- [x] `NopMetaQualityResult.isFalsePositive` 列在 ORM 中存在
- [x] `qualityBreachApproval/v1.xwf` 工作流定义完成
- [x] `QualityAlertWorkflowService` 实现正确（FAIL+ERROR 时创建工作流）
- [x] `judgeByRuleId(String)` 从 DB 加载 rule 全参并正确执行
- [x] BizModel `approve` override 正确执行 re-judge
- [x] BizModel `reject` override 正确设置 `isFalsePositive=true`
- [x] 端到端集成测试覆盖 agree→re-judge 和 disagree→falsePositive 两条路径
- [x] `./mvnw test -pl nop-metadata -am` 通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] `12-data-contract-and-governance-workflow.md` 更新 Phase 3 为已实现
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 `judge()` FAIL+ERROR 路径在运行时确实调用了 `QualityAlertWorkflowService`，BizModel `approve` override 正确驱动 re-judge、`reject` override 正确标记 falsePositive，无空壳组件
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

### 告警通知（邮件/IM/Webhook）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 工作流本身已完整（创建→处理→关闭），通知属于产品层增强。nop-wf 工作项可通过外部轮询获取，不阻塞告警处理流的核心语义。
- Successor Required: `no`

### 独立事件总线

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 `MetaQualityRuleExecutor` 直接调用 `QualityAlertWorkflowService` 是同步路径，未来若需要异步解耦（多个 listener 订阅同一事件）可引入事件总线。首版直接调用更简单且功能等价。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 评估 `judgeByRuleId` 的并发安全（同一 rule 同时触发多个告警工作流）
- 评估是否需要 `NopMetaQualityResult` 的 `tableId` 冗余列（当前通过 qualityRule→entityId 间接获取）

## Closure

Status Note: Phase 1 + Phase 2 all items ticked; 664 tests pass; roadmap G3 done; design doc Phase 3 updated; closure audit by independent subagent completed — all checks pass.
Completed: 2026-07-21

Closure Audit Evidence:

- Reviewer / Agent: AI (opencode mission-driver, closure-audit subagent)
- Audit Session: `MISSION_DRIVER closure audit - 2026-07-21`
- Evidence:
  - Phase 1 Exit Criteria: ALL [x] — PASS
    - `nop-wf-core` dependency in `nop-metadata-service/pom.xml`: PASS
    - `NopMetaQualityResult.isFalsePositive` column in ORM XML (boolFlag, nullable): PASS
    - `qualityBreachApproval/v1.xwf` exists and XDef syntax correct: PASS
    - `./mvnw compile -pl nop-metadata -am` passes: PASS
    - No silent-no-op: new column nullable, no impact on existing data: PASS
  - Phase 2 Exit Criteria: ALL [x] — PASS
    - `QualityAlertWorkflowService.createAlertWorkflow()` creates nop-wf instance on FAIL+ERROR: PASS
    - `judge()` FAIL+ERROR triggers workflow creation (mock `IWorkflowManager`): PASS
    - `judge()` FAIL+WARNING does NOT trigger workflow: PASS
    - `judge()` PASS does NOT trigger workflow: PASS
    - `judgeByRuleId(String)` loads rule from DB and executes correctly: PASS
    - `NopMetaQualityResultBizModel.approve` override executes re-judge: PASS
    - `NopMetaQualityResultBizModel.reject` override sets `isFalsePositive=true`: PASS
    - E2E: FAIL+ERROR → workflow → agree → re-judge PASS → close: PASS
    - E2E: FAIL+ERROR → workflow → disagree → falsePositive → close: PASS
    - Wiring verification: `judge()` FAIL+ERROR calls `QualityAlertWorkflowService` (mock/spy): PASS
    - No silent-no-op: workflow create failure, re-judge failure, falsePositive marking failure all throw exceptions: PASS
    - `12-data-contract-and-governance-workflow.md` Phase 3 updated: PASS
    - `ai-dev/logs/` updated: PASS
  - Closure Gates: ALL [x] — PASS (all items ticked after this audit)
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit code 0: PASS (verified — exit code 0, plan checklist complete)
  - Anti-Hollow check result: `judge()` FAIL+ERROR → `QualityAlertWorkflowService.createAlertWorkflow()` call chain traced; BizModel `approve` → re-judge → status update traced; BizModel `reject` → `isFalsePositive=true` traced; no empty method bodies, no silent catch blocks, no placeholder implementations found. The entire call chain from `MetaQualityRuleExecutor.judge()` through `QualityAlertWorkflowService` through nop-wf back to BizModel actions is connected and each node has meaningful logic. PASS
  - Deferred items classification check: "告警通知（邮件/IM/Webhook)" = `out-of-scope improvement` — legitimately out of scope; "独立事件总线" = `optimization candidate` — legitimately deferred optimization. No in-scope live defect or contract drift downgraded to deferred. PASS

Follow-up:

- no remaining plan-owned work
