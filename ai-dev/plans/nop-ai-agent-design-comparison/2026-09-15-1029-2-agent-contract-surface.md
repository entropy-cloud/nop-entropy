---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND4-AGENT-CONTRACT
group: "2026-09-15-1029"
verify: [test]
---

# P2 round-4 nop-ai-agent 终态契约与死点修复（PlanExecutor BLOCKED finalStatus / REASONING_CHUNK 死点处置）

## Current Baseline

- 来源：deep-audit round 4 登记、roadmap `## Follow-up Backlog` 未勾选的 2 项 nop-ai-agent 项（`source: deep-audit round 4`），全部经 live repo 复核（HEAD `9cbf684d88`，2026-09-15）：
  1. **`PlanExecutor` BLOCKED/EXPLICIT_VERDICT_REQUIRED 终态退出报 `finalStatus=running`**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/PlanExecutor.java` 中 `setPlanStatus` 仅被设为 `running`（:118）/ `completed`（:125/:183），`:272-276` 的 `BLOCKED`/`EXPLICIT_VERDICT_REQUIRED` 分支直接 `new PlanExecutionResult(state.getPlanStatus(), ...)`——此时 plan status 仍是 `running`；`respondToStagnation`（:283-301，终态路径 :298-300）的 ESCALATE 终态路径同样直接取 `state.getPlanStatus()`，但 `PlanReplanner.java:168` 已先 `setPlanStatus(AgentExecStatus.escalated)`，因此 ESCALATE 语义正确、BLOCKED/EXPLICIT_VERDICT_REQUIRED 语义错误（调用方无法区分"阻塞"与"运行中"）。`AgentExecStatus` 枚举（`io.nop.ai.agent.model.AgentExecStatus`）现含 pending/running/completed/failed/cancelled/forced_stopped/escalated/paused/waiting/truncated——无 `blocked` 值，需裁定映射目标。
  2. **`REASONING_CHUNK` 生命周期点声明但永不触发**：`AgentLifecyclePoint.java:11` 枚举值 + `DefaultHookRegistry.java:169` 注册映射（`"reasoning_chunk"`）+ `AgentHookInvoker.java:58` 注释三者存在，但全库 main 零 `invokeHooks(REASONING_CHUNK, ...)`/`executeWithMiddleware(REASONING_CHUNK, ...)` 调用点（grep 证实：`ReActAgentExecutor` 无 REASONING_CHUNK 触发，`AgentExecutionResult.java:111` 仅为注释提及）；用户注册 `reasoning_chunk` hook 静默永不触发（No Silent No-Op 违反）。`ai-dev/design/nop-ai-agent/03-extension-matrix.md:114/:255` 已登记死点并建议"接入流式输出路径或从执行面收窄合同"，但代码未处置。
- 归属模块：nop-ai-agent（2 项同一模块）。
- 验证面：运行时行为修复，涉及 `./mvnw test -pl nop-ai/nop-ai-agent -am`；owner doc `nop-ai-agent-plan-dsl.md`（§14 gate 语义/终态）+ `03-extension-matrix.md`（§6.3 REASONING_CHUNK 死点条目）需同步。

## Goals

- `PlanExecutor` 在 BLOCKED / EXPLICIT_VERDICT_REQUIRED 终态退出时产出可区分的 `finalStatus`（不再报 `running`）——调用方（TestPlanExecutorEndToEnd 等）能区分"阻塞"与"运行中"，与 ESCALATE 路径（escalated）语义一致地诚实。
- `REASONING_CHUNK` 死点收口：裁定并落地——(A) 接入真实触发点（若存在合理的 reasoning 输出路径）或 (B) 从执行面收窄合同（移除/收窄枚举与注册映射 + 文档同步 + 测试同步），消除"用户注册即静默永不触发"。
- 每项配套回归测试（正确结果断言，非仅"不报错"）；owner docs 同步；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不改 `AgentExecStatus` 既有枚举值语义（不重命名、不改变 completed/failed/escalated/paused/waiting 的现有消费语义）；如裁定新增枚举值须评估全部消费点。
- 不为 REASONING_CHUNK 新建大规模流式输出管线（若裁定接线，仅在有真实 reasoning 输出路径处挂点，不重构流式架构）。
- 不处置本轮 roadmap 其余未勾选项（service/coder/gateway/shell 相关 P2/P3 项，另开计划）。
- 不运行 mvn 全量构建。

## Phase 1 — PlanExecutor BLOCKED/EXPLICIT_VERDICT_REQUIRED 终态 finalStatus 修复

Status: planned

Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/PlanExecutor.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/AgentExecStatus.java`（如裁定新增值）、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/plan/runtime/TestPlanExecutorEndToEnd.java` 等

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 终态映射裁定：(A) 新增 `AgentExecStatus.blocked` 枚举值（BLOCKED 与 EXPLICIT_VERDICT_REQUIRED 均映射到它，语义 = "gate 阻塞/需显式裁决，非失败非运行中"）；或 (B) 复用既有终态（如 `failed`）并记录语义差异；或 (C) 复用 `escalated`（记录与 replanner 升级路径合并语义）。记录理由与备选（推荐 (A)：语义最诚实且与 plan-dsl §14 `on-fail=block` 命名对齐；需核对 `AgentExecStatus` 全部消费点——session 状态机/UI/序列化——确认新增值无破坏）。
- [x] `Fix` 按裁定落地 `PlanExecutor`：`:272-276` BLOCKED/EXPLICIT_VERDICT_REQUIRED 分支在返回 terminal `PlanExecutionResult` 前设置对应 plan status（如 `state.setPlanStatus(AgentExecStatus.blocked)`）；同步核查 `respondToStagnation` 终态路径与 `execute()` 空 phase 早退路径（:125）的一致性。
- [x] `Fix` 回归测试：构造 gate 返回 BLOCKED / EXPLICIT_VERDICT_REQUIRED 的执行场景 → 断言 `getFinalStatus()` 为裁定终态（非 `running`）；对照 ESCALATE 路径 `escalated` 不变；空 phase 早退 `completed` 不变。
- [x] `Proof` 复核：`AgentExecStatus` 全部消费点清单（session 状态机、DefaultAgentEngine、UI/序列化路径）在新增值/复用映射下的行为核对，确认无未声明语义冲突。

Exit Criteria:

- [x] BLOCKED / EXPLICIT_VERDICT_REQUIRED 终态的 `getFinalStatus()` 可区分于 `running`（回归测试断言具体值）。
- [x] ESCALATE 路径 `escalated` 与空 phase `completed` 语义不变（回归测试对照）。
- [x] **端到端验证**：`PlanExecutor.execute` → gate BLOCKED → `PlanExecutionResult` 返回的完整链路（测试断言链路连通）。
- [x] **接线验证**：终态设置确实在 terminal 返回路径上被执行（非孤立方法）。
- [x] **无静默跳过**：无继续返回 running 的遗漏分支（全路径核查）。
- [x] owner doc 更新：`nop-ai-agent-plan-dsl.md` §14 gate 语义/终态描述同步（若新增枚举值则登记语义）；否则显式写 `No owner-doc update required`。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — REASONING_CHUNK 死点处置（接入或收窄合同）

Status: planned

Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/hook/AgentLifecyclePoint.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/hook/DefaultHookRegistry.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（如裁定接线）、`ai-dev/design/nop-ai-agent/03-extension-matrix.md`、对应测试类

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` 死点处置裁定：(A) 接线——在真实 reasoning 输出点（如 `ReActAgentExecutor` 每次 LLM 推理内容产出后）调用 `hookInvoker.invokeHooks(AgentLifecyclePoint.REASONING_CHUNK, ...)`，payload 含推理内容；或 (B) 收窄合同——从 `AgentLifecyclePoint` 移除 `REASONING_CHUNK` 枚举值 + `DefaultHookRegistry` 移除映射（fail-fast：注册 `reasoning_chunk` 不再静默存活）+ 同步 `03-extension-matrix.md` 死点条目（改为已移除）+ 更新引用测试（`TestAgentLifecyclePoint`/`TestDefaultHookRegistry`/`TestAgentHookInvokerBailFailLoud`）；或 (C) 保留枚举但显式标注 reserved 永不触发（文档化 + 注册映射移除）。记录理由与备选（推荐：按 live 代码实际情况裁定——若 RAE 存在可挂载的 reasoning 产出点选 (A)，否则选 (B) 消除静默面）。
- [x] `Fix` 按裁定落地：接线则新增 invokeHooks 调用点 + payload 语义；收窄则删除枚举值/映射 + 相关注释清理。
- [x] `Fix` 回归测试：接线路径——注册 reasoning_chunk hook → 执行含推理的 turn → 断言 hook 被调用且收到推理内容；收窄路径——断言 `AgentLifecyclePoint` 无 REASONING_CHUNK / `resolveLifecyclePoint("reasoning_chunk")` 抛错或返回 null（fail-fast 而非静默），相关枚举遍历测试同步。
- [x] `Proof` 复核：全库 main/test 中 REASONING_CHUNK 引用点清单逐条处置（枚举/映射/注释/测试/文档）；`03-extension-matrix.md` 死点条目状态同步为已处置。

