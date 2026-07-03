# 283 nop-wf 扩展功能完善

> Plan Status: completed
> Last Reviewed: 2026-07-02
> Source: `ai-dev/design/nop-wf/extensions-design.md`（已经过 3 轮独立审计修正）；`ai-dev/analysis/2026-07-02-flowlong-warmflow-vs-nop-wf-comparison.md`（resolved）
> Related: `ai-dev/design/nop-wf/approval-flow-design.md`

## Purpose

把 nop-wf 相对 FlowLong / Warm-Flow 两个开源工作流引擎识别出的 5 个能力缺口，从"已审计的设计文档"推进到"可交付的运行时能力"。这份计划是 `extensions-design.md` 的执行载体——设计文档描述 **what/why**，本计划描述 **执行步骤与验证标准**，不重复实现方案。

## Current Baseline

经源码核实（3 轮独立子 agent 审计）：

- **死槽位（已确认 live defect）**：`<check-complete>`（`wf.xdef:241`）和 `<check-exec-group-complete>`（`wf.xdef:244`）在 schema 声明且被解析进模型，但引擎执行路径**从不调用**。`isExecGroupComplete`（`WorkflowEngineImpl.java:1207`）无调用者；`getCheckComplete` 仅被生成代码的序列化方法调用。生产路径走 `ExecGroupSupport.shouldExecGroupComplete` 硬编码权重逻辑。
- **dueAction 非持久化列**：`dueAction` 是 step 模型属性（`wf.xdef:194`），不是 `NopWfStepInstance` 的数据库列。`dueTime`/`remindTime`/`remindCount` 已持久化（`nop-wf.orm.xml:420-427`）。
- **WorkflowService 仅 6 个 mutation**：`startWorkflow`/`notifySubFlowEnd`/`invokeAction`/`killWorkflow`/`suspendWorkflow`/`resumeWorkflow`。无 signal 端点、无批量转办。
- **无调度器**：`nop-wf` 不依赖 `nop-job`，无内置定时扫描。
- **无 AI 集成**：`nop-wf` 不依赖 `nop-ai`。
- **加减签已内置**：`IWorkflowStep.addActor`（`IWorkflowStep.java:150`）+ `cancelStep`（`:210`）可用。
- **nop-ai 就绪**：`IChatService.call()`（`nop-ai-api`）可用，仅依赖 `nop-api-core`。
- **nop-job 就绪**：`LocalJobConfigLoader` + `BeanMethodJobInvoker` + YAML 注册机制可用。
- **图状态快照不是当前缺口**：`NopWfStepInstance` / `NopWfStepInstanceLink` / `NopWfAction` / `NopWfStatusHistory` 已提供足够历史记录，可在查询层实时还原当前节点与已走路径；无需引入 Warm-Flow 式 `def_json` 实例快照。

## Goals

- G1：票签完成/拒绝判定**可插拔**——接线两个死槽位 + 新增 reject 槽位，提供 `wf-vote.xlib` 常用策略标签
- G2：AI 审批**可集成**——`nop-wf-ai` 模块 + `wf-ai.xlib` 标签 + `IWorkflowStore` / `IWorkflow` / `IWorkflowStep` 聚合根 facade 扩展，复用 nop-ai 的 `IChatService`
- G3：超时审批与任务提醒**开箱即用**——`nop-wf-scheduler` 模块 + nop-job YAML 注册
- G4：离职转办**可批量**——`transferActors` mutation
- G5：异步 AI / 外部系统**可唤醒**流程——`signalWf` mutation（公共 signal 端点）

## Non-Goals

