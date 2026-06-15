# 194 nop-ai-agent 审计 Logger 默认装配（Audit Trail Secure by Default）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: AUDIT-13-02
> Last Reviewed: 2026-06-15
> Source: carry-over from `ai-dev/plans/193-nop-ai-agent-secure-by-default.md`（Non-Blocking Follow-ups 第一条，标注 `successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b（AUDIT-13-02 ❌ 未修复）；`ai-dev/audits/2026-06-15-1146-deep-audit-nop-ai-agent/13-security-permission.md` 发现 [13-3]
> Related: `193-nop-ai-agent-secure-by-default.md`（交付了 Layer 1 secure-by-default + `warnIfInsecureDefaults` WARN 框架，本计划扩展该框架）、`144-nop-ai-agent-audit-logger.md`（交付了 `Slf4jAuditLogger` 但故意未装配为默认）

## Purpose

把 `DefaultAgentEngine` 与 `ReActAgentExecutor` 的审计 logger 默认从"开箱静默丢弃审计事件"（`NoOpAuditLogger`）收敛为"开箱即记录审计事件到 SLF4J"（`Slf4jAuditLogger`），并扩展 plan 193 新增的 `warnIfInsecureDefaults` WARN 机制以枚举 `NoOpAuditLogger`。本计划只负责这一件事：让引擎默认产生可见审计轨迹，而不是依赖集成商记得显式装配 audit logger。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-15）：

- **`Slf4jAuditLogger` 已存在且实现完整**（`security/Slf4jAuditLogger.java`）：`log(AuditEvent)` 输出 `AUDIT|<decision>|session=...|agent=...|tool=...|rule=...|reason=...|path=...` 到 SLF4j INFO 级别。已有单测 `TestSlf4jAuditLogger`。
- **`NoOpAuditLogger` 已存在**（`security/NoOpAuditLogger.java`）：`log(AuditEvent)` 仅输出 TRACE 级 `audit logging disabled`，实质丢弃审计事件。已有单测 `TestNoOpAuditLogger`。
- **`IAuditLogger` 接口已存在**（`security/IAuditLogger.java`）：单方法 `void log(AuditEvent event)`。
- **`ReActAgentExecutor` 已有完整审计调用链**：executor 内部 `auditLogger.log(new AuditEvent(...))` 在 8 处工具决策点被调用（deny/approve/override 等，行 819/843/867/1202/1474/1529/1574）。Builder 已有 `auditLogger(IAuditLogger)` 方法与 `private IAuditLogger auditLogger` 字段。
- **GAP-1：`ReActAgentExecutor.Builder.build()` null 兜底仍为 NoOpAuditLogger**（`ReActAgentExecutor.java:503`）：`auditLogger != null ? auditLogger : new NoOpAuditLogger()`。即任何不显式传 auditLogger 的构造路径，审计事件被静默丢弃。
- **GAP-2：`DefaultAgentEngine` 无 `auditLogger` 字段、无 setter、无构造器参数**：grep 确认 main 代码中 `DefaultAgentEngine` 未持有任何 `IAuditLogger` 字段。其他 Layer 2/3 组件（`denialLedger`/`postDenialGuard`/`checkpointManager`/`memoryStoreProvider` 等）均遵循"private 字段 + 默认值 + public setter"模式（如 `DefaultAgentEngine.java:448 setDenialLedger`），audit logger 缺失这一连线。
- **GAP-3：`resolveExecutor` 未调用 `.auditLogger(...)`**（`DefaultAgentEngine.java:1295-1321`）：Builder 链装配了 21 个组件（chatService/toolManager/.../memoryStoreProvider），但**不包含** `.auditLogger(...)`——因此从 engine 入口构造的 executor 的 auditLogger 恒为 null，回退到 GAP-1 的 `NoOpAuditLogger`。
- **GAP-4：`warnIfInsecureDefaults` 不覆盖 audit logger**（`DefaultAgentEngine.java:217-232`）：plan 193 的 WARN 方法签名是 `(IToolAccessChecker, IPathAccessChecker)`，仅检查两个 AllowAll checker；`NoOpAuditLogger` 不在检查范围内。Javadoc（行 209-211）明确标注 `NoOpAuditLogger default-downgrade enumeration is a successor extension point (AUDIT-13-02 / [13-4])`。
- **既有测试影响面**：约 3 处测试直接构造 executor 并传 auditLogger（`TestDispatchPathApprovalGate:311` 通过 Builder 传 `CollectingAuditLogger`，注释 `engine has no auditLogger setter`；`TestReActAgentExecutorBuilder:63` 传 `new NoOpAuditLogger()`）。改为默认 `Slf4jAuditLogger` 后，通过 engine 短构造器运行的测试现在会产生 SLF4j INFO 审计日志（不影响断言，但 TestDispatchPathApprovalGate 的注释需更新）。
- **roadmap §5b**：`AUDIT-13-02 | P1 | ❌ 未修复 | 默认 NoOpAuditLogger 丢弃审计事件`。本计划就是关闭这一行。

## Goals

- `new DefaultAgentEngine(chatService, toolManager)`（及全部构造器）构造出的引擎，默认通过 `Slf4jAuditLogger` 将审计事件记录到 SLF4J——无需集成商额外装配。
- 引擎持有 `auditLogger` 字段并提供 setter，使集成商可按需替换为自定义实现（如写入数据库的 logger）。
- `resolveExecutor` 将 engine 的 auditLogger 透传到 `ReActAgentExecutor.Builder`，确保从 engine 入口构造的 executor 审计事件不被丢弃。
- `ReActAgentExecutor.Builder.build()` 的 null 兜底默认从 `NoOpAuditLogger` 改为 `Slf4jAuditLogger`（与 engine 默认一致，避免绕过 engine 直接构造 executor 时仍丢弃审计）。
- plan 193 的 `warnIfInsecureDefaults` WARN 机制被扩展：当 audit logger 为 `NoOpAuditLogger` 实例时发出一次性 WARN，使审计降级**可见**而非静默。
- roadmap §5b `AUDIT-13-02` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **[13-4] Layer 2/3 默认收敛**（`AutoApproveGate`/`NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard`）——独立 successor；本计划只扩展 WARN 以覆盖 audit logger，不枚举 Layer 2/3 组件。
- **AUDIT-09-01**（`NopAiAgentException` 基类）、**AUDIT-14-01**（并发）、**AUDIT-14-04**（原子写）等其他 P1——独立 work item，见 roadmap §5b。
- 审计日志的持久化后端（数据库审计 logger、远程审计服务）——超出本计划范畴；本计划只确保 SLF4j 默认可用，集成商可通过 setter 注入自定义实现。
- `AuditEvent` 结构本身的字段扩展——审计事件 schema 已稳定，不在本计划改动范围。
- 审计日志的脱敏/PII 过滤——安全增强，独立 work item。

## Scope

### In Scope

- `engine/DefaultAgentEngine.java`：新增 `auditLogger` 字段（默认 `Slf4jAuditLogger`）、`setAuditLogger` setter、`resolveExecutor` Builder 链新增 `.auditLogger(this.auditLogger)`、扩展 `warnIfInsecureDefaults` 检查 `NoOpAuditLogger`。
- `engine/ReActAgentExecutor.java`：`Builder.build()` line 503 null 兜底从 `NoOpAuditLogger` 改为 `Slf4jAuditLogger`。
- 新增 focused 测试：默认引擎产生审计日志、setter 替换生效、WARN-on-NoOpAuditLogger、no-WARN-on-Slf4j、端到端审计事件从 engine 入口到 SLF4J 输出。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`：记录"审计 logger 默认安全"决策与 WARN 扩展语义。
- roadmap §5b `AUDIT-13-02` 状态同步。

