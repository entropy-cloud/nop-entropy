# Guardrail & NoOp Baseline Contract

> Status: active
> Plan: 2056 (Guardrail Safety & NoOp Baseline)
> Last Updated: 2026-07-31

## Purpose

Document the minimum guard configuration required for production safety, and
classify every SPI interface in `nop-ai-agent` by implementation maturity.

## Minimum Production Safety Guards

The following guards **must** be non-NoOp for minimum production safety.
Constructing an engine with any of these in insecure-default mode emits a
**WARN** via `DefaultAgentEngine.warnIfInsecureDefaults()`.

| Guard | Secure Default | Insecure (WARN) | Why |
|-------|---------------|-----------------|-----|
| `IToolAccessChecker` | `DefaultToolAccessChecker` | `AllowAllToolAccessChecker` | Blocks dangerous tools (bash, write-file, etc.) |
| `IPathAccessChecker` | `DefaultPathAccessChecker` | `AllowAllPathAccessChecker` | Blocks sensitive paths (~/.ssh/, /etc/, .env) |
| `IAuditLogger` | `Slf4jAuditLogger` | `NoOpAuditLogger` | Records tool decisions; silent = no audit trail |
| `IDenialLedger` | `DefaultDenialLedger` | `NoOpDenialLedger` | Counts denials and pauses sessions on threshold |
| `IPostDenialGuard` | `DefaultPostDenialGuard` | `PassThroughPostDenialGuard` | Blocks blind retries of denied operations |
| `ISecurityLevelResolver` | `DefaultSecurityLevelResolver` | `NoOpSecurityLevelResolver` | Classifies operations by security level |
| `IPermissionMatrix` | `DefaultPermissionMatrix` | `PassThroughPermissionMatrix` | Enforces channel × level permissions |
| `IApprovalGate` | `DefaultApprovalGate` | `AutoApproveGate` | Defense-in-depth deny for RESTRICTED operations |

## SPI Interface Classification

Every SPI interface in `nop-ai-agent` is classified into one of:

- **production-ready**: production-grade implementation shipped
- **partial**: functional but non-persistent (in-memory) implementation
- **partial-with-implementation**: basic implementation that works but is not production-grade
- **no-production**: only NoOp or test-only implementation

### Production-Ready

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IRetryPolicy` | `StandardRetryPolicy` | Configurable retry with backoff |
| `ICheckpointManager` | `FileBackedCheckpointManager` / `DBCheckpointManager` | Persistent checkpoint recording |
| `ICircuitBreaker` | `ThresholdBreaker` | Configurable failure threshold |
| `ISustainer` | `SisypheanSustainer` | Configurable sustain rounds |

### Partial (In-Memory Only)

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `IWriteIntentRegistry` | `InMemoryWriteIntentRegistry` | Cross-process detection is a future successor |
| `IFencingTokenService` | `DefaultFencingTokenService` | Single-JVM; cross-process fencing is a successor |
| `IContributionRegistry` | `InMemoryContributionRegistry` | Contributions lost on JVM restart |

### Partial-With-Implementation

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `ISkillProvider` | `FileSystemSkillProvider` | Basic filesystem provider; no DB support |
| `ISkillCurator` | `LLMCurator` | Basic LLM-based curator; no rule-based mode |
| `IGoalTracker` | `SessionGoalTracker` | In-memory per-session; no persistent tracking |

### No-Production (NoOp Only)

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IBudgetProvider` | `NoOpBudgetProvider` | No production alternative exists. INFO-level awareness at construction. |

> `IContentGuardrail` no longer belongs here: a production-grade implementation
> (`PromptInjectionGuardrail`) is shipped (see `Production-Ready Content Guardrail`
> below). The shipped engine default remains `NoOpContentGuardrail` for backwards
> compatibility, so constructing an engine with the NoOp default still emits a
> **WARN** (there is now a production alternative to swap to — see
> `NoOp Awareness Design`).