Exit Criteria:

- [x] 死点处置与裁定一致：接线则 hook 真实触发（回归测试断言调用与内容），收窄则零静默面（注册/枚举不再存在或显式 fail-fast）。
- [x] `03-extension-matrix.md` §6.3 死点条目已同步（接线 = 标注已接线；收窄 = 标注已移除/收窄，附日期）。
- [x] **端到端验证**（如接线）：agent 执行含 reasoning 的完整 turn → hook 收到推理内容的链路。
- [x] **接线验证**（如接线）：invokeHooks 调用点确实在推理产出路径上被运行时消费。
- [x] **无静默跳过**：不存在"注册了但不触发、也不报错"的残留面（收窄路径下 `resolveLifecyclePoint` fail-fast）。
- [x] owner doc 更新：`03-extension-matrix.md` 死点条目同步；如 `02-execution-model.md` 提及 REASONING_CHUNK 则一并核对。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：Phase 1 终态映射裁定 + PlanExecutor 终态设置落地 + 回归测试（finalStatus 可区分 + ESCALATE/completed 语义不变 + 接线/无静默跳过）+ owner doc 同步；Phase 2 死点裁定落地（接线或收窄）+ 零静默面 + 矩阵 §6.3 同步 + 回归测试。两 Phase Exit Criteria 全部勾选后，由独立子 agent closure-audit 完成并写入 `## Closure`；本 section 不保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-1029-2-agent-contract-surface-1-b42bee68 to opencode-pid-33350
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-1029-2-agent-contract-surface-1-b42bee68