### Out Of Scope

- Layer 2/3 默认收敛（[13-4]）、异常体系（AUDIT-09-01）、并发与持久化（AUDIT-14-x）。
- 审计持久化后端、`AuditEvent` schema 扩展、PII 脱敏。

## Execution Plan

### Phase 1 - 审计 Logger 默认装配策略裁定与设计落档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Decision`

- [x] 裁定并落档默认值切换策略：`DefaultAgentEngine` 新增 `private IAuditLogger auditLogger = new Slf4jAuditLogger()` 字段 + `setAuditLogger` setter，遵循现有 `setDenialLedger`/`setCheckpointManager` 等组件的 field+setter 模式（不新增构造器重载，因为现有 9 层构造器链已足够，audit logger 与其他 Layer 2/3 setter 组件同级）。
- [x] 裁定并落档 `resolveExecutor` 透传策略：在 Builder 链中新增 `.auditLogger(this.auditLogger)`，位置紧邻 `.denialLedger(...)`/`.postDenialGuard(...)` 等 Layer 2/3 组件（`DefaultAgentEngine.java:1316-1317` 附近）。
- [x] 裁定并落档 `ReActAgentExecutor.Builder.build()` null 兜底策略：line 503 从 `new NoOpAuditLogger()` 改为 `new Slf4jAuditLogger()`，与 engine 默认一致。
- [x] 裁定并落档 WARN 扩展语义：`warnIfInsecureDefaults` 方法签名扩展为接收 `IAuditLogger` 参数（或新增重载），当检测到 `NoOpAuditLogger` 实例时发出一次性 WARN，文案需点明"审计事件将被丢弃，工具决策（deny/approve/override）无记录"+ 指引如何显式装配 `Slf4jAuditLogger` 或自定义 logger。明确该 WARN 与 plan 193 的 AllowAll WARN 共存于同一方法，不引入新的 WARN 入口点。
- [x] 裁定并落档向后兼容处理：改为默认 `Slf4jAuditLogger` 后，通过 engine 短构造器运行的测试会产生 SLF4j INFO 审计日志（不影响断言）。`TestDispatchPathApprovalGate:297-298` 的注释 `engine has no auditLogger setter` 需更新为反映新能力。不预期有测试因审计日志产生而失败（SLF4j INFO 不抛异常、不改变返回值）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-security-and-permissions.md` 在已有 §4.6"默认安全策略"小节旁新增（或扩展为）"审计 Logger 默认装配"子节，明确记录：选了什么方案、为什么选 field+setter 而非构造器重载、拒绝了哪些替代方案（如"仅 WARN 不改默认值"为何不采用、为何不新增构造器重载）、向后兼容处理原则。
- [x] 设计文档不含类签名/伪代码（只记录决策与契约，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`（本模块无独立 owner doc 章节；如裁定需要，在此条注明具体文件）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现默认审计装配与 WARN 扩展

