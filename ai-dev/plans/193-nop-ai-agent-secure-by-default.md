# 193 nop-ai-agent Layer 1 安全检查器默认装配（Secure by Default）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-13-01
> Last Reviewed: 2026-06-15
> Source: `ai-dev/audits/2026-06-15-1146-deep-audit-nop-ai-agent/13-security-permission.md`（发现 [13-1]/[13-2]）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b（AUDIT-13-01 ❌ 未修复）
> Related: `139-nop-ai-agent-permission-provider.md`、`140-nop-ai-agent-tool-access-checker.md`、`141-nop-ai-agent-path-access-checker.md`、`144-nop-ai-agent-audit-logger.md`（交付了 Default* 实现但**故意**保留 AllowAll 为默认）；`190`/`191`（明确把 13-01 默认值问题切出为独立 work item）

## Purpose

把 `DefaultAgentEngine` 的 Layer 1 访问控制默认从"开箱全放行"（AllowAll）收敛为"开箱即拒绝危险工具与敏感路径"（Default*），并增加启动期可见性告警。本计划只负责这一件事：让引擎默认安全，而不是依赖集成商记得显式装配 checker。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-15）：

- **Default* 实现已存在但从未被默认装配**：
  - `security/DefaultToolAccessChecker.java` — 硬编码 deny-list（`bash`/`write-file`/`delete-file`/`move-file`/`patch-file`/`apply-delta`/`http-request`/`graphql-query`，大小写不敏感），无参构造。grep 确认 main 代码**零**引用，仅 test 使用。
  - `security/DefaultPathAccessChecker.java` — 拒绝 `~/.ssh/`、`/etc/`、`.env`、`id_rsa` 等敏感前缀 + 词法规范化。main 代码**零**自动装配。
  - `security/Slf4jAuditLogger.java` 已存在（本计划不装配它，见 Non-Goals）。
- **`DefaultAgentEngine` 全部短构造器委派到 AllowAll**（`engine/DefaultAgentEngine.java`）：
  - line 118：4-arg → `new AllowAllToolAccessChecker()`
  - line 125：5-arg → `new AllowAllPathAccessChecker()`
  - line 165-166：字段兜底 `toolAccessChecker != null ? ... : new AllowAllToolAccessChecker()` / `... : new AllowAllPathAccessChecker()`
- **`resolveExecutor`（line 1149-1180）** 将 engine 的 checker 透传给 `ReActAgentExecutor.builder()`；未调用 `.auditLogger(...)`（→ 回退 `NoOpAuditLogger`，属 AUDIT-13-02，本计划不含）。
- **`ReActAgentExecutor.Builder.build()`（line 502-503）** 自身 null 兜底也用 AllowAll。
- **既有测试影响面**：约 100+ 处测试用短构造器构造 `DefaultAgentEngine(...)`（`TestDefaultAgentEngine`、`TestRestoreSession`、`TestDBSessionStoreEngineWiring`、`TestSubAgentPermission*` 等）。改默认值后，凡是通过默认引擎调用 deny-list 内工具名（bash/write-file 等）或访问敏感路径的测试将失败，需显式 opt-in AllowAll 或改用非 deny-list 工具名。
- **roadmap §5b**：`AUDIT-13-01 | P1 | ❌ 未修复 | 默认 AllowAll/PassThrough 装配，开箱全放行`。本计划就是关闭这一行。

## Goals