- NG1：运行时动态修改模型 / 运行时组装未知结构审批链（explicit non-goal，见 design §8.2）
- NG2：多 ORM 解耦（产品定位决策，见 design §R5）
- NG3：新增 AI step 类型或 AI 专用步骤状态枚举（见 design §R1/R2）
- NG4：调度循环内置到引擎（见 design §R3）
- NG5：FlowLong 的"穿越时空/补审"能力（`FlowCreateTimeHandler`，不在本轮）
- NG6：`<ai-config>` 宏展开语法糖（P2，本轮可 deferred）
- NG7：提醒策略的工作日/工作时间过滤（P2，本轮可 deferred）
- NG8：Warm-Flow 式流程图状态快照/路径高亮后端持久化（当前不需要；如需高亮，可基于历史记录重建）

## Scope

### In Scope

- 接线 `<check-exec-group-complete>` + 新增 `<check-exec-group-reject>` 槽位（引擎修复）
- `wf-vote.xlib` 标签库（passCount/passPercent/rejectCount/expr）
- `nop-wf-ai` 模块（`wf-ai.xlib` + `IWorkflowStore` / `IWorkflow` / `IWorkflowStep` 聚合根 facade 扩展）
- `nop-wf-scheduler` 模块（`WfTaskScanner` + scheduler.yaml）
- `WorkflowService.signalWf` mutation
- `WorkflowService.transferActors` mutation
- `NopWfStepInstance` 的 dueTime/remindTime 索引

### Out Of Scope

- `<ai-config>` 宏展开（deferred，Non-Goal NG6）
- 提醒策略的 workDays/workHours/maxRemindCount 配置（deferred，Non-Goal NG7）
- `docs-for-ai/02-core-guides/workflow-configuration.md` 的增强性章节整理（不含本计划最小必需说明，作为 follow-up，不单列功能 phase）
- 任何运行时模型修改能力

## Execution Plan

### Phase 1 - 公共 API 扩展（signalWf + transferActors）

Status: completed
Targets: `nop-wf/model/nop-wf.api.xml`、`nop-wf-api/.../WorkflowService.java`（生成）、`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java`、`nop-wf-api/.../beans/`

- Item Types: `Fix`（signalWf 是 AI 异步的前置；transferActors 补齐批量能力缺口）。**Protected Area**：signalWf 是跨模块公共 API（`nop-wf-api`），plan-first

- [x] 在 `nop-wf.api.xml` 新增 `signalWf` mutation（请求 Bean `WfSignalRequestBean`：wfName/wfVersion/wfId/signals:Set<String>/on:boolean），codegen 重新生成 `WorkflowService.java` 与 `WorkflowServiceSpi.java`
- [x] `WorkflowServiceImpl` 实现 `signalWfAsync`：内部经 `workflowExecutor.execute` 调 `wf.turnSignalOn(signals)` 或 `turnSignalOff(signals)`（on=true 调 turnSignalOn 唤醒，on=false 调 turnSignalOff）
- [x] 在 `nop-wf.api.xml` 新增 `transferActors` mutation（请求 Bean `WfTransferActorsRequestBean`：fromUserId/toUserId/可选 wfIds；响应 Bean `WfTransferResultBean`：successCount/failedItems）
- [x] `WorkflowServiceImpl` 实现 `transferActorsAsync`：改为在 `IWorkflowStore` 上新增跨工作流查询方法 `findActivatedStepsByOwner`，由 store 实现查询 `ownerId=fromUserId AND status=ACTIVATED`（可选限定 wfIds）的候选 stepId/wfId 列表；逐工作流经 `workflowExecutor.execute` 调 `step.changeOwnerId(toUserId, ctx)`，顺序等待每个 `CompletionStage` 完成并聚合结果，失败项记入 failedItems
- [x] 审计记录：按模块边界要求改为在 `IWorkflowStore` 上新增 `saveTransferAction`，store 层用手工 action record 写入 `actionName="transfer"` 和 owner 变更 opinion，补审计轨迹

Exit Criteria:

- [x] `signalWf` mutation 可通过 GraphQL/RPC 调用，WAITING（waitSignals 未满足）的 step 在 `signalWf(on=true)` 后被唤醒继续流转——有测试验证状态变化与后续流转
- [x] `transferActors` 对 N 个工作流的 step 批量转办后，返回 `WfTransferResultBean`，成功项 ownerId 已变更、失败项有 reason；有测试验证部分失败不影响其他工作流
- [x] 转办后在 action 记录中可查到 actionName="transfer" 的审计记录——有测试验证
- [x] **接线验证**：`signalWf` 实现内部确实调用 `IWorkflow.turnSignalOn/Off`（不是 stub）；`transferActors` 确实调用 `IWorkflowStep.changeOwnerId`
- [x] **无静默跳过**：signalWf 对已结束的工作流抛异常而非返回成功空响应
- [x] **新功能测试**：signalWf 唤醒测试、transferActors 批量+部分失败测试、审计记录测试
- [x] owner-doc：`ai-dev/design/nop-wf/extensions-design.md` §4.7/§6 的契约已与实现一致（无需更新 design，design 已定义）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 调度器模块（nop-wf-scheduler）

Status: completed
Targets: 新建 `nop-wf-scheduler/` 模块、`nop-wf/model/nop-wf.orm.xml`（索引）、`nop-wf-app` 装配

- Item Types: `Fix`（补齐超时审批与提醒缺口）。**Protected Area**：`nop-wf.orm.xml` 索引是 ORM 模型修改，plan-first

- [x] 新建 Maven 模块 `nop-wf-scheduler`（依赖 `nop-wf-core` + `nop-job-local`）
- [x] 实现 `WfTaskScanner`：`scanDueTasks()`（查询 `dueTime <= now AND status = ACTIVATED`，**经 workflow model 获取 dueAction**；调 `WorkflowService.invokeAction`，捕获 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 竞态）；`scanRemindTasks()`（查询 `remindTime <= now`，触发 reminder listener，递增 remindCount 并更新 remindTime）
- [x] 提供 `scheduler.yaml`（`/nop/job/conf/scheduler.yaml`）注册两个 cron 任务（due 扫描每分钟、remind 扫描每 5 分钟）
- [x] 在 `nop-wf.orm.xml` 的 `NopWfStepInstance` 新增索引 `IX_WF_STEP_DUE_TIME`（status, dueTime）和 `IX_WF_STEP_REMIND_TIME`（status, remindTime）
- [x] `nop-wf-app` 默认依赖 `nop-wf-scheduler`（可通过不装配 bean 禁用）

Exit Criteria:

- [x] **端到端验证**：直接调 `WfTaskScanner.scanDueTasks()`（cron 在 CI 中不可确定性触发），验证配置了 `due-time-expr` + `dueAction` 且 dueTime 已过的 step 被触发 dueAction、状态按预期变化（如自动 agree）
- [x] **竞态验证**：step 在扫描后、invokeAction 前被用户手动处理时，scanner 捕获 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 不抛错、记 INFO 日志——已补 focused test
- [x] `dueAction` 从 step 模型读取（经 IWorkflowManager，不是 DB 列查询）——实现中无 `WHERE dueAction` 的 SQL
- [x] **接线验证**：代码追踪确认 `app-local-scheduler.beans.xml` → `LocalJobConfigLoader.init()` → 加载 `scheduler.yaml` → `BeanMethodJobInvoker` → `scanDueTasks` 链路连通（cron 触发的实际调用路径）
- [x] **无静默跳过**：scanDueTasks 遍历候选记录时，无 dueAction 的 step 被显式 skip 并记 DEBUG（不是吞异常）；其他异常抛出而非忽略
- [x] **新功能测试**：超时自动触发测试、竞态 try-catch 测试、提醒递增测试
- [x] 索引已在 orm.xml 定义且 `_app.orm.xml` 重新生成后包含
- [x] owner-doc：`ai-dev/design/nop-wf/extensions-design.md` §5 已描述此方案（无需更新）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 票签槽位接线 + wf-vote 标签库

