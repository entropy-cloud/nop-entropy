# 200 nop-ai-agent Layer 2/3 全套 Secure 默认实现（NoOp/PassThrough → Default*）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L23-SDI
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/199-nop-ai-agent-layer23-secure-defaults.md`（Non-Blocking Follow-ups 第一条："Layer 2/3 全套 secure 默认实现"，标 `successor plan required`）
> Related: `193`（Layer 1 secure-by-default）、`194`（audit logger default）、`199`（AutoApproveGate 收紧 + WARN 框架扩展覆盖 5 个 Layer 2/3 组件，本计划是其直接 successor）

## Purpose

为 nop-ai-agent 的 4 个 Layer 2/3 安全组件创建 Default* secure 替代实现并切换 engine 默认，使引擎开箱即用具备安全级别分类、通道权限矩阵、拒绝计数/暂停、盲重试阻断能力——而非依赖集成商显式注册功能组件。本计划只负责这一件事：把 plan 199 Non-Goals 明确切出的"全套 secure 默认实现"收口。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **4 个 Layer 2/3 组件以 NoOp/PassThrough 单例作为 engine 默认**（plan 199 确认的基线，未改变）：
  - `NoOpSecurityLevelResolver` — `resolve()` 恒返回 `SecurityLevel.STANDARD`（`security/NoOpSecurityLevelResolver.java:28-30`）
  - `PassThroughPermissionMatrix` — `check()` 恒返回 `MatrixDecision.allow()`（`security/PassThroughPermissionMatrix.java:27-29`）
  - `NoOpDenialLedger` — `recordDenial` 返回 count=0/threshold-not-exceeded，`isPaused` 恒 false（`security/NoOpDenialLedger.java:37-57`）
  - `PassThroughPostDenialGuard` — `checkBeforeDispatch` 恒返回 null，recording/reset 空实现（`security/PassThroughPostDenialGuard.java:40-64`）
- **DefaultAgentEngine 字段默认值仍为 NoOp/PassThrough**（`engine/DefaultAgentEngine.java:101-106`）：
  - `permissionMatrix = PassThroughPermissionMatrix.passThrough()` (line 101)
  - `securityLevelResolver = NoOpSecurityLevelResolver.noOp()` (line 102)
  - `denialLedger = NoOpDenialLedger.noOp()` (line 105)
  - `postDenialGuard = PassThroughPostDenialGuard.passThrough()` (line 106)
- **4 个 setter 的 null 兜底仍为 NoOp/PassThrough**（`DefaultAgentEngine.java:425-426, 452-454, 542-543, 572-574`）
- **ReActAgentExecutor 构造器 null 兜底仍为 NoOp/PassThrough**（`engine/ReActAgentExecutor.java:196-213`）
- **ReActAgentExecutor.Builder.build() 传递这 4 个字段给构造器**（`ReActAgentExecutor.java:516-521`），构造器再做 null 兜底
- **warnIfInsecureDefaults 当前为 8-arg**（`DefaultAgentEngine.java:244-314`），其中这 4 个组件属于"conditionally-checked"——构造期传 null 跳过（`DefaultAgentEngine.java:187-188`），各 setter 只传自己刚设的组件（lines 427-428, 456-457, 544-545, 576-577）。原因：plan 199 Non-Goals 明确不改这 4 个组件的默认值，故构造期 WARN 会成为噪音。
- **Layer 2 consultation 已在 dispatch path 激活**（`ReActAgentExecutor.java:1513-1541`）：
  - `levelHintsProducer.produce(toolName, ...)` → 产出 hints
  - `securityLevelResolver.resolve(toolName, hints)` → 解析安全级别
  - `permissionMatrix.check(channel, principal, level)` → 通道权限检查
  - 若 denied → 审计日志 + deny 事件
- **DenialLedger / PostDenialGuard 已在 dispatch loop 激活**（`ReActAgentExecutor.java:593, 814, 1192, 1198, 1244`）：isPaused 检查、checkBeforeDispatch 咨询、recordDeniedAction 记录、recordDenial 计数。
- **DefaultLevelHintsProducer 已产出语义化 hints（非全 false stub）**（`security/DefaultLevelHintsProducer.java:43-78`）：
  - `trustedSource = true`（agent 自身推理链产生 → DefaultContentTrustEvaluator 判定 trusted）
  - `highImpact = true` for `shell.exec/code.exec/bash/sh/file.delete/rm/...`（tool-name 匹配）
  - `needsNetwork = true` for `web.fetch/http.request/network.fetch/...`
  - `writesOutsideWorkspace` = 路径分析结果
  - `crossesTrustBoundary = false`（保守）
- **关键行为风险**：设计 §5.1 规则表（`ISecurityLevelResolver` javadoc, lines 12-19）对 `shell.exec` 标注 `highImpact → RESTRICTED`。当前 DefaultLevelHintsProducer 对 shell.exec 产出 `highImpact=true`（`DefaultLevelHintsProducer.java:49-52`）。若 DefaultSecurityLevelResolver 完整实现 §5.1 表，shell.exec 将被分类为 RESTRICTED → DefaultApprovalGate 拒绝 RESTRICTED → 引擎无法执行 shell 工具。这是 **Phase 1 必须裁定的核心设计冲突**。
- **已有功能性实现**：`DBDenialLedger`（需 DataSource + schema init，`security/DBDenialLedger.java:50-216`）、`FingerprintPostDenialGuard`（纯内存，`security/FingerprintPostDenialGuard.java:37-104`）。但无 DefaultSecurityLevelResolver / DefaultPermissionMatrix 的任何实现（grep 确认 zero results）。
- **roadmap §5b**：plan 199 新增了 `AUDIT-13-04` 行并标记 ✅（AutoApproveGate 收紧 + WARN 扩展）。本计划的全套 secure 默认实现尚无独立 roadmap 行（需新增）。

## Goals

- 4 个 Default* secure 实现存在并实现设计 §5.1/§5.3/§6.2/§6.3 语义（具体裁定的激进程度由 Phase 1 决定）。
- DefaultAgentEngine 字段默认值切换到 Default*；4 个 setter 的 null 兜底切换到 Default*；ReActAgentExecutor 构造器 null 兜底切换到 Default*。
- NoOp/PassThrough 4 个组件保留为 public opt-in（与 AutoApproveGate/AllowAll* 模式一致）。
- warnIfInsecureDefaults 中这 4 个组件从"conditionally-checked"迁移为"always-checked"（构造期传非 null，与 toolChecker/pathChecker/auditLogger/approvalGate 一致）。
- 受影响既有测试得到一致处理（显式 opt-in NoOp/PassThrough + 注释，或适配新行为），不出现"为图省事全局关防护"的回退。
- roadmap §5b 新增本工作项行并标记状态。

## Non-Goals

- **AgentExecutionContext 的 channelKind/principal 字段增强**——当前 `ctx.getChannelKind()` / `ctx.getPrincipal()` 已存在并被 consultation 使用；若返回 null，DefaultPermissionMatrix 的 fail-closed 语义是 Phase 1 裁定项，但新增字段不在本计划 scope。
- **DB-backed 默认**——DBDenialLedger 需 DataSource，不适合作为 engine 零依赖默认。DefaultDenialLedger 为纯内存实现；DB 持久化是显式 opt-in（已有 DBDenialLedger）。
- **[13-5]/[13-6]/[13-7]** checker 内部逻辑增强——独立 optimization candidate。
- **设计 §5.1 规则表本身的修订**——本计划裁定如何在默认配置下安全地应用该表（如 trusted-by-default 基线），不修订表本身。
- **Layer 4 组件**（ISandboxBackend、Actor Runtime 等）——超出本计划范畴。

## Scope

### In Scope

- 新增 `DefaultSecurityLevelResolver`（实现 §5.1 规则表的 safe-by-default 变体，具体变体由 Phase 1 裁定）
- 新增 `DefaultPermissionMatrix`（实现 §5.3 channel×level 矩阵 + fail-closed for unknown/null）
- 新增 `DefaultDenialLedger`（纯内存 threshold-based 计数 + 暂停）
- 新增 `DefaultPostDenialGuard` 或直接将 `FingerprintPostDenialGuard` 提升为默认（Phase 1 裁定）
- `DefaultAgentEngine` 字段默认值/setter null 兜底切换
- `ReActAgentExecutor` 构造器 null 兜底切换
- `warnIfInsecureDefaults` 策略迁移（4 个组件 → always-checked）
- 新增 focused 测试 + 受影响既有测试处理
- `nop-ai-agent-security-and-permissions.md` 更新
- roadmap §5b 新增行

### Out Of Scope

- AgentExecutionContext 字段增强、DB-backed 默认、checker 逻辑增强（[13-5/6/7]）、设计 §5.1 表修订、Layer 4 组件。

## Execution Plan

### Phase 1 - Secure-Default 语义裁定与设计落档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Decision`