- `new DefaultAgentEngine(chatService, toolManager)`（及更短构造器）构造出的引擎，默认拒绝 deny-list 内的危险工具、默认拒绝敏感路径——无需集成商额外装配。
- 引擎构造期对"仍处于不安全默认（AllowAll）"的情况发出一次性 WARN，使安全降级**可见**而非静默。
- 受影响的既有测试得到一致处理（显式 opt-in AllowAll 并注明"测试 allow 路径"，或改用安全工具名），不出现"为图省事全局关 checker"的回退。
- roadmap §5b `AUDIT-13-01` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **AUDIT-13-02 / 发现 [13-3]**（`NoOpAuditLogger` 默认 + 缺 `setAuditLogger`/字段/builder 调用）——独立 successor，本计划不装配审计 logger，但所加 WARN 机制会被设计为可被后续 plan 扩展以覆盖 NoOp 审计 logger。
- **发现 [13-4]**（Layer 2/3 默认 disabled：`AutoApproveGate`/`NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard`）——独立 successor；本计划的 WARN 只覆盖 in-scope 的 Layer 1 两个 checker，不枚举 Layer 2/3 组件。
- 发现 [13-5]/[13-6]/[13-7]（路径参数同义词、递归嵌套、symlink 解析）——checker 内部逻辑改造，独立 work item。
- [09-1]（`NopAiAgentException` 基类）、[14-x]（并发/原子写）等其他 P1——独立 work item，见 roadmap §5b。
- toolkit 内各 executor（Bash/WriteFile/...）自身的安全性——超出本模块范畴（审计盲区自评 #1）。

## Scope

### In Scope

- `engine/DefaultAgentEngine.java`：字段默认值、构造器委派链、构造期"不安全默认"WARN。
- `engine/ReActAgentExecutor.java`：`Builder.build()` 的 null 兜底默认改为 Default*（一致性，避免内部构造路径仍 AllowAll）。
- 既有测试受影响站点的统一处理 + 新增 secure-by-default / WARN 的 focused 测试。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`：记录"默认安全"决策与 WARN 语义。
- roadmap §5b `AUDIT-13-01` 状态同步。

### Out Of Scope

- 审计 logger 装配（AUDIT-13-02）、Layer 2/3 默认收敛（[13-4]）、checker 内部逻辑（[13-5/6/7]）、异常体系（[09-1]）、并发与持久化（[14-x]）。
- `nop-ai-toolkit` 的工具执行器内部安全。
- 默认装配 `DefaultPathAccessChecker` 的 symlink 解析增强（[13-7]）。

## Execution Plan

### Phase 1 - Secure-by-Default 策略裁定与设计落档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Decision`

- [x] 裁定并落档默认值切换策略（推荐方案：将字段默认与构造器委派链改为 `DefaultToolAccessChecker` / `DefaultPathAccessChecker`；保留 `AllowAll*` 类为 public 供集成商显式 opt-in；不引入新的"全局开关"配置项，除非裁定需要）。
- [x] 裁定并落档 WARN 语义：触发时机（构造期、每个 engine 实例至多一次）、触发条件（path/tool checker 解析为 AllowAll* 实例）、日志级别（WARN）、文案（需点明"开箱不安全，危险工具/敏感路径无防护"+ 指引如何显式装配）。明确 WARN 当前**只**覆盖 in-scope 的 Layer 1 两个 checker；Layer 2/3 与审计 logger 的枚举是 successor 扩展点。
- [x] 裁定并落档向后兼容处理：受影响测试的统一处理原则（显式传 `AllowAll*` 并注释"测试 allow 路径"，或改用非 deny-list 工具名），避免为绕过测试而整体关闭防护。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-security-and-permissions.md` 新增"默认安全策略"小节，明确记录：选了什么方案、为什么选、拒绝了哪些替代方案（如"仅 WARN 不改默认值"为何不采用）、向后兼容处理原则。
- [x] 设计文档不含类签名/伪代码（只记录决策与契约，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`（本模块无独立 owner doc 章节；如裁定需要，在此条注明具体文件）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现默认安全装配与启动 WARN

Status: completed
Targets: `engine/DefaultAgentEngine.java`、`engine/ReActAgentExecutor.java`

- Item Types: `Fix`

- [x] `DefaultAgentEngine`：将 `toolAccessChecker` 字段默认值与全部构造器委派链（line 118、165 等）从 `AllowAllToolAccessChecker` 改为 `DefaultToolAccessChecker`。
- [x] `DefaultAgentEngine`：将 `pathAccessChecker` 字段默认值与全部构造器委派链（line 125、166 等）从 `AllowAllPathAccessChecker` 改为 `DefaultPathAccessChecker`。
- [x] `DefaultAgentEngine`：新增构造期"不安全默认"WARN —— 当解析后的 path 或 tool checker 为 `AllowAll*` 实例时，按 Phase 1 裁定的语义打印一次性 WARN。实现须 fail-loud（WARN 必须真实输出），不得静默吞掉或写成空方法体。
- [x] `ReActAgentExecutor.Builder.build()`：将 null 兜底默认（line 502-503）从 AllowAll 改为 Default*，与 engine 默认保持一致（避免绕过 engine 直接构造 executor 时仍 AllowAll）。**仅 Layer 1 两个 checker**；`auditLogger` 兜底属 AUDIT-13-02，本计划不改。