Status: completed
Targets: `engine/DefaultAgentEngine.java`、`engine/ReActAgentExecutor.java`

- Item Types: `Fix`

- [x] `DefaultAgentEngine`：新增 `private IAuditLogger auditLogger = new Slf4jAuditLogger()` 字段（位置紧邻 `denialLedger`/`postDenialGuard` 字段块），import `Slf4jAuditLogger`。
- [x] `DefaultAgentEngine`：新增 `public void setAuditLogger(IAuditLogger auditLogger)` setter，null 入参保留默认（`this.auditLogger = auditLogger != null ? auditLogger : new Slf4jAuditLogger()`），遵循 `setDenialLedger` 的 null 防御模式。赋值后必须调用 `warnIfInsecureDefaults(...)` 传入 `this.auditLogger`——这是 `NoOpAuditLogger` 实际进入引擎的主路径（构造期字段默认为 `Slf4jAuditLogger`，构造期检查不会命中 NoOp），故 setter-time WARN 是"让审计降级可见而非静默"的核心。注意：`setDenialLedger` 本身不触发 WARN，此处对 setter 的额外 WARN 要求是 audit-logger 特有的，不能照搬 `setDenialLedger` 的无-WARN 行为。
- [x] `DefaultAgentEngine`：`resolveExecutor`（line 1295-1321）Builder 链新增 `.auditLogger(this.auditLogger)`，位置紧邻 `.denialLedger(...)`/`.postDenialGuard(...)`。
- [x] `DefaultAgentEngine`：扩展 `warnIfInsecureDefaults`（line 217-232）以检查 `NoOpAuditLogger`——新增 `IAuditLogger auditLogger` 参数（或方法签名扩展为三参），当 `auditLogger instanceof NoOpAuditLogger` 时发出一次性 WARN（文案点明审计事件丢弃风险 + 装配指引）。该方法有两个调用点：(a) 构造器调用点（line 178）传入 `this.auditLogger`——构造期字段默认为 `Slf4jAuditLogger`，此项为 defense-in-depth，默认构造不会命中 NoOp；(b) `setAuditLogger` setter 赋值后传入 `this.auditLogger`（见上一条）——`NoOpAuditLogger` 经 setter 注入时的实际命中路径。
- [x] `ReActAgentExecutor.Builder.build()`：line 503 null 兜底从 `new NoOpAuditLogger()` 改为 `new Slf4jAuditLogger()`，import `Slf4jAuditLogger`。