- [x] 裁定 **DefaultSecurityLevelResolver 激进程度**（关键）：设计 §5.1 表（`ISecurityLevelResolver` javadoc lines 12-19）对 `shell.exec` 标注 `highImpact → RESTRICTED`，而 DefaultLevelHintsProducer 对 shell.exec 产出 `highImpact=true`。完整实现表会导致 shell 工具被分类为 RESTRICTED → DefaultApprovalGate 拒绝 → 引擎不可用。须裁定以下之一或其他方案：(a) 完整实现表，接受 shell.exec 变为 RESTRICTED（需同步调整 DefaultApprovalGate 或豁免规则）；(b) trusted-by-default 变体——当 `trustedSource=true`（agent 自身推理链产生）时，highImpact 仅升级到 ELEVATED 而非 RESTRICTED，RESTRICTED 仅在 `!trustedSource && highImpact` 时触发；(c) 仅在 `!trustedSource` 时升级（highImpact 不单独触发 RESTRICTED）。裁定须写清安全语义与向后兼容理由。
- [x] 裁定 **DefaultPermissionMatrix fail-closed 语义**：`ctx.getChannelKind()` 在当前测试中可能返回 null。§5.3 表（`IPermissionMatrix` javadoc lines 9-18）对 unknown/null channel 标注"STANDARD only (fail-closed)"。须裁定：null channel 是否 fail-closed 到 STANDARD-only（保守，可能阻断 ELEVATED 操作），还是 fail-open 到 allow-all（向后兼容但 insecure）。裁定须与 resolver 裁定一致——若 resolver 不产出 ELEVATED/RESTRICTED（trusted-by-default），则 matrix 的 fail-closed 不可观测。
- [x] 裁定 **DefaultDenialLedger 行为**：threshold 值（设计 §6.2 = 3，`DBDenialLedger.DEFAULT_DENIAL_THRESHOLD`）、in-memory 数据结构（如 `ConcurrentHashMap<String, AtomicInteger>`）、anonymous session（null sessionId）处理、与 DBDenialLedger 的语义差异（非持久化）。裁定 switching 默认后既有测试中"多次 denied 但不期望暂停"的场景如何处理。
- [x] 裁定 **DefaultPostDenialGuard 命名**：(a) 直接将 FingerprintPostDenialGuard 提升为 engine 默认（field = `new FingerprintPostDenialGuard()`）；(b) 新建 DefaultPostDenialGuard 作为独立类（与 DefaultApprovalGate/DefaultToolAccessChecker 命名一致）。裁定须考虑命名一致性与避免不必要的 wrapper ceremony。
- [x] 裁定 **WARN 策略迁移**：4 个组件从 conditionally-checked 迁移到 always-checked 后，构造期 `warnIfInsecureDefaults` 调用（`DefaultAgentEngine.java:187-188`）需传非 null。裁定构造期是否需要对全部 4 个新 Default* 做 instanceof NoOp/PassThrough 检查（答案应为是——与 toolChecker/pathChecker/auditLogger/approvalGate 一致），以及 setter 调用是否仍只传自己刚设的组件（保持 noise control）。
- [x] 裁定 **向后兼容处理原则**：受影响测试的统一处理原则（显式 opt-in NoOp/PassThrough + 注释"测试需要 insecure 默认"，或适配新行为），不出现"为绕过测试而整体关闭防护"的回退。
- [x] 裁定 roadmap §5b 行创建/更新。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-security-and-permissions.md` 新增/扩展"Layer 2/3 全套 Secure 默认实现"小节，明确记录：每个组件选了什么 secure-default 语义、为什么选、拒绝了哪些替代方案（特别是 DefaultSecurityLevelResolver 的激进程度裁定）、向后兼容处理原则。
- [x] 设计文档不含类签名/伪代码（只记录决策与契约，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`（本模块无独立 owner doc 章节；如裁定需要，在此条注明具体文件）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现 Default* 类 + 切换 engine 默认 + WARN 迁移