Exit Criteria:

> 注：本 Phase 的接线验证与 WARN 断言 Exit Criteria 由 Phase 3 的 focused 测试落地后一并满足。Phase 2 实现已完成、既有测试无回归（1496/1496 pass）。

- [x] `new DefaultAgentEngine(chatService, toolManager)` 构造的引擎，其 `toolAccessChecker`/`pathAccessChecker` 字段为 `Default*` 实例（可在测试中反射或通过 `getToolAccessChecker()`/`getPathAccessChecker()` 断言，若 accessor 不存在则裁定新增只读 accessor）。
- [x] **接线验证（Minimum Rules #23）**：默认引擎实际把 checker 传到 `ReActAgentExecutor`——通过端到端测试证明默认引擎对 deny-list 工具/敏感路径真实拒绝（不只是字段类型正确）。
- [x] **无静默跳过（Minimum Rules #24）**：WARN 路径在 AllowAll 被检测到时确实执行日志输出（测试用 log appender / 计数器断言 WARN 被触发）；不存在空方法体或被吞异常。
- [x] `ReActAgentExecutor.Builder.build()` 的 checker null 兜底为 Default*（与 engine 一致）。
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` 已在 Phase 1 更新；本 Phase 不新增 owner-doc 变更。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试更新与新增 focused 验证 + 文档/roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 审计受影响既有测试：逐一确认哪些用短构造器的测试会因默认 deny 而失败（重点是使用 deny-list 工具名或敏感路径的用例），按 Phase 1 裁定原则统一处理。
- [x] 新增 focused 测试（Minimum Rules #25）：(1) 默认引擎拒绝 `bash` 工具调用（断言返回 deny 结果/错误而非执行）；(2) 默认引擎拒绝敏感路径（如 `~/.ssh/id_rsa`）；(3) 默认引擎允许安全工具/路径正常执行；(4) 显式传 `AllowAll*` 时 WARN 被触发一次；(5) 使用 Default*（或不传 checker 走默认）时**不**触发 WARN。
- [x] roadmap §5b：将 `AUDIT-13-01` 行 ❌ → ✅，落地 plan 标注 193。

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（受影响测试已按裁定处理，新增测试已加入并覆盖上述 5 个行为点）。
- [x] 新增测试**显式列出**所验证的新行为（deny-by-default for tools、deny-by-default for paths、WARN-on-AllowAll、no-WARN-on-Default、allow-safe-path），不是"原有测试通过"。
- [x] **端到端验证（Minimum Rules #22）**：至少一条测试从 `new DefaultAgentEngine(...)` 入口、经 ReAct 循环、到工具被默认 checker 拒绝，完整路径走通。
- [x] roadmap §5b `AUDIT-13-01` 行已更新为 ✅ 并指向本 plan。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部 `[x]` 后，方可将 `Plan Status` 改为 `completed`。关闭流程见 plan guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [x] 默认 `DefaultAgentEngine` 开箱即拒绝 deny-list 危险工具与敏感路径（live behavior 验证，非仅类型存在）。
- [x] 不安全默认（AllowAll）触发可见 WARN（被测试断言）。
- [x] 受影响既有测试已一致处理，无"整体关闭防护以让测试通过"的回退。
- [x] `ReActAgentExecutor.Builder` 默认与 engine 一致。
- [x] roadmap §5b `AUDIT-13-01` 同步为 ✅。
- [x] 设计文档记录了"默认安全"决策与 WARN 语义（最终状态，无 Proposed/Current 对比）。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（AUDIT-13-02、[13-4] 等已显式移入 Non-Goals/successor，属裁定移出而非隐藏）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证默认 checker 在运行时确被 ReAct 循环调用并拒绝（端到端断言），无空方法体/静默 no-op。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/193-nop-ai-agent-secure-by-default.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` — 在本计划触碰文件（`DefaultAgentEngine.java` 与 `ReActAgentExecutor.java`）中无 NEW high/critical findings（预期退出码非 0：15 个 pre-existing UOE stubs 均位于 out-of-scope 的 `ISessionStore`/`IAiMemoryStore`/`IAgentEngine`/`NoOpHookRegistry`；closure audit 须记录完整扫描输出）。

