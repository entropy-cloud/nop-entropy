# 199 nop-ai-agent Layer 2/3 默认收敛（Secure-by-Default Extension）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: [13-4]
> Last Reviewed: 2026-06-15
> Source: carry-over from `ai-dev/plans/193-nop-ai-agent-secure-by-default.md`（Non-Blocking Follow-ups 第二条 `[13-4]`，标 `successor plan required`）+ `ai-dev/plans/194-nop-ai-agent-audit-logger-default.md`（Non-Blocking Follow-ups 第一条 `[13-4]`，标 `successor plan required`）；`ai-dev/audits/2026-06-15-1146-deep-audit-nop-ai-agent/13-security-permission.md` 发现 [13-4]；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5b
> Related: `193`（交付 Layer 1 secure-by-default + `warnIfInsecureDefaults` WARN 框架，本计划扩展该框架）、`194`（扩展 WARN 框架覆盖 audit logger，将 `warnIfInsecureDefaults` 从 2-arg 扩展为 3-arg，本计划继续扩展）

## Purpose

把 nop-ai-agent 的 Layer 2/3 安全组件默认从"开箱全放行/全空操作"向更安全方向收敛。具体两件事：(1) 收紧 `AutoApproveGate` 使其不再无条件批准 RESTRICTED 级别的操作——作为 **defense-in-depth**，使任何注册了功能性 `ISecurityLevelResolver` 的集成商在 RESTRICTED 级别操作到达 approval gate 时不再被静默放行（注意：默认 `NoOpSecurityLevelResolver` 恒返回 `STANDARD`，因此收紧效果仅在集成商注册功能性 resolver 后可观测；本计划不创建功能性 resolver 默认实现，见 Non-Goals）；(2) 扩展 plan 193/194 已建立的 `warnIfInsecureDefaults` WARN 框架，覆盖剩余 5 个 Layer 2/3 NoOp/PassThrough 组件（`AutoApproveGate`、`NoOpSecurityLevelResolver`、`PassThroughPermissionMatrix`、`NoOpDenialLedger`、`PassThroughPostDenialGuard`），使安全降级可见。

> **WARN 策略关键区别**：plan 193/194 的 WARN 模式是"改默认为 secure，WARN 仅在显式 opt-in 回退时触发"。但本计划中 4 个组件（`NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard`）的默认**仍为 insecure**（Non-Goals 明确不改），因此直接 instanceof 检查会导致每次构造引擎都触发 WARN（噪音）。Phase 1 必须裁定这些 unchanged-insecure-default 组件的 WARN 策略（如：summary WARN 只在 setter 注入 NoOp 时触发、或构造期摘要式一次性 WARN、或仅覆盖行为已变更的 AutoApproveGate），不能照搬 193/194 的 opt-in WARN 模式。

## Current Baseline

基于 plans 193/194 交付物与 roadmap 核对（2026-06-15）：

- **`warnIfInsecureDefaults` 当前为 3-arg 签名**（plan 193 创建 2-arg 覆盖 `AllowAllToolAccessChecker`/`AllowAllPathAccessChecker`，plan 194 扩展为 3-arg 追加 `NoOpAuditLogger`）。该方法位于 `DefaultAgentEngine`，在构造期与 `setAuditLogger` setter 赋值后两处调用。Javadoc 显式标注 Layer 2/3 NoOp/PassThrough 组件的枚举为 successor 扩展点（[13-4]）。
- **5 个 Layer 2/3 组件以 NoOp/PassThrough 单例形式存在**（`security/` 包下，均采用 `INSTANCE` 单例 + `getInstance()` 模式）：
  - `AutoApproveGate.java` — Layer 2 审批门，无条件批准所有操作（含 RESTRICTED 级别）
  - `NoOpSecurityLevelResolver.java` — 不解析安全级别，恒返回默认值
  - `PassThroughPermissionMatrix.java` — 透传所有权限检查（全部 allow）
  - `NoOpDenialLedger.java` — 不记录拒绝事件
  - `PassThroughPostDenialGuard.java` — 透传 post-denial 检查（不阻止）
