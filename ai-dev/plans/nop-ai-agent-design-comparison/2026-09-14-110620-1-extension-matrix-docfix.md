---
status: active
mission: nop-ai-agent-design-comparison
work-item: extension-matrix-docfix
group: "2026-09-14-110620"
verify: [test]
---

# Owner Doc 03-extension-matrix.md 6 项勘误线索修订

## Current Baseline

- WI5（2026-09-12）在 ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md ⑤ 节登记了 6 项 owner doc 勘误线索，结论为"docs bug，登记待后续处理"，并明确"owner doc 修订不在本分析计划内"；WI5 plan 的 Closure Follow-up 将该项列为唯一的非阻塞 follow-up。本计划是该项的收口。
- 6 项线索逐条经 live repo 复核，**全部仍成立**（2026-09-14 复核）：
  - ① `IContentGuardrail` 已有功能实现并接线：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/PromptInjectionGuardrail.java:31` 实现该接口；`DefaultAgentEngine.java:338` 与 `DefaultAgentEngineConfig.java:929` 提供 `setContentGuardrail` 装配点。但 owner doc `03-extension-matrix.md:98` 仍标 `功能默认实现=无 / 状态=🟡 半闭合`，§5.2 半闭合清单（:190）仍收录 `IContentGuardrail`。
  - ② 接口计数矛盾：owner doc `:12` 声称"全模块共 **67 个接口**"；2026-09-12 WI5 普查 live 主源码 `io.nop.ai.agent` 下 public interface 约 73 个（2026-09-14 复核仍为 73）。
  - ③ "真正扩展点 53 vs 52"自相矛盾：`:12` 称"真正扩展点 53"，`§三 分类表 :44` 称"真正扩展点 = 52"。
  - ④ `AgentHookInvoker.java:58` 注释称 ON_ERROR/REASONING_CHUNK/POST_COMPACT "continue to call invokeHooks directly"；实测 `REASONING_CHUNK` 全库零触发点（仅 enum `AgentLifecyclePoint.java:11`、注册映射 `DefaultHookRegistry.java:169` 与注释存在，`ReActAgentExecutor.java:1289` 与 `AgentExecutionResult.java:111` 仅为注释提及），owner doc 未标注该死点。
  - ⑤ `DefaultHookRegistry.java:122` 定义 `HookToMiddlewareAdapter`（private static class）但其构造函数（:125）全库零引用——无任何 `new HookToMiddlewareAdapter(...)` 调用点，属死代码；owner doc 未标注。
  - ⑥ "主要消费者 DAE/RAE"缩写系统性失真：owner doc 正文 47 处 `DAE` / `RAE` 缩写（§4.5 缩写表定义其含义），但实际消费点已散入 6+ 个协作者类（IHookRegistry、AgentHookInvoker、security chain、team 等），`DAE, RAE` 作为默认消费标注无法反映真实消费分布。
- owner doc `03-extension-matrix.md` 日期为 2026-06-21，晚于该日期的代码新增（IContentGuardrail 功能实现、接口数量增长）未回写。
- 无代码变更；纯文档修订。owner doc 属于 `ai-dev/design/`，其修订不影响任何产品行为。

## Goals

- 按 live 代码现状修订 `ai-dev/design/nop-ai-agent/03-extension-matrix.md`，消除 6 项已登记勘误，使 doc 的接口计数、IContentGuardrail 闭合状态、扩展点数量、REASONING_CHUNK/HookToMiddlewareAdapter 死点标注、消费者标注与 live baseline 一致。
- 修订后运行 `node ai-dev/tools/check-doc-links.mjs --strict` 保持 0 error。

## Non-Goals

- 不修改任何源码（`nop-ai/nop-ai-agent/` 下 Java 代码一律不动）。
- 不重新裁定各扩展点的能力级别（那是 WI5/04 文档的权威范围，本计划只同步 owner doc 到已裁定结论）。
- 不修订同主题的其它 owner doc（02-execution-model.md 等），除非在修订中发现并登记新 docs bug。
- 不新增/调整 roadmap 工作项；roadmap 全部 WI 已勾选，本计划是已关闭 WI5 的 follow-up 收口。
- 不运行 mvn 构建与测试（纯文档任务）。

## Phase 1 — 接口计数与分类口径修正（线索 ②③）

Status: planned

Targets: `ai-dev/design/nop-ai-agent/03-extension-matrix.md` §一 设计结论、§三 接口分类表

- Item Types: `Fix | Proof`

- [x] `Proof` 以 live repo 为准复核 `io.nop.ai.agent` 下 public interface 总数（含 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 全部子包，`_gen` 除外按 owner doc 原口径核对），并核对 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下各角色分类计数（核心契约/真正扩展点/回调契约/NoOp-only/预留原语），记录精确数字与 grep 命令依据。
- [x] `Fix` 修订 `:12` 的接口总数与角色分项数字，消除与 §三 分类表（:44 "真正扩展点 = 52"）的矛盾，使两处口径一致且与 live 计数一致。
- [x] `Fix` 更新 `§三 接口分类表` 的"真正扩展点"数量及其它分项数量，保证 §一、§三、§4.x Layer 标题计数（4.1 18 / 4.2 17 / 4.3 9 / 4.4 21 等）三者一致，若有出入以 live 枚举结果为准并逐一核对每行。
- [x] `Proof` 若 §5.1 闭合度分布（✅ 60 / 🟡 3 / 🔴 1 / ⚪ 3，占比 89.6% / 4.5% / 1.5% / 4.5%）受 Phase 1/2 修订影响，同步重算计数与占比。

Exit Criteria:

- [x] owner doc `:12` 与 `§三 分类表`、各 Layer 标题中的计数数字全部一致，且与 live repo 实测计数一致；修订记录（grep 命令 + 数字）写入本 plan 的 Verification 段。
- [x] 文档中不再存在相互矛盾的接口总数 / 扩展点数量表述。
- [x] **端到端验证**（不适用）：纯文档修订，无运行时路径。
- [x] **接线验证**（不适用）：无新组件与已有组件协作。
- [x] **无静默跳过**（不适用）：无代码变更。
- [x] 修订若改变 owner doc 的 §5.1 统计口径：§5.1 数字已同步；否则显式写 `No §5.1 update required`。
- [x] 本 Phase 修订的 owner doc（`03-extension-matrix.md`）即交付物本身；不涉及其它 owner doc 更新。
- [x] No new test required: 纯文档修订，无代码变更。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 2 — 逐项事实勘误（线索 ①④⑤⑥）

Status: planned

Targets: `ai-dev/design/nop-ai-agent/03-extension-matrix.md` §4.2 L2 矩阵、§5.2 半闭合清单、§6 反例清单、§4.5 消费者缩写表

- Item Types: `Fix | Proof`

- [x] `Fix` ① IContentGuardrail：将 `:98` 的"功能默认实现"列从 `无` 改为 `PromptInjectionGuardrail`（live 下另有第二功能实现 `RuleGraphGuardrail`，`guardrail/rule/RuleGraphGuardrail.java:45`，一并如实登记），状态从 `🟡 半闭合` 改为 `✅`（或按"仅接线点、无默认装配"的实际语义如实标注，以 live 装配行为为准），并在该行备注 `DefaultAgentEngine.setContentGuardrail` 装配点；同步从 `§5.2 半闭合清单`（:190）移除 `IContentGuardrail`，相应更新 §5.1 计数。
- [x] `Fix` ④ REASONING_CHUNK 死点：在 owner doc 中对该 lifecycle point（如 §4.2 IAgentLifecycleHook 相关行或新增注记）明确标注"declared but never triggered（全库零触发点，2026-09-14 复核）"，避免读者误以为流式输出路径已接线。
- [x] `Fix` ⑤ HookToMiddlewareAdapter 死代码：在 owner doc 相关注记（§4.2 IHookRegistry 或 §6 反例）中标注 `DefaultHookRegistry.HookToMiddlewareAdapter` 为"零引用死代码（全库无实例化点）"，或将该反例移入 §6 命名/接线反例清单并在 §6.2/§6.3 登记。
- [x] `Proof` ⑥ 消费者标注失真：核对 `DAE` / `RAE` 缩写作为"主要消费者"的实际消费分布（至少抽查 ISessionStore、IToolAccessChecker、IHookRegistry、IAgentMessenger 等行的真实消费类），将失真最严重的行改为实际消费类名（如 `IHookRegistry`、`AgentHookInvoker`、security chain 相关类）；对无法逐行核实的行，在 §4.5 缩写表或文档脚注登记"DAE/RAE 为粗粒度默认标注，精确消费见各接口 javadoc"，并把该免责注记写为一行清晰说明。
- [x] `Fix` 更新 owner doc 头部日期为本次修订日期，并加一行"修订记录"说明依据 WI5 登记的 6 项勘误线索与 live 代码复核修订。

Exit Criteria:

- [x] `03-extension-matrix.md` 中 6 项已登记线索全部有对应修订：① IContentGuardrail 状态与实现列更新；②③ 计数一致（Phase 1）；④ REASONING_CHUNK 死点已标注；⑤ HookToMiddlewareAdapter 死代码已标注；⑥ 消费者标注已按抽查结果更新或登记免责注记。
- [x] 每项修订均可从 owner doc 回溯到 live 代码锚点（类名 + 文件相对路径），无仅凭注释/文档的表述。
- [x] **端到端验证**（不适用）：纯文档修订，无运行时路径。
- [x] **接线验证**（不适用）：无新组件与已有组件协作。
- [x] **无静默跳过**（不适用）：无代码变更。
- [x] 受影响的 owner doc（本文件自身）已同步；如修订中顺带发现同主题其它 owner doc（02-execution-model.md 等）存在同类事实错误，作为新 docs bug 在 `ai-dev/logs/` 登记，不在本计划内修改。
- [x] No new test required: 纯文档修订，无代码变更。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（0 errors；已有存量 warnings 与本次修订无关时说明）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Draft Review Record

- （待独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-14-110620-1-extension-matrix-docfix-1-3f9a2c71 to ses_f62074b70ffeUSF46VLhi5BOVx
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-14-110620-1-extension-matrix-docfix-1-3f9a2c71

## Verification

- pass test 20260914-122348 exit=0

### Phase 1 grep 计数依据（2026-09-14 live 复核）

- 接口总数：`rg '^public interface ' nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/ --glob '!**/_gen/**' | wc -l` → **72 个顶层 public interface**（与 WI5 "约 73" 差 1：另有 1 个嵌套接口 `AdapterBackedMemoryStoreProvider.StoreFactory`，共 73 个声明）。`_gen` 下零接口（`rg -l '^public interface ' .../agent/_gen/ | wc -l` → 0）。
- 角色分类（68 个矩阵接口 = 72 − 4 矩阵外）：核心契约 4（IAgentEngine/IAgentExecutor/IAgentEventPublisher/ActorRegistry）、回调契约 4（+TaskRunner）、NoOp-only 2（ITalent/IBudgetProvider）、预留原语 1（IFencingTokenService）、真正扩展点 57、矩阵外 4（NopAiAgentErrors + guardrail/test 3 个测试 SPI）。
- 4 个迁移/合并接口：`IModelSwitchedMessageWriter`（现 `nop-ai-core/io/nop/ai/core/agent/IModelSwitchedMessageWriter.java:22`）、`ICircuitBreaker` / `IRetryPolicy`（现 `nop-ai-core/.../reliability/`）、`ILevelHintsProducer`（plan 304 合并入 `ISecurityLevelResolver`，全库无接口声明）。
- 5 个 live 补登接口锚点：`middleware/IAgentMiddleware.java:31`（消费者 AgentHookInvoker）、`security/SecurityCheckpoint.java:7`（消费者 AgentSecurityConsultation/SecurityCheckpointChain）、`reliability/IWaitCoordinator.java:24`（消费者 RAE :178）、`session/ICompactionSnapshotArchive.java:41`（消费者 AgentCompactionCoordinator/AgentSession）、`plan/runtime/TaskRunner.java:18`（消费者 PlanExecutor）。
- Layer 标题计数（= 表格行数）：4.1=10 / 4.2=16 / 4.3=12 / 4.4=23；§4.0=8（4 核心 + 4 回调）；合计 69 行 − 1 重复（IAgentLifecycleHook 同时列于 §4.0/§4.2）= 68 唯一接口。
- §5.1 重算：✅ 61（89.7%）/ 🟡 2（2.9%）/ 🔴 1（1.5%）/ ⚪ 4（5.9%）= 68。

### Phase 2 修订锚点（全部 live 复核）

- ① `guardrail/PromptInjectionGuardrail.java:31`、`guardrail/rule/RuleGraphGuardrail.java:45`（implements IContentGuardrail）；`DefaultAgentEngineConfig.java:929` setContentGuardrail、`DefaultAgentEngine.java:182/:338` 装配；调用点 `AgentPromptAssembly.java:69/:296`（INPUT/OUTPUT check）。
- ④ `hook/AgentLifecyclePoint.java:11`、`hook/DefaultHookRegistry.java:169`（注册映射）、`engine/AgentHookInvoker.java:58/:166`（注释）、`engine/ReActAgentExecutor.java:1289`、`engine/AgentExecutionResult.java:111`（注释）——全库零 `invokeHooks(REASONING_CHUNK...)` / `executeWithMiddleware(REASONING_CHUNK...)` 调用。
- ⑤ `hook/DefaultHookRegistry.java:122/:125`（类定义 + 构造器）——全库零 `new HookToMiddlewareAdapter`；`:30-31` 注释与实际不符（middleware 链由 `AgentHookInvoker.executeWithMiddleware` 经 `IHookRegistry.getMiddlewares` 构建）。
- ⑥ 抽查结果：ISessionStore 消费含 AgentCompactionCoordinator；IToolAccessChecker/IPathAccessChecker/IPermissionProvider 消费含 SecurityCheckpointChain（AgentSecurityConsultation）；IHookRegistry 消费以 AgentHookInvoker 为主；IAgentMessenger 消费已含 tools/ATEC（未改）。§4.5 已登记一行免责注记。

### 验证命令

- `node ai-dev/tools/check-doc-links.mjs --strict` → 退出码 0（0 errors；8 warnings 均为其它计划既有存量：2261-nop-metadata 5 条 + nop-treesitter 3 条，与本次修订无关）。
- `echo 'nop-ai-agent-design-comparison: no typecheck step (analysis mission)' && echo 'nop-ai-agent-design-comparison: no build step (analysis mission)' && echo 'nop-ai-agent-design-comparison: no lint step (analysis mission)'` → 纯文档任务，无 mvn 构建/测试（计划 Non-Goals 明确排除）。

## Closure

- dispatch audit #audit-20260914-122348-mission-driver-2026-09-14-110620-1-extension-matrix-docfix-1-3fc732be to opencode-go/deepseek-v4-flash models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260914-122348-mission-driver-2026-09-14-110620-1-extension-matrix-docfix-1-3fc732be：独立 closure audit 通过——6 项 WI5 勘误线索全部在 `03-extension-matrix.md` 修订并可回溯到 live 代码锚点（① PromptInjectionGuardrail.java:31 / RuleGraphGuardrail.java:45 / DefaultAgentEngineConfig.java:929；②③ live `rg '^public interface '` = 72 顶层接口，§一/§三/§4.x 计数一致，§5.1 ✅61/🟡2/🔴1/⚪4=68；④ REASONING_CHUNK 全库零触发点已标注 §4.2 L2 注 + §6.3；⑤ HookToMiddlewareAdapter 零实例化已标注 §6.3；⑥ 消费者抽查已改列 + §4.5 免责注记）；验证命令 `node ai-dev/tools/check-doc-links.mjs --strict` exit=0（0 errors，8 warnings 均其它计划既有存量）；纯文档任务无代码变更（`git status --short nop-ai/` 空），Anti-Hollow/端到端/接线不适用；plan-check.mjs --strict exit=0；无 in-scope defect 被降级；roadmap 引用计数已同步（66→68）、ai-dev/logs/2026/09-14.md 已记录

Status Note: 6 项 WI5 勘误线索已全部在 `ai-dev/design/nop-ai-agent/03-extension-matrix.md` 中修订完毕（纯文档，无代码变更），计数与 live repo 一致，check-doc-links 0 error。Ledger 格式：completion 由引擎从本段证据推导，frontmatter `status: active` 保持不变。

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，task_id `ses_f61e5fe15ffe5DnxpTWawXhI1q`，fresh session，非执行会话）
- Evidence: 16 项检查全部 PASS，最终裁定 **ACCEPT**：
  - Phase 1 计数：live `rg '^public interface ' ... --glob '!**/_gen/**'` = 72；§一 :14 与 §三 :43-52 角色分项一致（4/57/4/2/1 + 4 矩阵外 = 72）；Layer 标题与表格行数一致（4.1=10 / 4.2=16 / 4.3=12 / 4.4=23）；§5.1 ✅61/🟡2/🔴1/⚪4 = 68 = 72−4；4 个迁移/合并接口（IModelSwitchedMessageWriter / ILevelHintsProducer / ICircuitBreaker / IRetryPolicy）agent 模块零声明。
  - Phase 2 事实勘误：① §4.2 :103 IContentGuardrail ✅ + PromptInjectionGuardrail/RuleGraphGuardrail（live 锚点 `PromptInjectionGuardrail.java:31`、`RuleGraphGuardrail.java:45`、`DefaultAgentEngineConfig.java:929`），§5.2 已移除；④ §4.2 L2 注 :114 + §6.3 :254（REASONING_CHUNK 零触发点）；⑤ §6.3 :255（HookToMiddlewareAdapter 零实例化）；⑥ §4.1 :79-81/:96 + §4.5 :183 免责注记。
  - 每项修订均可回溯到 live 代码锚点（类名 + 文件相对路径）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；8 warnings 均为其它计划既有存量，与本次修订无关）。
  - Anti-Hollow：`git status --short nop-ai/` 与 `git diff --stat nop-ai/` 均为空——纯文档修订，无代码变更；无空壳/静默跳过适用场景。
  - `ai-dev/logs/2026/09-14.md` 条目已更新（2026-09-14 顶部）。
  - 全部 Phase 1/2 条目 + Exit Criteria + Closure Gates 已勾选 `[x]`；frontmatter 仍为 `status: active`（ledger 格式，不写 completed）。

Follow-up:

- no remaining plan-owned work；roadmap `nop-ai-agent-design-comparison-roadmap.md` 的矩阵引用计数已同步（66→68），roadmap 全部 WI 已勾选，无新增工作项（计划 Non-Goals 约定）。