### Production-Ready Content Guardrail

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IContentGuardrail` | `PromptInjectionGuardrail` | Regex-based detection of prompt_override / role_hijack / exfiltration / invisible_char (OpenSquilla taxonomy). Modes OFF/REPORT/ENFORCE. Shipped engine default is still `NoOpContentGuardrail`; `PromptInjectionGuardrail` is wired explicitly via `setContentGuardrail()`. |

## NoOp Awareness Design

Two checks fire at construction when NoOp-only defaults are detected. Their log
levels differ because a production alternative now exists for one but not the
other:

1. **NoOpContentGuardrail** → **WARN** (`AgentStartupWarnings.warnIfInsecureDefaults`):
   "No production implementation available for IContentGuardrail — content safety
   is not enforced." WARN is correct here because a production alternative
   (`PromptInjectionGuardrail`) **does** exist to swap to, so the warning has a
   remediation path.
2. **NoOpBudgetProvider** → **INFO**: "No production implementation available for
   IBudgetProvider — execution budget is unlimited." INFO remains correct here
   because no production alternative exists to swap to — WARN would create noise
   with no remediation path.

## NoOpSessionTakeoverLock Design Rationale

`NoOpSessionTakeoverLock` is **excluded** from INFO/WARN checks. Single-process
deployments rely on the engine's in-process `runningExecutions.putIfAbsent`
guard (plan 197). The takeover lock is incremental capability (NoOp → engine
walks existing path), not a security downgrade. The design decision is recorded
in the `NoOpSessionTakeoverLock` javadoc (lines 338-341).

## Recommended Minimum Guard Configuration for Production

```java
DefaultAgentEngine engine = DefaultAgentEngine.builder(chatService, toolManager)
    .toolAccessChecker(new DefaultToolAccessChecker())
    .pathAccessChecker(new DefaultPathAccessChecker())
    .auditLogger(new Slf4jAuditLogger())  // or custom DB logger
    .approvalGate(new DefaultApprovalGate())
    .securityLevelResolver(new DefaultSecurityLevelResolver())
    .permissionMatrix(new DefaultPermissionMatrix())
    .denialLedger(new DefaultDenialLedger())
    .postDenialGuard(new DefaultPostDenialGuard())
    .build();
```

All defaults above are already the engine's shipped defaults (constructor /
Builder), so the builder call without overrides satisfies the minimum
production guard configuration.

---

## 外部调研驱动的增量设计（2026-08-01：Guardrail 测试闭环 / Guideline 关系图 / BAIL）

> 来源：agent-survey（promptfoo Plugin+Grader / parlant Guideline 关系图+BAIL）。nop 已有运行时 guardrail 执行（security 6 层 + ContentOrigin），本节补"验收"与"关系建模"两个缺失维度。

### 增量 1：Guardrail 测试闭环（Plugin + Grader 分离）— final

> **Status: final**（W5-1，2026-08-02）。7 条裁定 A–G 已固化，接口契约可直接编码。

nop 有 guardrail 执行但无系统化测试。本节交付一个 **test-time 组件**（`GuardrailTestSuite`，非运行时），对标 promptfoo 的 Plugin（生成攻击）+ Grader（rubric 打分）分离范式，对既有 `IContentGuardrail` 做红队验收。组件树：

```
guardrail-test（测试时组件，非运行时；不调用真实 LLM / 真实 agent session）：
  ├── AttackPlugin（声明式语料提供者，返回 AttackCase 列表）
  │   ├── 分类语料：prompt_injection / role_hijack / exfiltration / jailbreak /
  │   │              hallucination / invisible_char / privilege_escalation / 行业垂直集
  │   └── AttackTransform（策略层装饰器）：base64 / crescendo 二次变换（绕过简单防御）
  ├── GuardrailGrader（确定性 rubric 判定：GuardrailResult 三态 + expectedBehavior → 判定态）
  └── GuardrailTestReport（可度量、可回归：拦截率 / 漏报率 / 误报率 / per-category）
