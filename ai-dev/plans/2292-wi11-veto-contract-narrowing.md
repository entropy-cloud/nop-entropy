# 2292 WI11 veto 死配置收敛（refactor roadmap M3）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI11（Deliverable: 代码变更/类型收窄 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI11 本 roadmap 吸收）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/03-extension-matrix.md`

## Purpose

hook 生命周期点 veto 合同收窄：类型上每点都"声明"了 veto 能力（IAgentLifecycleHook→HookResult 四态），但 9 个点的 veto 返回无人消费。收敛方式：活点保持，死点 fail-loud（复用 Bail 白名单先例），ON_ERROR/POST_COMPACT 两个非链点 observer 化登记；live census 重做。

## Current Baseline

（执行时 HEAD `fffc9112b6`，2026-10-02 考察锚点）

- **能力声明**：`AgentLifecyclePoint` 11 值无能力文档；veto 无点位校验（`invokeHooks:197-199` 一律接受并短路返回首个 Veto）；Bail 有白名单（`validateBailPoint:224-230` 仅 POST_REASONING/POST_CALL）；Reenter 有白名单（`:177-183` 仅 BEFORE/AFTER_TOOL_RESULT_PROCESSED）。`HookResult.java:71-75` 自述 POST 侧 veto 无意义但代码静默接受。
- **veto 消费矩阵**（活/死）：活 = PRE_CALL（ReActAgentExecutor:640）、PRE_REASONING（:851）、POST_REASONING bail（:1123）/POST_CALL bail（:1785）、BEFORE/AFTER_TOOL_RESULT_PROCESSED reenter（AgentToolDispatcher:774/:846）、4 个 ExecutionPoint 全活（LlmCallCoordinator:271/:298、AgentToolDispatcher:655/:746）。死 = PRE_ACTING（:753 丢弃）/POST_ACTING（:842 丢弃）/POST_CALL veto 维度/PRE_COMPACT（AgentCompactionCoordinator:85 丢弃）/POST_COMPACT（:121,125 非链点）/ON_ERROR（invokeOnError:203-209 非链点，双层 catch）/BEFORE/AFTER_TOOL_RESULT_PROCESSED veto 维度。
- **隐性副作用**：invokeHooks 首个 veto 短路后续 hooks（:197-199）；HookToMiddlewareAdapter（DefaultHookRegistry:130-137）veto 短路剩余 middleware 链——收窄后死点不再有此行为。
- **xdef**：`<hooks event>` 自由字符串无 veto 声明；未知名静默跳过（DefaultHookRegistry:107-109）——xdef 收敛归 WI13。
- **census 滞后**：`04-extension-capability-matrix.md:49-66` 仍含 REASONING_CHUNK 行、漏 ON_ERROR；dsh-D2 快照（6 死点含 REASONING_CHUNK）作废。
- **测试证据**：`vetoFromPreActingSkipsTool`（TestHookInReActLoop:271）名实不符（断言 status==completed 但工具照常执行）；`TestBailInReActLoop:379` 文档化 POST_REASONING veto 被忽略。

## Goals

- **veto 白名单合同**（复用 Bail fail-loud 先例）：新增 `validateVetoPoint(point)`——仅 PRE_CALL/PRE_REASONING 合法返回 VetoResult；死点（PRE_ACTING/POST_ACTING/POST_COMPACT/PRE_COMPACT/ON_ERROR/POST_REASONING/POST_CALL/BEFORE/AFTER_TOOL_RESULT_PROCESSED 共 9 点）返回 VetoResult 即 fail-loud 抛 NopAiAgentException。BEFORE/AFTER 点 Reenter 通道不变。
- **PRE_COMPACT 裁定**：fail-loud（设计 §8.3 归档+压缩为无条件流，census 无消费意图；实现消费=新功能扩张超出 WI11 收窄范围）。
- **ON_ERROR/POST_COMPACT observer 登记**：非链点返回值本就丢弃，运行时 fail-loud 会在错误处理路径内抛错（危险）——登记为 observer 语义（validateVetoPoint 白名单不含此两点，但 invokeOnError/POST_COMPACT 直调路径对 VetoResult 仅 LOG.warn 一次后丢弃，不抛、不短路——比静默可观测）。
- **live census 重做**：04-extension-capability-matrix.md 删 REASONING_CHUNK 行、补 ON_ERROR 行、各点能力矩阵与收敛后合同一致。
- **测试名实修正**：vetoFromPreActingSkipsTool 改为断言 fail-loud。