Status: completed
Targets: `security/Default*.java`（新增）、`engine/DefaultAgentEngine.java`、`engine/ReActAgentExecutor.java`

- Item Types: `Fix`

- [x] 按 Phase 1 裁定实现 4 个 Default* 类（或 FingerprintPostDenialGuard 提升方案）。每个类实现对应接口的全部方法，无空方法体/静默跳过（Minimum Rules #24）。
- [x] 更新 `DefaultAgentEngine` 字段默认值（lines 101-102, 105-106）从 NoOp/PassThrough 切换到 Default*。
- [x] 更新 4 个 setter 的 null 兜底（lines 425-426, 452-454, 542-543, 572-574）从 NoOp/PassThrough 切换到 Default*。
- [x] 更新 `ReActAgentExecutor` 构造器 null 兜底（lines 196-213）从 NoOp/PassThrough 切换到 Default*。
- [x] 更新 `warnIfInsecureDefaults` 构造期调用（lines 187-188）：传非 null 给全部 4 个组件（迁移到 always-checked）。确认 instanceof 检查文案与新 Default* 默认一致（NoOp/PassThrough 实例仍触发 WARN，Default* 不触发）。
- [x] 更新 `warnIfInsecureDefaults` Javadoc（lines 207-242）：将 4 个组件从"Conditionally-checked"描述迁移到"Always-checked"。