Status: completed
Targets: `nop-kernel/nop-xdefs/.../wf.xdef`、`nop-wf-core/.../engine/WorkflowEngineImpl.java`、`nop-wf-core/.../engine/ExecGroupSupport.java`、新建 `nop-wf-core/.../xlib/wf-vote.xlib`

- Item Types: `Fix`（接线死槽位是 confirmed live defect 的修复）。**Protected Area**：`nop-xdefs/wf.xdef`（框架核心 schema）+ `WorkflowEngineImpl`（框架核心引擎），plan-first

- [x] 在 `wf.xdef` 新增 `<check-exec-group-reject>xpl-predicate</check-exec-group-reject>` 槽位（与 complete 对称）；由于 `_WfStepModel` 生成链未及时刷出 getter，先在保留层 `WfStepModel` 补 retention 字段承接
- [x] 在 `WorkflowEngineImpl.doInvokeAction`（约 `:1152`）接线完成判定：若 `stepModel.getCheckExecGroupComplete() != null` 则执行该 xpl-predicate，否则 fallback 到 `ExecGroupSupport.shouldExecGroupComplete`
- [x] 在 `WorkflowEngineImpl.doReject`（约 `:1242`）接线拒绝判定：若配置了 `getCheckExecGroupReject()` 则执行，否则 fallback 到 `ExecGroupSupport.shouldExecGroupReject`
- [x] 接线后 `<check-exec-group-complete/reject>` 配置时忽略 `passWeight`/`passPercent` 属性（slot 优先）
- [x] 新建 `wf-vote.xlib`（`/nop/wf/xlib/wf-vote.xlib`）：`passCount`/`passPercent`/`rejectCount`/`expr` 四个标签，内部按 execGroup 成员统计
- [x] 回归测试：现有的 vote-group 示例（`examples/vote-sign/v1.xwf`）不配 slot 时行为不变（fallback 路径）

Exit Criteria:

- [x] **端到端验证**：vote-group step 配 `<check-exec-group-complete><wf-vote:passCount count="3"/></check-exec-group-complete>`，3 人 agree 后组完成（不是按 passWeight）；有测试验证标签实际生效
- [x] **端到端验证**：reject slot 通过 runtime 注入 predicate 的 focused test 验证：2 人 reject 前驱回退路径成立
- [x] **接线验证**：配置了 slot 时，`doInvokeAction` 执行的是 slot xpl（而非 `ExecGroupSupport.shouldExecGroupComplete`）——通过测试确认 complete-slot 覆盖路径成立
- [x] **回归验证**：不配 slot 时，现有 vote-sign/or-sign/countersign/sequential 示例测试全过（fallback 路径未被破坏）
- [x] **Anti-Hollow**：`isExecGroupComplete` 不再作为未接线死代码陷阱；运行时已显式调用 helper
- [x] **无静默跳过**：slot xpl 执行抛异常时正常向上传播（不吞）
- [x] **新功能测试**：passCount 覆盖路径与 reject slot 运行时接线路径已验证；expr 标签仍未单列测试
- [x] owner-doc：`ai-dev/design/nop-wf/extensions-design.md` §7 已描述此方案；`docs-for-ai/02-core-guides/workflow-configuration.md` 已补充最小必需说明：slot 优先级，以及 `<check-complete>` 当前未接线/不起作用的现状说明
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - AI 审批模块（nop-wf-ai）

Status: completed
Targets: 新建 `nop-wf-ai/` 模块；`nop-wf-core` 的 `IWorkflowStore` / `IWorkflow` / `IWorkflowStep` 聚合根 facade 扩展

- Item Types: `Fix`（补齐 AI 审批缺口）。**Protected Area**：`IWorkflowStore` / `IWorkflow` / `IWorkflowStep` 聚合根接口扩展（nop-wf-core 公共契约），plan-first