```

> 替代旧草案中的 `└── Nunjucks 式 rubric 模板（Java: TemplateRenderer）` 行。
> `TemplateRenderer` 在 nop-ai 中不存在；首版按裁定 C 采用结构化确定性判定，
> rubric 模板留 successor。

#### 裁定 A — 组件归属与模块边界

`GuardrailTestSuite` 放在 **`nop-ai-agent` main source 的 `io.nop.ai.agent.guardrail.test` 包**（非独立模块、非 `src/test`）。理由：

- 它是测试时库，需被消费方（含下游模块的测试）引用——main source 中的测试时库是既定惯例（类 JUnit support lib）；放在 `src/test` 则不能跨模块引用（test 类不进入发布产物）。
- 不污染运行时类路径语义：运行时执行路径（`AgentPromptAssembly` 的 INPUT/OUTPUT 调用点）只调 `IContentGuardrail.check()`，从不引用 `guardrail.test` 包；该包是纯验收工具。
- 与 `nop-ai-agent-eval-design.md`（草案）正交：eval 走真实 agent session + LLM-judge 测 ReAct 行为；本套件对 `IContentGuardrail.check()` 做纯函数式拦截验收（无 LLM、无 session）。两者目标不同、共存不冲突。

#### 裁定 B — AttackPlugin 契约形态（声明式语料提供者）

AttackPlugin 是**声明式语料提供者**：返回静态 `AttackCase` 列表（确定性回归）。LLM 动态生成留 successor（已在 Deferred 裁定为 non-blocking）。

`AttackCase` 数据模型字段（契约）：

| 字段 | 类型 | 语义 |
|------|------|------|
| `id` | String | 稳定用例标识（如 `pi-001`），用于断言与回归快照 |
| `category` | String | 攻击类别（如 `prompt_injection`、`role_hijack`） |
| `threatClass` | String | OWASP LLM 威胁分类标签（如 `LLM01_prompt_injection`），用于 per-category 度量 |
| `payload` | String | 攻击/对照文本本身 |
| `direction` | `GuardrailDirection` | 测试方向（`INPUT` / `OUTPUT`），默认 `INPUT`；既支持用户注入也支持 LLM 回显 |
| `expectedBehavior` | `ExpectedBehavior` | 期望：`BLOCK`（攻击应被拦截）/ `PASS`（良性应被放行） |
| `description` | String | 可选人类可读说明 |
| `transform` | String | 可选已施加变换标记（`base64` / `crescendo`），base 用例为 null |

`AttackPlugin` SPI 契约：`name()` → 插件标识；`cases()` → 该插件提供的 `AttackCase` 列表（确定性、可重复）。

#### 裁定 C — GuardrailGrader 确定性 rubric 判定（结构化，非模板）

Grader 对 `GuardrailResult`（Pass/Block/Modify 三态）+ `AttackCase.expectedBehavior` 做**确定性结构化判定**，不使用模板渲染：

| expectedBehavior | 实际 GuardrailResult | 判定 |
|------------------|----------------------|------|
| `BLOCK`（攻击） | `BlockResult` | `PASS`（正确拦截） |
| `BLOCK`（攻击） | `PassResult` | `FAIL`（漏报 leak） |
| `BLOCK`（攻击） | `ModifyResult` | `PARTIAL`（改写式拦截，半通过） |
| `PASS`（良性） | `PassResult` | `PASS`（正确放行） |
| `PASS`（良性） | `BlockResult` | `FAIL`（误报 false positive） |
| `PASS`（良性） | `ModifyResult` | `PARTIAL`（修改了良性内容） |

`GuardrailGrader` SPI 契约：`grade(AttackCase, GuardrailResult) → GradeResult`。`GradeResult` 字段：`verdict`（`PASS`/`FAIL`/`PARTIAL`）、`caseId`、`actual`（实际 GuardrailResult）、`reason`。默认实现 `DefaultGuardrailGrader` 按上表判定。rubric 模板（Nunjucks/`TemplateRenderer`）留 successor。

#### 裁定 D — GuardrailTestReport 度量维度（可度量、首版非硬门禁）

`GuardrailTestReport`（不可变结果对象）度量集：

| 度量 | 定义 |
|------|------|
| `blockRate`（拦截率） | 攻击被 Block 的比例（`BLOCK` 期望中 verdict=PASS 的比例） |
| `leakRate`（漏报率） | 攻击被放行的比例（`BLOCK` 期望中 verdict=FAIL 的比例） |
| `falsePositiveRate`（误报率） | 良性被 Block 的比例（`PASS` 期望中 verdict=FAIL 的比例） |
| per-`category` / per-`threatClass` | 分类别同上三率（用于定位哪类威胁拦截薄弱） |
| per-case `CaseResult` 详情 | 每条用例的 verdict + actual，供回归快照 |

**CI 门禁形态裁定**：首版为**可度量记录**（report 提供具体数值可断言），不内置硬 fail-fast 阈值。门禁阈值策略由消费方裁定（见 Non-Blocking Follow-ups 的 CI 门禁自动化）。

#### 裁定 E — 策略层 payload 变换（AttackCase 装饰器）

策略层（base64 / crescendo）实现为 `AttackTransform` 装饰器：`name()` → 变换名；`apply(AttackCase) → AttackCase`，产出变换后变体（新 payload、`expectedBehavior` 不变——变换不改变攻击性质 → 仍应 Block、`transform` 标记设为变换名）。变换后变体**走完整 guardrail→grader 链**（不旁路），验证 guardrail 对变换后攻击的鲁棒性。

#### 裁定 F — 语料库组织形式（外部声明式 YAML）

60+ 攻击用例以**外部声明式 YAML 语料文件**存储（`src/main/resources` 下按类别组织，可 git diff、可 Delta 定制、确定性回归）。`CorpusLoader` 用 nop 既有工具（`ResourceHelper` + `JsonTool.parseYaml`，与 `FileSystemSkillProvider` 同构）加载 YAML → `AttackCase` 列表。YAML 根为语料列表，每个元素字段对齐 `AttackCase`（裁定 B）。

#### 裁定 G — SPI 契约汇总（可直接编码）

四个组件的 I/O 契约（消除 Phase 1→Phase 2 断层，Phase 2 executor 不需自行发明 SPI 形态）：

| 组件 | 入口 | 输入 → 输出 |
|------|------|-------------|
| `AttackPlugin` | `cases()` | 无参 → `List<AttackCase>`（确定性语料） |
| `AttackTransform` | `apply(case)` | `AttackCase` → `AttackCase`（变换变体，expectedBehavior 不变） |
| `GuardrailGrader` | `grade(case, result)` | `(AttackCase, GuardrailResult)` → `GradeResult`（确定性判定） |
| `GuardrailTestSuite` | `run(...)` | `(IContentGuardrail, List<AttackPlugin>, List<AttackTransform>, AgentExecutionContext)` → `GuardrailTestReport` |
| `GuardrailTestReport` | （不可变值对象） | 度量字段见裁定 D |

**Orchestrator 数据流**（端到端）：Plugin 造攻击（+ Transform 生成变体）→ 调既有 `IContentGuardrail.check(direction, payload, ctx)` → Grader 判定 → 聚合 `GuardrailTestReport`。**无静默跳过**：guardrail 返回 null 抛错（非跳过该用例）；不调用真实 LLM / 真实 agent session（guardrail 是纯函数式检查）。

- 复用 promptfoo 60+ 攻击类型为测试语料库（声明式静态形态）
- 与运行时 guardrail 的关系：Plugin 造攻击 → nop guardrail 拦截 → Grader 判定拦截效果
- 形成"建设（运行时，已有）+ 验收（本套件）"闭环（执行已有，补验收）

### 增量 2：Guideline 依赖/排除关系图（规则关系建模）

nop guardrail 当前是线性检查链。增加规则间关系（对标 parlant RelationalResolver）：

```
GuardrailRule（规则定义增加关系）：
  - dependsOn: 命中 A 自动拉入 B（上下文收敛）
  - excludes:  命中 A 排除 C（上下文收窄）
  - 关系图使规则靠结构收敛，而非 LLM 注意力