Exit Criteria:

> 注：本 Phase 的接线验证与行为断言 Exit Criteria 由 Phase 3 的 focused 测试落地后一并满足。

- [x] 4 个 Default* 类存在于 `security/` 包，实现对应接口全部方法，无空方法体。
- [x] `DefaultAgentEngine` 字段默认值为 Default*（这 4 个字段声明行不再引用 NoOp/PassThrough）。
- [x] 4 个 setter null 兜底为 Default*。
- [x] `ReActAgentExecutor` 构造器 null 兜底为 Default*。
- [x] **接线验证（Minimum Rules #23）**：新 Default* 组件在运行时确实被调用——通过测试证明 `securityLevelResolver.resolve` / `permissionMatrix.check` / `denialLedger.recordDenial` / `postDenialGuard.checkBeforeDispatch` 的返回值影响 dispatch 行为。（Phase 3 focused 测试覆盖）
- [x] **无静默跳过（Minimum Rules #24）**：每个 Default* 类的方法体非空，在检测到风险条件时返回 deny/count/fingerprint-block 而非静默 allow/0/null。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试更新与新增 focused 验证 + 文档/roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Proof`、`Follow-up`

- [x] 审计既有测试受影响面：逐一确认哪些测试依赖 NoOp/PassThrough 行为（安全级别恒 STANDARD、permission 恒 allow、denial 不计数/不暂停、retry 不阻断），按 Phase 1 裁定原则统一处理。
- [x] 新增 focused 测试（Minimum Rules #25），覆盖以下行为点：
  - (1) **DefaultSecurityLevelResolver 分类**：按 Phase 1 裁定的规则表变体验证——trusted action → STANDARD/ELEVATED（裁定依赖）、untrusted+highImpact → RESTRICTED（裁定依赖）。
  - (2) **DefaultPermissionMatrix 矩阵**：按 §5.3 channel×level 表验证 allow/deny（含 null channel fail-closed 裁定）。
  - (3) **DefaultDenialLedger 计数/暂停**：recordDenial 累计计数 → threshold 暂停 → reset 清零。
  - (4) **DefaultPostDenialGuard 盲重试阻断**：recordDeniedAction → checkBeforeDispatch 返回 deny → reset 后重试通过。
  - (5) **WARN 迁移验证**：构造期使用 NoOp/PassThrough 实例时 4 个组件均触发 WARN（always-checked）；使用 Default* 时不触发。
  - (6) **端到端 dispatch 行为**：从 `new DefaultAgentEngine(...)` 入口，经 ReAct 循环，验证新 Default* 组件在 dispatch path 被调用并影响工具执行结果（如 untrusted action 被拒绝、多次 denied 后暂停、盲重试被阻断）。