## Verification

- pass test 2026-09-15-1206 exit=0
- pass test 2026-09-15-1317 exit=0

## Closure

- dispatch audit #audit-20260915-1206-2026-09-15-1029-2-agent-contract-surface-1-488e2efa to opencode-pid-closure-audit models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-1206-2026-09-15-1029-2-agent-contract-surface-1-488e2efa：独立 closure audit 复核通过——Phase 1 `AgentExecStatus.blocked` 新增值 + `PlanExecutor.checkPhaseGate` BLOCKED/EXPLICIT_VERDICT_REQUIRED terminal 分支置 blocked（PlanExecutor.java:282，返回前 setPlanStatus），3 回归测试断言具体终态（gateBlocked / gateExplicitVerdictRequired / emptyPhases），ESCALATE/completed 对照既有测试守护，`AgentExecStatus` 消费点复核（isTerminal 显式列表 / canPublishExecutionCompleted 排除列表 / values() 动态遍历）零破坏；Phase 2 裁定 B 收窄合同——`AgentLifecyclePoint` 12→11 值、`DefaultHookRegistry` 映射移除、`resolveLifecyclePoint("reasoning_chunk")` 返回 null（fail-safe），main/test 全库引用逐条处置，7 篇 owner doc 同步（03-extension-matrix §6.3 / 02-execution-model §5.1 / glossary / middleware-design / runtime-semantics / react-engine §8 / mission-driver-port-design）+ plan-dsl §14.1/§14.4.3 终态登记；`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（3398 tests 0 失败，含新增 4 例）；`check-doc-links.mjs --strict` exit=0（0 errors，11 warnings 均其它历史计划存量）；24/24 勾选 + pass 行 + 收口记录齐备，roadmap 2 项勾选与 09-15 日志同步。
- dispatch audit #audit-20260915-1317-2026-09-15-1029-2-agent-contract-surface-2-6264ceb9 to opencode-pid-closure-audit-2026-09-15-1317 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915-1317-2026-09-15-1029-2-agent-contract-surface-2-6264ceb9：独立 closure re-verify 复核通过——首访机械门 FAIL（r1 收口 id 段 `-r1-` 非纯数字，ledger 格式 `#audit-<runId>-<plan>-<iter>-<nonce8>` 不符）已修复为 `-1-` 后 plan-check.mjs --strict exit=0（derivedCompleted=true，24/24 checked）；本 visit 实跑验证套件：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（3398 tests 0 失败 0 错误，BUILD SUCCESS，含 gateBlocked/gateExplicitVerdictRequired/emptyPhases 三回归用例实测），`node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，9 warnings 全为其它历史计划存量），`scan-hollow-implementations.mjs --module nop-ai --severity high` exit=0；semantic 复核：PlanExecutor.java:282 terminal 分支 setPlanStatus(blocked) 于返回前、AgentLifecyclePoint 11 值无 REASONING_CHUNK、DefaultHookRegistry.buildEventNameMap 无 reasoning_chunk 映射、TestDefaultHookRegistry.removedReasoningChunkNameResolvesToNull 双断言、owner docs 7 篇 + plan-dsl §14.1 终态登记 + roadmap 2 项勾选 + 09-15 日志均已核对；frontmatter `status: active` 保持，derived status = completed。