## Non-Goals

- xdef 自由字符串收敛（WI13 承接）。
- ExecutionPoint 4 点（全活，不动）。
- POST 侧返回类型收窄为 Pass|Bail 的类型系统改造（fail-loud 校验已达合同目的）。

## Scope

### In Scope

- `AgentHookInvoker.validateVetoPoint` + invokeHooks 校验接线；PRE_COMPACT fail-loud；ON_ERROR/POST_COMPACT 直调路径 VetoResult WARN 登记。
- AgentLifecyclePoint javadoc（每点能力合同）；HookResult javadoc 修正；04-extension-capability-matrix.md census 重做。
- 测试：白名单内外 veto 行为；既有 veto 测试更新（vetoFromPreActingSkipsTool 名实修正）；既有测试零回归评估。

### Out Of Scope

- xdef 收敛（WI13）；订阅者失败（WI10 已完成）；PRE_ACTING veto 消费实现（执行级等价物已在位）。

## Execution Plan

### Phase 1 - veto 白名单合同与 census 重做

Status: planned
Targets: hook/engine 包、census 文档

- Item Types: `Fix`

- [x] `validateVetoPoint(point)` 白名单（PRE_CALL/PRE_REASONING）+ invokeHooks 接线；9 死点 fail-loud（ON_ERROR/POST_COMPACT 非链点 WARN-drop 例外）
- [x] AgentLifecyclePoint 每点能力 javadoc；HookResult VetoResult javadoc 修正（POST 侧不适用→合同化）
- [x] census 重做：04-extension-capability-matrix.md 删 REASONING_CHUNK/补 ON_ERROR/能力矩阵更新
- [x] 测试：白名单内 veto 照常（PRE_CALL/PRE_REASONING 既有测试零回归）；死点 veto fail-loud 断言；vetoFromPreActingSkipsTool 名实修正；ON_ERROR/POST_COMPACT WARN-drop 断言

Exit Criteria:

- [x] 全部单测通过 + 既有测试零回归评估（凡依赖死点 veto 的既有测试改为新合同断言）
- [x] census 文档与 live 合同一致
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] log 条目

### Phase 2 - owner doc 与收口

Status: planned
Targets: 03-extension-matrix.md、log

- Item Types: `Proof`

- [x] 03-extension-matrix.md veto 合同节（白名单/observer 登记/收敛裁定）
- [x] doc-links 0 error

Exit Criteria:

- [x] owner doc 在位；全量测试通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI11 checkbox → git commit → 本 plan 标 completed。

- [x] veto 白名单合同端到端连通（活点保持/死点 fail-loud/observer 登记/census 一致）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2292-wi11-veto-contract-narrowing.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: veto 白名单合同（PRE_CALL/PRE_REASONING 合法、9 死点 fail-loud/observer WARN-drop）+ live census 重做（03-extension-matrix 权威矩阵 + 04 快照更新注）+ HookResult/AgentLifecyclePoint javadoc 合同化全部落地；closure audit 首轮 FAIL 2 blocker（HookResult javadoc/WARN-drop 断言缺失）已补齐并复核 PASS——WI11 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_237cb2bb-7652-49d0-a8be-47ed24bf8e46（fresh session，两轮）
- Evidence:
  - 首轮审计 5 验证点：3 PASS + 2 blocker（HookResult javadoc 声称完成未做；ON_ERROR/POST_COMPACT WARN-drop 断言缺失）
  - 修复：HookResult VetoResult/BailResult javadoc 合同化 + 新增 AgentHookInvokerVetoContractTest(4：死点 fail-loud/observer WARN-drop 不抛/invokeOnError 直调/PRE_REASONING 透传)——复核全 PASS（断言经真实性核验）
  - 附带：AgentLifecyclePoint 每点能力 javadoc；03-extension-matrix.md veto 合同节 + 修订记录；04-extension-capability-matrix.md WI11 更新注；vetoFromPreActingSkipsTool 名实修正；postReasoningVeto 测试改名
  - surefire agent 3477 / 0F / 0E；hollow exit 0；doc-links exit 0；java-lint 0 error
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2292-wi11-veto-contract-narrowing.md --strict` exit 0（completed 后 0 failed）

Follow-up:

- xdef 自由字符串收敛（WI13）；invariant-catalog 同步（随 WI13）