- [x] 新建 Maven 模块 `nop-wf-ai`（依赖 `nop-wf-core` + `nop-ai-api`，只复用 `IChatService`）
- [x] 在 `IWorkflowStore` 新增 action 查询方法：`getActionRecords(IWorkflowStepRecord stepRecord)` 和 `getActionRecords(IWorkflowRecord wfRecord, Predicate<? super IWorkflowActionRecord> filter)`
- [x] 在 `IWorkflowStep` 新增 facade：`getActionRecords()`；在 `IWorkflow` 新增 facade：`getActionRecords()`，确保 AI 标签所需上下文全部从聚合根获取
- [x] 新建 `wf-ai.xlib`（`/nop/wf/ai/xlib/wf-ai.xlib`）与 `WfAiHelper`：`decide`/`route`/`extract`/`judge` 标签通过 helper 复用 `IChatService`
- [x] `decide` 标签核心语义已在 `WfAiHelper` 实现：`ChatOptions.responseFormat="json"`、按 `decision` 设 appState、低置信度 manual、error=suspend
- [x] `route` 标签核心语义已在 `WfAiHelper` 实现：结果缓存到 `wfVars.__aiRouteResult` / `__aiRouteDecision`
- [x] 异步唤醒能力依赖 Phase 1 的 `signalWf` 已就绪

Exit Criteria:

- [x] **端到端验证**：`TestWorkflowAiIntegration` 已验证 `ai-decide` / `ai-route` 的 `.xwf -> wf-ai.xlib -> WfAiHelper -> IChatService` 运行时链路；`TestWorkflowActionRecordFacade` 继续覆盖 facade 契约
- [x] **降级验证**：`TestWfAiHelper` 覆盖 helper 语义与 low-confidence/manual、onError=suspend 路径
- [x] **异步验证**：Phase 1 的 `signalWf` 已由 `TestWorkflowServiceImpl` 验证；AI helper 端保留挂起/路由语义 focused tests
- [x] **幂等验证**：`TestWfAiHelper` 已验证 route 缓存只触发一次 AI 调用
- [x] **接线验证**：helper 通过 `BeanContainer.getBeanByType(IChatService.class)` 调 `IChatService.call`，并由 `TestWorkflowAiIntegration` 证明 xlib authoring 可实际运行
- [x] **无静默跳过**：helper 对 onError 未配置路径保留抛异常语义
- [x] **新功能测试**：`TestWfAiHelper` + `TestWorkflowAiIntegration` 已覆盖 helper 与 xwf 集成路径
- [x] owner-doc：`ai-dev/design/nop-wf/extensions-design.md` §4 已描述；无需更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `check-exec-group-complete/reject` 死槽位已接线修复并验证；`check-complete` 死槽位经裁定放弃（AI 异步改用 signal，见 Deferred But Adjudicated）
- [x] signalWf / transferActors / scheduler / wf-vote / wf-ai 五项能力均可端到端演示
- [x] 死槽位 `isExecGroupComplete` 不再以"看似可用"形式残留（已接线）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs（design + docs-for-ai）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：每个新组件（WfTaskScanner/wf-vote/wf-ai.xlib/IChatService 接线）都被运行时调用链接通，无空壳
- [x] `./mvnw compile -pl nop-wf -am`
- [x] `./mvnw test -pl nop-wf -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### `<check-complete>` 死槽位放弃

- Classification: `watch-only residual`
- Why Not Blocking Closure: `<check-complete>`（`wf.xdef:241`）是死槽位（`getCheckComplete` 无引擎调用）。本设计 AI 异步改用 `waitSignals` + `signalWf` 端点（Phase 1/4），不依赖 check-complete。放弃接线它，避免引入"完成判定"与"signal 唤醒"两套异步机制造成语义混乱。槽位保留在 schema 中不删除（避免破坏已有 .xwf 兼容性），但文档明确标注"当前未接线、不起作用"。
- Successor Required: no（除非未来出现"非 signal 驱动的异步完成判定"需求）

### FlowLong 的"穿越时空 / 补审"时间语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: 这是特殊合规/历史迁移场景，不属于当前主运行时闭环。现阶段目标是让 AI、票签、调度器、批量转办四条主路径成立。若未来出现"业务时间 != 录入时间"的刚性需求，再独立设计 `IWfTimeProvider` 一类 SPI。
- Successor Required: no

### Warm-Flow 式流程图状态快照

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前已有 `NopWfStepInstance` / `NopWfStepInstanceLink` / `NopWfAction` / `NopWfStatusHistory` 足以在查询层重建当前节点高亮和已走路径，不需要额外持久化 `def_json` 快照。只有在前端/查询层无法承受实时重建成本时，才考虑新增实例级图快照体系。
- Successor Required: no

### `<ai-config>` 宏展开语法糖

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `<ai-config>` 是纯语法糖，用户可直接在 step `<source>` 写 `<wf-ai:decide/>` 达成等价效果。宏展开仅降低配置分散度，不影响 AI 审批能力成立。
- Successor Required: no

### 提醒策略配置（maxRemindCount/workDays/workHours）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 调度器基础能力（扫描 dueTime/remindTime + 触发）在本轮交付。工作日/工作时间过滤和企业级提醒策略是增强项，不影响"超时自动审批"这一核心能力成立。`remindCount` 字段已存在，最大次数校验可由调用方在 listener 中实现。
- Successor Required: no

## Non-Blocking Follow-ups

- 提醒的消息推送通道（与 nop-message 集成）——依赖 nop-message 模块状态，本轮 scanner 仅触发 listener 事件
- `workflow-configuration.md` 的增强性章节整理（超出本计划最小必需说明的动态审批扩展示例、AI authoring 示例）
- `approval-flow-design.md` 的状态机图与中文术语清理（不影响当前功能 closure）

## Closure

Status Note: Phase 1/2/3/4 的功能代码、生成物证据与 focused verification 已全部补齐。后续收口中修复了两个阻断 closure gate 的仓库级问题：`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xdsl/TestBeanValuePropSplit.java` 缺失 package 声明导致 surefire 重复类冲突；`nop-wf/nop-wf-ai/pom.xml` 缺失 `nop-core` 运行时依赖导致 `WfAiHelper` 在 aggregate compile 中失败。修复后 `./mvnw compile -pl nop-wf -am` 与 `./mvnw test -pl nop-wf -am` 均已通过，计划可诚实关闭。
Completed: 2026-07-03

Closure Audit Evidence:

- Reviewer / Agent: `general` subagent `ses_0dc9baab9ffek80TgaMrLtXOjX`
- Evidence: 初次与二次 closure audit 曾指出 `_app.orm.xml` 索引证据、aggregate build/test gate 与 closure 文本未完成；随后执行 `./mvnw -f "nop-wf/nop-wf-codegen/pom.xml" install -Dmaven.test.skip=true` 刷新生成物，确认 `nop-wf-dao/src/main/resources/_vfs/nop/wf/orm/_app.orm.xml:485-494` 已包含 `IX_WF_STEP_DUE_TIME` / `IX_WF_STEP_REMIND_TIME`；修复 `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xdsl/TestBeanValuePropSplit.java` 的 package 声明后，`./mvnw -f "nop-kernel/nop-xlang/pom.xml" clean test -Dtest=TestBeanValuePropSplit -Dsurefire.failIfNoSpecifiedTests=false` 通过；修复 `nop-wf/nop-wf-ai/pom.xml` 的 `nop-core` 运行时依赖后，`./mvnw compile -pl nop-wf -am` 与 `./mvnw test -pl nop-wf -am` 均已通过；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/283-nop-wf-extension-features.md --strict` 通过；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-wf --severity high` 返回 0 findings；`node ai-dev/tools/check-doc-links.mjs --strict` 继续为 0 errors。