## Deferred But Adjudicated

（暂无；本计划范围窄，未发现需裁定的 residual。）

## Non-Blocking Follow-ups

- **AUDIT-13-02 / [13-3]**：装配 `Slf4jAuditLogger` 为默认 + 给 `DefaultAgentEngine` 加 `auditLogger` 字段/setter + `resolveExecutor` 调用 `.auditLogger(...)`。建议该 successor 同时扩展本计划新增的 WARN 机制以枚举 NoOp 审计 logger。（Classification: successor plan required）
- **[13-4]**：Layer 2/3 默认收敛（`AutoApproveGate` 对 RESTRICTED 的构造参数、启动 WARN 摘要枚举 NoOp/PassThrough 组件）。建议复用本计划的 WARN 框架。（Classification: successor plan required）
- **[13-5]/[13-6]/[13-7]**：checker 内部逻辑增强（参数名同义词、嵌套递归、symlink 解析）。（Classification: optimization candidate）

## Closure

Status Note: Plan 193 关闭。`DefaultAgentEngine` 的 Layer 1 默认装配已从 AllowAll 收敛为 Default*（4-arg/5-arg 构造器委派链 + 最长构造器字段兜底 + `ReActAgentExecutor.Builder.build()` null 兜底全部切换），开箱即拒绝 deny-list 危险工具（bash/write-file/...）与敏感路径（~/.ssh/、/etc/、.env、id_rsa 等）。新增构造期一次性 WARN（AllowAll* 实例触发，fail-loud via `LOG.warn`），让显式 opt-in 全放行的集成商可见降级。5 个 in-scope 行为点由 `TestSecureByDefault` 6 tests 端到端覆盖。受影响既有测试 3 文件按裁定原则一致处理（显式 AllowAll* + 注释 / 改用非 deny-list 值），无"整体关 checker"回退。AUDIT-13-02、[13-4]、[13-5/6/7] 已显式裁定为 successor，非隐藏降级。
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（opencode general subagent，session id `ses_1361d7f34ffetytVi80RyW3A7v`，fresh session 非复用实现阶段 session）
- Evidence:
  - **Phase 2 接线（Minimum Rules #23）PASS**：`DefaultAgentEngine.java:120`（4-arg → `new DefaultToolAccessChecker()`）、`:127`（5-arg → `new DefaultPathAccessChecker()`）、`:167-168`（最长构造器字段兜底 Default*）；`ReActAgentExecutor.java:501-502`（Builder null 兜底 Default*）；`auditLogger` 兜底 `:503` 未改（NoOpAuditLogger，out-of-scope）
  - **WARN fail-loud（Minimum Rules #24）PASS**：`DefaultAgentEngine.java:212-227` `warnIfInsecureDefaults` 两个 `instanceof` 分支均调用 `LOG.warn(...)` 多行描述性文案，无空方法体/吞异常。被 `TestSecureByDefault.explicitAllowAllTriggersConstructionWarn` via Logback `ListAppender` 端到端断言
  - **端到端验证（Minimum Rules #22）PASS**：`TestSecureByDefault.defaultEngineDeniesDenyListToolEndToEnd`（短构造器引擎 → ReAct 循环 → `bash` 被 Layer 1 拒绝，toolInvoked=false）；`defaultEngineDeniesSensitivePathEndToEnd`（`~/.ssh/id_rsa` 端到端拒绝）；`defaultEngineAllowsSafeToolAndPath`（`/tmp/test-data.txt` 不被 over-block）
  - **5 行为点覆盖 PASS**：(1) deny bash `defaultEngineDeniesDenyListToolEndToEnd`、(2) deny sensitive path `defaultEngineDeniesSensitivePathEndToEnd`、(3) allow safe `defaultEngineAllowsSafeToolAndPath`、(4) WARN-on-AllowAll `explicitAllowAllTriggersConstructionWarn`、(5) no-WARN-on-Default `defaultCheckersDoNotTriggerWarn`；bonus `builderNullFallbackUsesDefaultCheckers` 验证 builder 一致性
  - **既有测试处理 PASS**：`TestPermissionInReActLoop.java:140-147`（AllowAllToolAccessChecker + 注释 isolate permission-provider）；`TestChainRepairerInReActLoop.java`（`/etc/hosts` → `/tmp/test-read.txt`，5 处）；`TestSubAgentPermissionEndToEnd.java:345-356`（6-arg 构造器显式 AllowAll* + 注释 backward-compat baseline）
  - **roadmap PASS**：`nop-ai-agent-roadmap.md:262` `AUDIT-13-01 | P1 | ✅ 已修复 | plan 193：...`
  - **设计文档 PASS**：`nop-ai-agent-security-and-permissions.md` §4.6（lines 190-217）记录决策 + 拒绝的 3 替代方案 + WARN 语义 + 向后兼容原则，无类签名/伪代码（Minimum Rules #14）
  - **Anti-Hollow PASS**：closure audit 验证 `warnIfInsecureDefaults` 非空壳（`LOG.warn` 真实输出）；默认 checker 在运行时确被 ReAct 循环调用并拒绝（`defaultEngineDeniesDenyListToolEndToEnd` 端到端断言 toolInvoked=false + deny response 产生）
  - **`check-plan-checklist.mjs --strict`** 退出码 0（Passed: 1, Failed: 0）
  - **`scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high`** 退出码 0；15 个 pre-existing UOE stubs 全部位于 out-of-scope 的 `ISessionStore`/`IAiMemoryStore`/`IAgentEngine`/`NoOpHookRegistry` + `DefaultAgentEngine.java:1226` 的 `mode=plan` UOE（pre-existing，行号因新增 `warnIfInsecureDefaults` 方法位移，非 NEW finding）；触碰文件 `DefaultAgentEngine.java` 与 `ReActAgentExecutor.java` 无 NEW high/critical findings
  - **Deferred 项分类诚实性 PASS**：AUDIT-13-02（审计 logger）、[13-4]（Layer 2/3 默认）、[13-5/6/7]（checker 逻辑）均显式移入 Non-Goals + Non-Blocking Follow-ups，标注 `successor plan required` / `optimization candidate`，非隐藏 in-scope live defect
  - **`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`**：BUILD SUCCESS，1502 tests / 0 failures（1496 baseline + 6 new）