- **`DefaultAgentEngine` 通过 field+setter 模式装配 Layer 2/3 组件**（遵循 plan 194 确认的 `denialLedger`/`postDenialGuard`/`auditLogger` 等模式）：这些组件当前默认值为 NoOp/PassThrough/AutoApprove 实例，`resolveExecutor` 将它们透传到 `ReActAgentExecutor.Builder`。
- **`warnIfInsecureDefaults` 当前调用点有限**：仅在构造器与 `setAuditLogger` 两处被调用。Layer 2/3 的其他 setter（如 `setApprovalGate`/`setDenialLedger`/`setPermissionMatrix`/`setSecurityLevelResolver`/`setPostDenialGuard`）当前**不**调用 `warnIfInsecureDefaults`。Phase 1 需裁定这些组件的 WARN 触发时机。
- **默认配置下 RESTRICTED 级别不可达**：默认 `NoOpSecurityLevelResolver` 恒返回 `SecurityLevel.STANDARD`，dispatch 链以 STANDARD 调用 approval gate——RESTRICTED 永远不会到达 gate。AutoApproveGate RESTRICTED 收紧是 defense-in-depth：仅当集成商注册功能性 resolver 返回 RESTRICTED 时才可观测。
- **roadmap §5b**：`[13-4]` 在 plans 193/194 的 Non-Blocking Follow-ups 中标记为 carry-over / successor plan required，无 active plan 覆盖。roadmap §5b 表格中**尚无** `[13-4]` / `AUDIT-13-04` 行（需新增，非同步已有行）。

## Goals

- `AutoApproveGate`（或其 successor 默认实现）不再无条件批准 RESTRICTED 级别的操作——至少对 RESTRICTED 级别拒绝或要求显式批准（defense-in-depth：默认配置下因 `NoOpSecurityLevelResolver` 返回 STANDARD 不可观测，仅在集成商注册功能性 resolver 后生效）。
- `warnIfInsecureDefaults` 扩展为覆盖全部 Layer 2/3 NoOp/PassThrough 组件（`AutoApproveGate`/`NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard`）。WARN 策略须区分"行为已变更的组件"（AutoApproveGate）与"默认仍为 insecure 的组件"（其他 4 个），避免每次引擎构造触发噪音 WARN——具体策略由 Phase 1 裁定。
- 受影响的既有测试得到一致处理（显式 opt-in 不安全默认 + 注释，或适配新行为），不出现"为图省事全局关防护"的回退。
- roadmap §5b 新增 `[13-4]` / `AUDIT-13-04` 行并标记状态（当前无此行，需创建）。

## Non-Goals

- **为其他 4 个 Layer 2/3 组件创建全套 secure 默认实现**（如 `DefaultSecurityLevelResolver`/`DefaultPermissionMatrix`/`DefaultDenialLedger`/`DefaultPostDenialGuard`）——本计划只收紧 `AutoApproveGate` 行为 + 扩展 WARN 可见性；为其他组件创建 secure 替代实现是独立 successor。
- **[13-5]/[13-6]/[13-7]**（checker 内部逻辑增强：参数名同义词、递归嵌套、symlink 解析）——独立 optimization candidate。
- **AUDIT-09-x**（异常体系）、**AUDIT-14-x**（并发/原子写）等其他 P1——独立 work item，见 roadmap §5b。
- Layer 2/3 组件的持久化后端或远程服务集成——超出本计划范畴。
- 安全级别体系本身的重新设计——本计划复用现有 security level 语义。

## Scope

### In Scope