Exit Criteria:

> 注：本 Phase 的接线验证与 WARN 断言 Exit Criteria 由 Phase 3 的 focused 测试落地后一并满足。

- [x] `new DefaultAgentEngine(chatService, toolManager)` 构造的引擎，其 `auditLogger` 字段为 `Slf4jAuditLogger` 实例（通过反射或 `getAuditLogger()` accessor 断言，若 accessor 不存在则裁定新增只读 accessor 或在 Phase 3 用行为断言替代）。
- [x] **接线验证（Minimum Rules #23）**：默认 engine 实际把 auditLogger 传到 `ReActAgentExecutor`——通过端到端测试证明默认引擎在工具决策时产生 SLF4j 审计日志（不只是字段类型正确）。
- [x] **无静默跳过（Minimum Rules #24）**：WARN 路径在 `NoOpAuditLogger` 被检测到时确实执行日志输出（测试用 log appender / 计数器断言 WARN 被触发）；不存在空方法体或被吞异常。
- [x] `ReActAgentExecutor.Builder.build()` 的 auditLogger null 兜底为 `Slf4jAuditLogger`（与 engine 一致）。
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` 已在 Phase 1 更新；本 Phase 不新增 owner-doc 变更。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试更新与新增 focused 验证 + 文档/roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Proof`、`Follow-up`

- [x] 审计既有测试受影响面：确认哪些测试因默认 audit logger 从 NoOp 变为 Slf4j 而需要调整（预期仅注释更新，如 `TestDispatchPathApprovalGate:297-298`；无断言变更，因 SLF4j INFO 不影响行为）。
- [x] 新增 focused 测试（Minimum Rules #25），覆盖以下行为点：
  - (1) **默认引擎产生审计日志**：通过 engine 短构造器构造引擎，执行一次工具决策（deny 或 approve），断言 SLF4j 审计日志被输出（用 Logback `ListAppender` 捕获 `Slf4jAuditLogger` 的 INFO 输出，或用 `CollectingAuditLogger` 替换验证 engine 确实调用了 auditLogger）。
  - (2) **setter 替换生效**：`setAuditLogger(customLogger)` 后，引擎使用自定义 logger 而非默认 Slf4j。
  - (3) **WARN-on-NoOpAuditLogger**：`setAuditLogger(new NoOpAuditLogger())` 后，由 setter 触发的 `warnIfInsecureDefaults` WARN 被触发一次（构造期字段默认为 `Slf4jAuditLogger` 不命中；NoOp 经 setter 注入时命中——扩展 plan 193 的 `TestSecureByDefault` WARN 测试模式）。
  - (4) **no-WARN-on-Slf4j**：默认构造或 `setAuditLogger(new Slf4jAuditLogger())` 时**不**触发 NoOp audit WARN。
  - (5) **端到端审计**：从 `new DefaultAgentEngine(...)` 入口、经 ReAct 循环、到工具被 deny，审计事件完整记录（deny decision + tool name + matched rule 等字段）。
