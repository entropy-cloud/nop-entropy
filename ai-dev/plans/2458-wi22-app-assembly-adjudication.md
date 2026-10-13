# 2458 WI22 nop-ai-app 产品组装闭环裁定

> Plan Status: completed
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI22；装配普查 = 2026-10-13 live HEAD（plan 2454 后）
> Related: WI19/plan 2456（completed）——gateway 单模块裁定；module-groups AI 子系统分层描述

## Purpose

裁定 nop-ai-app 的产品定位：当前 app 依赖 service/web/auth/quarkus 但**不依赖 agent 与 gateway**（pom 实证 0 引用），application.yaml 无 agent/gateway 配置——555 文件的 agent 引擎没有任何默认宿主。裁定二选一（roadmap §WI22）：①app 补装 agent/gateway；②显式登记「nop-ai 是库集合、宿主由下游应用承担」定位。

## Current Baseline

> 2026-10-13 live HEAD 实测。

- `nop-ai-app` pom 依赖 = nop-ai-service/nop-ai-web/nop-auth-web/nop-auth-service/web-amis-editor/web-site/quarkus starters；**nop-ai-agent 与 nop-ai-gateway 零引用**。
- agent 引擎唯一装配途径 = gateway（或测试/下游应用自装 beans）；gateway 的 MCP 出口依赖 agent bean。 Quarkus 应用栈。
- module-groups :17 已载「nop-ai-agent 的 nop-dao/nop-message-core 为 test scope……MCP 集成模块独立发布周期」——即 nop-ai 定位本就是**库集合**，app 只是参考宿主之一。

## Adjudications

### A1 定位 = 库集合、宿主由下游应用承担（选项②）

- 依据：①module-groups 既有分层描述已按"库集合"叙述（dao/message-core test scope 隔离即为此设计）；②补装 agent/gateway 会使 app 拉入 agent 555 文件 + channel/auth/feishu 重依赖面，与 app 现有轻量 Quarkus 参考宿主定位冲突；③gateway 的真实宿主（如独立网关部署）与 app 的 AI 管理后台部署是两种不同部署形态，强绑单一 app 反而限制；④autonomous-execution roadmap 的 harness 演进（TestAiFileTool 等容器装配测试）已提供 agent 装配的测试级宿主证明，无"从未组装运行"hollow 风险。
- 登记：module-groups AI 行增补一句定位声明 + roadmap 括注。
- 重启条件：若产品要求 AI 管理后台默认内置 agent 对话能力（app 增 agent 依赖 + application.yaml agent 配置段），立 successor。

## Execution Plan

### Phase 1 - 定位登记与收口

Status: completed
Targets: module-groups、roadmap、log、plan closure

- Item Types: `Decision`

- [x] module-groups AI 行增补定位声明（库集合、宿主由下游承担、重启条件）
- [x] roadmap `## Work Item Status` WI22 → `done`；状态块解析核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2458-wi22-app-assembly-adjudication.md --strict` 0
- [x] pathspec 限定单一原子提交

Exit Criteria:

- [x] roadmap 更新后解析核对通过（21 行 WI 条目数不变）
- [x] `ai-dev/logs/2026/10-12.md` 条目含：裁定、A1 依据、commit hash

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] 裁定登记完成（定位② + 依据 + 重启条件）
- [x] 不存在被静默降级的 in-scope live defect（A1 为显式裁定，roadmap Rules :253 授权 WI22）
- [x] owner docs 已同步：module-groups 定位声明
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2458-wi22-app-assembly-adjudication.md --strict` 退出码 0
- [x] scan-hollow-implementations：N/A（纯裁定型 plan，无代码变更）

## Deferred But Adjudicated

### app 补装 agent/gateway

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: A1 裁定定位②——轻量参考宿主不背 agent/gateway 重依赖面
- Successor Required: `no`（重启条件触发时另立 successor）

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: 定位②裁定落地——nop-ai 显式登记为库集合（宿主由下游应用承担），module-groups 定位声明增补；app 保持轻量 Quarkus 参考宿主；重启条件登记。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（自审内联——纯裁定型 plan 无代码变更面，装配普查以 pom 证据在 Baseline 在录；Rules :253 授权 WI22 维持/登记）
- Evidence:
  - 装配普查：app pom 零 agent/gateway 引用（live grep 在录）
  - module-groups 定位声明增补在位（本提交）
  - 裁定：**APPROVE closure**（纯裁定项，重启条件登记）
- `node ai-dev/tools/check-plan-checklist.mjs 2458-wi22-app-assembly-adjudication.md --strict` 退出码 0（closure 前末次确认）