- `security/AutoApproveGate.java`（或新增 successor 默认类）：RESTRICTED 级别行为收紧。
- `engine/DefaultAgentEngine.java`：扩展 `warnIfInsecureDefaults` 覆盖 5 个 Layer 2/3 组件；如有默认装配变更（AutoApproveGate → 收紧后实现）则同步字段默认值/setter/`resolveExecutor` Builder 链。
- 新增 focused 测试：AutoApproveGate RESTRICTED 收紧行为、每个 Layer 2/3 组件的 WARN-on-insecure、no-WARN-on-secure、端到端 approval 决策。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`：记录 Layer 2/3 默认收敛决策与 WARN 扩展语义。
- roadmap §5b 新增 `[13-4]` / `AUDIT-13-04` 行并标记状态。

### Out Of Scope

- 全套 Layer 2/3 secure 默认实现（SecurityLevelResolver/PermissionMatrix/DenialLedger/PostDenialGuard 的 Default* 类）、checker 内部逻辑（[13-5/6/7]）、异常体系（AUDIT-09）、并发持久化（AUDIT-14）、持久化后端、安全级别体系重设计。

## Execution Plan

### Phase 1 - Layer 2/3 收紧策略裁定与设计落档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Decision`

- [x] 裁定 AutoApproveGate RESTRICTED 收紧策略：是否直接修改 `AutoApproveGate` 行为（使其对 RESTRICTED 级别拒绝或要求显式批准），还是引入新 `Default*` 类作为 engine 默认（保留 `AutoApproveGate` 供显式 opt-in，与 plan 193 `AllowAll*` 模式一致）。裁定需考虑向后兼容性与测试影响面。需显式落档：AutoApproveGate RESTRICTED 收紧是 **defense-in-depth**——默认 `NoOpSecurityLevelResolver` 恒返回 `STANDARD`，RESTRICTED 仅在集成商注册功能性 resolver 后到达 gate。
- [x] 裁定并落档 **WARN 噪音策略**（关键）：4 个组件（`NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard`）的默认仍为 insecure（Non-Goals 不改），因此不能照搬 plan 193/194 的"改默认为 secure + WARN 仅在 opt-in 回退时触发"模式。需裁定以下之一或其他方案：(a) 这些组件仅在 setter 被显式设为 NoOp/PassThrough 时触发 WARN（构造期默认值不触发）；(b) 构造期发出一条摘要式 WARN（合并列出所有 insecure 默认，而非逐组件 4 条）；(c) 仅覆盖行为已变更的 AutoApproveGate，其他 4 个推迟到创建 secure 默认实现后。裁定须写清噪音控制理由。
- [x] 裁定并落档 **WARN 调用点策略**：当前 `warnIfInsecureDefaults` 仅在构造器与 `setAuditLogger` 两处调用。Layer 2/3 其他 setter（`setApprovalGate`/`setDenialLedger`/`setPermissionMatrix`/`setSecurityLevelResolver`/`setPostDenialGuard`）当前不调用它。需裁定：是否在这些 setter 赋值后也调用 `warnIfInsecureDefaults`（参照 plan 194 `setAuditLogger` 模式），还是仅在构造期统一检查。裁定须与上一条的噪音策略一致。
- [x] 裁定并落档 WARN 签名重构方案：`warnIfInsecureDefaults` 如何从当前 3-arg 扩展为覆盖 5 个 Layer 2/3 组件——是否引入参数对象/列表，还是继续追加参数。WARN 文案（每个组件需点明降级风险 + 装配指引）。明确不引入新的 WARN 入口点，与 plan 193/194 的 WARN 统一在同一方法。
- [x] 裁定并落档向后兼容处理：受影响测试的统一处理原则（显式 opt-in 不安全默认 + 注释，或适配新行为），避免为绕过测试而整体关闭防护。
- [x] 裁定 roadmap §5b 行创建：roadmap §5b 当前无 `[13-4]` / `AUDIT-13-04` 行，Phase 3 需**新增**该行（非同步已有行）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-security-and-permissions.md` 新增（或扩展 §4.6/§4.7）"Layer 2/3 默认收敛"小节，明确记录：选了什么方案、为什么选、拒绝了哪些替代方案（如"仅 WARN 不改 AutoApproveGate"为何不采用）、向后兼容处理原则。
- [x] 设计文档不含类签名/伪代码（只记录决策与契约，遵循 Minimum Rules #14）。
- [x] No owner-doc update required for `docs-for-ai/`（本模块无独立 owner doc 章节；如裁定需要，在此条注明具体文件）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 实现 AutoApproveGate 收紧与 WARN 扩展

Status: completed
Targets: `security/AutoApproveGate.java`（或新 Default* 类）、`engine/DefaultAgentEngine.java`

- Item Types: `Fix`

- [x] 实现 Phase 1 裁定的 AutoApproveGate RESTRICTED 收紧（直接修改 `AutoApproveGate` 行为，或引入新默认类 + 更新 engine 默认装配），使 RESTRICTED 级别操作不再被无条件批准。
- [x] 扩展 `warnIfInsecureDefaults` 以覆盖 5 个 Layer 2/3 组件（`AutoApproveGate`/`NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard`），**按 Phase 1 裁定的噪音策略与调用点策略实现**（可能不在构造期对所有 unchanged-insecure-default 组件触发 WARN，而是仅在 setter 注入时或摘要式触发）。实现须 fail-loud（WARN 必须真实输出 via `LOG.warn`），不得静默吞掉或写成空方法体。
- [x] 如裁定变更 engine 默认装配（AutoApproveGate → 收紧后实现）：更新 `DefaultAgentEngine` 字段默认值/setter/`resolveExecutor` Builder 链以保持一致。

Exit Criteria:

> 注：本 Phase 的接线验证与 WARN 断言 Exit Criteria 由 Phase 3 的 focused 测试落地后一并满足。

- [x] AutoApproveGate（或 successor 默认实现）对 RESTRICTED 级别的操作不再无条件批准（可通过 focused 测试断言 RESTRICTED 级别被拒绝或要求显式批准）。
- [x] **接线验证（Minimum Rules #23）**：收紧后的 approval gate 在运行时确实被调用——通过测试证明 RESTRICTED 级别操作被拒绝或要求显式批准（需注入功能性 `ISecurityLevelResolver` 返回 RESTRICTED，因默认 `NoOpSecurityLevelResolver` 恒返回 STANDARD 使 RESTRICTED 不可达——这是 defense-in-depth 验证）。
- [x] **无静默跳过（Minimum Rules #24）**：每个新增 WARN 分支在检测到不安全实例时确实执行 `LOG.warn` 输出（测试用 log appender / 计数器断言 WARN 被触发）；不存在空方法体或被吞异常。
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` 已在 Phase 1 更新；本 Phase 不新增 owner-doc 变更。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试更新与新增 focused 验证 + 文档/roadmap 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/**`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`

- Item Types: `Proof`、`Follow-up`

- [x] 审计既有测试受影响面：逐一确认哪些测试依赖 AutoApproveGate 无条件批准 RESTRICTED 级别（或依赖其他 Layer 2/3 组件的 NoOp/PassThrough 行为），按 Phase 1 裁定原则统一处理。
- [x] 新增 focused 测试（Minimum Rules #25），覆盖以下行为点：
  - (1) **AutoApproveGate RESTRICTED 收紧**：收紧后的 approval gate 对 RESTRICTED 级别操作拒绝或要求显式批准（断言返回 deny/需批准结果而非 approve）。
  - (2) **非 RESTRICTED 正常批准**：收紧后的 approval gate 对 NORMAL/非 RESTRICTED 级别仍正常批准（不被 over-block）。
  - (3) **WARN 覆盖验证**：按 Phase 1 裁定的噪音策略，验证不安全组件被检测时 WARN 确实触发（具体覆盖范围取决于裁定：可能是 setter 注入时触发、或构造期摘要式触发、或仅 AutoApproveGate）——必须验证裁定方案下 WARN 真实输出且无意外噪音（默认构造不触发非预期 WARN）。
  - (4) **no-WARN-on-secure-default**：使用安全默认（收紧后的 AutoApproveGate / 非 NoOp 非 PassThrough 组件）时不触发 WARN。
  - (5) **端到端 approval 决策（defense-in-depth）**：从 `new DefaultAgentEngine(...)` 入口、注入功能性 `ISecurityLevelResolver`（返回 RESTRICTED）、经 ReAct 循环、到 RESTRICTED 级别操作被 approval gate 拦截，完整路径走通。注意：必须显式注入 resolver，因默认 `NoOpSecurityLevelResolver` 返回 STANDARD 使 RESTRICTED 不可达。
- [x] roadmap §5b：**新增** `[13-4]` / `AUDIT-13-04` 行并标记状态（当前无此行）。
- [x] `nop-ai-agent-security-and-permissions.md`：Phase 1 裁定已在 Phase 1 落档；本 Phase 确认文档与实现一致（如有偏差，同步修正）。

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿（受影响测试已处理，新增测试已加入并覆盖上述 5 个行为点）。
- [x] 新增测试**显式列出**所验证的新行为（restrict-RIGHTED-deny、non-RESTRICTED-allow、WARN-on-each-insecure-component、no-WARN-on-secure、end-to-end-approval），不是"原有测试通过"。
- [x] **端到端验证（Minimum Rules #22）**：至少一条测试从 `new DefaultAgentEngine(...)` 入口（注入功能性 `ISecurityLevelResolver` 返回 RESTRICTED）、经 ReAct 循环、到 RESTRICTED 级别操作被 approval gate 拦截，完整路径走通。
- [x] roadmap §5b 已**新增** `[13-4]` / `AUDIT-13-04` 行并标记状态。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] AutoApproveGate（或 successor 默认实现）对 RESTRICTED 级别操作不再无条件批准（live behavior 验证，非仅类型存在）。
- [x] `warnIfInsecureDefaults` 覆盖全部 5 个 Layer 2/3 NoOp/PassThrough 组件，WARN 按 Phase 1 裁定的噪音策略触发（真实输出被测试断言，且默认构造不产生非预期噪音 WARN）。
- [x] 受影响既有测试已一致处理，无"整体关闭防护以让测试通过"的回退。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（其他 Layer 2/3 secure 默认实现等已显式移入 Non-Goals/successor，属裁定移出而非隐藏）。
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）收紧后的 approval gate 在运行时确实被调用并拦截 RESTRICTED 操作（defense-in-depth 验证，需注入功能性 resolver 使 RESTRICTED 可达），（b）WARN 分支非空方法体/静默 no-op。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/199-nop-ai-agent-layer23-secure-defaults.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 在本计划触碰文件中无 NEW high/critical findings（closure audit 须记录完整扫描输出，区分 pre-existing 与本计划引入的新增）。

## Deferred But Adjudicated

（暂无；本计划范围窄，未发现需裁定的 residual。）

## Non-Blocking Follow-ups

- **Layer 2/3 全套 secure 默认实现**：为 `NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard` 创建 Default* secure 替代实现并切换 engine 默认。本计划只收紧 AutoApproveGate + 扩展 WARN 可见性；这些组件的 secure 默认是独立 successor。（Classification: successor plan required）
- **[13-5]/[13-6]/[13-7]**：checker 内部逻辑增强（参数名同义词、嵌套递归、symlink 解析）。（Classification: optimization candidate）

## Closure

Status Note: Plan 199 收敛了 nop-ai-agent Layer 2/3 安全组件默认——新增 `DefaultApprovalGate` 作为 engine 默认（STANDARD/ELEVATED 批准，RESTRICTED defense-in-depth 拒绝），保留 `AutoApproveGate` 为 public opt-in；`warnIfInsecureDefaults` 扩展为覆盖全部 5 个 Layer 2/3 组件，按噪音策略区分构造期与 setter 期检查。全部 1563 tests 全绿（1547 existing + 16 new），零回归。roadmap §5b AUDIT-13-04 已标记 ✅ 已修复。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session，explore agent，task_id `ses_133e3ca75ffe6CdX0XM4IH0NN3`）
- Evidence:
  - **Item 1 PASS**: `DefaultApprovalGate.java` lines 41–49 — RESTRICTED → `deny(OTHER, RESTRICTED_DENY_REASON)`，STANDARD/ELEVATED → `approve("default")`。Deny reason 非空多句。
  - **Item 2 PASS**: `DefaultAgentEngine.java` line 104 — field default = `new DefaultApprovalGate()`；line 514 — `setApprovalGate(null)` fallback = `new DefaultApprovalGate()`；lines 244–251 — `warnIfInsecureDefaults` 8-arg 覆盖全部 5 Layer 2/3 组件；lines 187–188 — constructor 传 4 个 null（噪音控制）；全部 6 个 setter 赋值后调用 WARN。
  - **Item 3 PASS**: `ReActAgentExecutor.java` lines 205–207 — Builder null fallback = `new DefaultApprovalGate()`。
  - **Item 4 PASS**: `AutoApproveGate.java` lines 35–40 — 行为未变（unconditional approve），javadoc 标注 opt-in。
  - **Item 5 PASS**: `warnIfInsecureDefaults` 全部 8 个 instanceof 分支均调用 `LOG.warn(...)` 带详细文案，无空方法体。null 参数安全跳过（`instanceof null` = false）。
  - **Item 6 PASS**: `TestDefaultApprovalGate` 6 tests（RESTRICTED-deny / STANDARD-allow / ELEVATED-allow / deny-kind-reason / approver / null-safety）+ `TestLayer23SecureDefaults` 10 tests（default-is-DefaultApprovalGate / WARN-on-AutoApproveGate-setter / WARN-on-4-NoOp-setters / no-WARN-on-construction / no-WARN-on-functional-setters / end-to-end-RESTRICTED-denied / end-to-end-RESTRICTED-audit-event）。
  - **Item 7 PASS**: `nop-ai-agent-roadmap.md` line 273 — AUDIT-13-04 行存在，标记 ✅ 已修复，引用 plan 199。
  - **Item 8 PASS**: `nop-ai-agent-security-and-permissions.md` §4.8 lines 239–269 — 全部 5 条裁定 + 3 条拒绝替代方案落档，无类签名/伪代码。
  - **Anti-Hollow Check**: (a) `endToEndRestrictedDeniedByDefaultApprovalGate` 证明 DefaultApprovalGate 在运行时被 ReAct dispatch loop 调用并拦截 RESTRICTED 操作（toolInvoked=false + "Approval denied" response）；(b) 全部 WARN 分支非空方法体（6 个 WARN-trigger 测试断言 LOG.warn 真实输出）。
  - **`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`**: 1563 tests, 0 failures, 0 errors, BUILD SUCCESS。
  - **`scan-hollow-implementations.mjs --severity high`**: 15 findings，全部 pre-existing（历史 plan 84/86/97/98 的 UOE stubs），本计划触碰文件中无 NEW findings（`DefaultApprovalGate.java` 0 findings，`DefaultAgentEngine.java` 唯一 finding `line 1531 plan mode UOE` 为 pre-existing）。
  - **Deferred 项分类检查**: 2 项 Non-Blocking Follow-ups（Layer 2/3 全套 secure 默认实现 = successor plan required；[13-5/6/7] checker 逻辑 = optimization candidate），均非 in-scope live defect 降级。

Follow-up:

- Layer 2/3 全套 secure 默认实现：为 `NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard` 创建 Default* secure 替代实现并切换 engine 默认（successor plan required）。
- [13-5]/[13-6]/[13-7]：checker 内部逻辑增强（参数名同义词、嵌套递归、symlink 解析）（optimization candidate）。

## Follow-up handled by 200-nop-ai-agent-layer23-secure-default-impls.md

Layer 2/3 全套 secure 默认实现（Non-Blocking Follow-ups 第一条，标 `successor plan required`）已由 successor plan `ai-dev/plans/200-nop-ai-agent-layer23-secure-default-impls.md` 接管：为 `NoOpSecurityLevelResolver`/`PassThroughPermissionMatrix`/`NoOpDenialLedger`/`PassThroughPostDenialGuard` 创建 Default* secure 替代实现并切换 engine 默认，同时将 `warnIfInsecureDefaults` 中这 4 个组件从 conditionally-checked 迁移到 always-checked。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