- [x] roadmap §5b：将 `AUDIT-13-02` 行 ❌ → ✅，落地 plan 标注 194。
- [x] `nop-ai-agent-security-and-permissions.md`：Phase 1 裁定已在 Phase 1 落档；本 Phase 确认文档与实现一致（如有偏差，同步修正）。

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（受影响测试已处理，新增测试已加入并覆盖上述 5 个行为点）。
- [x] 新增测试**显式列出**所验证的新行为（default-audit-to-SLF4j、setter-replaces-logger、WARN-on-NoOp、no-WARN-on-Slf4j、end-to-end-audit-event），不是"原有测试通过"。
- [x] **端到端验证（Minimum Rules #22）**：至少一条测试从 `new DefaultAgentEngine(...)` 入口、经 ReAct 循环、到工具决策审计事件被记录，完整路径走通。
- [x] **接线验证（Minimum Rules #23）**：测试断言默认 engine 的 auditLogger 在运行时确实被 `ReActAgentExecutor` 调用（通过审计日志输出或 CollectingAuditLogger 计数断言），不只是字段类型正确。
- [x] roadmap §5b `AUDIT-13-02` 行已更新为 ✅ 并指向本 plan。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：本 section 与每个 Phase 的 Exit Criteria 全部 `[x]` 后，方可将 `Plan Status` 改为 `completed`。关闭流程见 plan guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [x] 默认 `DefaultAgentEngine` 开箱即通过 `Slf4jAuditLogger` 记录审计事件到 SLF4J（live behavior 验证，非仅类型存在）。
- [x] `resolveExecutor` 确实把 engine 的 auditLogger 透传到 executor（端到端断言）。
- [x] `ReActAgentExecutor.Builder.build()` auditLogger null 兜底为 `Slf4jAuditLogger`（与 engine 一致）。
- [x] 不安全审计默认（`NoOpAuditLogger`）触发可见 WARN（被测试断言）。
- [x] roadmap §5b `AUDIT-13-02` 同步为 ✅。
- [x] 设计文档记录了"审计 logger 默认装配"决策与 WARN 扩展语义（最终状态，无 Proposed/Current 对比）。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（[13-4] Layer 2/3 收敛等已显式移入 Non-Goals/successor，属裁定移出而非隐藏）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证 auditLogger 在运行时确被 executor 调用（端到端审计日志断言），无空方法体/静默 no-op。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/194-nop-ai-agent-audit-logger-default.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` — 在本计划触碰文件（`DefaultAgentEngine.java` 与 `ReActAgentExecutor.java`）中无 NEW high/critical findings（closure audit 须记录完整扫描输出，区分 pre-existing UOE stubs 与本计划引入的新增）。

## Deferred But Adjudicated

（暂无；本计划范围窄，未发现需裁定的 residual。）

## Non-Blocking Follow-ups

- **[13-4]**：Layer 2/3 默认收敛（`AutoApproveGate` 对 RESTRICTED 的构造参数、启动 WARN 摘要枚举 NoOp/PassThrough 组件）。建议复用本计划扩展后的 WARN 框架（现在已覆盖 tool checker / path checker / audit logger 三项）。（Classification: successor plan required）
- **[13-5]/[13-6]/[13-7]**：checker 内部逻辑增强（参数名同义词、嵌套递归、symlink 解析）。（Classification: optimization candidate）
- **审计持久化后端**：数据库审计 logger / 远程审计服务。本计划只确保 SLF4j 默认可用，集成商可通过 `setAuditLogger` 注入自定义实现。（Classification: out-of-scope improvement）

## Closure

Status Note: Plan 194 closes AUDIT-13-02. `DefaultAgentEngine` now produces a visible SLF4j audit trail out-of-the-box via a `Slf4jAuditLogger` default field, wired end-to-end through `resolveExecutor` → `ReActAgentExecutor.Builder.auditLogger(...)` → the 7 dispatch-loop `auditLogger.log(...)` sites. A `NoOpAuditLogger` downgrade (the pre-plan shipped default) is now made visible via a one-time WARN on the `setAuditLogger` hit-path rather than silently discarding audit events. Integrators retain full replacement capability via `setAuditLogger`. `ReActAgentExecutor.Builder.build()` null-fallback is consistent with the engine default (`Slf4jAuditLogger`). All 5 behaviour points covered by `TestAuditLoggerDefault`; 1523 module tests green; independent closure audit APPROVED all 12 verification items.
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (explore agent, task_id `ses_1359d3f3cffeOS611Pbz7j3X5S`, fresh session — not the implementation session).
- Audit Session: ses_1359d3f3cffeOS611Pbz7j3X5S
- Evidence:
  - Item 1 (field): PASS — `DefaultAgentEngine.java:111` `private IAuditLogger auditLogger = new Slf4jAuditLogger();`
  - Item 2 (setter): PASS — `DefaultAgentEngine.java:529-532` null-guards to Slf4j + calls `warnIfInsecureDefaults(...)` AFTER assignment
  - Item 3 (accessor): PASS — `DefaultAgentEngine.java:538-540` `getAuditLogger()`
  - Item 4 (wiring): PASS — `DefaultAgentEngine.java:1370` `.auditLogger(this.auditLogger)` in resolveExecutor Builder chain
  - Item 5 (WARN extension): PASS — `DefaultAgentEngine.java:226-250` 3-arg signature, NoOp branch emits `LOG.warn(...)` fail-loud
  - Item 6 (constructor call site): PASS — `DefaultAgentEngine.java:186` passes `this.auditLogger`
  - Item 7 (Builder null-fallback): PASS — `ReActAgentExecutor.java:503` `new Slf4jAuditLogger()` (not NoOp)
  - Item 8 (focused tests): PASS — `TestAuditLoggerDefault.java` 5 @Test methods map 1:1 to the 5 behaviour points; live run `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - Item 9 (roadmap): PASS — `nop-ai-agent-roadmap.md:272` `AUDIT-13-02 | P1 | ✅ 已修复 | plan 194`
  - Item 10 (design doc): PASS — §4.7 "审计 Logger 默认装配" at line 219; §4.6 WARN coverage note mentions `IAuditLogger`
  - Item 11 (Anti-Hollow wiring trace): PASS — `execute` → `doExecute` → `resolveExecutor`(.auditLogger @1370) → `Builder.build`(@503) → constructor(@184) → field(@130) → 7 `auditLogger.log(...)` dispatch sites (@819/843/867/1202/1474/1529/1574). No null/NoOp override in between.
  - Item 12 (no empty bodies): PASS — field initializer non-null; setter body non-empty; NoOp WARN branch non-empty
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/194-nop-ai-agent-audit-logger-default.md --strict` 退出码为 0 (all checklist items ticked + Closure Evidence written)
  - Anti-Hollow scan: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` exit 0; 15 high findings ALL pre-existing `UnsupportedOperationException` interface stubs (IAgentEngine/ISessionStore/IAiMemoryStore/NoOpHookRegistry + DefaultAgentEngine `mode=plan` @1380) — 0 NEW findings introduced by plan 194 in the touched audit-logger code
  - Build: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1523 tests, 0 failures, 0 errors
  - Deferred 项分类检查: [13-4] Layer 2/3 收敛 explicitly in Non-Goals (successor plan required); [13-5]/[13-6]/[13-7]/审计持久化 in Non-Blocking Follow-ups with explicit non-blocking rationale — no in-scope live defect downgraded

Overall verdict: **APPROVED** — all 12 verification items PASS against live code; no issues found.

Follow-up:

- [13-4] Layer 2/3 默认收敛（successor plan required，已在 Non-Goals 显式记录）
- [13-5]/[13-6]/[13-7] checker 内部逻辑增强（optimization candidate）
- 审计持久化后端（out-of-scope improvement）
- 无剩余 plan-owned work（本计划范围窄，AUDIT-13-02 已完整收敛）

## Follow-up handled by 195-nop-ai-agent-atomic-file-write.md

AUDIT-14-04（Non-Goals 引用为"独立 work item，见 roadmap §5b"的 FileBacked 非原子写）已由 successor plan `195-nop-ai-agent-atomic-file-write.md` 接管：将 `SessionFileWriter` 与 `CheckpointSnapshotWriter` 的 `Files.write(... TRUNCATE_EXISTING)` 非原子写入收敛为 crash-safe write-to-tmp + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` 模式（参照仓库内 nop-stream `LocalFileCheckpointStorage` 先例）。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