- [x] roadmap §5b：新增本工作项行并标记状态。
- [x] `nop-ai-agent-security-and-permissions.md`：Phase 1 裁定已在 Phase 1 落档；本 Phase 确认文档与实现一致。

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（受影响测试已处理，新增测试已加入并覆盖上述 6 个行为点）。
- [x] 新增测试**显式列出**所验证的新行为（resolver-classification / matrix-allow-deny / ledger-count-pause / guard-block-retry / WARN-always-checked / end-to-end-dispatch），不是"原有测试通过"。
- [x] **端到端验证（Minimum Rules #22）**：至少一条测试从 `new DefaultAgentEngine(...)` 入口、经 ReAct 循环、到新 Default* 组件影响 dispatch 结果，完整路径走通。
- [x] roadmap §5b 已新增本工作项行并标记状态。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 4 个 Default* secure 实现存在并实现设计语义（live behavior 验证，非仅类型存在）。
- [x] Engine 默认（field + setter + ReActAgentExecutor 构造器）全部切换到 Default*。
- [x] NoOp/PassThrough 4 个组件保留为 public opt-in。
- [x] warnIfInsecureDefaults 4 个组件已迁移到 always-checked。
- [x] 受影响既有测试已一致处理，无"整体关闭防护以让测试通过"的回退。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect。
- [x] 受影响的 owner docs 已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）新 Default* 组件在运行时确实被 dispatch path 调用（不只是 field 赋值），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/200-nop-ai-agent-layer23-secure-default-impls.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 在本计划触碰文件中无 NEW high/critical findings。

## Deferred But Adjudicated

（暂无；本计划范围聚焦于 4 个 Default* 实现。Phase 1 裁定后若有 residual 再补充。）

## Non-Blocking Follow-ups

- **DefaultSecurityLevelResolver 的 crossesTrustBoundary 信号**：当前 DefaultLevelHintsProducer 对 crossesTrustBoundary 保守返回 false。若未来增强能产出该信号，DefaultSecurityLevelResolver 可据此进一步升级安全级别。（Classification: optimization candidate）
- **DefaultDenialLedger 的 sticky-pause recovery**：设计 §6.2 的 full sticky recovery protocol（人工干预恢复流程）是 deferred successor。本计划只实现 in-memory count + pause + reset。（Classification: out-of-scope improvement）

## Closure

Status Note: 4 个 Layer 2/3 安全组件的 engine 默认从 NoOp/PassThrough 切换为功能性 Default* 实现，使引擎开箱即用具备安全级别分类、通道权限矩阵、拒绝计数/暂停、盲重试阻断能力。NoOp/PassThrough 保留为 public opt-in。全部 1595 tests 通过（+32 new focused tests），独立 closure audit 验证 7 个 audit points 全部 PASS。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (explore, task_id ses_133b25986ffe3JS5jH7998g9Kx)
- Audit Session: ses_133b25986ffe3JS5jH7998g9Kx
- Evidence:
  - Point 1 (4 Default* exist with real logic): PASS — 4 files exist, each with real branching logic (resolver 7-way branch, matrix 6-way branch, ledger incrementAndGet + threshold check, guard delegates to fingerprint matching)
  - Point 2 (Engine defaults switched): PASS — field defaults lines 105/106/109/110 use Default*; 4 setter null fallbacks use Default*; construction warnIfInsecureDefaults passes non-null for all 4
  - Point 3 (ReActAgentExecutor constructor): PASS — 4 null fallbacks (lines 202/205/214/217) use Default*
  - Point 4 (NoOp/PassThrough retained): PASS — all 4 classes public final with public static factories
  - Point 5 (WARN always-checked): PASS — construction passes non-null; 4 instanceof checks (lines 288/297/304/311) fire WARN for NoOp/PassThrough
  - Point 6 (Anti-Hollow): PASS — (a) resolver has real 7-way branching, (b) matrix has real 6-way branching, (c) ledger increments + checks threshold, (d) guard delegates to real fingerprint matching
  - Point 7 (Test coverage): PASS — 5 test files exist with 32 tests total, including end-to-end dispatch tests verifying live behavior from new DefaultAgentEngine through ReAct loop
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: 1595 tests, 0 failures (PASS)
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high`: exit code 0, no NEW high/critical findings in plan-touched files
  - Deferred 项分类检查: 无 in-scope live defect 被降级到 deferred；2 个 Non-Blocking Follow-ups 均为 optimization candidate / out-of-scope improvement

Follow-up:

- **DefaultSecurityLevelResolver 的 crossesTrustBoundary 信号**：当前 DefaultLevelHintsProducer 保守返回 false。若未来增强能产出该信号，resolver 可据此进一步升级安全级别。（optimization candidate，非阻塞）
- **DefaultDenialLedger 的 sticky-pause recovery**：设计 §6.2 的 full sticky recovery protocol（人工干预恢复流程）是 deferred successor。本计划只实现 in-memory count + pause + reset。（out-of-scope improvement，非阻塞）
- Stale inline comments in ReActAgentExecutor (lines 818, 1176-1177, 1196, 1509-1510) reference old NoOp/PassThrough defaults — documentation-only, non-blocking cleanup candidate.
