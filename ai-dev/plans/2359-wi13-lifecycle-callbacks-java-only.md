# 2359 WI13 — 生命周期回调声明面处置

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI13（v11）
> Related: plan 2358（WI12 同型裁定先例：coders Java-only）、plan 2346（WI0 授权面）

## Purpose

把 roadmap WI13 收口到：`onStart`/`onEnd`/`onError` 三生命周期回调完成「接线 or 显式 Java-only 记录」裁定并落地，交付裁定记录（含回调时机与错误面契约预设、回调不得吞作业失败）+ 一致性回归。

## Current Baseline

live 核实（2026-10-10，HEAD 648b89348a）：

- **xdef 形态**：`stream.xdef:297-299` 三回调为顶层 xpl-fn 体——`onStart(streamCtx)=>void`、`onEnd(streamCtx,err)=>void`、`onError(streamCtx,err)=>void`（xdef 层签名已定义，lambda 字面量被 XLang ERR_XPL_FN_BODY_IS_FUNCTION 在 parse 期拒绝——`TestStreamModelDslBuilderFailFast.lifecycleCallbacksFailsFast` 注释钉）。
- **恒拒现状**：`failFastOnUnsupportedRegistries:670-674` 对任一非空回调 `ERR_STREAM_NOT_IMPLEMENTED`；`StreamModelLinter.scanRegistries:279-282` warning 镜像。
- **引擎侧生命周期机制现状（审查复核后的真论据）**：nop-stream-runtime **已有 job 级生命周期事件总线**——`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/event/StreamJobEvent.java`（EventType 含 JOB_STARTED/JOB_FINISHED/JOB_FAILED/JOB_CANCELED/CHECKPOINT_*/RECOVERY_*/JOB_DEGRADED，从 JobCoordinator/CheckpointCoordinator 真实生命周期路径 fire）+ `StreamJobEventListener`/`StreamJobEventBus`（addListener/removeListener，同步分发）+ `JobCoordinator.addJobEventListener:542` + 生产消费者 `alert/AlertService` 先例；模型经 `JobGraph.getStreamModel()` 已达 runtime 接缝（JobCoordinatorMain:165/OpsJobManager:156）。**但接线仍三缺**：① `streamCtx` 参数类型全仓无定义（新增公共 API 面无授权）；② onEnd/onError≈JOB_FINISHED/JOB_FAILED 是 post-hoc 分发，onStart「任务启动前」的时机语义未设计；③ **core 本地路径（execute()/runLocal，今日 XDSL 主消费路径）无总线**——接线需动 core。生命周期是运行期执行链行为，与已放行的构建期 builder 行为（WI11/WI12 全部先例）不同层。
- **授权归属（修正表述）**：WI0（plan 2346）授权通道只覆盖 WI9/WI11/WI16–WI20——**WI13 不在其清单**；vision §六六决策点均未列举执行链生命周期钩子；接线超出 WI11/WI12 的构建期 builder 行为先例 → **引擎接线需 owner 新决策**，无既有授权通道可循。
- **合同账**：`lifecycleCallbacksFailsFast` 钉（NOT_IMPLEMENTED + "lifecycle" token）；WI12 刚把 CorpusLintGate 的 delta 载体换到 coders——lifecycle 的 WI13 归属注记仍在 lint 消息里。

## 裁定：显式记为 Java-only

**理由链**（三选一对照）：
1. 接线：runtime 事件总线虽在，但 `streamCtx` 类型缺失（新公共 API 面无授权）+ onStart 时机语义未设计 + 本地主消费路径（core execute）无总线需动 core 内核 + 无任何既有授权通道覆盖 WI13 → 需 owner 新决策。**否**。
2. 移除 schema 面：三回调的 xdef 签名已定义、语义清晰（启动/完成/失败通知），是引擎成熟后（生命周期监听 API 授权）的直接接线路径；移除会销毁该预留面。**否**。
3. **显式记为 Java-only——采**：保留 xdef + fail-fast（错误码保持 `ERR_STREAM_NOT_IMPLEMENTED`，与 coders 同款合同账）+ 消息改写（一句裁定 + 重启指针：接线候选缝=runtime StreamJobEventBus 的 JOB_STARTED/JOB_FINISHED/JOB_FAILED 映射，缺 streamCtx 类型/时机设计/本地路径总线/owner 授权四项）+ 文档裁定记录（时机契约预设与「回调异常不得吞作业失败」条款全文落 user guide，非异常消息）。

## Goals

- builder fail-fast 消息与 lint 文本双侧改写为 Java-only 裁定记录（含时机契约预设与不得吞失败条款）。
- 文档裁定记录落 user guide（含重启触发条件）。
- 合同账回归：既有 lifecycle 钉保持（改写后断言更新）、零回归。

## Non-Goals