Follow-up:

- AUDIT-13-02（审计 logger 装配）、[13-4]（Layer 2/3 默认收敛）、[13-5/6/7]（checker 逻辑）为已裁定 successor，见 Non-Blocking Follow-ups。本计划无剩余 plan-owned work。

## Follow-up handled by 194-nop-ai-agent-audit-logger-default.md

AUDIT-13-02（Non-Blocking Follow-ups 第一条）已由 successor plan `194-nop-ai-agent-audit-logger-default.md` 接管：装配 `Slf4jAuditLogger` 为默认审计 logger、给 `DefaultAgentEngine` 加 `auditLogger` 字段/setter、`resolveExecutor` 调用 `.auditLogger(...)`、并扩展本计划新增的 `warnIfInsecureDefaults` WARN 机制以枚举 `NoOpAuditLogger`。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。

## Follow-up handled by 196-nop-ai-agent-exception-base-class.md

AUDIT-09-01（Non-Goals 引用为"[09-1]（`NopAiAgentException` 基类）... 独立 work item，见 roadmap §5b"的 P1）已由 successor plan `196-nop-ai-agent-exception-base-class.md` 接管：将 `NopAiAgentException` 从 `extends RuntimeException` 改为 `extends NopException`，补齐 `serialVersionUID` 与 `(ErrorCode)` / `(ErrorCode, Throwable)` 构造器，使模块异常纳入框架统一异常体系。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