```

适用：企业合规复杂规则集（多规则冲突时靠结构决策）。

### 增量 3：BAIL 中断语义（硬阻断）— final

> **Status: final**（W5-3 已落地，2026-08-02）。详细架构决策见 `nop-ai-agent-middleware-design.md` §5.4。

nop 拦截是"改写/拒绝"，增加 BAIL 语义（对标 parlant hooks.py + grok-build GateKind Stop）：

- `BailResult`（`HookResult` 第四态）：中断并**不作用**于当前响应。仅在 `POST_REASONING` / `POST_CALL` 两个 POST 点有效。
- 与 nop 现有 middleware 的关系：作为 middleware 链可返回的硬阻断决策态。区别于：
  - `VetoResult`（PRE 侧拒绝、core 不执行）——POST 侧 core 已执行，Veto 语义不适用
  - `GuardrailResult.ModifyResult`（改写式拦截、修改响应内容）——改写是放行，BAIL 是不作用
  - `promptAssembly.checkOutputGuardrail`（硬编码丢弃、仅 promptAssembly 可触发）——BAIL 是**通用机制**（任意中间件可返回），`checkOutputGuardrail` 作为内置快速路径共存（§5.4 裁定 A）

#### POST_REASONING vs POST_CALL BAIL 语义

| 触发点 | 语义 | 效果 |
|--------|------|------|
| `POST_REASONING` | 不作用于此轮响应 + 重新提示 | 跳过该轮 tool_calls 执行 + 不视为最终答案 + `continue`（消耗 iteration）+ per-request bail cap（超限 fail-loud） |
| `POST_CALL` | 结果阻断状态 | `AgentExecutionResult.bailReason` 标记阻断；流式已发 chunk 不可撤回（显式限制）；`EXECUTION_COMPLETED` payload 加 `guardrailBlocked` 标记 |

消息/checkpoint/session-store 在 POST_REASONING 前已落账——BAIL 是"不作用 + 重新提示"，非"未发生"（与 `checkOutputGuardrail` 同边界）。

### 与 hook-skill-engine 的边界

- guardrail-contract：规则定义 + 测试闭环 + 关系建模（本篇）
- hook-skill-engine：12 生命周期点 + skill 加载（已存在）
- 关系：BAIL 由 hook 生命周期点触发，规则评估在 security 层