- 不改 core execute()/runLocal（无授权）。
- 不定义 streamCtx 公共类型。
- 不移除 xdef 回调签名（预留接路面）。

## Scope

### In Scope

- `StreamModelDslBuilder.failFastOnUnsupportedRegistries` lifecycle 分支消息改写。
- `StreamModelLinter.scanRegistries` lifecycle 文本同步（去 WI13 归属注记，改裁定措辞）。
- `TestStreamModelDslBuilderFailFast.lifecycleCallbacksFailsFast` 断言更新（新消息 token）。
- user guide 裁定记录 + roadmap 勾选（header v12）。

### Out Of Scope

- 引擎执行链改动；streamCtx 类型定义；回调 DSL 接线。

## Execution Plan

### Phase 1 - 裁定落地（单 Phase：改写 + 钉子 + 文档）

Status: completed
Targets: `StreamModelDslBuilder.java`、`StreamModelLinter.java`、`TestStreamModelDslBuilderFailFast.java`、user guide、roadmap、本 plan

- Item Types: `Decision` + `Follow-up`（文档）+ `Proof`

- [x] builder lifecycle 消息改写：错误码保持 NOT_IMPLEMENTED；detail 一句话裁定 + 重启指针（runtime StreamJobEventBus 缝 + 四缺失项）；契约全文落 user guide
- [x] lint 文本同步（去 roadmap WI13 release face 注记，改裁定措辞）
- [x] `lifecycleCallbacksFailsFast` 钉保持——消息保留 "lifecycle" token，断言原样成立（无伪工作项）
- [x] flow 全量回归（284/0）+ runtime 全量回归（1352/0，零豁免触发）
- [x] user guide 裁定记录段（时机契约预设 + 不得吞失败条款 + 交付语映射：时机契约以文本记录+钉子 token 替代时机测试，WI27 fixture 钉归属不变）+ **矩阵表 B lifecycle 行归属列改裁定措辞**（SSOT）+ roadmap WI13 勾选（header v12）+ check-doc-links/check-plan-checklist/scan-hollow 三工具
- [x] 独立子 agent closure audit + 单次提交——**APPROVE**（agent_abc902ca：双侧改写/钉子保持/真论据/授权/文档/工具全 PASS；条件项 runtime 回归已完成回写、checkbox/Closure 回填为本次收尾）

Exit Criteria:

- [x] 裁定落地且双侧一致（builder/lint）
- [x] 既有合同账不破（错误码/token 核对）+ 全量回归绿
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 裁定表与 live 一致（双侧消息 + 错误码）
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：不适用新组件（纯裁定+消息改写），核对为「无双面不一致 + 无静默放行」——audit 确认实质一致（同裁定/同四缺/同 token/同错误码）
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

（暂无）

## Closure

Status Note: 生命周期回调裁定（Java-only）双侧落地并有独立审计背书：核心论据经审查修正后全部 live 复核成立（runtime StreamJobEventBus 事件总线在——JOB_STARTED/JOB_FINISHED/JOB_FAILED 从真实协调器路径 fire；但 streamCtx 类型/onStart 时机契约/本地 execute 路径总线覆盖/owner 授权四缺，且 WI0 授权通道不含 WI13、§六未列举——需 owner 新决策）；builder/linter 双侧消息改写（错误码保持 NOT_IMPLEMENTED，合同账不破）；钉子 token 保持零测试改动；user guide 裁定段（时机契约预设+不得吞失败条款+交付语映射）+ 表 B 行 SSOT 同步；flow 284 / runtime 1352 回归绿。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_abc902ca（独立 fresh-session closure auditor）
- Audit Session: agent_abc902ca-b182-4fff-97b5-2c7b773160a6
- Evidence:
  - 双侧改写：builder :670-684 / linter :279-288（裁定+指定缝+四缺齐全；NOT_IMPLEMENTED 保持；git diff 纯消息改写无行为变化）
  - 钉子保持：lifecycleCallbacksFailsFast 零 diff（"lifecycle" token），audit 亲跑 18/0
  - 真论据：StreamJobEvent.java:24-35 枚举核实、JobCoordinator:539-543 注册接缝、core 零事件总线引用（本地路径论据成立）
  - 授权：plan 2346:10 授权清单（WI9/WI11/WI16–WI20）不含 WI13
  - 文档：user guide :178 裁定段 + :278 表 B 行；roadmap v12 + WI13 [x] + 历史行完整；doc-links --strict 0
  - 工具：check-plan-checklist --strict 0（Closure 回填后终验）；scan-hollow 0 findings（59 文件）
  - 回归：flow 284/0 + runtime 1352/0（audit 抽查 TestJobCoordinatorLifecycleEvents 5/0 绿）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2359-wi13-lifecycle-callbacks-java-only.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work
